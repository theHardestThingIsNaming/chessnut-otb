package app.chessnut.otb.play

/** What a settled board reading means against the game's position. */
sealed interface BoardReading {
    /** The board shows the position. */
    data object Same : BoardReading

    /** The board shows one legal move played from the position. */
    data class Move(val uci: String) : BoardReading

    /** Pieces lifted and nothing new put down: a move under way, never a mistake. */
    data class InHand(val lifted: List<String>) : BoardReading

    /** Anything else, such as castling half done or a piece on the wrong square. */
    data class Unexpected(val squares: List<String>) : BoardReading
}

/**
 * The one place that reads the board against a position, for every screen that plays on it.
 * Screens agree on what a reading means; only what they do with it differs.
 */
object BoardRead {
    /**
     * How long an [BoardReading.Unexpected] board must stay that way before it's named: a move
     * passes through such readings (castling, a capture), and naming them flickers lights.
     */
    const val MISMATCH_MS = 2_000L

    /**
     * The board shows an illegal move by the side to move: one of its pieces gone from one square and
     * on another (empty, or the opponent's), where no legal move puts it. Returned as "e2e5". Halves of
     * legal moves don't count: the king's step in castling, a pawn's step in an en passant capture,
     * and a pawn set on the last rank before it's swapped for the new piece.
     */
    fun illegalMove(fen: String, placement: String): String? {
        if (PlayPosition.uciMatching(fen, placement) != null) return null
        val want = squares(fen)
        val have = squares(placement)
        val changed = (0 until 64).filter { want[it] != have[it] }
        if (changed.size != 2) return null
        val white = fen.split(' ').getOrNull(1) != "b"
        fun mine(c: Char) = c != '.' && c.isUpperCase() == white
        val from = changed.firstOrNull { mine(want[it]) && have[it] == '.' } ?: return null
        val to = changed.first { it != from }
        val piece = want[from]
        if (have[to] != piece || mine(want[to])) return null
        val uci = name(from) + name(to)
        val pawn = piece.lowercaseChar() == 'p'
        val king = piece.lowercaseChar() == 'k'
        if (king && from / 8 == to / 8 && kotlin.math.abs(from % 8 - to % 8) == 2) return null
        if (pawn && name(to) == fen.split(' ').getOrNull(3)) return null
        if (pawn && (to / 8 == 0 || to / 8 == 7)) return null
        return uci
    }

    private fun name(i: Int) = "${'a' + i % 8}${8 - i / 8}"

    private fun squares(fen: String): CharArray {
        val out = CharArray(64) { '.' }
        var i = 0
        for (c in fen.substringBefore(' ')) when {
            c == '/' -> continue
            c.isDigit() -> i += c - '0'
            i < 64 -> out[i++] = c
        }
        return out
    }

    /** At most this many wrong squares are lit; more would only confuse. */
    const val MAX_LIT = 8

    fun read(fen: String, placement: String): BoardReading {
        if (PlayPosition.matches(fen, placement)) return BoardReading.Same
        PlayPosition.uciMatching(fen, placement)?.let { return BoardReading.Move(it) }
        val wrong = PlayPosition.differences(fen, placement)
        val lifted = liftedFrom(fen, placement)
        return if (lifted != null) BoardReading.InHand(lifted) else BoardReading.Unexpected(wrong)
    }

    /** The squares lit for an unexpected board, or none when there are too many to help. */
    fun lightsFor(reading: BoardReading.Unexpected): List<String> =
        if (reading.squares.size <= MAX_LIT) reading.squares else emptyList()

    /** Squares emptied, when every piece still on the board stands where the position has it. */
    private fun liftedFrom(fen: String, placement: String): List<String>? {
        val wrong = PlayPosition.differences(fen, placement)
        if (wrong.isEmpty()) return null
        val emptied = PlayPosition.emptiedSquares(fen, placement)
        // Every difference is a square that's been emptied: nothing new was put down.
        return if (emptied.size == wrong.size) emptied else null
    }
}
