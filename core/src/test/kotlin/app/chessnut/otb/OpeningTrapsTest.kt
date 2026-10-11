package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpeningTrapsTest {
    @Test
    fun everyTrapIsLegalAndComesUpInAnOpeningYouDrill() {
        val openings = OpeningCatalog.browse("").first().names
        val linesBySide = listOf(true, false).associateWith { white ->
            openings.filter { (OpeningBook.side(it) == OpeningSide.White) == white }.map { OpeningTree.lines(it, white) }
        }
        for (trap in OpeningTraps.all) {
            val replay = runCatching { ChessLines.replay(trap.before + trap.moves) }.getOrNull()
            assertTrue(replay != null && replay.uci.size == trap.before.size + trap.moves.size, "${trap.name} is legal")
            // The opponent makes the mistake, so it's their move when the trap starts.
            val youWhite = trap.before.size % 2 == 1
            assertTrue(linesBySide.getValue(youWhite).any { OpeningTraps.fits(trap, it) }, "${trap.name} comes up from a line you drill")
        }
        assertEquals(trapNames().size, trapNames().toSet().size, "trap names are unique")
        assertEquals(2, OpeningTraps.at("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3".split(" ")).size)
    }

    @Test
    fun aGameFitsYourLinesOnlyIfTheOpponentLeftThem() {
        val lines = listOf("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6".split(" "))
        // Black left the line with 4...Qh4: the opponent's choice.
        assertTrue(OpeningTraps.fits("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5".split(" "), lines, white = true))
        // White left it with 5.Nc3 where your line plays 5.Nxc6.
        assertFalse(OpeningTraps.fits("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nc3 Bb4".split(" "), lines, white = true))
        // Too short to be this opening.
        assertFalse(OpeningTraps.fits("e4 e5 Nf3".split(" "), lines, white = true))
    }

    private fun trapNames() = OpeningTraps.all.map { it.name }
}
