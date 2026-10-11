package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. With PUZZLE_DIR holding cand.csv (Lichess puzzle rows)
 * and games.ndjson (their games, from /api/games/export/_ids), keeps the
 * puzzles whose game followed one of your lines in PUZZLE_OPENINGS
 * ("Scotch Game|w,Pirc Defense|b") until at most GAP plies before the
 * opponent's mistake. Identical mistakes are grouped: how many puzzles share
 * one hints at how often it happens. Writes PUZZLE_DIR/found.tsv.
 */
class PuzzleGamesTest {
    @Test
    fun find() {
        val dir = System.getenv("PUZZLE_DIR")?.let(::File) ?: return
        val gap = 6
        val games = mutableMapOf<String, List<String>>()
        dir.resolve("games.ndjson").forEachLine { row ->
            val game = JsonLite.parse(row) as? Json.Obj ?: return@forEachLine
            val id = game.str("id") ?: return@forEachLine
            games[id] = game.str("moves").orEmpty().split(' ').filter { it.isNotBlank() }
        }
        val openings = System.getenv("PUZZLE_OPENINGS").split(',').map { it.split('|') }.map { (name, side) ->
            Triple(name, side == "w", OpeningTree.lines(name, side == "w"))
        }
        data class Hit(val opening: String, val before: List<String>, val moves: List<String>, val id: String, val rating: Int, val plays: Int, val themes: String, val past: Int)
        val hits = mutableListOf<Hit>()
        dir.resolve("cand.csv").forEachLine { row ->
            val f = row.split(',')
            val fen = f[1].split(' ')
            val ply = (fen[5].toInt() - 1) * 2 + if (fen[1] == "b") 1 else 0
            val id = Regex("lichess.org/([A-Za-z0-9]{8})").find(f[8])?.groupValues?.get(1) ?: return@forEachLine
            val game = games[id] ?: return@forEachLine
            if (game.size <= ply) return@forEachLine
            val before = game.take(ply)
            val replay = runCatching { ChessLines.replay(before) }.getOrNull() ?: return@forEachLine
            if (replay.fens.last().split(' ')[0] != fen[0]) return@forEachLine
            val moves = runCatching { ChessLines.sanOf(f[1], f[2].split(' ')) }.getOrNull() ?: return@forEachLine
            for ((name, white, lines) in openings) {
                // The opponent errs: Black's move when you are White.
                if ((ply % 2 == 1) != white) continue
                val line = lines.filter { l -> val n = minOf(l.size, ply); before.take(n) == l.take(n) }
                    .maxByOrNull { minOf(it.size, ply) } ?: continue
                // A book move of your own line isn't a mistake.
                if (ply < line.size && line[ply] == moves.first()) continue
                val past = ply - line.size
                if (past > gap) continue
                hits += Hit(name, before, moves, f[0], f[3].toInt(), f[6].toInt(), f[7], maxOf(past, 0))
            }
        }
        val grouped = hits.groupBy { it.opening to (it.before + it.moves.first()) }
            .map { (_, same) -> same.size to same.maxBy { it.plays } }
            .sortedWith(compareBy({ it.second.opening }, { -it.first }, { it.second.past }))
        dir.resolve("found.tsv").writeText(grouped.joinToString("\n") { (count, h) ->
            listOf(h.opening, count, h.past, h.rating, h.plays, h.id, h.themes, h.before.joinToString(" "), h.moves.joinToString(" ")).joinToString("\t")
        })
    }
}
