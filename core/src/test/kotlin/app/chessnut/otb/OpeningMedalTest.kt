package app.chessnut.otb

import app.chessnut.otb.repertoire.OpeningMedal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpeningMedalTest {
    @Test
    fun medalsFollowLinesLearnedAndSurvival() {
        assertNull(OpeningMedal.of(0, 8, 9))
        assertEquals(OpeningMedal.Bronze, OpeningMedal.of(1, 8, null))
        assertEquals(OpeningMedal.Bronze, OpeningMedal.of(8, 8, 1))
        assertEquals(OpeningMedal.Silver, OpeningMedal.of(4, 8, 2))
        assertEquals(OpeningMedal.Silver, OpeningMedal.of(8, 8, 4))
        assertEquals(OpeningMedal.Gold, OpeningMedal.of(8, 8, 5))
        assertEquals(OpeningMedal.Silver, OpeningMedal.of(7, 8, 12))
    }
}
