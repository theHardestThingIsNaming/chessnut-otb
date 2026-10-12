package app.chessnut.otb.puzzles

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.repertoire.LineReview
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.play.PlayPosition

/**
 * A puzzle from the Lichess puzzle database (CC0): [fen] is the position
 * before your opponent's move, the first of [moves]; the rest alternate
 * between your answers and their replies, ending on yours.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    val rating: Int,
    val themes: List<String>,
    /** Where it's from, such as "From a Scotch game" or "Rook ending". */
    val origin: String,
) {
    /** You solve for this side: the one not making the first move. */
    val solverWhite: Boolean get() = fen.split(' ')[1] == "b"

    /** The main ideas, in words: "Fork, Discovered attack". */
    val ideas: List<String> get() = themes.mapNotNull { THEME_NAMES[it] }

    companion object {
        val THEME_NAMES = linkedMapOf(
            "mateIn1" to "Mate in 1", "mateIn2" to "Mate in 2", "mateIn3" to "Mate in 3", "mateIn4" to "Mate in 4",
            "backRankMate" to "Back-rank mate", "smotheredMate" to "Smothered mate", "fork" to "Fork", "pin" to "Pin",
            "skewer" to "Skewer", "discoveredAttack" to "Discovered attack", "doubleCheck" to "Double check",
            "hangingPiece" to "Loose piece", "trappedPiece" to "Trapped piece", "sacrifice" to "Sacrifice",
            "deflection" to "Deflection", "attraction" to "Attraction", "clearance" to "Clearance",
            "interference" to "Interference", "intermezzo" to "In-between move", "quietMove" to "Quiet move",
            "defensiveMove" to "Defence", "xRayAttack" to "X-ray", "zugzwang" to "Zugzwang",
            "promotion" to "Promotion", "underPromotion" to "Underpromotion", "advancedPawn" to "Passed pawn",
            "exposedKing" to "Exposed king", "kingsideAttack" to "Kingside attack", "queensideAttack" to "Queenside attack",
            "capturingDefender" to "Removing the defender", "attackingF2F7" to "Attack on f7",
        )
    }
}

/**
 * Playing one puzzle through. [start] makes the opponent's move; each right
 * [answer] brings their reply until the line is done. A different mating move
 * counts too, as on Lichess.
 */
class PuzzleRun(val puzzle: Puzzle) {
    var fen: String = puzzle.fen
        private set
    private var step = 0

    /** The move you're looking for now, or null once solved. */
    val expected: String? get() = puzzle.moves.getOrNull(step)?.takeIf { step % 2 == 1 }

    val solved: Boolean get() = step >= puzzle.moves.size

    sealed class Answer {
        /** Right. [reply] is the opponent's answer, already on the board, or null when [solved]. */
        data class Right(val reply: String?, val solved: Boolean) : Answer()
        data object Wrong : Answer()
    }

    /** Plays the opponent's first move. Returns it. */
    fun start(): String {
        val first = puzzle.moves.first()
        fen = PlayPosition.after(puzzle.fen, first)?.second ?: error("Bad puzzle ${puzzle.id}")
        step = 1
        return first
    }

    fun answer(uci: String): Answer {
        val wanted = expected ?: return Answer.Wrong
        val after = PlayPosition.after(fen, uci) ?: return Answer.Wrong
        val mates = ChessLines.terminalOf(after.second) == Terminal.Checkmate
        if (!uci.equals(wanted, ignoreCase = true) && !mates) return Answer.Wrong
        fen = after.second
        step++
        if (mates || solved) {
            step = puzzle.moves.size
            return Answer.Right(null, solved = true)
        }
        val reply = puzzle.moves[step]
        fen = PlayPosition.after(fen, reply)?.second ?: error("Bad puzzle ${puzzle.id}")
        step++
        return Answer.Right(reply, solved = solved)
    }
}

/**
 * How often a puzzle comes back. A clean solve returns once, after three days,
 * and a second clean solve finishes it. A miss comes back tomorrow.
 * Opening lines keep the longer 1, 3, 7, 14, 30 day steps.
 */
object PuzzleReview {
    const val CONFIRM_DAYS = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Solved cleanly at least once. A miss waits a day too, so a future date alone doesn't mean solved. */
    fun solved(review: LineReview?): Boolean = review != null && review.box >= 1

    fun grade(previous: LineReview?, clean: Boolean, now: Long): LineReview = when {
        !clean -> LineReview(0, now + DAY_MS)
        previous != null && previous.box >= 1 -> LineReview(previous.box, Long.MAX_VALUE)
        else -> LineReview(1, now + CONFIRM_DAYS * DAY_MS)
    }
}

/** The shipped puzzle sets: middlegame tactics per opening and side, endgames by type. */
object PuzzleSets {
    /** Endgame types in the order shown, with their names. */
    val ENDGAMES = linkedMapOf(
        "pawnEndgame" to "Pawn endings",
        "rookEndgame" to "Rook endings",
        "knightEndgame" to "Knight endings",
        "bishopEndgame" to "Bishop endings",
        "queenEndgame" to "Queen endings",
        "queenRookEndgame" to "Queen and rook endings",
    )

    private val middlegame: List<Pair<Pair<String, Boolean>, Puzzle>> by lazy {
        read("/puzzles-middlegame.tsv").mapNotNull { f ->
            if (f.size < 8) return@mapNotNull null
            val opening = f[0]
            val family = opening.substringBefore(' ')
            (opening to (f[1] == "w")) to Puzzle(f[2], f[5], f[6].split(' '), f[3].toInt(), f[4].split(' '), "From a $family game")
        }
    }

    private val endgame: List<Pair<String, Puzzle>> by lazy {
        read("/puzzles-endgame.tsv").mapNotNull { f ->
            if (f.size < 6) return@mapNotNull null
            f[0] to Puzzle(f[1], f[4], f[5].split(' '), f[2].toInt(), f[3].split(' '), ENDGAMES[f[0]]?.removeSuffix("s") ?: "Endgame")
        }
    }

    /** "mg:Scotch Game:w" or "eg:rookEndgame". */
    fun middlegameKey(opening: String, white: Boolean) = "mg:$opening:${if (white) "w" else "b"}"

    fun endgameKey(type: String) = "eg:$type"

    /** The puzzles of a set, easiest first. */
    fun of(key: String): List<Puzzle> = when {
        key.startsWith("mg:") -> {
            val opening = key.removePrefix("mg:").substringBeforeLast(':')
            val white = key.endsWith(":w")
            middlegame.filter { it.first == (opening to white) }.map { it.second }
        }
        key.startsWith("eg:") -> endgame.filter { it.first == key.removePrefix("eg:") }.map { it.second }
        else -> emptyList()
    }.sortedBy { it.rating }

    /** The set's name: "Scotch Game middlegame tactics" or "Rook endings". */
    fun title(key: String): String = when {
        key.startsWith("mg:") -> "Middlegame tactics"
        else -> ENDGAMES[key.removePrefix("eg:")] ?: "Endgames"
    }

    private fun read(path: String): List<List<String>> =
        PuzzleSets::class.java.getResourceAsStream(path)?.bufferedReader()?.readLines().orEmpty()
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
}
