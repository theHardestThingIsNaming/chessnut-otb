package app.chessnut.otb.chess

import app.chessnut.otb.pgn.GameHeaders
import app.chessnut.otb.pgn.applyHeaders
import app.chessnut.otb.pgn.movetextOf
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

data class ReconstructedGame(
    val sanMoves: List<String>,
    val isComplete: Boolean,
    val stopReason: String?,
) {
    fun toPgn(headers: GameHeaders = GameHeaders()): String = applyHeaders(movetextOf(sanMoves), headers)
}

/**
 * Turns the placement list a Chessnut board stores (one FEN piece-placement
 * per recorded position, no side to move) back into SAN.
 *
 * Walks from the standard initial position. Each new placement must be the
 * result of exactly one legal move. Castling, en passant and promotion are
 * whatever legal move produces that placement. A placement that no legal
 * move explains stops the walk and keeps the moves already found.
 */
object StoredGameReconstructor {
    private val initialPlacement: String = placementOf(Board().fen)

    fun reconstruct(placements: List<String>): ReconstructedGame {
        val frames = collapseDuplicates(placements.map { placementOf(it) })
        if (frames.isEmpty()) {
            return ReconstructedGame(emptyList(), isComplete = false, stopReason = "No positions.")
        }
        if (frames.first() != initialPlacement) {
            return ReconstructedGame(
                emptyList(),
                isComplete = false,
                stopReason = "The recording does not start from the initial position.",
            )
        }

        val board = Board()
        val sans = mutableListOf<String>()
        for (index in 1 until frames.size) {
            val target = frames[index]
            val move = board.legalMoves().firstOrNull { matches(board, it, target) }
            if (move == null) {
                // The Air records the lift (square empty) before the drop. Skip those frames.
                continue
            }
            sans += sanOf(board, move)
            board.doMove(move)
        }
        if (sans.isEmpty() && frames.size > 1) {
            return ReconstructedGame(
                emptyList(),
                isComplete = false,
                stopReason = "No legal move matched the recorded positions.",
            )
        }
        return ReconstructedGame(sans, isComplete = true, stopReason = null)
    }

    private fun matches(board: Board, move: Move, target: String): Boolean {
        val next = board.clone()
        next.doMove(move)
        return placementOf(next.fen) == target
    }

    private fun sanOf(board: Board, move: Move): String {
        val list = MoveList(board.fen)
        list.add(move)
        return list.toSan().trim()
    }

    private fun placementOf(fen: String): String = fen.trim().substringBefore(' ')

    private fun collapseDuplicates(placements: List<String>): List<String> {
        val out = ArrayList<String>(placements.size)
        for (placement in placements) {
            if (out.lastOrNull() != placement) out += placement
        }
        return out
    }
}
