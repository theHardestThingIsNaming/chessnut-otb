package app.chessnut.otb.repertoire

import app.chessnut.otb.analysis.Score
import java.util.Locale

/**
 * How a move that ended a Survival run compares with the engine's choice: where it ranked among
 * the engine's top moves and how much it gave away, in pawns, or what it did to a mate.
 */
object MoveVerdict {
    /**
     * [rank] is the move's place in the engine's top moves (1 = its best), or null when it's outside
     * them. [best] is the position's score with the best move, [after] the score after the move
     * played, both from White's point of view as the engine reports them.
     */
    fun describe(playedSan: String, bestSan: String?, rank: Int?, best: Score, after: Score, whiteMoved: Boolean): String {
        val bestForMover = best.forMover(whiteMoved)
        val afterForMover = after.forMover(whiteMoved)
        val place = rank?.let { "the engine's ${ordinal(it)} choice" } ?: "outside the engine's top ${TOP}"
        val mateMissed = (bestForMover.mate ?: 0) > 0 && (afterForMover.mate ?: 0) <= 0
        val mateAllowed = (afterForMover.mate ?: 0) < 0 && (bestForMover.mate ?: 0) >= 0
        return when {
            mateMissed -> "You played $playedSan, $place. That misses a forced mate."
            mateAllowed -> "You played $playedSan, $place. That allows mate."
            else -> {
                val pawns = (bestForMover.cappedCp - afterForMover.cappedCp).coerceAtLeast(0) / 100.0
                val than = bestSan?.let { " than $it" }.orEmpty()
                "You played $playedSan, $place: ${String.format(Locale.ROOT, "%.1f", pawns)} pawns worse$than."
            }
        }
    }

    /** How many of the engine's top moves are ranked. */
    const val TOP = 5

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }
}
