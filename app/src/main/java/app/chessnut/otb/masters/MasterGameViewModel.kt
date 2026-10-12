package app.chessnut.otb.masters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.board.BoardSounds
import app.chessnut.otb.board.MoveSpeaker
import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.chess.squareIndex
import app.chessnut.otb.openings.Guess
import app.chessnut.otb.openings.MasterGame
import app.chessnut.otb.openings.MasterGames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MasterGameUi(
    val game: MasterGame? = null,
    val fen: String = "",
    val lastUci: String? = null,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    /** The master's move, shown after your guess or as the second hint. */
    val masterUci: String? = null,
    /** The opponent's threat after their move, drawn in red. */
    val threatUci: String? = null,
    val wrongUci: String? = null,
    /** The heading: whose move, or how your guess went. */
    val status: String = "",
    /** Why the master's last move was played, kept on screen with your result until your next guess. */
    val yourNote: String? = null,
    /** The opponent's last move and why it was played. */
    val theirNote: String? = null,
    /** The game's introduction, until your first guess. */
    val intro: String? = null,
    val pass: Boolean? = null,
    val mark: String? = null,
    val matched: Int = 0,
    val good: Int = 0,
    val guessed: Int = 0,
    val done: Boolean = false,
    val busy: Boolean = true,
)

/**
 * Guess the master's moves: the game starts where it reached your line, the opponent's
 * moves play out with their threat in red, and you guess each of the master's moves.
 * Your guess is kept only if it was the master's; the game always goes on as it was played.
 */
class MasterGameViewModel(application: Application, private val id: String) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val game = MasterGames.byId(id)
    private val replay = game?.let { ChessLines.replay(it.moves) }
    private val sounds = BoardSounds()
    private val speech = MoveSpeaker(application)
    private val state = MutableStateFlow(MasterGameUi(game = game))
    val ui: StateFlow<MasterGameUi> = state.asStateFlow()

    private var ply = 0
    private var hints = 0
    private var job: Job? = null

    init {
        restart()
    }

    fun restart() {
        val g = game ?: return
        val r = replay ?: return
        job?.cancel()
        ply = g.start
        hints = 0
        state.value = MasterGameUi(
            game = g, fen = r.fens[ply], lastUci = r.uci.getOrNull(ply - 1), busy = true,
            status = "The game reached your line here.", intro = g.intro,
        )
        advance()
    }

    /** Plays the opponent's moves until it's your guess, or the stretch is over. */
    private fun advance() {
        val g = game ?: return
        val r = replay ?: return
        job = viewModelScope.launch {
            while (ply < g.end && (ply % 2 == 0) != g.userWhite) {
                delay(OPPONENT_DELAY_MS)
                val san = g.moves[ply]
                if (app.phoneSounds) speech.say(san)
                state.update {
                    it.copy(
                        fen = r.fens[ply + 1], lastUci = r.uci[ply], selected = null, targets = emptySet(),
                        masterUci = null, wrongUci = null, threatUci = g.threats[ply],
                        theirNote = g.notes[ply]?.let { n -> "${moveLabel(ply)}$san  $n" },
                    )
                }
                ply++
            }
            if (ply >= g.end) {
                finish()
            } else {
                hints = 0
                state.update { it.copy(busy = false, status = "Your move: what did ${g.master} play?") }
            }
        }
    }

    fun tap(square: Int) {
        val s = state.value
        if (s.busy || s.done) return
        when (val tap = MoveInput.tap(s.fen, s.selected, square)) {
            is MoveInput.Tap.Play -> guess(tap.uci)
            is MoveInput.Tap.Select -> state.update { it.copy(selected = tap.square, targets = MoveInput.targets(s.fen, tap.square)) }
        }
    }

    private fun guess(uci: String) {
        val g = game ?: return
        val r = replay ?: return
        val master = r.uci[ply]
        val san = g.moves[ply]
        // A hint counts as help: the move is shown, not guessed.
        val verdict = if (hints > 0) Guess.Miss else MasterGames.judge(g, ply, uci, master)
        if (app.phoneSounds) if (verdict == Guess.Miss) sounds.illegal() else speech.say(san)
        val words = when (verdict) {
            Guess.Match -> "Yes, ${g.master} played $san."
            Guess.Good -> "Good too: the engine likes your move. ${g.master} played $san."
            Guess.Miss -> "${g.master} played $san."
        }
        state.update {
            it.copy(
                busy = true, fen = r.fens[ply + 1], lastUci = master, selected = null, targets = emptySet(),
                masterUci = master, threatUci = null, wrongUci = if (verdict == Guess.Miss && !uci.equals(master, true)) uci else null,
                pass = verdict != Guess.Miss, mark = words, status = words, intro = null, theirNote = null,
                yourNote = g.notes[ply]?.let { n -> "${moveLabel(ply)}$san  $n" },
                guessed = it.guessed + 1,
                matched = it.matched + if (verdict == Guess.Match) 1 else 0,
                good = it.good + if (verdict == Guess.Good) 1 else 0,
            )
        }
        ply++
        job = viewModelScope.launch {
            delay(AFTER_GUESS_MS)
            advance()
        }
    }

    /** First the piece, then the move, as in the puzzles. A hint means the move isn't counted as guessed. */
    fun hint() {
        val s = state.value
        val r = replay ?: return
        if (s.busy || s.done) return
        val master = r.uci[ply]
        hints++
        if (hints == 1) {
            val from = squareIndex(master.substring(0, 2))
            state.update { it.copy(selected = from, targets = MoveInput.targets(s.fen, from), status = "This piece moves.") }
        } else {
            state.update { it.copy(masterUci = master, status = "The arrow shows ${game?.master}'s move.") }
        }
    }

    private fun finish() {
        val g = game ?: return
        val rest = g.moves.size - g.end
        state.update {
            it.copy(
                busy = false, done = true, threatUci = null,
                status = "You found ${it.matched} of ${it.guessed}" + (if (it.good > 0) ", and ${it.good} more as good." else "."),
                theirNote = "The game went on for ${(rest + 1) / 2} more moves: ${g.result}, ${g.event}.",
            )
        }
    }

    private fun moveLabel(at: Int): String = "${at / 2 + 1}${if (at % 2 == 0) ". " else "… "}"

    override fun onCleared() {
        sounds.release()
        speech.release()
    }

    class Factory(private val id: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            return MasterGameViewModel(application, id) as T
        }
    }

    companion object {
        private const val OPPONENT_DELAY_MS = 700L
        private const val AFTER_GUESS_MS = 1_600L
    }
}
