package app.chessnut.otb.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.GameReport
import app.chessnut.otb.analysis.MoveReport
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.engine.StockfishProcess
import app.chessnut.otb.engine.UciEngine
import app.chessnut.otb.games.SavedGame
import com.github.bhlangonijr.chesslib.Board
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.chessnut.otb.chess.MoveInput

/** A move played on the board while exploring away from the game. */
data class SideMove(val san: String, val uci: String, val fen: String)

data class EngineView(val score: Score?, val terminal: Terminal?, val lines: List<ShownLine>, val live: Boolean)

data class ShownLine(val score: Score, val san: List<String>, val firstUci: String?, val depth: Int)

enum class ArrowKind(val key: String, val onByDefault: Boolean) {
    Best("arrow_best", true),
    Better("arrow_better", true),
    Threat("arrow_threat", false),
}

/** The move the opponent would play from [fen] if the side to move passed. */
data class Threat(val fen: String, val uci: String)

data class ReviewState(
    val game: SavedGame? = null,
    val fens: List<String> = emptyList(),
    val uci: List<String> = emptyList(),
    val analysis: GameAnalysis? = null,
    val report: GameReport? = null,
    val progress: Float? = null,
    /** Mainline position index: 0 is the start, n is after move n. */
    val cursor: Int = 0,
    /** Side line branching from [cursor]; empty when following the game. */
    val side: List<SideMove> = emptyList(),
    val selected: Int? = null,
    val engine: EngineView? = null,
    val flipped: Boolean = false,
    val arrows: Set<ArrowKind> = ArrowKind.entries.filter { it.onByDefault }.toSet(),
    val threat: Threat? = null,
) {
    val exploring: Boolean get() = side.isNotEmpty()
    val fen: String get() = side.lastOrNull()?.fen ?: fens.getOrElse(cursor) { Board().fen }

    /** The game move that led here, when Stockfish preferred a different one. */
    val betterThanPlayed: MoveReport? get() {
        if (exploring || cursor == 0) return null
        val move = report?.moves?.getOrNull(cursor - 1) ?: return null
        return move.takeIf { it.bestUci != null && it.bestUci != uci.getOrNull(cursor - 1) }
    }

    val lastUci: String? get() = side.lastOrNull()?.uci ?: uci.getOrNull(cursor - 1)

    /** Where the held piece can go. */
    val targets: Set<Int> get() = MoveInput.targets(fen, selected)
}

class ReviewViewModel(application: Application, private val gameId: String) : AndroidViewModel(application) {
    val app = application as OtbApp
    private val state = MutableStateFlow(ReviewState())
    val ui: StateFlow<ReviewState> = state.asStateFlow()

    private val linkFlow = MutableStateFlow(app.lichessUrl(gameId))

    /** The game's page on Lichess, once it has been uploaded. */
    val lichessLink: StateFlow<String?> = linkFlow.asStateFlow()

    init {
        viewModelScope.launch {
            app.uploadVersion.collect { linkFlow.value = app.lichessUrl(gameId) }
        }
    }

    fun uploadToLichess(onDone: (String) -> Unit) {
        val game = app.store.load(gameId) ?: return
        app.uploadToLichess(game) { message ->
            viewModelScope.launch(Dispatchers.Main) { onDone(message) }
        }
    }

    private val engineLock = Mutex()
    @Volatile private var liveEngine: UciEngine? = null
    private var liveJob: Job? = null

    private val threatLock = Mutex()
    @Volatile private var threatEngine: UciEngine? = null
    private var threatJob: Job? = null

    init {
        val arrows = ArrowKind.entries.filter { app.settings.getBoolean(it.key, it.onByDefault) }.toSet()
        state.update { it.copy(arrows = arrows) }
        load()
        viewModelScope.launch {
            combine(app.analysisProgress, app.analysisVersion) { progress, _ -> progress[gameId] }.collect { progress ->
                val analysis = if (progress == null) withContext(Dispatchers.IO) { app.store.loadAnalysis(gameId) } else null
                state.update { s ->
                    val updated = analysis ?: s.analysis
                    s.copy(progress = progress, analysis = updated, report = updated?.let { reportFor(s, it) } ?: s.report)
                }
                refreshEngine()
            }
        }
    }

    private fun load() {
        val game = app.store.load(gameId) ?: return
        val replay = ChessLines.replay(game.sanMoves)
        val analysis = app.store.loadAnalysis(gameId)
        val flipped = app.myName.isNotBlank() && game.black.equals(app.myName, ignoreCase = true)
        state.update {
            val base = it.copy(game = game, fens = replay.fens, uci = replay.uci, analysis = analysis, flipped = flipped)
            base.copy(report = analysis?.let { a -> reportFor(base, a) })
        }
    }

    private fun reportFor(s: ReviewState, analysis: GameAnalysis): GameReport? =
        s.game?.let { AccuracyCalculator.report(it.sanMoves, s.uci, analysis) }

    fun goTo(cursor: Int) {
        state.update { it.copy(cursor = cursor.coerceIn(0, it.fens.lastIndex.coerceAtLeast(0)), side = emptyList(), selected = null) }
        refreshEngine()
    }

    fun step(delta: Int) {
        val s = state.value
        if (s.exploring) {
            if (delta < 0) {
                state.update { it.copy(side = it.side.dropLast(1), selected = null) }
                refreshEngine()
            }
            return
        }
        goTo(s.cursor + delta)
    }

    fun backToGame() = goTo(state.value.cursor)

    fun flip() = state.update { it.copy(flipped = !it.flipped) }

    fun toggleArrow(kind: ArrowKind) {
        val on = kind !in state.value.arrows
        app.settings.edit().putBoolean(kind.key, on).apply()
        state.update { it.copy(arrows = if (on) it.arrows + kind else it.arrows - kind) }
        if (kind == ArrowKind.Threat) refreshThreat()
    }

    fun tapSquare(square: Int) {
        val s = state.value
        when (val tap = MoveInput.tap(s.fen, s.selected, square)) {
            is MoveInput.Tap.Play -> play(tap.uci)
            is MoveInput.Tap.Select -> state.update { it.copy(selected = tap.square) }
        }
    }

    fun drop(from: Int, to: Int) {
        if (from == to) return
        val uci = MoveInput.drop(state.value.fen, from, to)
        if (uci == null) {
            state.update { it.copy(selected = null) }
            return
        }
        play(uci)
    }

    /** Plays [uci] from the shown position: follows the game if it is the game's move, otherwise explores. */
    fun play(uci: String) {
        val s = state.value
        if (!s.exploring && s.uci.getOrNull(s.cursor) == uci) {
            goTo(s.cursor + 1)
            return
        }
        val board = Board().apply { loadFromFen(s.fen) }
        val move = board.legalMoves().firstOrNull { it.toString().lowercase() == uci } ?: return
        val san = ChessLines.sanOf(s.fen, listOf(uci)).firstOrNull() ?: return
        board.doMove(move)
        state.update { it.copy(side = it.side + SideMove(san, uci, board.fen), selected = null) }
        refreshEngine()
    }

    fun saveInfo(white: String, black: String, event: String, date: String, result: String) {
        val game = state.value.game ?: return
        val updated = game.copy(white = white.trim(), black = black.trim(), event = event.trim(), date = date.trim(), result = result)
        app.store.save(updated)
        app.refreshGames()
        state.update { it.copy(game = updated) }
    }

    fun reanalyse() = app.analyse(gameId, replace = true)

    fun delete() {
        app.store.delete(gameId)
        app.refreshGames()
    }

    private fun refreshThreat() {
        threatJob?.cancel()
        val s = state.value
        val fen = s.fen
        if (s.threat?.fen != fen) state.update { it.copy(threat = null) }
        if (ArrowKind.Threat !in s.arrows || s.threat?.fen == fen) return
        val passed = ChessLines.passFen(fen) ?: return
        threatJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val engine = threatLock.withLock {
                    threatEngine ?: UciEngine(StockfishProcess.start(app), threads = 1).also { threatEngine = it }
                }
                engine.start()
                val lines = engine.analyseLive(passed, multiPv = 1).first { it.first().depth >= THREAT_DEPTH }
                val uci = lines.first().uci.firstOrNull() ?: return@launch
                state.update { if (it.fen == fen) it.copy(threat = Threat(fen, uci)) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No legal reply after passing, or the engine failed: show no threat.
            }
        }
    }

    private fun refreshEngine() {
        refreshThreat()
        liveJob?.cancel()
        val s = state.value
        val stored = if (!s.exploring) s.analysis?.positions?.getOrNull(s.cursor) else null
        if (stored != null) {
            state.update { it.copy(engine = EngineView(stored.score, stored.terminal, shown(stored.fen, stored.lines), live = false)) }
            return
        }
        val fen = s.fen
        val terminal = ChessLines.terminalOf(fen)
        if (terminal != null) {
            state.update { it.copy(engine = EngineView(null, terminal, emptyList(), live = false)) }
            return
        }
        state.update { it.copy(engine = EngineView(null, null, emptyList(), live = true)) }
        liveJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val engine = engineLock.withLock {
                    liveEngine ?: UciEngine(StockfishProcess.start(app), threads = 2).also { liveEngine = it }
                }
                engine.start()
                engine.analyseLive(fen, multiPv = 3).collect { lines ->
                    state.update { current ->
                        if (current.fen != fen) current else current.copy(
                            engine = EngineView(lines.firstOrNull()?.score, null, shown(fen, lines), live = true),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(engine = EngineView(null, null, emptyList(), live = false)) }
            }
        }
    }

    private fun shown(fen: String, lines: List<EngineLine>): List<ShownLine> =
        lines.map { ShownLine(it.score, ChessLines.sanOf(fen, it.uci.take(10)), it.uci.firstOrNull(), it.depth) }

    /** Screen became visible again: restart the live engine if the cursor needs it. */
    fun resume() = refreshEngine()

    /** View models live as long as the activity, so the screen stops the engine when it leaves. */
    fun pause() {
        liveJob?.cancel()
        liveJob = null
        val engine = liveEngine
        liveEngine = null
        engine?.close()
        threatJob?.cancel()
        threatJob = null
        val threats = threatEngine
        threatEngine = null
        threats?.close()
    }

    override fun onCleared() {
        liveJob?.cancel()
        liveEngine?.close()
        threatJob?.cancel()
        threatEngine?.close()
    }


    private companion object {
        const val THREAT_DEPTH = 14
    }

    class Factory(private val gameId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return ReviewViewModel(application, gameId) as T
        }
    }
}