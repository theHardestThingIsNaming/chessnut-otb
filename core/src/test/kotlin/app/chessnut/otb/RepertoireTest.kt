package app.chessnut.otb

import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineReview
import app.chessnut.otb.repertoire.StudyPgn
import kotlin.test.Test
import kotlin.test.assertEquals

class RepertoireTest {
    private val export = """
        [Event "My Repertoire: Italian"]
        [Site "https://lichess.org/study/abcd1234/efgh5678"]
        [ChapterName "Italian"]
        [Orientation "white"]

        1. e4 { Best by test } e5 2. Nf3 Nc6 (2... d6 3. d4) 3. Bc4 Bc5 (3... Nf6 4. Ng5!? d5 (4... Bc5) 5. exd5) 4. c3 *

        [Event "My Repertoire: Caro"]
        [ChapterName "Caro-Kann"]
        [Orientation "black"]

        1. e4 c6 2. d4 d5 $1 *

        [Event "My Repertoire: Endgame"]
        [ChapterName "Endgame"]
        [FEN "8/8/8/4k3/8/8/4K3/8 w - - 0 1"]
        [SetUp "1"]

        1. Ke3 *
    """.trimIndent()

    @Test
    fun readsChaptersVariationsAndSides() {
        val chapters = StudyPgn.chapters(export)
        assertEquals(listOf("Italian", "Caro-Kann"), chapters.map { it.name })
        assertEquals(listOf(true, false), chapters.map { it.userWhite })
        assertEquals(
            setOf(
                "e4 e5 Nf3 d6 d4",
                "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5",
                "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5",
                "e4 e5 Nf3 Nc6 Bc4 Bc5 c3",
            ),
            chapters[0].lines.map { it.joinToString(" ") }.toSet(),
        )
        assertEquals(listOf(listOf("e4", "c6", "d4", "d5")), chapters[1].lines)
    }

    @Test
    fun cleanRunsWaitLongerAndMissesComeBackTomorrow() {
        val day = 24L * 60 * 60 * 1000
        val first = Drill.grade(null, clean = true, now = 0)
        assertEquals(LineReview(0, day), first)
        val second = Drill.grade(first, clean = true, now = day)
        assertEquals(LineReview(1, day + 3 * day), second)
        assertEquals(LineReview(0, 5 * day), Drill.grade(second, clean = false, now = 4 * day))
    }

    @Test
    fun nextIsTheMostOverdue() {
        val a = listOf("e4", "e5")
        val b = listOf("e4", "c5")
        val reviews = mapOf(Drill.key(a) to LineReview(2, 500), Drill.key(b) to LineReview(1, 100))
        assertEquals(b, Drill.next(listOf(a, b), reviews, now = 1000))
        // A review that has come due goes before a line never drilled; otherwise new lines come first.
        val fresh = listOf("d4")
        assertEquals(b, Drill.next(listOf(fresh, a, b), reviews, now = 1000))
        assertEquals(fresh, Drill.next(listOf(a, fresh, b), reviews, now = 50))
        assertEquals(2, Drill.overdue(listOf(fresh, a, b), reviews, now = 1000))
        assertEquals(2, Drill.due(listOf(a, b), reviews, now = 1000))
    }
}
