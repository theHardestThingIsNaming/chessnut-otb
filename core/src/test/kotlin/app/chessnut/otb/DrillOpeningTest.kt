package app.chessnut.otb

import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineReview
import kotlin.test.Test
import kotlin.test.assertEquals

class DrillOpeningTest {
    private val day = 24L * 60 * 60 * 1000
    private val a = listOf("e4", "e5")
    private val b = listOf("e4", "c5")
    private val c = listOf("e4", "e6")

    @Test
    fun startsWithTheFirstLineBeforeAnyIsTried() {
        assertEquals(a, Drill.opening(listOf(a, b, c), emptyMap(), 0))
    }

    @Test
    fun picksTheLinePractisedLongestAgoOverANewOne() {
        val now = 100 * day
        // b was drilled 5 days ago into box 2 (a 7-day wait); c yesterday, missed (back in a day). a is new.
        val reviews = mapOf(Drill.key(b) to LineReview(2, now + 2 * day), Drill.key(c) to LineReview(0, now + day))
        assertEquals(b, Drill.opening(listOf(a, b, c), reviews, now))
    }

    @Test
    fun aDueReviewComesFirst() {
        val now = 100 * day
        val reviews = mapOf(Drill.key(b) to LineReview(4, now + 25 * day), Drill.key(c) to LineReview(1, now - day))
        assertEquals(c, Drill.opening(listOf(a, b, c), reviews, now))
    }
}
