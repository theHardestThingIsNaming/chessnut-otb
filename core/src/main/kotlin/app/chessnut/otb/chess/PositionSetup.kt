package app.chessnut.otb.chess

import com.github.bhlangonijr.chesslib.Board

/**
 * Turns pieces set up on a board into a full position to play or analyse
 * from. The board only knows where pieces stand, so castling is allowed
 * wherever king and rook are still on their home squares, and there is no
 * en passant square.
 */
object PositionSetup {
    sealed interface Result {
        data class Ok(val fen: String) : Result
        data class Problem(val reason: String) : Result
    }

    fun fen(placement: String, whiteToMove: Boolean): Result {
        val squares = squaresOf(placement)
        val whiteKings = squares.count { it == 'K' }
        val blackKings = squares.count { it == 'k' }
        if (whiteKings != 1 || blackKings != 1) return Result.Problem("Each side needs exactly one king on the board.")
        val backRanks = (0 until 8) + (56 until 64)
        if (backRanks.any { squares[it] == 'P' || squares[it] == 'p' }) return Result.Problem("A pawn can't stand on the first or last rank.")
        val castling = buildString {
            if (squares[squareIndex("e1")] == 'K') {
                if (squares[squareIndex("h1")] == 'R') append('K')
                if (squares[squareIndex("a1")] == 'R') append('Q')
            }
            if (squares[squareIndex("e8")] == 'k') {
                if (squares[squareIndex("h8")] == 'r') append('k')
                if (squares[squareIndex("a8")] == 'r') append('q')
            }
        }.ifEmpty { "-" }
        val fen = "${placementOf(squares)} ${if (whiteToMove) "w" else "b"} $castling - 0 1"
        val board = runCatching { Board().apply { loadFromFen(fen) } }.getOrNull()
            ?: return Result.Problem("That position can't be read.")
        // The side that just moved can't have left its own king in check.
        val other = Board().apply { loadFromFen(fen.replaceFirst(if (whiteToMove) " w " else " b ", if (whiteToMove) " b " else " w ")) }
        if (other.isKingAttacked) {
            return Result.Problem("${if (whiteToMove) "Black" else "White"} is in check, so it can't be ${if (whiteToMove) "White" else "Black"}'s move.")
        }
        if (board.legalMoves().isEmpty()) {
            return Result.Problem(if (board.isKingAttacked) "That side is already checkmated." else "That side has no legal move: it's stalemate.")
        }
        return Result.Ok(fen)
    }

    private fun placementOf(squares: CharArray): String = (7 downTo 0).joinToString("/") { rank ->
        buildString {
            var empty = 0
            for (file in 0 until 8) {
                val c = squares[rank * 8 + file]
                if (c == '.') {
                    empty++
                } else {
                    if (empty > 0) append(empty)
                    empty = 0
                    append(c)
                }
            }
            if (empty > 0) append(empty)
        }
    }
}
