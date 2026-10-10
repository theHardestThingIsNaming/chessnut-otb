package app.chessnut.otb

import app.chessnut.otb.openings.MoveIdeas
import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("Takes the pawn on d5.", MoveIdeas.explain(listOf("e4", "d5", "exd5"), afterE4D5, "e4d5"))
    }

    @Test
    fun movesSayWhatTheyDoOnTheBoard() {
        fun idea(fen: String, uci: String, san: String) = MoveIdeas.explain(listOf(san), fen, uci).orEmpty()

        val fork = idea("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", "b5c7", "Nc7+")
        assertTrue("Forks the king and the rook on a8." in fork, fork)

        val pin = idea("4k3/8/2n5/8/8/8/8/4KB2 w - - 0 1", "f1b5", "Bb5")
        assertTrue("Pins the knight on c6 to the king." in pin, pin)

        val threat = idea("4k3/8/3p4/2b5/8/8/1P6/4K3 w - - 0 1", "b2b4", "b4")
        assertTrue("Threatens the bishop on c5." in threat, threat)
        val loose = idea("4k3/8/8/2b5/8/8/1P6/4K3 w - - 0 1", "b2b4", "b4")
        assertTrue("Attacks the undefended bishop on c5." in loose, loose)

        val uncovered = idea("3qk3/8/8/8/3N4/8/8/3RK3 w - - 0 1", "d4f5", "Nf5")
        assertTrue("Opens the rook on d1 onto the queen on d8." in uncovered, uncovered)

        val defends = idea("4k3/8/5n2/8/4P3/8/8/3QK3 w - - 0 1", "d1e2", "Qe2")
        assertTrue(defends.startsWith("Brings the queen to e2."), defends)
        assertTrue("Defends the pawn on e4." in defends, defends)

        // 1. e4 e5 2. Bc4 Nc6 3. Qf3: the queen joins the bishop against f7.
        var fen = Board().fen
        for (san in listOf("e4", "e5", "Bc4", "Nc6")) fen = PlayPosition.after(fen, PlayPosition.uciOfSan(fen, san)!!)!!.second
        val f7 = MoveIdeas.explain(listOf("e4", "e5", "Bc4", "Nc6", "Qf3"), fen, "d1f3").orEmpty()
        assertTrue("Adds a second attacker to f7, which only the king defends." in f7, f7)
        assertFalse("Early queen moves" in f7, f7)

        val bishops = idea("3qk3/4b3/8/6B1/8/8/8/4K3 w - - 0 1", "g5e7", "Bxe7")
        assertTrue(bishops.startsWith("Trades bishops on e7."), bishops)
        assertFalse("Forks" in bishops, bishops)

        val discovered = idea("4k3/8/8/8/4N3/8/8/4RK2 w - - 0 1", "e4c5", "Nc5+")
        assertTrue("Discovered check from the rook on e1." in discovered, discovered)
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
