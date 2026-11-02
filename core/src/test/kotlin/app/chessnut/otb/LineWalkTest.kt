package app.chessnut.otb

import app.chessnut.otb.play.LineWalk
import app.chessnut.otb.play.PlayPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class LineWalkTest {
    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val line = listOf("e4", "e5", "Nf3", "Nc6", "Bc4")

    @Test
    fun noMovesIsTheStart() {
        val step = LineWalk.to(start, line, 0)
        assertEquals(start, step.fen)
        assertEquals(emptyList(), step.san)
        assertEquals(start, step.before)
    }

    @Test
    fun steppingForwardThenBackReturnsToTheSameBoard() {
        val three = LineWalk.to(start, line, 3)
        val four = LineWalk.to(start, line, 4)
        assertEquals(listOf("e4", "e5", "Nf3"), three.san)
        assertEquals(three.fen, four.before)
        assertEquals(three.fen, LineWalk.to(start, line, 4 - 1).fen)
    }

    @Test
    fun countsOutsideTheLineAreClamped() {
        assertEquals(start, LineWalk.to(start, line, -3).fen)
        assertEquals(line, LineWalk.to(start, line, 99).san)
    }

    @Test
    fun theLastMoveIsTheOneJustPlayed() {
        val step = LineWalk.to(start, line, 2)
        assertEquals(listOf("e2e4", "e7e5"), step.uci)
        assertEquals(PlayPosition.after(start, "e2e4")!!.second, step.before)
    }

    @Test
    fun aDamagedLineShowsAsFarAsItGoes() {
        val step = LineWalk.to(start, listOf("e4", "e5", "Qh8", "Nc6"), 4)
        assertEquals(listOf("e4", "e5"), step.san)
    }
}
