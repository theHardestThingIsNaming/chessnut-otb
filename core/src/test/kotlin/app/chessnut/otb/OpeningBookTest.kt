package app.chessnut.otb

import app.chessnut.otb.openings.OpeningBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningBookTest {
    @Test
    fun readingTheBookAsWrittenMatchesReplayingIt() {
        val rows = OpeningBook.bothReadings()
        assertTrue(rows.size > 4000)
        for ((pgn, fast, slow) in rows) {
            // A line the fast reading takes must be exactly what the replay gives.
            if (fast != null) assertEquals(slow, fast, pgn)
        }
        // Nearly every line takes the fast path; the replay is only a fallback.
        assertTrue(rows.count { it.second == null } < 20, "${rows.count { it.second == null }} lines replayed")
    }
}
