package app.chessnut.otb

import app.chessnut.otb.openings.MoveIdeas
import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveIdeasTest {
    @Test
    fun everyNoteIsALegalLine() {
        val broken = MoveIdeas.noted.filter { line ->
            var fen = Board().fen
            line.split(" ").any { san ->
                val uci = PlayPosition.uciOfSan(fen, san) ?: return@any true
                fen = PlayPosition.after(fen, uci)!!.second
                false
            }
        }
        assertTrue(broken.isEmpty(), "illegal: $broken")
    }

    @Test
    fun writtenNoteWins() {
        val start = Board().fen
        assertEquals(
            "Grabs the centre and opens lines for the queen and the light-squared bishop.",
            MoveIdeas.explain(listOf("e4"), start, "e2e4"),
        )
    }

    @Test
    fun otherMovesAreDescribed() {
        val start = Board().fen
        assertEquals("Develops the knight towards the centre.", MoveIdeas.explain(listOf("Nh3"), start, "g1h3"))
        assertEquals("Makes room to fianchetto the bishop.", MoveIdeas.explain(listOf("b3"), start, "b2b3"))
        val afterE4D5 = PlayPosition.after(PlayPosition.after(start, "e2e4")!!.second, "d7d5")!!.second
        assertEquals("Takes on d5.", MoveIdeas.explain(listOf("e4", "d5", "exd5"), afterE4D5, "e4d5"))
    }

    @Test
    fun sacrificeIsNamed() {
        // Muzio position before 5. O-O: the castling move leaves the f3 knight to g4xf3.
        var fen = Board().fen
        val line = listOf("e4", "e5", "f4", "exf4", "Nf3", "g5", "Bc4", "g4")
        for (san in line) fen = PlayPosition.after(fen, PlayPosition.uciOfSan(fen, san)!!)!!.second
        val offer = MoveIdeas.offered(fen, "e1g1")
        assertEquals("KNIGHT", offer?.type?.name)
        assertEquals("F3", offer?.square?.name)
        assertEquals(true, offer?.ignored)
        // A quiet developing move from the start offers nothing.
        assertEquals(null, MoveIdeas.offered(Board().fen, "g1f3"))
    }
}
