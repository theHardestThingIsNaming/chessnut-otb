package app.chessnut.otb.analysis

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

/** Engine score from White's point of view. Exactly one of [cp] or [mate] is set. */
data class Score(val cp: Int? = null, val mate: Int? = null) {
    init {
        require((cp == null) != (mate == null)) { "A score is either centipawns or mate" }
    }

    /** Centipawns capped at ±1000, with mate counted as the cap, as Lichess does for win chances. */
    val cappedCp: Int
        get() = when {
            mate != null -> if (mate > 0) CP_CAP else -CP_CAP
            else -> cp!!.coerceIn(-CP_CAP, CP_CAP)
        }

    /** Chance of White winning, 0..100. */
    val whiteWinPercent: Double
        get() = 50 + 50 * (2 / (1 + exp(-0.00368208 * cappedCp)) - 1)

    fun label(): String = when {
        mate != null -> if (mate > 0) "#$mate" else "#-${abs(mate)}"
        else -> {
            val pawns = cp!! / 100.0
            (if (pawns > 0) "+" else "") + String.format(Locale.ROOT, "%.2f", pawns)
        }
    }

    fun forMover(whiteToMove: Boolean): Score = if (whiteToMove) this else negate()

    fun negate(): Score = Score(cp = cp?.let { -it }, mate = mate?.let { -it })

    companion object {
        const val CP_CAP = 1000
        val WhiteMated = Score(cp = -CP_CAP)
        val BlackMated = Score(cp = CP_CAP)
        val Even = Score(cp = 0)
    }
}

data class EngineLine(val score: Score, val uci: List<String>, val depth: Int)

enum class Terminal { Checkmate, Stalemate }

/** One analysed position. [lines] is best first; empty when [terminal] is set. */
data class PositionEval(val fen: String, val lines: List<EngineLine>, val terminal: Terminal? = null) {
    val score: Score
        get() = when (terminal) {
            Terminal.Checkmate -> if (fen.split(' ')[1] == "w") Score.WhiteMated else Score.BlackMated
            Terminal.Stalemate -> Score.Even
            null -> lines.firstOrNull()?.score ?: Score.Even
        }
}

/** Evaluations for every position of a game: index 0 is the start, index n is after move n. */
data class GameAnalysis(val engine: String, val nodes: Long, val positions: List<PositionEval>)
