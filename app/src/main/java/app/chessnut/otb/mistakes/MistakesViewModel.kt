package app.chessnut.otb.mistakes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.engine.EngineHolder
import app.chessnut.otb.play.PlayPosition
import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineReview
import com.github.bhlangonijr.chesslib.Board
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.chessnut.otb.chess.MoveInput

data class MistakesUi(
    val loading: Boolean = true,
    val mistake: Mistake? = null,
    val fen: String = Board().fen,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    /** The move that led to the position on the board, highlighted. */
    val lastUci: String? = null,
    /** The engine's move, shown after a hint or once solved. */
    val bestUci: String? = null,
    /** The last wrong answer, shown in red. */
    val wrongUci: String? = null,
    val status: String = "",
    val solved: Boolean = false,
    val checking: Boolean = false,
    val index: Int = 0,
    /** Positions in this session. */
    val total: Int = 0,
    /** Shown instead of a position once the session is over, or when nothing is due. */
    val summary: SessionSummary? = null,
)

/** How a practice session went, and when the next position is due. */
data class SessionSummary(
    val firstTry: Int,
    val helped: Int,
    val skipped: Int,
    /** When the next position is due, or null if there are no positions at all. */
    val nextDueAt: Long?,
    /** True when the session never started because nothing was due. */
    val nothingDue: Boolean,
    /** Every position, due or not, for "Practise all". */
    val all: Int,
    /** Positions due now, such as skipped ones. */
    val stillDue: Int = 0,
)

private enum class Outcome { FirstTry, Helped, Skipped }

/**
 * Practise positions where a mistake or blunder was played. A session covers
 * the positions that are due, most overdue first, and ends with a summary.
 * [gameId] limits the positions to one game.
 */
class MistakesViewModel(application: Application, private val gameId: String?) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val reviews = ReviewFile(File(application.filesDir, "mistakes/reviews.tsv"))
    private val state = MutableStateFlow(MistakesUi())
    val ui: StateFlow<MistakesUi> = state.asStateFlow()

    /** Every position, most overdue first. */
    private var all: List<Mistake> = emptyList()
    /** The positions of the current session. */
    private var deck: List<Mistake> = emptyList()
    private val results = mutableMapOf<String, Outcome>()
    private var missed = false
    /** The second step: the opponent has replied and you find the follow-up. */
    private var followUp = false
    /** Follow-up moves that count, and the engine's own, for a hint. */
    private var followGood: Set<String> = emptySet()
    private var followBest: String? = null
    private val engines = EngineHolder(application)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val games = app.store.all().filter { gameId == null || it.id == gameId }
            val live = games.flatMap { game ->
                val analysis = app.store.loadAnalysis(game.id) ?: return@flatMap emptyList()
                MistakeDeck.collect(game, analysis, app.myNames)
            }
            // Positions from games the list has since let go.
            val kept = app.digests.all().filter { gameId == null || it.gameId == gameId }.flatMap { it.mistakes }
            val found = (live + kept).distinctBy { it.key }
            val known = reviews.all()
            val sorted = found.sortedWith(compareBy<Mistake> { known[it.key]?.dueAt ?: 0L }.thenByDescending { it.classification == MoveClass.Blunder })
            val due = sorted.filter { (known[it.key]?.dueAt ?: 0L) <= now }
            withContext(Dispatchers.Main) {
                all = sorted
                state.update { it.copy(loading = false) }
                if (due.isEmpty() && sorted.isNotEmpty()) finish(nothingDue = true) else begin(due)
            }
        }
    }

    /** False once the game a position came from has been cleared from the list. */
    fun hasGame(id: String): Boolean = app.store.contains(id)

    private fun begin(positions: List<Mistake>) {
        deck = positions
        results.clear()
        state.update { it.copy(summary = null, total = positions.size) }
        show(0)
    }

    /** Goes over the positions not solved first time in the session just finished. */
    fun retryMissed() = begin(deck.filter { results[it.key] != Outcome.FirstTry })

    /** Every position, due or not. */
    fun practiseAll() = begin(all)

    private fun finish(nothingDue: Boolean = false) {
        val now = System.currentTimeMillis()
        val known = reviews.all()
        val nextDue = all.minOfOrNull { known[it.key]?.dueAt ?: 0L }?.coerceAtLeast(now)
        state.update {
            it.copy(
                mistake = null,
                summary = SessionSummary(
                    firstTry = results.values.count { r -> r == Outcome.FirstTry },
                    helped = results.values.count { r -> r == Outcome.Helped },
                    skipped = results.values.count { r -> r == Outcome.Skipped },
                    nextDueAt = nextDue,
                    nothingDue = nothingDue,
                    all = all.size,
                    stillDue = all.count { m -> (known[m.key]?.dueAt ?: 0L) <= now },
                ),
            )
        }
    }

    private fun show(index: Int) {
        val mistake = deck.getOrNull(index)
        missed = false
        followUp = false
        followGood = emptySet()
        followBest = null
        if (mistake == null) {
            if (all.isEmpty()) {
                state.update {
                    it.copy(mistake = null, index = index, status = "No mistakes to practise yet. Analysed games with a mistake or blunder show up here.")
                }
            } else {
                finish()
            }
            return
        }
        val side = if (mistake.whiteToMove) "White" else "Black"
        val what = if (mistake.classification == MoveClass.Blunder) "a blunder" else "a mistake"
        state.update {
            it.copy(
                mistake = mistake,
                fen = mistake.fen,
                lastUci = mistake.lastUci,
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

    fun next() {
        val current = state.value
        current.mistake?.let { if (!current.solved) results.putIfAbsent(it.key, Outcome.Skipped) }
        show(current.index + 1)
    }

    fun hint() {
        val mistake = state.value.mistake ?: return
        missed = true
        val shown = if (followUp) followBest else mistake.bestUci
        state.update { it.copy(bestUci = shown, status = "The arrow shows the engine's move. Play it, or find your own.") }
    }

    fun tap(square: Int) {
        val current = state.value
        if (current.mistake == null || current.solved || current.checking) return
        when (val tap = MoveInput.tap(current.fen, current.selected, square)) {
            is MoveInput.Tap.Play -> {
                state.update { it.copy(selected = null, targets = emptySet()) }
                answer(tap.uci)
            }
            is MoveInput.Tap.Select -> state.update {
                it.copy(selected = tap.square, targets = MoveInput.targets(current.fen, tap.square))
            }
        }
    }

    private fun answer(uci: String) {
        val mistake = state.value.mistake ?: return
        if (followUp) {
            answerFollowUp(mistake, uci)
            return
        }
        state.update { it.copy(checking = true, wrongUci = null, status = "Checking…") }
        viewModelScope.launch {
            val (winBest, winAnswer) = if (uci.equals(mistake.bestUci, ignoreCase = true)) null to null else evaluate(mistake.fen, uci)
            // Skip or Next while the engine worked: this answer belongs to a position no longer shown.
            if (!stillOn(mistake)) return@launch
            if (!MistakeDeck.solved(uci, mistake.bestUci, winBest, winAnswer)) {
                missed = true
                val lost = if (winBest != null && winAnswer != null) " It gives away about ${Math.round(winBest - winAnswer)}% winning chances." else ""
                state.update { it.copy(checking = false, wrongUci = uci, status = "Not quite.$lost Try again, or take a hint.") }
                return@launch
            }
            val after = PlayPosition.after(mistake.fen, uci)
            val best = mistake.bestUci
            val engineNote = if (best != null && !uci.equals(best, ignoreCase = true)) {
                " The engine's choice was ${ChessLines.sanOf(mistake.fen, listOf(best)).firstOrNull() ?: best}."
            } else {
                ""
            }
            val good = "Good: ${after?.first ?: uci}.$engineNote"
            if (after == null || ChessLines.terminalOf(after.second) != null) {
                complete(mistake, after?.second, uci, good)
                return@launch
            }
            state.update { it.copy(fen = after.second, lastUci = uci, bestUci = null, status = "$good Your opponent is replying…") }
            // The opponent answers with the engine's best, then you find the follow-up.
            val reply = topLines(after.second, 1).firstOrNull()?.uci?.firstOrNull()
            if (!stillOn(mistake)) return@launch
            val replied = reply?.let { PlayPosition.after(after.second, it) }
            if (replied == null || ChessLines.terminalOf(replied.second) != null) {
                complete(mistake, replied?.second ?: after.second, reply ?: uci, good)
                return@launch
            }
            val lines = topLines(replied.second, MistakeDeck.FOLLOW_UP_CHOICES)
            if (!stillOn(mistake)) return@launch
            followGood = MistakeDeck.goodFollowUps(lines, whiteMoves = replied.second.split(' ')[1] == "w")
            followBest = lines.firstOrNull()?.uci?.firstOrNull()
            if (followGood.isEmpty()) {
                complete(mistake, replied.second, reply, good)
                return@launch
            }
            followUp = true
            state.update {
                it.copy(
                    fen = replied.second,
                    lastUci = reply,
                    checking = false,
                    status = "$good They reply ${replied.first}. Now find the follow-up.",
                )
            }
        }
    }

    private fun stillOn(mistake: Mistake): Boolean = state.value.mistake?.key == mistake.key && !state.value.solved

    /** The second step: a move close to the engine's best after the opponent's reply. */
    private fun answerFollowUp(mistake: Mistake, uci: String) {
        val fen = state.value.fen
        if (uci.lowercase() !in followGood) {
            missed = true
            state.update { it.copy(wrongUci = uci, status = "Not the follow-up. Try again, or take a hint.") }
            return
        }
        val after = PlayPosition.after(fen, uci)
        complete(mistake, after?.second, uci, "Follow-up ${after?.first ?: uci}: well played.")
    }

    /**
     * Both moves found: without help, the position is done and doesn't come
     * back unless you practise them all; with a hint or a wrong try, it comes
     * back tomorrow.
     */
    private fun complete(mistake: Mistake, fen: String?, lastUci: String, lead: String) {
        followUp = false
        results[mistake.key] = if (missed) Outcome.Helped else Outcome.FirstTry
        val again = if (missed) {
            reviews.grade(mistake.key, clean = false, now = System.currentTimeMillis())
            "You needed a hint or another try, so this position comes back tomorrow."
        } else {
            reviews.retire(mistake.key)
            "Solved without help, so it won't come back unless you practise all."
        }
        state.update {
            it.copy(
                fen = fen ?: it.fen,
                lastUci = lastUci,
                solved = true,
                checking = false,
                wrongUci = null,
                bestUci = null,
                status = "$lead $again",
            )
        }
    }

    /** The engine's top [count] lines for [fen]. */
    private suspend fun topLines(fen: String, count: Int): List<EngineLine> = withContext(Dispatchers.IO) {
        runCatching { engines.get().analyseMillis(fen, 900, count) }.getOrDefault(emptyList())
    }


    /** The mover's winning chances after the engine's best move and after [uci]. */
    private suspend fun evaluate(fen: String, uci: String): Pair<Double?, Double?> = withContext(Dispatchers.IO) {
        runCatching {
            val eng = engines.get()
            val whiteMoves = fen.split(' ')[1] == "w"
            val best = eng.analyseMillis(fen, 700, 1).firstOrNull()?.score ?: Score.Even
            val after = PlayPosition.after(fen, uci)?.second ?: return@runCatching null to null
            val reply = when (ChessLines.terminalOf(after)) {
                Terminal.Checkmate -> if (whiteMoves) Score.BlackMated else Score.WhiteMated
                Terminal.Stalemate -> Score.Even
                null -> eng.analyseMillis(after, 700, 1).firstOrNull()?.score ?: Score.Even
            }
            fun mover(score: Score) = if (whiteMoves) score.whiteWinPercent else 100 - score.whiteWinPercent
            mover(best) to mover(reply)
        }.getOrDefault(null to null)
    }

    override fun onCleared() {
        engines.close()
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

    /** Solved for good: kept out of practice unless you practise them all. */
    @Synchronized
    fun retire(key: String) {
        reviews[key] = LineReview(Drill.INTERVAL_DAYS.lastIndex, Long.MAX_VALUE)
        save()
    }

    @Synchronized
    fun grade(key: String, clean: Boolean, now: Long): LineReview {
        val next = Drill.grade(reviews[key], clean, now)
        reviews[key] = next
        save()
        return next
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(reviews.entries.joinToString("") { (k, r) -> "$k\t${r.box}\t${r.dueAt}\n" })
    }
}
