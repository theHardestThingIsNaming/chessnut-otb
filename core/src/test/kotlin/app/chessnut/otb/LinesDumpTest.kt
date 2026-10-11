package app.chessnut.otb

import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/** Not a check: a tool. Writes every main opening's drill lines to LINES_OUT. */
class LinesDumpTest {
    @Test
    fun dump() {
        val out = System.getenv("LINES_OUT") ?: return
        File(out).writeText(OpeningCatalog.browse("").first().names.joinToString("\n\n") { name ->
            val white = OpeningBook.side(name) == OpeningSide.White
            "## $name (${if (white) "White" else "Black"})\n" + OpeningTree.lines(name, white).joinToString("\n") { it.joinToString(" ") }
        })
    }
}
