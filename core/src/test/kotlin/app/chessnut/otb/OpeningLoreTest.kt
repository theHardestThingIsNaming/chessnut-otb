package app.chessnut.otb

import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningLore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningLoreTest {
    @Test
    fun everyStoryNamesABookOpening() {
        val missing = OpeningLore.names.filter { OpeningBook.line(it) == null }
        assertTrue(missing.isEmpty(), "not in the book: $missing")
    }

    @Test
    fun variationFallsBackToItsFamily() {
        assertEquals(OpeningLore.of("Italian Game"), OpeningLore.of("Italian Game: Rousseau Gambit"))
        assertNull(OpeningLore.exact("Italian Game: Rousseau Gambit"))
        assertNotNull(OpeningLore.exact("Italian Game: Evans Gambit"))
    }

    @Test
    fun customMemeLinesAreTrainable() {
        assertEquals(listOf("g4"), OpeningBook.replies(listOf("d4", "Nf6"), "Kilkenny Gambit"))
        assertEquals(listOf("Nb3"), OpeningBook.replies("e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5".split(" "), "The Cow"))
        // The book's official name still wins in analysis.
        assertEquals("Indian Defense: Gibbins-Weidenhagen Gambit", OpeningBook.view(listOf("d4", "Nf6", "g4")).name)
    }
}
