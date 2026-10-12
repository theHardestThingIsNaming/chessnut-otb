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
