package app.chessnut.otb.mistakes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.engine.StockfishProcess
import app.chessnut.otb.engine.UciEngine
import app.chessnut.otb.play.PlayPosition
import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineReview
import app.chessnut.otb.ui.squareIndex
import app.chessnut.otb.ui.squareName
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MistakesUi(
    val loading: Boolean = true,
    val mistake: Mistake? = null,
    val fen: String = Board().fen,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    /** The engine's move, shown after a hint or once solved. */
    val bestUci: String? = null,
    /** The last wrong answer, shown in red. */
    val wrongUci: String? = null,
    val status: String = "",
    val solved: Boolean = false,
    val checking: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val due: Int = 0,
)

/**
 * Practise positions where a mistake or blunder was played: due ones first,
 * then the rest. [gameId] limits the deck to one game.
 */
class MistakesViewModel(application: Application, private val gameId: String?) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val reviews = ReviewFile(File(application.filesDir, "mistakes/reviews.tsv"))
    private val state = MutableStateFlow(MistakesUi())
    val ui: StateFlow<MistakesUi> = state.asStateFlow()

    private var deck: List<Mistake> = emptyList()
    private var missed = false
    private var engine: UciEngine? = null
    private val engineLock = Mutex()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val games = app.store.all().filter { gameId == null || it.id == gameId }
            val all = games.flatMap { game ->
                val analysis = app.store.loadAnalysis(game.id) ?: return@flatMap emptyList()
                MistakeDeck.collect(game, analysis, app.myName)
            }
            val known = reviews.all()
            // Due first (most overdue first), then the rest by when they come back.
            deck = all.sortedWith(compareBy<Mistake> { known[it.key]?.dueAt ?: 0L }.thenByDescending { it.classification == MoveClass.Blunder })
            val due = deck.count { (known[it.key]?.dueAt ?: 0L) <= now }
            withContext(Dispatchers.Main) {
                state.update { it.copy(loading = false, total = deck.size, due = due) }
                show(0)
            }
        }
    }

    private fun show(index: Int) {
        val mistake = deck.getOrNull(index)
        missed = false
        if (mistake == null) {
            state.update {
                it.copy(
                    mistake = null,
                    index = index,
                    status = if (deck.isEmpty()) {
                        "No mistakes to practise yet. Analysed games with a mistake or blunder show up here."
                    } else {
                        "That's every position. Come back later for the ones due again."
                    },
                )
            }
            return
        }
        val side = if (mistake.whiteToMove) "White" else "Black"
        val what = if (mistake.classification == MoveClass.Blunder) "a blunder" else "a mistake"
        state.update {
            it.copy(
                mistake = mistake,
                fen = mistake.fen,
                selected = null,
                targets = emptySet(),
                bestUci = null,
                wrongUci = null,
                solved = false,
                checking = false,
                index = index,
                status = "${mistake.moveLabel} was $what. Find a better move for $side.",
            )
        }
    }

    fun next() = show(state.value.index + 1)

    fun hint() {
        val mistake = state.value.mistake ?: return
        missed = true
        state.update { it.copy(bestUci = mistake.bestUci, status = "The arrow shows the engine's move. Play it, or find your own.") }
    }

    fun tap(square: Int) {
        val current = state.value
        if (current.mistake == null || current.solved || current.checking) return
        val board = Board().apply { loadFromFen(current.fen) }
        val from = current.selected
        if (from != null) {
            val move = board.legalMoves().filter {
                squareIndex(it.from.value().lowercase()) == from && squareIndex(it.to.value().lowercase()) == square
            }.firstOrNull { it.promotion == Piece.NONE || it.promotion.pieceType == PieceType.QUEEN }
            if (move != null) {
                state.update { it.copy(selected = null, targets = emptySet()) }
                answer(move.toString().lowercase())
                return
            }
        }
        val piece = board.getPiece(Square.valueOf(squareName(square).uppercase()))
        val own = piece != Piece.NONE && piece.pieceSide == board.sideToMove
        val selected = if (own && from != square) square else null
        val targets = if (selected == null) emptySet() else board.legalMoves()
            .filter { squareIndex(it.from.value().lowercase()) == selected }
            .map { squareIndex(it.to.value().lowercase()) }
            .toSet()
        state.update { it.copy(selected = selected, targets = targets) }
    }

    private fun answer(uci: String) {
        val mistake = state.value.mistake ?: return
        state.update { it.copy(checking = true, wrongUci = null, status = "Checking…") }
        viewModelScope.launch {
            val (winBest, winAnswer) = if (uci.equals(mistake.bestUci, ignoreCase = true)) null to null else evaluate(mistake.fen, uci)
            val good = MistakeDeck.solved(uci, mistake.bestUci, winBest, winAnswer)
            if (good) {
                val review = reviews.grade(mistake.key, clean = !missed, now = System.currentTimeMillis())
                val days = Drill.days(review, System.currentTimeMillis())
                val after = PlayPosition.after(mistake.fen, uci)
                val again = if (missed) {
                    "You needed a hint or another try, so this position comes back tomorrow."
                } else {
                    "Solved first time. You'll see this position again in $days ${if (days == 1) "day" else "days"}."
                }
                val best = mistake.bestUci
                val engineNote = if (best != null && !uci.equals(best, ignoreCase = true)) {
                    " The engine's choice was ${ChessLines.sanOf(mistake.fen, listOf(best)).firstOrNull() ?: best}."
                } else {
                    ""
                }
                state.update {
                    it.copy(
                        fen = after?.second ?: it.fen,
                        solved = true,
                        checking = false,
                        bestUci = mistake.bestUci,
                        status = "Good: ${after?.first ?: uci}.$engineNote $again",
                    )
                }
            } else {
                missed = true
                val lost = if (winBest != null && winAnswer != null) " It gives away about ${Math.round(winBest - winAnswer)}% winning chances." else ""
                state.update { it.copy(checking = false, wrongUci = uci, status = "Not quite.$lost Try again, or take a hint.") }
            }
        }
    }

    /** The mover's winning chances after the engine's best move and after [uci]. */
    private suspend fun evaluate(fen: String, uci: String): Pair<Double?, Double?> = withContext(Dispatchers.IO) {
        runCatching {
            engineLock.withLock {
                val eng = engine ?: UciEngine(StockfishProcess.start(getApplication()), threads = 2).also {
                    it.start()
                    engine = it
                }
                val whiteMoves = fen.split(' ')[1] == "w"
                val best = eng.analyseMillis(fen, 700, 1).firstOrNull()?.score ?: Score.Even
                val after = PlayPosition.after(fen, uci)?.second ?: return@withLock null to null
                val reply = when (ChessLines.terminalOf(after)) {
                    Terminal.Checkmate -> if (whiteMoves) Score.BlackMated else Score.WhiteMated
                    Terminal.Stalemate -> Score.Even
                    null -> eng.analyseMillis(after, 700, 1).firstOrNull()?.score ?: Score.Even
                }
                fun mover(score: Score) = if (whiteMoves) score.whiteWinPercent else 100 - score.whiteWinPercent
                mover(best) to mover(reply)
            }
        }.getOrDefault(null to null)
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }

    class Factory(private val gameId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return MistakesViewModel(application, gameId) as T
        }
    }
}

/** Review dates per key, in a small tab-separated file. */
class ReviewFile(private val file: File) {
    private val reviews: MutableMap<String, LineReview> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf()
        file.readLines().mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size == 3) parts[0] to LineReview(parts[1].toInt(), parts[2].toLong()) else null
        }.toMap().toMutableMap()
    }.getOrDefault(mutableMapOf())

    @Synchronized
    fun all(): Map<String, LineReview> = reviews.toMap()

    @Synchronized
    fun grade(key: String, clean: Boolean, now: Long): LineReview {
        val next = Drill.grade(reviews[key], clean, now)
        reviews[key] = next
        file.parentFile?.mkdirs()
        file.writeText(reviews.entries.joinToString("") { (k, r) -> "$k\t${r.box}\t${r.dueAt}\n" })
        return next
    }
}
