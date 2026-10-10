package app.chessnut.otb.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * Turns taps and drags on an on-screen board into moves, the same way on every
 * screen: tap one of your pieces to pick it up, tap a legal square to play,
 * tap anything else to put it down. Pawns always promote to a queen.
 */
object MoveInput {
    sealed interface Tap {
        /** Play this UCI move. */
        data class Play(val uci: String) : Tap

        /** Hold this square now, or nothing when null. */
        data class Select(val square: Int?) : Tap
    }

    fun tap(fen: String, selected: Int?, square: Int): Tap {
        val board = boardOf(fen)
        if (selected != null) {
            move(board, selected, square)?.let { return Tap.Play(it) }
        }
        val piece = board.getPiece(Square.valueOf(squareName(square).uppercase()))
        val own = piece != Piece.NONE && piece.pieceSide == board.sideToMove
        return Tap.Select(if (own && selected != square) square else null)
    }

    /** The UCI move for dragging a piece from [from] to [to], or null when that is not legal. */
    fun drop(fen: String, from: Int, to: Int): String? = if (from == to) null else move(boardOf(fen), from, to)

    /** Where the piece on [from] can legally go. */
    fun targets(fen: String, from: Int?): Set<Int> {
        if (from == null) return emptySet()
        return boardOf(fen).legalMoves()
            .filter { indexOf(it.from) == from }
            .map { indexOf(it.to) }
            .toSet()
    }

    private fun move(board: Board, from: Int, to: Int): String? = board.legalMoves()
        .filter { indexOf(it.from) == from && indexOf(it.to) == to }
        .firstOrNull { it.promotion == Piece.NONE || it.promotion.pieceType == PieceType.QUEEN }
        ?.let(::uciOf)

    private fun boardOf(fen: String) = Board().apply { loadFromFen(fen) }

    private fun indexOf(square: Square) = squareIndex(square.value())

    private fun uciOf(move: Move) = move.toString().lowercase()
}
