package app.chessnut.otb

import app.chessnut.otb.play.FinishedLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinishedLineTest {
    private fun offer(
        done: Boolean = true,
        clean: Boolean = true,
        learning: Boolean = false,
        survival: Boolean = false,
        punish: Boolean = false,
        trap: Boolean = false,
        reviewing: Boolean = false,
    ) = FinishedLine.offer(done, clean, learning, survival, punish, trap, reviewing)

    @Test
    fun midLineKeepsTheOpeningsButtonAndOffersNothingToPlayOn() {
        val o = offer(done = false, clean = false)
        assertTrue(o.openings)
        assertFalse(o.playOn)
        assertFalse(o.engine)
    }

    @Test
    fun aCleanFinishOffersPlayOnAndHidesOpenings() {
        val o = offer()
        assertTrue(o.playOn)
        assertTrue(o.engine)
        assertFalse(o.openings)
    }

    @Test
    fun aMissOffersOnlyTryAgainAndNext() {
        val o = offer(clean = false)
        assertFalse(o.playOn)
        assertFalse(o.engine)
        assertFalse(o.openings)
    }

    @Test
    fun learningNeverShowsOpeningsOrPlayOn() {
        val o = offer(done = false, clean = false, learning = true)
        assertFalse(o.openings)
        assertFalse(o.playOn)
    }

    @Test
    fun aReviewCanPlayOnButNotAgainstTheEngine() {
        val o = offer(reviewing = true)
        assertTrue(o.playOn)
        assertFalse(o.engine)
    }

    @Test
    fun survivalAndPunishKeepTheirOwnButtons() {
        assertTrue(offer(survival = true).openings)
        assertTrue(offer(punish = true).openings)
        assertFalse(offer(survival = true).engine)
        assertEquals(false, offer(trap = true).playOn)
    }
}
