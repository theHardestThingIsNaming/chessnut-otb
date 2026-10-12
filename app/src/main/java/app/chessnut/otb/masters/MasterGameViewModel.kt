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
import app.chessnut.otb.games.PgnImport
import app.chessnut.otb.openings.Guess
import app.chessnut.otb.openings.MasterGame
import app.chessnut.otb.openings.MasterGames
import app.chessnut.otb.openings.MoveIdeas
import app.chessnut.otb.play.BOARD_SETTLE_MS
import app.chessnut.otb.play.PlayPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
    /** Your moves in the line itself, before the master's own moves begin. */
    val lineMatched: Int = 0,
    val lineGuessed: Int = 0,
    val done: Boolean = false,
    val busy: Boolean = true,
    /** Playing out the rest of the game after your guesses. */
    val watching: Boolean = false,
    /** The whole game has been shown. */
    val over: Boolean = false,
    /** Played on the connected board: moves are lit and read from it. */
    val onBoard: Boolean = false,
    /** Why the game is on the screen with a board connected, or what the board is waiting for. */
    val boardNote: String? = null,
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

    /** On the board: the position it has to show before play goes on, and who's waiting for it. */
    private var physical = false
    /** Whether Play had the board engaged before this game took it, to give it back as it was. */
    private val engagedBefore = app.playEngaged
    private var boardWaiter: CompletableDeferred<Unit>? = null
    private var boardTarget: String? = null
    private var settleJob: Job? = null

    init {
        restart()
        viewModelScope.launch {
            app.placements.collect { placement ->
                settleJob?.cancel()
                // A slide reports every square the piece crosses. Wait until it stops.
                settleJob = launch {
                    delay(BOARD_SETTLE_MS)
                    onSettled(placement)
                }
            }
        }
    }

    private fun onSettled(placement: String) {
        if (!physical) return
        val target = boardTarget
        if (target != null) {
            if (PlayPosition.matches(target, placement)) {
                boardTarget = null
                light(emptyList())
                boardWaiter?.complete(Unit)
            } else {
                // Light what still has to change, when there are few enough squares to help.
                val wrong = PlayPosition.differences(target, placement)
                if (wrong.size <= MAX_LIT) light(wrong)
            }
            return
        }
        val s = state.value
        if (s.busy || s.done) return
        PlayPosition.uciMatching(s.fen, placement)?.let { guess(it) }
    }

    /** Waits until the board shows [fen], lighting the squares that differ. */
    private suspend fun awaitBoard(fen: String, lit: List<String>) {
        if (!physical) return
        val placement = app.latestPlacement
        if (placement != null && PlayPosition.matches(fen, placement)) return
        val waiter = CompletableDeferred<Unit>()
        boardWaiter = waiter
        boardTarget = fen
        light(lit)
        waiter.await()
    }

    private fun light(squares: List<String>) {
        val session = app.boardSession ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { session.setLeds(squares) } }
    }

    private fun squaresOf(uci: String): List<String> = listOf(uci.substring(0, 2), uci.substring(2, 4))

    fun restart() {
        val g = game ?: return
        val r = replay ?: return
        job?.cancel()
        // From move 1: your line first, then the master's own moves.
        ply = 0
        hints = 0
        physical = app.boardSession != null
        app.boardBorrowed = physical
        // The board's own game recorder would speak and save these moves too.
        if (physical) app.playEngaged = true
        boardTarget = null
        boardWaiter?.cancel()
        val start = r.fens[0]
        val ready = !physical || app.latestPlacement?.let { PlayPosition.matches(start, it) } == true
        state.value = MasterGameUi(
            game = g, fen = start, busy = true, onBoard = physical, intro = g.intro,
            status = if (ready) "Your line first, from move 1." else "Set up the starting position.",
            boardNote = if (ready) null else "Squares still to set up are lit.",
        )
        job = viewModelScope.launch {
            if (!ready) {
                val wrong = app.latestPlacement?.let { PlayPosition.differences(start, it) }.orEmpty()
                awaitBoard(start, if (wrong.size <= MAX_LIT) wrong else emptyList())
                state.update { it.copy(boardNote = null, status = "Your line first, from move 1.") }
            }
            playOn()
        }
    }

    /** The note for the move at [at]: the game's own note, or, in your line, the opening notes. */
    private fun noteAt(at: Int): String? {
        val g = game ?: return null
        val r = replay ?: return null
        val note = g.notes[at] ?: if (at < g.start) MoveIdeas.explain(g.moves.take(at + 1), r.fens[at], r.uci[at]) else null
        return note?.let { "${moveLabel(at)}${g.moves[at]}  $it" }
    }

    private fun advance() {
        job = viewModelScope.launch { playOn() }
    }

    /** Plays the opponent's moves until it's your guess, or the stretch is over. */
    private suspend fun playOn() {
        val g = game ?: return
        val r = replay ?: return
        run {
            while (ply < g.end && (ply % 2 == 0) != g.userWhite) {
                if (ply == g.start) lineEnds()
                delay(OPPONENT_DELAY_MS)
                val san = g.moves[ply]
                if (app.phoneSounds) speech.say(san)
                state.update {
                    it.copy(
                        fen = r.fens[ply + 1], lastUci = r.uci[ply], selected = null, targets = emptySet(),
                        masterUci = null, wrongUci = null, threatUci = g.threats[ply],
                        theirNote = noteAt(ply),
                    )
                }
                // On the board, you play the opponent's move: it's lit until the board shows it.
                awaitBoard(r.fens[ply + 1], squaresOf(r.uci[ply]))
                ply++
            }
            if (ply >= g.end) {
                finish()
            } else {
                if (ply == g.start) lineEnds()
                hints = 0
                val ask = if (ply < g.start) "Your move: play your line." else "Your move: what did ${g.master} play?"
                state.update { it.copy(busy = false, status = ask) }
            }
        }
    }

    /** Marks where the line ends and the master's own moves begin. */
    private fun lineEnds() {
        val g = game ?: return
        state.update { it.copy(intro = "Your line ends here. From now on, guess ${g.master}'s moves.") }
    }

    fun tap(square: Int) {
        val s = state.value
        // On the board, the guess comes from the pieces.
        if (s.busy || s.done || physical) return
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
        val inLine = ply < g.start
        val words = when {
            inLine && verdict == Guess.Match -> "Yes: $san, your line's move."
            inLine -> "Your line plays $san."
            verdict == Guess.Match -> "Yes, ${g.master} played $san."
            verdict == Guess.Good -> "Good too: the engine likes your move. ${g.master} played $san."
            else -> "${g.master} played $san."
        }
        state.update {
            it.copy(
                busy = true, fen = r.fens[ply + 1], lastUci = master, selected = null, targets = emptySet(),
                masterUci = master, threatUci = null, wrongUci = if (verdict == Guess.Miss && !uci.equals(master, true)) uci else null,
                pass = verdict != Guess.Miss, mark = words, status = words, intro = null, theirNote = null,
                yourNote = noteAt(ply),
                lineGuessed = it.lineGuessed + if (inLine) 1 else 0,
                lineMatched = it.lineMatched + if (inLine && verdict == Guess.Match) 1 else 0,
                guessed = it.guessed + if (inLine) 0 else 1,
                matched = it.matched + if (!inLine && verdict == Guess.Match) 1 else 0,
                good = it.good + if (!inLine && verdict == Guess.Good) 1 else 0,
            )
        }
        val after = r.fens[ply + 1]
        ply++
        job = viewModelScope.launch {
            // On the board, a different guess has to be put right: the changed squares are lit.
            if (physical && !uci.equals(master, true)) {
                state.update { it.copy(boardNote = "Put the board right: play ${g.master}'s move instead.") }
                awaitBoard(after, (squaresOf(uci) + squaresOf(master)).distinct())
                state.update { it.copy(boardNote = null) }
            }
            delay(AFTER_GUESS_MS)
            advance()
        }
    }

    /** After your guesses: plays the rest of the game out on the board, one move a second. */
    fun watchRest() {
        val g = game ?: return
        val r = replay ?: return
        if (!state.value.done || state.value.watching) return
        job?.cancel()
        state.update { it.copy(watching = true, masterUci = null, wrongUci = null, threatUci = null, pass = null, mark = null, yourNote = null) }
        job = viewModelScope.launch {
            while (ply < g.moves.size) {
                delay(WATCH_MS)
                state.update { it.copy(fen = r.fens[ply + 1], lastUci = r.uci[ply], theirNote = "${moveLabel(ply)}${g.moves[ply]}") }
                ply++
            }
            state.update { it.copy(watching = false, over = true, theirNote = outcome(g)) }
        }
    }

    /**
     * Saves the master game to your games, in the Master games folder, for analysis.
     * Returns its id. It adds nothing to your mistakes: neither player is you.
     */
    fun saveForAnalysis(): String? {
        val g = game ?: return null
        val movetext = g.moves.mapIndexed { i, san -> if (i % 2 == 0) "${i / 2 + 1}. $san" else san }.joinToString(" ")
        val pgn = """[Event "${g.event}"]
            |[White "${g.white}"]
            |[Black "${g.black}"]
            |[Date "${g.year}.??.??"]
            |[Result "${g.result}"]
            |
            |$movetext ${g.result}
            |""".trimMargin()
        val saved = runCatching { PgnImport.parse(pgn, System.currentTimeMillis()) }.getOrNull() ?: return null
        if (app.store.addIfNew(saved)) {
            app.store.moveToFolder(saved.id, MASTER_FOLDER)
            app.analyse(saved.id)
            app.refreshGames()
        }
        return saved.id
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
            light(listOf(master.substring(0, 2)))
        } else {
            state.update { it.copy(masterUci = master, status = "The arrow shows ${game?.master}'s move.") }
            light(squaresOf(master))
        }
    }

    private fun finish() {
        val g = game ?: return
        val rest = g.moves.size - g.end
        state.update {
            it.copy(
                busy = false, done = true, threatUci = null,
                status = "Your line: ${it.lineMatched} of ${it.lineGuessed}. ${g.master}'s moves: ${it.matched} of ${it.guessed}" +
                    (if (it.good > 0) ", and ${it.good} more as good." else "."),
                theirNote = "The game went on for ${(rest + 1) / 2} more moves. Play it out to see how it ended.",
            )
        }
    }

    /** "White won after 17 moves." */
    private fun outcome(g: MasterGame): String {
        val moves = (g.moves.size + 1) / 2
        return when (g.result) {
            "1-0" -> "White won after $moves moves."
            "0-1" -> "Black won after $moves moves."
            "1/2-1/2" -> "A draw after $moves moves."
            else -> "The game ended after $moves moves."
        }
    }

    private fun moveLabel(at: Int): String = "${at / 2 + 1}${if (at % 2 == 0) ". " else "… "}"

    override fun onCleared() {
        if (physical) {
            app.boardBorrowed = false
            app.playEngaged = engagedBefore
            light(emptyList())
        }
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
        private const val WATCH_MS = 1_000L
        const val MASTER_FOLDER = "Master games"
        /** Up to this many squares to put right are lit; more would only confuse. */
        private const val MAX_LIT = 8
    }
}
