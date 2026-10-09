package app.chessnut.otb.openings

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

data class Opening(val eco: String, val name: String, val san: List<String>) {
    val isGambit: Boolean get() = name.contains("gambit", ignoreCase = true)
}

/**
 * Lichess opening book (CC0). [name] is the longest named line that is a prefix of [san].
 * [gambits] are named gambit lines that continue the current position in one move.
 */
data class OpeningView(val name: String?, val eco: String?, val gambits: List<Opening>)

object OpeningBook {
    private val lines: List<Opening> by lazy { load() }
    private val byPrefix: Map<List<String>, Opening> by lazy { lines.associateBy { it.san } }

    fun view(san: List<String>): OpeningView {
        var best: Opening? = null
        for (n in san.size downTo 1) {
            val hit = byPrefix[san.take(n)]
            if (hit != null) {
                best = hit
                break
            }
        }
        val gambits = if (san.size >= 24) emptyList() else lines.filter { line ->
            line.isGambit && line.san.size == san.size + 1 && line.san.dropLast(1) == san
        }.distinctBy { it.name }
        return OpeningView(best?.name, best?.eco, gambits)
    }

    private fun load(): List<Opening> {
        val text = OpeningBook::class.java.getResource("/openings.tsv")?.readText() ?: return emptyList()
        return text.lineSequence().drop(1).mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val san = sanOf(parts[2]) ?: return@mapNotNull null
            Opening(parts[0], parts[1], san)
        }.toList()
    }

    private fun sanOf(pgn: String): List<String>? {
        val list = MoveList()
        if (runCatching { list.loadFromSan(pgn) }.isFailure) return null
        val board = Board()
        val moves = MoveList(board.fen)
        for (move in list) {
            if (move !in board.legalMoves()) return null
            moves.add(move)
            board.doMove(move)
        }
        return moves.toSan().trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }
}
