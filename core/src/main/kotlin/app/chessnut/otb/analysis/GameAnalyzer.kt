package app.chessnut.otb.analysis

import app.chessnut.otb.engine.UciEngine
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

/** Positions and UCI moves of a game given in SAN. */
data class ReplayedGame(val fens: List<String>, val uci: List<String>)

object ChessLines {
    fun replay(sanMoves: List<String>): ReplayedGame {
        val board = Board()
        val fens = mutableListOf(board.fen)
        val uci = mutableListOf<String>()
        for (san in sanMoves) {
            val move = MoveList(board.fen).apply { loadFromSan(san) }.last()
            uci += move.toString().lowercase()
            board.doMove(move)
            fens += board.fen
        }
        return ReplayedGame(fens, uci)
    }

    /** SAN for a UCI line from [fen]; stops at the first move that does not parse. */
    fun sanOf(fen: String, uciMoves: List<String>): List<String> {
        val board = Board().apply { loadFromFen(fen) }
        val list = MoveList(fen)
        for (uci in uciMoves) {
            val move = runCatching { Move(uci, board.sideToMove) }.getOrNull() ?: break
            if (move !in board.legalMoves()) break
            list.add(move)
            board.doMove(move)
        }
        if (list.isEmpty()) return emptyList()
        return list.toSan().trim().split(Regex("""\s+"""))
    }

    /**
     * [fen] with the other side to move, so the engine's best move there is the threat.
     * Null when the side to move is in check, since passing would be illegal.
     */
    fun passFen(fen: String): String? {
        val parts = fen.split(' ').toMutableList()
        if (parts.size < 4) return null
        val board = Board().apply { loadFromFen(fen) }
        if (board.isKingAttacked) return null
        parts[1] = if (parts[1] == "b") "w" else "b"
        parts[3] = "-"
        return parts.joinToString(" ")
    }

    fun terminalOf(fen: String): Terminal? {
        val board = Board().apply { loadFromFen(fen) }
        return when {
            board.isMated -> Terminal.Checkmate
            board.isStaleMate -> Terminal.Stalemate
            else -> null
        }
    }
}

/** Runs the engine over every position of a game. */
class GameAnalyzer(
    private val engine: UciEngine,
    private val nodes: Long = DEFAULT_NODES,
    private val multiPv: Int = 3,
) {
    suspend fun analyse(sanMoves: List<String>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): GameAnalysis {
        engine.start()
        val fens = ChessLines.replay(sanMoves).fens
        val positions = fens.mapIndexed { index, fen ->
            onProgress(index, fens.size)
            val terminal = ChessLines.terminalOf(fen)
            if (terminal != null) {
                PositionEval(fen, emptyList(), terminal)
            } else {
                PositionEval(fen, engine.analyse(fen, nodes, multiPv))
            }
        }
        onProgress(fens.size, fens.size)
        return GameAnalysis(engine.name, nodes, positions)
    }

    companion object {
        const val DEFAULT_NODES = 1_000_000L
    }
}
