package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.MiddlegamePlans
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiddlegamePlansTest {
    @Test
    fun everyPlanIsLegalAndStartsFromYourLines() {
        for (plan in MiddlegamePlans.all) {
            val replay = runCatching { ChessLines.replay(plan.before + plan.moves) }.getOrNull()
            assertTrue(replay != null && replay.uci.size == plan.before.size + plan.moves.size, "${plan.name} is legal")
            val lines = OpeningTree.lines(plan.opening, plan.white)
            assertTrue(lines.any { it.take(plan.before.size) == plan.before }, "${plan.name} starts from a line you drill")
        }
        assertEquals(4, MiddlegamePlans.of("Scotch Game", true).size)
        assertEquals(4, MiddlegamePlans.of("Pirc Defense", false).size)
    }
}
