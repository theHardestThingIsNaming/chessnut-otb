package app.chessnut.otb.board

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.chessnut.otb.OtbApp
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.live.LiveEnd
import app.chessnut.otb.live.LiveEvent
import app.chessnut.otb.live.LiveGameTracker
import app.chessnut.otb.live.LiveSnapshot
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.protocol.ByteLog
import app.chessnut.otb.protocol.ChessnutSession
import app.chessnut.otb.protocol.FoundBoard
import app.chessnut.otb.protocol.GattLink
import app.chessnut.otb.protocol.LedCodec
import app.chessnut.otb.protocol.StoredBoardGame
import app.chessnut.otb.protocol.StoredGameReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BoardUiState(
    val optionIndex: Int = 0,
    val scanning: Boolean = false,
    val needsPermission: Boolean = false,
    val found: List<FoundBoard> = emptyList(),
    val connected: FoundBoard? = null,
    val busy: String? = null,
    val message: String? = null,
    val livePlacement: String? = null,
    val live: LiveSnapshot = LiveSnapshot(),
    val lastSavedGameId: String? = null,
    val batteryPercent: Int? = null,
    val log: List<String> = emptyList(),
)

/**
 * Drives the hardware milestones one at a time: live position, count only,
 * one game, all games, and (only on request) read-and-clear. Every byte in
 * and out is kept in the log and written to `files/logs`.
 */
class BoardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OtbApp
    val options: List<LinkOption> = BoardLinks.options(application)

    private val state = MutableStateFlow(BoardUiState())
    val ui: StateFlow<BoardUiState> = state.asStateFlow()

    private val logFile: File = File(application.filesDir, "logs").let {
        it.mkdirs()
        File(it, "board-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt")
    }
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val log = ByteLog { text ->
        val line = "${clock.format(Date())} $text"
        state.update { it.copy(log = (it.log + line).takeLast(MAX_LOG)) }
        runCatching { logFile.appendText(line + "\n") }
    }

    private var link: GattLink? = null
    private var session: ChessnutSession? = null
    private var pausedForPlay = false
    private var scanJob: Job? = null
    private var liveJob: Job? = null
    private var settleJob: Job? = null
    private var tickJob: Job? = null
    private var lightsJob: Job? = null
    private var batteryJob: Job? = null
    private var batteryPoll: Job? = null
    private val tracker = LiveGameTracker(TimeControl.fromLabel(app.settings.getString(KEY_TIME, TimeControl.Default.label).orEmpty()))
    private val sounds = BoardSounds()
    private val speech = MoveSpeaker(application)
    private var spokenPlies = 0

    val selected: LinkOption get() = options[state.value.optionIndex]

    init {
        autoConnect()
    }

    fun select(index: Int) {
        if (index == state.value.optionIndex || state.value.busy != null) return
        disconnect()
        link = null
        state.update { it.copy(optionIndex = index, found = emptyList(), message = null) }
        autoConnect()
    }

    /** Looks for the last Chessnut Air, and connects when it is on. */
    fun autoConnect() {
        if (state.value.connected != null || state.value.busy != null) return
        if (selected.needsPermissions && !bluetoothGranted()) {
            state.update { it.copy(needsPermission = true, scanning = false, message = null) }
            return
        }
        state.update { it.copy(needsPermission = false) }
        scan(connectKnown = true)
    }

    private fun scan(connectKnown: Boolean = false) {
        scanJob?.cancel()
        val current = link ?: selected.create(log).also { link = it }
        state.update { it.copy(scanning = true, found = emptyList(), message = null) }
        log.line("scan with ${selected.label}")
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_TIME) {
                    current.scan().collect { board ->
                        state.update { s ->
                            if (s.found.any { it.address == board.address }) s else s.copy(found = s.found + board)
                        }
                        if (connectKnown && state.value.connected == null && board.address == savedAddress()) {
                            connect(board)
                        }
                    }
                }
                if (state.value.connected == null) {
                    val found = state.value.found
                    val saved = savedAddress()
                    when {
                        connectKnown && saved == null && found.size == 1 -> connect(found.first())
                        found.isEmpty() -> state.update {
                            it.copy(message = "No Chessnut board found. Is it switched on and not connected to another app?")
                        }
                        connectKnown && saved != null && found.none { it.address == saved } -> state.update {
                            it.copy(message = "${savedName()} isn't on.")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            } finally {
                state.update { it.copy(scanning = false) }
            }
        }
    }

    fun connect(board: FoundBoard) {
        scanJob?.cancel()
        val current = link ?: return
        run("Connecting") {
            val opened = ChessnutSession(current, viewModelScope, log = log)
            opened.open(board.address)
            session = opened
            app.attachBoard(opened)
            tracker.reset()
            spokenPlies = 0
            state.update { it.copy(lastSavedGameId = null) }
            liveJob = viewModelScope.launch {
                opened.livePlacement.collect { p ->
                    state.update { it.copy(livePlacement = p) }
                    val placement = p ?: return@collect
                    app.notePlacement(placement)
                    if (app.playEngaged) return@collect
                    // A slide reports every square the piece crosses. Wait until it stops.
                    settleJob?.cancel()
                    settleJob = viewModelScope.launch {
                        delay(200)
                        if (app.playEngaged) return@launch
                        val now = System.currentTimeMillis()
                        val event = tracker.onPlacement(placement, now)
                        publishLive(now)
                        cue(event)
                        when (event) {
                            is LiveEvent.Finished -> persistLive(event)
                            is LiveEvent.Move -> log.line("live ${event.san}")
                            is LiveEvent.Illegal -> log.line("live illegal")
                            LiveEvent.Ignored -> Unit
                        }
                    }
                }
            }
            startTicks()
            opened.startLive()
            opened.announceConnected(sound = app.boardSounds)
            batteryJob = viewModelScope.launch {
                opened.batteryPercent.collect { percent ->
                    state.update { it.copy(batteryPercent = percent) }
                }
            }
            batteryPoll = viewModelScope.launch {
                while (isActive) {
                    delay(60_000)
                    runCatching { opened.requestBattery() }
                }
            }
            runCatching { opened.requestBattery() }
            remember(board)
            state.update { it.copy(connected = board, message = null, needsPermission = false) }
        }
    }

    private fun remember(board: FoundBoard) {
        app.settings.edit()
            .putString(KEY_BOARD_ADDRESS, board.address)
            .putString(KEY_BOARD_NAME, board.name.trim())
            .apply()
    }

    private fun savedAddress(): String? = app.settings.getString(KEY_BOARD_ADDRESS, null)?.takeIf { it.isNotBlank() }

    private fun savedName(): String = app.settings.getString(KEY_BOARD_NAME, null)?.takeIf { it.isNotBlank() } ?: "Your board"

    private fun bluetoothGranted(): Boolean {
        val names = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return names.all { ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED }
    }

    fun countGames() = withSession("Asking for the game count") { s ->
        val count = StoredGameReader(s).count()
        s.startLive()
        "The board reports $count stored game${if (count == 1) "" else "s"}."
    }

    fun readOne() = read(limit = 1, clear = false)

    fun readAll() = read(limit = Int.MAX_VALUE, clear = false)

    /** Sends `39 01 00` after each game is saved here. Only offered behind a confirmation. */
    fun readAllAndClear() = read(limit = Int.MAX_VALUE, clear = true)

    private fun read(limit: Int, clear: Boolean) = withSession(if (clear) "Reading and clearing" else "Reading games") { s ->
        var added = 0
        var seen = 0
        val result = StoredGameReader(s).readGames(limit = limit, clearAfterSave = clear) { game ->
            seen++
            if (save(game)) added++
        }
        buildString {
            append("Board has ${result.boardCount}. Read $seen, $added new.")
            if (result.repeatedGame) {
                append(" The board sent the same game twice, so it does not move on without the delete command.")
            }
        }
    }

    private fun save(game: StoredBoardGame): Boolean {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(now))
        val saved = SavedGame.fromBoard(game.placements, now, date)
        log.line("game ${saved.id.take(8)}: ${game.placements.size} positions, ${saved.sanMoves.size} moves, complete=${saved.isComplete} ${saved.stopReason.orEmpty()}")
        val isNew = app.store.addIfNew(saved)
        app.refreshGames()
        if (isNew && saved.sanMoves.isNotEmpty()) {
            app.analyse(saved.id)
            app.autoUploadToLichess(saved)
        }
        return isNew
    }

    fun setTimeControl(control: TimeControl) {
        app.settings.edit().putString(KEY_TIME, control.label).apply()
        val now = System.currentTimeMillis()
        tracker.setControl(control, now)
        publishLive(now)
    }

    fun saveLiveGame() {
        persistLive(tracker.finishNow(System.currentTimeMillis()) ?: return)
    }

    fun resignWhite() = finishAction { tracker.resign(white = true, now = it) }

    fun resignBlack() = finishAction { tracker.resign(white = false, now = it) }

    fun offerDrawWhite() = finishAction { tracker.offerDraw(white = true, now = it) }

    fun offerDrawBlack() = finishAction { tracker.offerDraw(white = false, now = it) }

    private fun finishAction(block: (Long) -> LiveEvent) {
        val now = System.currentTimeMillis()
        val event = block(now)
        publishLive(now)
        cue(event)
        if (event is LiveEvent.Finished) persistLive(event)
    }

    fun disconnect() {
        persistLive(tracker.finishNow(System.currentTimeMillis()))
        lightsJob?.cancel()
        batteryJob?.cancel()
        batteryPoll?.cancel()
        liveJob?.cancel()
        settleJob?.cancel()
        tickJob?.cancel()
        val closing = session
        session = null
        app.attachBoard(null)
        tracker.reset()
        spokenPlies = 0
        speech.stop()
        state.update { it.copy(connected = null, livePlacement = null, live = LiveSnapshot(), lastSavedGameId = null, batteryPercent = null) }
        if (closing != null) viewModelScope.launch { withContext(NonCancellable) { closing.close() } }
    }

    fun logText(): String = state.value.log.joinToString("\n")

    private fun withSession(label: String, block: suspend (ChessnutSession) -> String) {
        val s = session ?: return
        run(label) {
            val message = block(s)
            state.update { it.copy(message = message) }
        }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (state.value.busy != null) return
        state.update { it.copy(busy = label, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                fail(e)
            } finally {
                state.update { it.copy(busy = null) }
            }
        }
    }

    private fun fail(e: Exception) {
        log.line("error: ${e.message}")
        state.update { it.copy(message = e.message ?: e.toString()) }
    }

    private fun startTicks() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val now = System.currentTimeMillis()
                if (app.playEngaged) {
                    if (!pausedForPlay) {
                        persistLive(tracker.finishNow(now))
                        tracker.reset()
                        spokenPlies = 0
                        pausedForPlay = true
                        publishLive(now)
                    }
                    continue
                }
                if (pausedForPlay) {
                    pausedForPlay = false
                    tracker.reset()
                    spokenPlies = 0
                    publishLive(now)
                }
                val event = tracker.poll(now)
                publishLive(now)
                cue(event)
                if (event is LiveEvent.Finished) persistLive(event)
            }
        }
    }

    private fun cue(event: LiveEvent) {
        when (event) {
            is LiveEvent.Move -> {
                if (app.phoneSounds) speech.say(event.san)
                spokenPlies++
            }
            is LiveEvent.Finished -> {
                if (app.phoneSounds && event.end == LiveEnd.Checkmate) {
                    val last = event.sanMoves.lastOrNull()
                    if (last != null && event.sanMoves.size > spokenPlies) speech.say(last, mate = true)
                    else speech.sayCheckmate()
                }
                spokenPlies = event.sanMoves.size
            }
            else -> Unit
        }
        if (app.phoneSounds) {
            when {
                event is LiveEvent.Finished && event.end == LiveEnd.Checkmate -> sounds.checkmate()
                event is LiveEvent.Move && event.check -> sounds.check()
                event is LiveEvent.Illegal -> sounds.illegal()
            }
        }
        light(event)
    }

    private fun light(event: LiveEvent) {
        val squares = when (event) {
            is LiveEvent.Finished -> event.squares
            is LiveEvent.Illegal -> event.squares
            is LiveEvent.Move -> event.squares
            LiveEvent.Ignored -> return
        }
        val pulse = event is LiveEvent.Finished && event.end == LiveEnd.Checkmate
        val check = event is LiveEvent.Move && event.check
        val illegal = event is LiveEvent.Illegal
        if (!pulse && !check && !illegal) return
        lightsJob?.cancel()
        val connected = session ?: return
        lightsJob = viewModelScope.launch {
            try {
                if (pulse) {
                    val towardWhite = (event as? LiveEvent.Finished)?.result == "1-0"
                    val half = LedCodec.winnerHalf(towardWhite)
                    if (app.boardSounds) {
                        connected.beep(1200, 140)
                        delay(80)
                        connected.beep(1200, 220)
                    }
                    repeat(5) {
                        connected.setLeds(half)
                        delay(50)
                        connected.setLeds(emptyList())
                        delay(50)
                    }
                } else {
                    if (app.boardSounds) connected.beep(if (illegal) 240 else 880, if (illegal) 220 else 160)
                    connected.setLeds(squares)
                    delay(900)
                }
                connected.setLeds(emptyList())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.line("leds: ${e.message}")
            }
        }
    }

    private fun publishLive(now: Long) {
        state.update { it.copy(livePlacement = it.livePlacement, live = tracker.snapshot(now)) }
    }

    private fun persistLive(event: LiveEvent.Finished?) {
        if (event == null || event.sanMoves.isEmpty()) return
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(now))
        val saved = SavedGame(
            id = SavedGame.idFor(event.sanMoves, event.placements),
            createdAt = now,
            source = GameSource.Board,
            sanMoves = event.sanMoves,
            isComplete = event.result != "*",
            stopReason = when (event.end) {
                LiveEnd.Checkmate -> "Checkmate"
                LiveEnd.Stalemate -> "Stalemate"
                LiveEnd.Draw -> "Draw"
                LiveEnd.WhiteResign -> "White resigns"
                LiveEnd.BlackResign -> "Black resigns"
                LiveEnd.WhiteFlag -> "White flagged"
                LiveEnd.BlackFlag -> "Black flagged"
                null -> null
            },
            date = date,
            result = event.result,
            placements = event.placements,
        )
        val isNew = app.store.addIfNew(saved)
        tracker.markSaved()
        app.refreshGames()
        state.update { it.copy(lastSavedGameId = saved.id) }
        if (isNew) {
            app.analyse(saved.id)
            app.autoUploadToLichess(saved)
            log.line("live saved ${saved.id.take(8)}: ${saved.sanMoves.size} moves ${saved.result}")
        }
        publishLive(now)
    }

    override fun onCleared() {
        persistLive(tracker.finishNow(System.currentTimeMillis()))
        sounds.release()
        speech.release()
        app.attachBoard(null)
        lightsJob?.cancel()
        batteryJob?.cancel()
        batteryPoll?.cancel()
        liveJob?.cancel()
        settleJob?.cancel()
        tickJob?.cancel()
        session?.let { s -> CoroutineScope(Dispatchers.IO).launch { withTimeoutOrNull(2.seconds) { s.close() } } }
    }

    private companion object {
        const val MAX_LOG = 600
        const val KEY_TIME = "time_control"
        const val KEY_BOARD_ADDRESS = "board_address"
        const val KEY_BOARD_NAME = "board_name"
        val SCAN_TIME = 10.seconds
    }
}
