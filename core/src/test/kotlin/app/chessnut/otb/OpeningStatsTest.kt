package app.chessnut.otb

import app.chessnut.otb.openings.OpeningRecord
import app.chessnut.otb.openings.OpeningStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpeningStatsTest {
    @Test
    fun playIsTheLineInUci() {
        assertEquals("e2e4,e7e5,f2f4", OpeningStats.play("King's Gambit"))
    }

    @Test
    fun winRateBelongsToTheOpeningsSide() {
        val record = OpeningStats.parse("""{"white":500,"draws":100,"black":400,"moves":[],"opening":null}""")!!
        assertEquals(50, OpeningStats.winPercent("King's Gambit", record))
        assertEquals(40, OpeningStats.winPercent("Sicilian Defense", record))
        assertNull(OpeningRecord(10, 5, 10).winPercent(app.chessnut.otb.openings.OpeningSide.White))
    }

    @Test
    fun sortsBestFirstAndKeepsUnknownsInOrder() {
        val names = listOf("A", "B", "C", "D")
        val rates = mapOf("A" to null, "B" to 40, "C" to 55, "D" to null)
        assertEquals(listOf("C", "B", "A", "D"), OpeningStats.sorted(names, rates))
    }
}
