package app.chessnut.otb.protocol

import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf

data class FoundBoard(val address: String, val name: String, val rssi: Int?)

class GattNotification(val characteristic: UUID, val value: ByteArray)

class GattException(message: String) : Exception(message)

/**
 * The GATT operations the Chessnut protocol needs, and nothing else.
 * Android's `BluetoothGatt`, the BLEForEmulator bridge, and the fake board
 * all implement it.
 */
interface GattLink {
    val notifications: Flow<GattNotification>

    fun scan(): Flow<FoundBoard>

    suspend fun connect(address: String)

    /** Fails with [GattException] when the board does not expose that characteristic. */
    suspend fun enableNotify(service: UUID, characteristic: UUID)

    suspend fun write(service: UUID, characteristic: UUID, bytes: ByteArray)

    suspend fun disconnect()
}

object ChessnutUuids {
    private fun uuid(short: String): UUID = UUID.fromString("1b7e$short-2877-41c3-b46e-cf057c562023")

    val FEN_SERVICE: UUID = uuid("8261")
    val FEN_NOTIFY: UUID = uuid("8262")
    val OP_SERVICE: UUID = uuid("8271")
    val OP_WRITE: UUID = uuid("8272")
    val OP_RESPONSE: UUID = uuid("8273")
    val FILE_SERVICE: UUID = uuid("8281")
    val FILE_NOTIFY: UUID = uuid("8283")

    fun shortName(characteristic: UUID): String = when (characteristic) {
        FEN_NOTIFY -> "fen"
        OP_WRITE -> "op-write"
        OP_RESPONSE -> "op-response"
        FILE_NOTIFY -> "file"
        else -> characteristic.toString()
    }

    fun isClassicBoard(name: String?): Boolean {
        val n = name?.trim()?.trim('\n', '\r') ?: return false
        if (n.equals("Chessnut Move", ignoreCase = true)) return false
        return n.startsWith("Chessnut", ignoreCase = true)
    }

    /** Advertisements sometimes omit the local name; the Chessnut GATT UUID is enough. */
    fun isClassicBoard(name: String?, serviceUuids: List<String>): Boolean {
        if (isClassicBoard(name)) return true
        return serviceUuids.any { uuid ->
            uuid.contains("1B7E", ignoreCase = true) &&
                uuid.contains("2877-41C3-B46E-CF057C562023", ignoreCase = true)
        }
    }
}

/**
 * Test double that records writes and lets a test push notifications.
 * [onWrite] can answer a write the way a board would.
 */
open class FakeGattLink : GattLink {
    val written: MutableList<ByteArray> = mutableListOf()
    val subscribed: MutableList<UUID> = mutableListOf()
    var missingCharacteristics: Set<UUID> = emptySet()
    var connectedAddress: String? = null
        private set

    var onWrite: suspend FakeGattLink.(ByteArray) -> Unit = {}

    private val flow = MutableSharedFlow<GattNotification>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val notifications: SharedFlow<GattNotification> = flow.asSharedFlow()

    open val boards: List<FoundBoard> = listOf(FoundBoard("FA:KE:00:00:00:01", "Chessnut Air (simulated)", -40))

    override fun scan(): Flow<FoundBoard> = flowOf(*boards.toTypedArray())

    override suspend fun connect(address: String) {
        connectedAddress = address
    }

    override suspend fun enableNotify(service: UUID, characteristic: UUID) {
        if (characteristic in missingCharacteristics) throw GattException("No characteristic $characteristic")
        subscribed += characteristic
    }

    override suspend fun write(service: UUID, characteristic: UUID, bytes: ByteArray) {
        written += bytes.copyOf()
        onWrite(bytes)
    }

    override suspend fun disconnect() {
        connectedAddress = null
    }

    suspend fun emit(characteristic: UUID, bytes: ByteArray) {
        flow.emit(GattNotification(characteristic, bytes.copyOf()))
    }
}
