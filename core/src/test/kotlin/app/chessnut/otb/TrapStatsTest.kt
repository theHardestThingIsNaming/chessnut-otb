package app.chessnut.otb

import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.openings.TrapFrequency
import app.chessnut.otb.openings.TrapStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TrapStatsTest {
    @Test
    fun countsTheMistakeAgainstAllGamesInThePosition() {
        val trap = OpeningTraps.all.first { it.name == "The hanging knight" }
        val json = """{"white":600,"draws":100,"black":300,"moves":[
            {"uci":"d8e7","san":"Qe7","white":400,"draws":80,"black":220},
            {"uci":"f6g4","san":"Ng4","white":90,"draws":5,"black":25},
            {"uci":"f6d5","san":"Nd5","white":110,"draws":15,"black":55}]}"""
        val record = assertNotNull(TrapStats.parse(json, trap))
        assertEquals(120, record.mistakeGames)
        assertEquals(1000, record.totalGames)
        assertEquals(12, record.percent)
        assertEquals(TrapFrequency.Common, record.frequency)
        // A mistake nobody plays isn't in the list at all.
        val none = assertNotNull(TrapStats.parse("""{"white":50,"draws":0,"black":50,"moves":[]}""", trap))
        assertEquals(TrapFrequency.Rare, none.frequency)
        assertEquals("e2e4,e7e5,g1f3,b8c6,d2d4,e5d4,f3d4,g8f6,d4c6,b7c6,e4e5", TrapStats.play(trap))
    }
}
