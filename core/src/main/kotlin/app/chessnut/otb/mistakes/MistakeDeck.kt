package app.chessnut.otb.mistakes

import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.Score
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
    /** The opponent's move that led to this position, in UCI; null at the start of the game. */
    val lastUci: String? = null,
) {
    val key: String get() = "$gameId:$ply"

    /** "12... Qh5", in the usual move-number style. */
    val moveLabel: String get() = "${ply / 2 + 1}${if (whiteToMove) "." else "..."} $playedSan"
}

object MistakeDeck {
    /**
     * Mistakes and blunders from one analysed game. When one of [myNames] (the
     * name set in the app, the Lichess username) is a player, only that side's
     * moves count; otherwise both sides' do.
     */
    fun collect(game: SavedGame, analysis: GameAnalysis, myNames: List<String>): List<Mistake> {
        if (game.sanMoves.isEmpty()) return emptyList()
        val replay = runCatching { ChessLines.replay(game.sanMoves) }.getOrNull() ?: return emptyList()
        val report = AccuracyCalculator.report(game.sanMoves, replay.uci, analysis)
        val side = mySide(game, myNames)
        return report.moves.mapNotNull { move ->
            if (move.classification != MoveClass.Mistake && move.classification != MoveClass.Blunder) return@mapNotNull null
            if (side != null && move.whiteMoved != side) return@mapNotNull null
            val fen = replay.fens.getOrNull(move.ply) ?: return@mapNotNull null
            Mistake(
                game.id, move.ply, fen, move.san, move.bestUci, move.classification, move.whiteMoved, game.title,
                lastUci = replay.uci.getOrNull(move.ply - 1),
            )
        }
    }

    /** True for White, false for Black, null when the game does not say which side is the player. */
    fun mySide(game: SavedGame, myNames: List<String>): Boolean? {
        val names = myNames.map { it.trim() }.filter { it.isNotEmpty() } + "Me"
        val white = names.any { game.white.equals(it, ignoreCase = true) }
        val black = names.any { game.black.equals(it, ignoreCase = true) }
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

    /**
     * The follow-up moves that count, from the engine's top [lines]: those
     * within [TOLERANCE] of the best, so where only one move is good, only
     * that one counts. [whiteMoves] is the side to move.
     */
    fun goodFollowUps(lines: List<EngineLine>, whiteMoves: Boolean): Set<String> {
        fun mover(score: Score) = if (whiteMoves) score.whiteWinPercent else 100 - score.whiteWinPercent
        val best = lines.firstOrNull() ?: return emptySet()
        val top = mover(best.score)
        return lines.take(FOLLOW_UP_CHOICES)
            .filter { top - mover(it.score) <= TOLERANCE }
            .mapNotNull { it.uci.firstOrNull()?.lowercase() }
            .toSet()
    }

    /** The engine lines a follow-up may come from. */
    const val FOLLOW_UP_CHOICES = 3

    /** Win-percentage points an answer may give away against the engine's best and still count. */
    const val TOLERANCE = 5.0
}
