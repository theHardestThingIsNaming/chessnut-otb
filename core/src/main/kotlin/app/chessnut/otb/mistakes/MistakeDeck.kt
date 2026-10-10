package app.chessnut.otb.mistakes

import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.games.SavedGame

/** A position where a bad move was played, to be solved again. [fen] is before the move. */
data class Mistake(
    val gameId: String,
    val ply: Int,
    val fen: String,
    val playedSan: String,
    val bestUci: String?,
    val classification: MoveClass,
    val whiteToMove: Boolean,
    val gameTitle: String,
) {
    val key: String get() = "$gameId:$ply"

    /** "12... Qh5", in the usual move-number style. */
    val moveLabel: String get() = "${ply / 2 + 1}${if (whiteToMove) "." else "..."} $playedSan"
}

object MistakeDeck {
    /**
     * Mistakes and blunders from one analysed game. When [myName] names one of
     * the players, only that side's moves count; otherwise both sides' do.
     */
    fun collect(game: SavedGame, analysis: GameAnalysis, myName: String): List<Mistake> {
        if (game.sanMoves.isEmpty()) return emptyList()
        val replay = runCatching { ChessLines.replay(game.sanMoves) }.getOrNull() ?: return emptyList()
        val report = AccuracyCalculator.report(game.sanMoves, replay.uci, analysis)
        val side = mySide(game, myName)
        return report.moves.mapNotNull { move ->
            if (move.classification != MoveClass.Mistake && move.classification != MoveClass.Blunder) return@mapNotNull null
            if (side != null && move.whiteMoved != side) return@mapNotNull null
            val fen = replay.fens.getOrNull(move.ply) ?: return@mapNotNull null
            Mistake(game.id, move.ply, fen, move.san, move.bestUci, move.classification, move.whiteMoved, game.title)
        }
    }

    /** True for White, false for Black, null when the game does not say which side is the player. */
    fun mySide(game: SavedGame, myName: String): Boolean? {
        val me = myName.trim()
        if (me.isEmpty()) return null
        val white = game.white.equals(me, ignoreCase = true) || game.white == "Me"
        val black = game.black.equals(me, ignoreCase = true) || game.black == "Me"
        return when {
            white && !black -> true
            black && !white -> false
            else -> null
        }
    }

    /**
     * Whether an answer fixes the mistake: the engine's move, or one that keeps
     * the mover's winning chances within [TOLERANCE] of it.
     */
    fun solved(answerUci: String, bestUci: String?, winBest: Double?, winAnswer: Double?): Boolean {
        if (bestUci != null && answerUci.equals(bestUci, ignoreCase = true)) return true
        if (winBest == null || winAnswer == null) return false
        return winBest - winAnswer <= TOLERANCE
    }

    /** Win-percentage points an answer may give away against the engine's best and still count. */
    const val TOLERANCE = 5.0
}
