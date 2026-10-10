package app.chessnut.otb.play

import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.openings.OpeningBook
import kotlin.math.roundToInt

/** What an opening score needs from one game: who played, the moves, and each side's accuracy. */
data class ScoredGame(
    val white: String,
    val black: String,
    val sanMoves: List<String>,
    val whiteAccuracy: Double?,
    val blackAccuracy: Double?,
) {
    companion object {
        fun of(game: SavedGame, analysis: GameAnalysis): ScoredGame? {
            if (game.sanMoves.isEmpty()) return null
            val uci = runCatching { ChessLines.replay(game.sanMoves).uci }.getOrNull() ?: return null
            val report = AccuracyCalculator.report(game.sanMoves, uci, analysis)
            return ScoredGame(game.white, game.black, game.sanMoves, report.whiteAccuracy, report.blackAccuracy)
        }
    }
}

/** Best accuracy, 0..100, for games that reached each opening. */
object OpeningScores {
    fun best(games: List<Pair<SavedGame, GameAnalysis>>, names: List<String>, myName: String): Map<String, Int> =
        bestOf(games.mapNotNull { (game, analysis) -> ScoredGame.of(game, analysis) }, names, myName)

    fun bestOf(games: List<ScoredGame>, names: List<String>, myName: String): Map<String, Int> {
        val lines = names.mapNotNull { name -> OpeningBook.line(name)?.let { name to it.san } }
        if (lines.isEmpty()) return emptyMap()
        val best = mutableMapOf<String, Double>()
        for (game in games) {
            val score = mine(game.white, game.black, game.whiteAccuracy, game.blackAccuracy, myName) ?: continue
            for ((name, san) in lines) {
                if (game.sanMoves.size < san.size || game.sanMoves.take(san.size) != san) continue
                val current = best[name]
                if (current == null || score > current) best[name] = score
            }
        }
        return best.mapValues { it.value.roundToInt() }
    }

    private fun mine(whiteName: String, blackName: String, white: Double?, black: Double?, myName: String): Double? {
        val me = myName.trim()
        if (me.isNotEmpty()) {
            val asWhite = whiteName.equals(me, ignoreCase = true)
            val asBlack = blackName.equals(me, ignoreCase = true)
            if (asWhite || asBlack) {
                return listOfNotNull(if (asWhite) white else null, if (asBlack) black else null).maxOrNull()
            }
            if (!generic(whiteName) || !generic(blackName)) return null
        }
        return listOfNotNull(white, black).maxOrNull()
    }

    private fun generic(name: String): Boolean {
        val n = name.trim()
        return n.isEmpty() || n.equals("White", ignoreCase = true) || n.equals("Black", ignoreCase = true)
    }
}
