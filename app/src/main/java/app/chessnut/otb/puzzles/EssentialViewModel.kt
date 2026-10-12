package app.chessnut.otb.puzzles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.board.BoardSounds
import app.chessnut.otb.board.MoveSpeaker
import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.play.PlayPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EssentialUi(
    val essential: Essential? = null,
    val fen: String = "",
    val lastUci: String? = null,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val hintUci: String? = null,
    val wrongUci: String? = null,
    val status: String = "",
    val pass: Boolean? = null,
    val mark: String? = null,
    val done: Boolean = false,
    /** Waiting on the tablebase or the defence's reply. */
    val busy: Boolean = true,
    val yourMoves: Int = 0,
)

/**
 * One essential ending against perfect defence. Before each of your moves the
 * tablebase rates every move; one that gives up your result is turned back
 * with the move that kept it. The defence answers with the tablebase's best.
 */
class EssentialViewModel(application: Application, private val id: String) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val essential = Essentials.ALL.first { it.id == id }
    private val progress = PuzzleProgress(app.filesDir)
    private val sounds = BoardSounds()
    private val speech = MoveSpeaker(application)
    private val state = MutableStateFlow(EssentialUi(essential = essential, fen = essential.fen))
    val ui: StateFlow<EssentialUi> = state.asStateFlow()

    /** The tablebase's view of the position you're to move in. */
    private var view: TablebaseView? = null
    private var missed = false
    private var job: Job? = null

    init {
        restart()
    }

    fun restart() {
        job?.cancel()
        missed = false
        view = null
        state.value = EssentialUi(essential = essential, fen = essential.fen, status = goalLine())
        lookUpYourTurn()
    }

    private fun goalLine(): String {
        val side = if (essential.white) "White" else "Black"
        return when (essential.goal) {
            Goal.Win -> "You have $side. Win it."
            Goal.Draw -> "You have $side. Hold the draw."
        }
    }

    private fun lookUpYourTurn() {
        val fen = state.value.fen
        job = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { TablebaseHttp.lookup(fen) } }
            found.onSuccess { v ->
                view = v
                state.update { it.copy(busy = false) }
            }.onFailure {
                state.update { it.copy(busy = false, status = "This needs the internet: the tablebase is online. Check the connection and tap Restart.") }
            }
        }
    }

    fun tap(square: Int) {
        val current = state.value
        if (current.busy || current.done || view == null) return
        when (val tap = MoveInput.tap(current.fen, current.selected, square)) {
            is MoveInput.Tap.Play -> {
                state.update { it.copy(selected = null, targets = emptySet()) }
                play(tap.uci)
            }
            is MoveInput.Tap.Select -> state.update { it.copy(selected = tap.square, targets = MoveInput.targets(current.fen, tap.square)) }
        }
    }

    private fun play(uci: String) {
        val before = state.value.fen
        val rated = view ?: return
        rated.judge(uci)?.let { (left, kept) ->
            missed = true
            if (app.phoneSounds) sounds.illegal()
            val keptSan = PlayPosition.after(before, kept)?.first ?: kept
            val words = when {
                essential.goal == Goal.Win && left == Outcome.Draw -> "That lets the win slip to a draw. $keptSan keeps it."
                left == Outcome.Loss -> "That loses. $keptSan holds."
                else -> "That gives up the result. $keptSan keeps it."
            }
            state.update { it.copy(wrongUci = uci, hintUci = kept, pass = false, mark = words) }
            return
        }
        val step = PlayPosition.after(before, uci) ?: return
        if (app.phoneSounds) speech.say(step.first)
        val moves = state.value.yourMoves + 1
        state.update {
            it.copy(fen = step.second, lastUci = uci, wrongUci = null, hintUci = null, pass = null, mark = null, yourMoves = moves)
        }
        finished(step.second, promoted = uci.length == 5, moves)?.let { success(it); return }
        // The defence answers with the tablebase's best.
        state.update { it.copy(busy = true, status = "The defence replies…") }
        job = viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) { runCatching { TablebaseHttp.lookup(step.second) } }.getOrNull()?.best
            if (reply == null) {
                state.update { it.copy(busy = false, status = "Couldn't reach the tablebase. Tap Restart to try again.") }
                return@launch
            }
            delay(REPLY_MS)
            val answered = PlayPosition.after(step.second, reply) ?: return@launch
            if (app.phoneSounds) speech.say(answered.first)
            state.update { it.copy(fen = answered.second, lastUci = reply, status = goalLine()) }
            if (essential.goal == Goal.Draw && ChessLines.terminalOf(answered.second) == null &&
                answered.second.substringBefore(' ').count { c -> c.isLetter() } == 2
            ) {
                success("Only the kings are left: a draw. Held!")
                return@launch
            }
            view = null
            lookUpYourTurn()
        }
    }

    /** Your goal reached after [fen]: the words to show, or null to play on. */
    private fun finished(fen: String, promoted: Boolean, moves: Int): String? {
        val terminal = ChessLines.terminalOf(fen)
        val pieces = fen.substringBefore(' ').count { it.isLetter() }
        return when {
            terminal == Terminal.Checkmate -> "Checkmate in $moves ${if (moves == 1) "move" else "moves"}."
            terminal == Terminal.Stalemate -> "Stalemate: a draw. Held!"
            essential.goal == Goal.Win && promoted -> "The pawn queens with the win in hand."
            essential.goal == Goal.Draw && pieces == 2 -> "Only the kings are left: a draw. Held!"
            essential.goal == Goal.Draw && moves >= HOLD_MOVES -> "Held for $moves moves: the draw is safe."
            else -> null
        }
    }

    private fun success(words: String) {
        val clean = !missed
        if (clean) progress.master(PROGRESS_PREFIX + id) else progress.grade(PROGRESS_PREFIX + id, false, System.currentTimeMillis())
        state.update {
            it.copy(
                done = true, busy = false, pass = clean,
                mark = if (clean) "Mastered!" else "Done, with a slip",
                status = words + if (clean) "" else " Try it again without a slip to master it.",
            )
        }
    }

    /** The tablebase's best move, as an arrow. Counts as help. */
    fun hint() {
        val best = view?.best ?: return
        missed = true
        state.update { it.copy(hintUci = best, status = "The arrow shows the best move.") }
    }

    override fun onCleared() {
        sounds.release()
        speech.release()
    }

    class Factory(private val id: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            return EssentialViewModel(application, id) as T
        }
    }

    companion object {
        /** Progress keys for essentials, beside the puzzles'. */
        const val PROGRESS_PREFIX = "essential:"
        /** Holding a draw this many moves without a slip counts as held. */
        private const val HOLD_MOVES = 12
        private const val REPLY_MS = 500L
    }
}
