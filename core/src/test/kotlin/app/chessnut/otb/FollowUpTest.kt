package app.chessnut.otb

import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.mistakes.MistakeDeck
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowUpTest {
    private fun line(cp: Int, uci: String) = EngineLine(Score(cp = cp), listOf(uci), 18)

    @Test
    fun closeMovesAllCountButAnOnlyMoveStandsAlone() {
        // White to move: three moves within a few centipawns all count.
        val close = listOf(line(80, "e2e4"), line(70, "d2d4"), line(60, "g1f3"))
        assertEquals(setOf("e2e4", "d2d4", "g1f3"), MistakeDeck.goodFollowUps(close, whiteMoves = true))
        // One winning move, the rest throw it away.
        val only = listOf(line(500, "d1h5"), line(20, "e2e4"), line(-30, "d2d4"))
        assertEquals(setOf("d1h5"), MistakeDeck.goodFollowUps(only, whiteMoves = true))
        // Black to move reads the scores from Black's side.
        val black = listOf(line(-300, "e7e5"), line(-290, "d7d5"), line(50, "a7a6"))
        assertEquals(setOf("e7e5", "d7d5"), MistakeDeck.goodFollowUps(black, whiteMoves = false))
    }
}
