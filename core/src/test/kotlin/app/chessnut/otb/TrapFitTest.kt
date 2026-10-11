package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Reads FIT_IN (lines `label|before|moves`, SAN) and
 * writes FIT_OUT: whether each is legal and which main openings' lines it
 * comes up from, for the side that isn't making the mistake.
 */
class TrapFitTest {
    @Test
    fun fit() {
        val input = System.getenv("FIT_IN") ?: return
        val openings = OpeningCatalog.browse("").first().names
        val lines = openings.associateWith { name ->
            val white = OpeningBook.side(name) == OpeningSide.White
            white to OpeningTree.lines(name, white)
        }
        val out = File(input).readLines().filter { '|' in it }.joinToString("\n") { row ->
            val (label, before, moves) = row.split('|').map { it.trim() }
            val b = before.split(' ').filter { it.isNotBlank() }
            val m = moves.split(' ').filter { it.isNotBlank() }
            val legal = runCatching { ChessLines.replay(b + m).uci.size == b.size + m.size }.getOrDefault(false)
            val youWhite = b.size % 2 == 1
            val fits = lines.filter { (_, v) -> v.first == youWhite && OpeningTraps.fits(b, v.second, youWhite) }.keys
            "$label: ${if (legal) "legal" else "ILLEGAL"}; fits ${fits.ifEmpty { listOf("nothing") }.joinToString()}"
        }
        File(System.getenv("FIT_OUT")).writeText(out)
    }
}
