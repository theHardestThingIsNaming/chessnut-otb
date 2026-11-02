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
    fun offersANewLineBeforeRepeatingOne() {
        val now = 100 * day
        // b was drilled 5 days ago, c yesterday; a is new.
        val reviews = mapOf(Drill.key(b) to LineReview(2, now + 2 * day), Drill.key(c) to LineReview(0, now + day))
        assertEquals(a, Drill.opening(listOf(a, b, c), reviews, now))
    }

    @Test
    fun picksTheLinePractisedLongestAgoWhenNoneIsNew() {
        val now = 100 * day
        val reviews = mapOf(Drill.key(a) to LineReview(2, now + 3 * day), Drill.key(b) to LineReview(2, now + 2 * day), Drill.key(c) to LineReview(0, now + day))
        assertEquals(b, Drill.opening(listOf(a, b, c), reviews, now))
    }

    @Test
    fun aDueReviewComesFirst() {
        val now = 100 * day
        val reviews = mapOf(Drill.key(b) to LineReview(4, now + 25 * day), Drill.key(c) to LineReview(1, now - day))
        assertEquals(c, Drill.opening(listOf(a, b, c), reviews, now))
    }

    @Test
    fun theButtonSaysLearnForANewLine() {
        assertEquals(Drill.Step.Learn, Drill.openingStep(listOf(a, b), emptyMap(), 0))
    }

    @Test
    fun theButtonSaysReviewWhenALineIsDue() {
        val now = 100 * day
        val reviews = mapOf(Drill.key(a) to LineReview(1, now - day))
        assertEquals(Drill.Step.Review, Drill.openingStep(listOf(a, b), reviews, now))
    }

    @Test
    fun theButtonSaysPractiseWhenNothingIsDueOrNew() {
        val now = 100 * day
        val reviews = mapOf(Drill.key(a) to LineReview(1, now + day), Drill.key(b) to LineReview(1, now + 3 * day))
        assertEquals(Drill.Step.Practise, Drill.openingStep(listOf(a, b), reviews, now))
    }
}

class OpeningSearchTest {
    @Test
    fun theMainOpeningComesFirst() {
        val names = app.chessnut.otb.openings.OpeningCatalog.browse("Sicilian").single().names
        assertEquals("Sicilian Defense", names.first())
        kotlin.test.assertTrue(names.indexOfFirst { it.startsWith("Pterodactyl") } > names.indexOfFirst { it.startsWith("Sicilian Defense:") })
    }
}
