package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningTrapsTest {
    @Test
    fun everyTrapIsLegalAndAppearsInALineYouDrill() {
        val scotch = OpeningTree.lines("Scotch Game", userWhite = true)
        val pirc = OpeningTree.lines("Pirc Defense", userWhite = false)
        for (trap in OpeningTraps.all) {
            val replay = runCatching { ChessLines.replay(trap.before + trap.moves) }.getOrNull()
            assertTrue(replay != null && replay.uci.size == trap.before.size + trap.moves.size, "${trap.name} is legal")
            // The opponent makes the mistake: White's turn in the Pirc, Black's in the Scotch.
            val youWhite = trap.before.size % 2 == 1
            val lines = if (youWhite) scotch else pirc
            assertTrue(lines.any { OpeningTraps.follows(trap, it) }, "${trap.name} comes up from a line you drill")
        }
        assertEquals(5, OpeningTraps.all.count { it.before.size % 2 == 0 }, "Pirc traps")
        assertEquals(2, OpeningTraps.at("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3".split(" ")).size)
    }
}
