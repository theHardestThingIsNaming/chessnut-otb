package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Reads PUZZLES_IN, rows of the Lichess puzzle database
 * (CC0), and keeps the puzzles whose starting position comes up in a line of
 * the trainer's openings with your opponent to move: the puzzle's first move
 * is their mistake, the rest your punishment. Writes PUZZLES_OUT as
 * `opening \t side \t id \t rating \t popularity \t plays \t themes \t before \t moves`.
 */
class PuzzleMatchTest {
    @Test
    fun match() {
        val input = System.getenv("PUZZLES_IN") ?: return
        // Position (pieces, side, castling) to where it arises in your lines.
        data class Spot(val opening: String, val white: Boolean, val before: List<String>)
        val spots = mutableMapOf<String, MutableList<Spot>>()
        val names = OpeningCatalog.browse("").flatMap { it.names }
        for (name in names) {
            val sides = when (OpeningBook.side(name)) {
                OpeningSide.White -> listOf(true)
                OpeningSide.Black -> listOf(false)
                null -> listOf(true, false)
            }
            for (white in sides) {
                val lines = runCatching { OpeningTree.lines(name, white) }.getOrDefault(emptyList())
                val seen = mutableSetOf<List<String>>()
                for (line in lines) {
                    val fens = ChessLines.replay(line).fens
                    for (ply in line.indices) {
                        // The opponent's turn: White to move when you are Black.
                        if ((ply % 2 == 0) == white) continue
                        val before = line.take(ply)
                        if (!seen.add(before)) continue
                        spots.getOrPut(key(fens[ply])) { mutableListOf() } += Spot(name, white, before)
                    }
                }
            }
        }
        val out = StringBuilder()
        File(input).forEachLine { row ->
            val f = row.split(',')
            if (f.size < 10) return@forEachLine
            val at = spots[key(f[1])] ?: return@forEachLine
            val moves = f[2].split(' ')
            val san = runCatching { ChessLines.sanOf(f[1], moves) }.getOrNull() ?: return@forEachLine
            if (san.size != moves.size) return@forEachLine
            for (spot in at.distinctBy { it.opening to it.white }) {
                out.append(listOf(spot.opening, if (spot.white) "w" else "b", f[0], f[3], f[5], f[6], f[7], spot.before.joinToString(" "), san.joinToString(" ")).joinToString("\t")).append('\n')
            }
        }
        File(System.getenv("PUZZLES_OUT")).writeText(out.toString())
    }

    // En passant squares are written differently by different tools, so they're left out.
    private fun key(fen: String) = fen.split(' ').take(3).joinToString(" ")
}
