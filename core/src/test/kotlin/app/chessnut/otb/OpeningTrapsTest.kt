package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningTrapsTest {
    @Test
    fun everyTrapIsLegalAndAppearsInAScotchLine() {
        val lines = OpeningTree.lines("Scotch Game", userWhite = true)
        for (trap in OpeningTraps.all) {
            val replay = runCatching { ChessLines.replay(trap.before + trap.moves) }.getOrNull()
            assertTrue(replay != null && replay.uci.size == trap.before.size + trap.moves.size, "${trap.name} is legal")
            assertTrue(lines.any { it.take(trap.before.size) == trap.before }, "${trap.name} is reached in a Scotch line")
            // The opponent makes the mistake: it's their move when the trap starts.
            assertEquals(1, trap.before.size % 2, trap.name)
        }
        assertEquals(2, OpeningTraps.at("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5 Nb5".split(" ")).size)
    }
}
