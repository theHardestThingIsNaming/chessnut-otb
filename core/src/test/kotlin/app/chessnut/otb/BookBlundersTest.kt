package app.chessnut.otb

import app.chessnut.otb.openings.BookBlunders
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookBlundersTest {
    @Test
    fun yourLinesNeverTeachAKnownBlunder() {
        for (name in OpeningCatalog.browse("").first().names) {
            val white = OpeningBook.side(name) == OpeningSide.White
            for (line in OpeningTree.lines(name, white)) {
                for (ply in line.indices) {
                    if ((ply % 2 == 0) != white) continue
                    assertFalse(BookBlunders.avoid(line.take(ply), line[ply]), "$name teaches ${line[ply]} after ${line.take(ply).joinToString(" ")}")
                }
            }
        }
    }

    @Test
    fun theRuyLopezSidestepsNoahsArk() {
        val lines = OpeningTree.lines("Ruy Lopez", userWhite = true)
        val before = "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 b5 Bb3 d6 d4 Nxd4 Nxd4 exd4".split(" ")
        assertTrue(lines.any { it.take(before.size + 1) == before + "c3" })
    }
}
