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
        assertEquals(12, MiddlegamePlans.of("Scotch Game", true).size)
        assertEquals(16, MiddlegamePlans.of("Italian Game", true).size)
        assertEquals(28, MiddlegamePlans.of("Ruy Lopez", true).size)
        assertEquals(3, MiddlegamePlans.of("Vienna Game", true).size)
        assertEquals(3, MiddlegamePlans.of("English Opening", true).size)
        assertEquals(3, MiddlegamePlans.of("London System", true).size)
        assertEquals(22, MiddlegamePlans.of("Sicilian Defense", false).size)
        assertEquals(18, MiddlegamePlans.of("French Defense", false).size)
        assertEquals(4, MiddlegamePlans.of("Caro-Kann Defense", false).size)
        assertEquals(13, MiddlegamePlans.of("Slav Defense", false).size)
        assertEquals(10, MiddlegamePlans.of("Nimzo-Indian Defense", false).size)
        assertEquals(20, MiddlegamePlans.of("King's Indian Defense", false).size)
        assertEquals(14, MiddlegamePlans.of("Pirc Defense", false).size)
        assertEquals(4, MiddlegamePlans.of("Bishop's Opening", true).size)
    }

    @Test
    fun everyLineOfACoveredOpeningLeadsIntoAPlan() {
        for ((opening, white) in listOf(
            "Scotch Game" to true,
            "Italian Game" to true,
            "French Defense" to false,
            "King's Indian Defense" to false,
            "Vienna Game" to true,
            "London System" to true,
            "Nimzo-Indian Defense" to false,
            "Slav Defense" to false,
            "Pirc Defense" to false,
            "Sicilian Defense" to false,
            "Ruy Lopez" to true,
        )) {
            val plans = MiddlegamePlans.of(opening, white)
            for (line in OpeningTree.lines(opening, white)) {
                assertTrue(
                    plans.any { line.size >= it.before.size && line.take(it.before.size) == it.before },
                    "$opening: no plan after ${line.joinToString(" ")}",
                )
            }
        }
    }
}
