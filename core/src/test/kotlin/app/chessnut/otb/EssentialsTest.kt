package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.puzzles.Essentials
import app.chessnut.otb.puzzles.Outcome
import app.chessnut.otb.puzzles.TablebaseView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EssentialsTest {
    @Test
    fun readsTheTablebaseFromYourSide() {
        // White to move wins with Kd6 or Kf6; Kd5 lets it slip to a draw.
        val json = """{"category":"win","dtm":21,"moves":[
            {"uci":"e6d6","san":"Kd6","category":"loss"},{"uci":"e6f6","san":"Kf6","category":"loss"},
            {"uci":"e6d5","san":"Kd5","category":"draw"}]}"""
        val view = assertNotNull(TablebaseView.parse(json))
        assertEquals(Outcome.Win, view.outcome)
        assertEquals("e6d6", view.best)
        assertNull(view.judge("e6f6"), "another winning move is fine")
        assertEquals(Outcome.Draw to "e6d6", view.judge("e6d5"))
    }

    @Test
    fun everyEssentialIsALegalPositionWithFewPieces() {
        for (e in Essentials.ALL) {
            val pieces = e.fen.substringBefore(' ').count { it.isLetter() }
            assertTrue(pieces <= 7, "${e.id} fits the tablebase")
            assertTrue(runCatching { ChessLines.terminalOf(e.fen) }.isSuccess, e.id)
        }
        assertEquals(8, Essentials.ALL.size)
    }
}
