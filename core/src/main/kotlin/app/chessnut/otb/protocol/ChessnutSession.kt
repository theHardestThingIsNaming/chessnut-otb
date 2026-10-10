package app.chessnut.otb.protocol

import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

fun interface ByteLog {
    fun line(text: String)

    companion object {
        val None = ByteLog { }
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

/**
 * A connected Chessnut classic-profile board.
 *
 * Writes go to the operation characteristic, at least [writeGap] apart.
 * Frames from the response and file characteristics are what [read] returns.
 * Position frames from the FEN characteristic update [livePlacement] while the
 * board is in realtime mode; after `21 01 01` they are handed to [read] too,
 * because no capture shows which characteristic carries stored-game frames.
 */
class ChessnutSession(
    private val link: GattLink,
    private val scope: CoroutineScope,
    private val writeGap: Duration = 200.milliseconds,
    private val readTimeout: Duration = 5.seconds,
    private val log: ByteLog = ByteLog.None,
) : BoardTransport {
    private val replies = Channel<ByteArray>(Channel.UNLIMITED)
    private val live = MutableStateFlow<String?>(null)
    val livePlacement: StateFlow<String?> = live.asStateFlow()

    private val buffers = mutableMapOf<UUID, ByteArray>()
    private val battery = MutableStateFlow<Int?>(null)
    /** Percent 0–100, or null until the board answers `29 01 00`. */
    val batteryPercent: StateFlow<Int?> = battery.asStateFlow()
    private val writeLock = Mutex()
    private var lastWrite: TimeSource.Monotonic.ValueTimeMark? = null
    private var collector: Job? = null

    @Volatile
    private var uploadMode = false

    var fileNotifyAvailable: Boolean = false
        private set

    suspend fun open(address: String) {
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            link.notifications.collect { onNotification(it) }
        }
        log.line("connect $address")
        link.connect(address)
        link.enableNotify(ChessnutUuids.FEN_SERVICE, ChessnutUuids.FEN_NOTIFY)
        link.enableNotify(ChessnutUuids.OP_SERVICE, ChessnutUuids.OP_RESPONSE)
        fileNotifyAvailable = try {
            link.enableNotify(ChessnutUuids.FILE_SERVICE, ChessnutUuids.FILE_NOTIFY)
            true
        } catch (e: GattException) {
            log.line("file characteristic unavailable: ${e.message}")
            false
        }
        log.line("subscribed (file notify ${if (fileNotifyAvailable) "on" else "off"})")
    }

    suspend fun startLive() = write(REALTIME)

    /** Lights the given squares and turns the rest off. Empty clears every LED. */
    suspend fun setLeds(squares: Collection<String>) = write(LedCodec.encode(squares))

    /** Sounds the board buzzer. Frequency is Hz, duration is milliseconds. */
    suspend fun beep(frequencyHz: Int, durationMs: Int) = write(LedCodec.beep(frequencyHz, durationMs))

    suspend fun requestBattery() = write(BATTERY)

    /** Lights every square, and beeps once when [sound] is on, so a connect is obvious. */
    suspend fun announceConnected(sound: Boolean = true) {
        setLeds(LedCodec.ALL)
        if (sound) beep(1000, 180)
        delay(if (sound) 400 else 250)
        setLeds(emptyList())
    }

    override suspend fun write(bytes: ByteArray) {
        writeLock.withLock {
            val previous = lastWrite
            if (previous != null) {
                val wait = writeGap - previous.elapsedNow()
                if (wait.isPositive()) delay(wait)
            }
            if (bytes.size >= 3 && bytes[0] == 0x21.toByte() && bytes[1] == 0x01.toByte()) {
                uploadMode = bytes[2] == 0x01.toByte()
            }
            log.line("-> ${ChessnutUuids.shortName(ChessnutUuids.OP_WRITE)} ${bytes.toHex()}")
            link.write(ChessnutUuids.OP_SERVICE, ChessnutUuids.OP_WRITE, bytes)
            lastWrite = TimeSource.Monotonic.markNow()
        }
    }

    override suspend fun read(): ByteArray =
        withTimeoutOrNull(readTimeout) { replies.receive() }
            ?: throw ProtocolException("The board sent nothing for ${readTimeout.inWholeSeconds} s.")

    suspend fun close() {
        collector?.cancel()
        collector = null
        runCatching { link.disconnect() }
        log.line("disconnected")
    }

    private fun onNotification(notification: GattNotification) {
        val source = notification.characteristic
        log.line("<- ${ChessnutUuids.shortName(source)} ${notification.value.toHex()}")
        for (frame in split(source, notification.value)) {
            val isPosition = frame[0] == 0x01.toByte()
            if (frame.size >= 3 && frame[0] == 0x2A.toByte() && frame[1] == 0x02.toByte()) {
                battery.value = frame[2].toInt() and 0x7F
            }
            if (source == ChessnutUuids.FEN_NOTIFY && isPosition && !uploadMode) {
                PositionCodec.decodePlacement(frame)?.let { live.value = it }
            } else if (frame[0] != 0x2A.toByte()) {
                replies.trySend(frame)
            }
        }
    }

    /** Frames are `[opcode, length, payload...]` and may be split or joined across notifications. */
    private fun split(source: UUID, value: ByteArray): List<ByteArray> {
        var buffer = (buffers.remove(source) ?: ByteArray(0)) + value
        val frames = mutableListOf<ByteArray>()
        while (buffer.size >= 2) {
            val size = (buffer[1].toInt() and 0xFF) + 2
            if (buffer.size < size) break
            frames += buffer.copyOfRange(0, size)
            buffer = buffer.copyOfRange(size, buffer.size)
        }
        if (buffer.isNotEmpty() && buffer.size <= MAX_PARTIAL) buffers[source] = buffer
        return frames
    }

    private companion object {
        const val MAX_PARTIAL = 512
        val REALTIME = byteArrayOf(0x21, 0x01, 0x00)
        val BATTERY = byteArrayOf(0x29, 0x01, 0x00)
    }
}
