package app.chessnut.otb.puzzles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.board.BoardSounds
import app.chessnut.otb.board.MoveSpeaker
import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.chess.squareIndex
import app.chessnut.otb.play.PlayPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PuzzleUi(
    val title: String = "",
    val puzzle: Puzzle? = null,
    val fen: String = "",
    val lastUci: String? = null,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    /** The move lit by a hint, or the answer after a miss. */
    val hintUci: String? = null,
    val wrongUci: String? = null,
    val status: String = "",
    /** Pass or fail, shown as a mark: null while solving. */
    val pass: Boolean? = null,
    val mark: String? = null,
    val solved: Boolean = false,
    /** The opponent is moving, so taps wait. */
    val busy: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val summary: PuzzleSummary? = null,
)

/** How a session went. [nothingDue] when it never started. */
data class PuzzleSummary(val clean: Int, val helped: Int, val skipped: Int, val nothingDue: Boolean, val all: Int)

/**
 * A session of puzzles from one set: middlegame tactics of an opening, or an
 * endgame type. The opponent's move is played for you, then you find each
 * answer; a wrong move is turned back, a hint lights the piece, then the move.
 */
class PuzzleViewModel(application: Application, private val key: String) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val progress = PuzzleProgress(app.filesDir)
    private val sounds = BoardSounds()
    private val speech = MoveSpeaker(application)
    private val state = MutableStateFlow(PuzzleUi(title = PuzzleSets.title(key)))
    val ui: StateFlow<PuzzleUi> = state.asStateFlow()

    private var deck: List<Puzzle> = emptyList()
    private var run: PuzzleRun? = null
    private var missed = false
    private var hints = 0
    private val results = mutableMapOf<String, Boolean?>()
    private var moveJob: Job? = null

    init {
        val due = progress.session(key, System.currentTimeMillis())
        if (due.isEmpty()) finish(nothingDue = true) else begin(due)
    }

    private fun begin(puzzles: List<Puzzle>) {
        deck = puzzles
        results.clear()
        state.update { it.copy(summary = null, total = puzzles.size) }
        show(0)
    }

    /** Every puzzle in the set, due or not, easiest first. */
    fun practiseAll() = begin(PuzzleSets.of(key).take(PuzzleProgress.SESSION * 3))

    fun retryMissed() = begin(deck.filter { results[it.id] != true })

    private fun show(index: Int) {
        moveJob?.cancel()
        val puzzle = deck.getOrNull(index) ?: return finish()
        val next = PuzzleRun(puzzle)
        run = next
        missed = false
        hints = 0
        val side = if (puzzle.solverWhite) "White" else "Black"
        state.update {
            it.copy(
                puzzle = puzzle, fen = puzzle.fen, lastUci = null, selected = null, targets = emptySet(),
                hintUci = null, wrongUci = null, pass = null, mark = null, solved = false, busy = true, index = index,
                status = "${puzzle.origin}. Your opponent moves…",
            )
        }
        // The opponent's move plays out, so you see what you're answering.
        moveJob = viewModelScope.launch {
            delay(OPPONENT_DELAY_MS)
            val first = next.start()
            if (app.phoneSounds) PlayPosition.after(puzzle.fen, first)?.first?.let { speech.say(it) }
            state.update { it.copy(fen = next.fen, lastUci = first, busy = false, status = "Find the best move for $side.") }
        }
    }

    fun tap(square: Int) {
        val current = state.value
        val active = run ?: return
        if (current.busy || current.solved || current.puzzle == null) return
        when (val tap = MoveInput.tap(active.fen, current.selected, square)) {
            is MoveInput.Tap.Play -> {
                state.update { it.copy(selected = null, targets = emptySet()) }
                answer(active, tap.uci)
            }
            is MoveInput.Tap.Select -> state.update {
                it.copy(selected = tap.square, targets = MoveInput.targets(active.fen, tap.square))
            }
        }
    }

    private fun answer(active: PuzzleRun, uci: String) {
        val before = active.fen
        when (val answer = active.answer(uci)) {
            PuzzleRun.Answer.Wrong -> {
                missed = true
                if (app.phoneSounds) sounds.illegal()
                state.update { it.copy(wrongUci = uci, pass = false, mark = "Not that one. Try again, or take a hint.") }
            }
            is PuzzleRun.Answer.Right -> {
                val san = PlayPosition.after(before, uci)?.first
                if (app.phoneSounds && san != null) speech.say(san)
                if (answer.solved) {
                    complete(active.fen, uci)
                    return
                }
                val afterMine = PlayPosition.after(before, uci)?.second ?: active.fen
                state.update { it.copy(fen = afterMine, lastUci = uci, wrongUci = null, hintUci = null, pass = null, mark = null, busy = true, status = "Good. Your opponent replies…") }
                val reply = answer.reply ?: return
                moveJob = viewModelScope.launch {
                    delay(REPLY_DELAY_MS)
                    if (app.phoneSounds) PlayPosition.after(afterMine, reply)?.first?.let { speech.say(it) }
                    state.update { it.copy(fen = active.fen, lastUci = reply, busy = false, status = "Keep going: the next move.") }
                }
            }
        }
    }

    private fun complete(fen: String, lastUci: String) {
        val puzzle = run?.puzzle ?: return
        val clean = !missed
        results[puzzle.id] = clean
        val review = progress.grade(puzzle.id, clean, System.currentTimeMillis())
        val ideas = puzzle.ideas.take(3).joinToString(", ").ifEmpty { null }
        val again = if (clean) {
            val days = ((review.dueAt - System.currentTimeMillis()) / DAY_MS.toDouble()).let { Math.ceil(it).toInt() }.coerceAtLeast(1)
            "Back in $days ${if (days == 1) "day" else "days"}."
        } else {
            "Back tomorrow."
        }
        state.update {
            it.copy(
                fen = fen, lastUci = lastUci, solved = true, busy = false, hintUci = null, wrongUci = null,
                pass = clean, mark = if (clean) "Solved!" else "Solved with help",
                status = listOfNotNull(ideas?.let { i -> "Idea: $i." }, "Rated ${puzzle.rating}.", again).joinToString(" "),
            )
        }
    }

    /** First the piece to move, then the move itself. Either counts as help. */
    fun hint() {
        val active = run ?: return
        val move = active.expected ?: return
        missed = true
        hints++
        if (hints == 1) {
            val from = squareIndex(move.substring(0, 2))
            state.update { it.copy(selected = from, targets = MoveInput.targets(active.fen, from), status = "This piece moves.") }
        } else {
            state.update { it.copy(hintUci = move, status = "The arrow shows the move.") }
        }
    }

    fun next() {
        val current = state.value
        current.puzzle?.let { if (!current.solved) results.putIfAbsent(it.id, null) }
        show(current.index + 1)
    }

    private fun finish(nothingDue: Boolean = false) {
        moveJob?.cancel()
        state.update {
            it.copy(
                puzzle = null,
                summary = PuzzleSummary(
                    clean = results.values.count { r -> r == true },
                    helped = results.values.count { r -> r == false },
                    skipped = results.values.count { r -> r == null },
                    nothingDue = nothingDue,
                    all = PuzzleSets.of(key).size,
                ),
            )
        }
    }

    override fun onCleared() {
        sounds.release()
        speech.release()
    }

    class Factory(private val key: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            return PuzzleViewModel(application, key) as T
        }
    }

    private companion object {
        const val OPPONENT_DELAY_MS = 700L
        const val REPLY_DELAY_MS = 600L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
