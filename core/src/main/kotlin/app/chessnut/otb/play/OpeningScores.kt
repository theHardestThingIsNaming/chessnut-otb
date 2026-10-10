package app.chessnut.otb.play

import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.openings.OpeningBook
import kotlin.math.roundToInt

/** Best accuracy, 0..100, for games that reached each opening. */
object OpeningScores {
    fun best(games: List<Pair<SavedGame, GameAnalysis>>, names: List<String>, myName: String): Map<String, Int> {
        val lines = names.mapNotNull { name -> OpeningBook.line(name)?.let { name to it.san } }
        if (lines.isEmpty()) return emptyMap()
        val best = mutableMapOf<String, Double>()
        for ((game, analysis) in games) {
            if (game.sanMoves.isEmpty()) continue
            val uci = runCatching { ChessLines.replay(game.sanMoves).uci }.getOrNull() ?: continue
            val report = AccuracyCalculator.report(game.sanMoves, uci, analysis)
            val score = mine(game, report.whiteAccuracy, report.blackAccuracy, myName) ?: continue
            for ((name, san) in lines) {
                if (game.sanMoves.size < san.size || game.sanMoves.take(san.size) != san) continue
                val current = best[name]
                if (current == null || score > current) best[name] = score
            }
        }
        return best.mapValues { it.value.roundToInt() }
    }

    private fun mine(game: SavedGame, white: Double?, black: Double?, myName: String): Double? {
        val me = myName.trim()
        if (me.isNotEmpty()) {
            val asWhite = game.white.equals(me, ignoreCase = true)
            val asBlack = game.black.equals(me, ignoreCase = true)
            if (asWhite || asBlack) {
                return listOfNotNull(if (asWhite) white else null, if (asBlack) black else null).maxOrNull()
            }
            if (!generic(game.white) || !generic(game.black)) return null
        }
        return listOfNotNull(white, black).maxOrNull()
    }

    private fun generic(name: String): Boolean {
        val n = name.trim()
        return n.isEmpty() || n.equals("White", ignoreCase = true) || n.equals("Black", ignoreCase = true)
    }
}
