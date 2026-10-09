package app.chessnut.otb.analysis

import kotlin.math.exp
import kotlin.math.sqrt

enum class MoveClass { Best, Good, Inaccuracy, Mistake, Blunder }

data class MoveReport(
    val ply: Int,
    val san: String,
    val whiteMoved: Boolean,
    /** Mover's win chance before and after the move, 0..100. */
    val winBefore: Double,
    val winAfter: Double,
    val accuracy: Double,
    val classification: MoveClass,
    /** Engine's preferred move in the position before, in UCI. */
    val bestUci: String?,
)

data class GameReport(val whiteAccuracy: Double?, val blackAccuracy: Double?, val moves: List<MoveReport>)

/**
 * Accuracy using the formulas Lichess publishes (win percentage from
 * centipawns, per-move accuracy from the drop in win percentage, and a game
 * score averaging a volatility-weighted mean with a harmonic mean). Written
 * from the formulas, not from lila's source. Numbers come out close to
 * Lichess, not identical: depth and engine build differ.
 *
 * Classification uses the mover's win-percentage drop: 10 inaccuracy,
 * 20 mistake, 30 blunder.
 */
object AccuracyCalculator {
    fun moveAccuracy(winBefore: Double, winAfter: Double): Double {
        if (winAfter >= winBefore) return 100.0
        val drop = winBefore - winAfter
        val raw = 103.1668100711649 * exp(-0.04354415386753951 * drop) - 3.166924740191411
        return (raw + 1).coerceIn(0.0, 100.0)
    }

    fun classify(winDrop: Double, playedBest: Boolean): MoveClass = when {
        playedBest -> MoveClass.Best
        winDrop >= 30 -> MoveClass.Blunder
        winDrop >= 20 -> MoveClass.Mistake
        winDrop >= 10 -> MoveClass.Inaccuracy
        else -> MoveClass.Good
    }

    /** [playedUci] has one entry per move; [analysis] has one more position than moves. */
    fun report(sanMoves: List<String>, playedUci: List<String>, analysis: GameAnalysis): GameReport {
        val positions = analysis.positions
        val count = minOf(sanMoves.size, positions.size - 1)
        if (count <= 0) return GameReport(null, null, emptyList())
        val whiteWins = positions.take(count + 1).map { it.score.whiteWinPercent }

        val moves = (0 until count).map { ply ->
            val whiteMoved = positions[ply].fen.split(' ')[1] == "w"
            val before = if (whiteMoved) whiteWins[ply] else 100 - whiteWins[ply]
            val after = if (whiteMoved) whiteWins[ply + 1] else 100 - whiteWins[ply + 1]
            val best = positions[ply].lines.firstOrNull()?.uci?.firstOrNull()
            MoveReport(
                ply = ply,
                san = sanMoves[ply],
                whiteMoved = whiteMoved,
                winBefore = before,
                winAfter = after,
                accuracy = moveAccuracy(before, after),
                classification = classify(before - after, best != null && best == playedUci.getOrNull(ply)),
                bestUci = best,
            )
        }

        val windowSize = (count / 10).coerceIn(2, 8)
        val windows = buildList {
            repeat((windowSize.coerceAtMost(whiteWins.size) - 2).coerceAtLeast(0)) { add(whiteWins.take(windowSize)) }
            addAll(whiteWins.windowed(windowSize.coerceAtMost(whiteWins.size)))
        }
        val weights = windows.map { standardDeviation(it).coerceIn(0.5, 12.0) }

        fun sideAccuracy(white: Boolean): Double? {
            val mine = moves.mapIndexedNotNull { index, move ->
                if (move.whiteMoved == white) move.accuracy to weights.getOrElse(index) { weights.lastOrNull() ?: 1.0 } else null
            }
            if (mine.isEmpty()) return null
            val weighted = mine.sumOf { it.first * it.second } / mine.sumOf { it.second }
            val harmonic = mine.size / mine.sumOf { 1 / it.first.coerceAtLeast(1.0) }
            return (weighted + harmonic) / 2
        }
        return GameReport(sideAccuracy(true), sideAccuracy(false), moves)
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
