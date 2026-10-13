package app.chessnut.otb

import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.repertoire.OpeningTree
import com.github.bhlangonijr.chesslib.Board
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.Test

/**
 * Not a check: a tool. Finds master games for an opening's lines, and ranks the lines by how often
 * club players reach them, for choosing which lines get a master game first.
 * PICK_OPENING="Ruy Lopez|w|ruy" (name, side, folder under data-cache/pgnmentor), PICK_OUT=<dir>.
 * Matching is by position (pieces and side to move), so other move orders count.
 * Writes to PICK_OUT:
 * - `<folder>-lines.tsv`: line number, club games reaching it (from data-cache/games.ndjson,
 *   Lichess games at all levels), master games reaching it, the line; most club games first.
 * - `<folder>-cand.tsv`: per line, up to 12 classical master games that reach it, wins for your side
 *   and stronger players first: line, id, white, black, ratings, year, event, result, plies, the ply
 *   it reaches the line, moves.
 * - `<folder>-pgn.tsv`: `PGN \t id \t pgn` rows of those games, the input of MasterPrepTest.
 */
class MasterPickTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun key(board: Board) = board.fen.split(' ').take(2).joinToString(" ")

    private class Found(val line: Int, val reach: Int, val headers: Map<String, String>, val sans: List<String>, val pgn: String) {
        val white get() = headers["White"].orEmpty()
        val black get() = headers["Black"].orEmpty()
        val whiteElo get() = headers["WhiteElo"]?.toIntOrNull()
        val blackElo get() = headers["BlackElo"]?.toIntOrNull()
        val result get() = headers["Result"].orEmpty()
        val year get() = headers["Date"].orEmpty().take(4)
        val id get() = Integer.toUnsignedString((white + black + headers["Date"] + headers["Event"] + headers["Round"] + sans.size).hashCode(), 36).padStart(7, '0').take(8)
    }

    @Test
    fun pick() {
        val spec = System.getenv("PICK_OPENING") ?: return
        val out = File(System.getenv("PICK_OUT")).apply { mkdirs() }
        val (opening, side, folder) = spec.split('|')
        val white = side == "w"
        val lines = OpeningTree.lines(opening, white)
        // Each line's end position, and how deep it is.
        val ends = mutableMapOf<String, Int>()
        lines.forEachIndexed { i, line -> val b = Board(); line.forEach { b.doMove(it) }; ends.putIfAbsent(key(b), i) }
        val maxPly = lines.maxOf { it.size } + 8

        fun reached(sans: List<String>): Pair<Int, Int>? {
            val b = Board()
            for ((ply, san) in sans.withIndex()) {
                if (ply > maxPly) return null
                try { b.doMove(san) } catch (e: Exception) { return null }
                ends[key(b)]?.let { return it to ply + 1 }
            }
            return null
        }

        val club = IntArray(lines.size)
        File(root, "data-cache/games.ndjson").forEachLine { row ->
            val game = runCatching { JsonLite.parse(row) as? Json.Obj }.getOrNull() ?: return@forEachLine
            val sans = game.str("moves").orEmpty().split(' ').filter { it.isNotBlank() }
            reached(sans)?.let { club[it.first]++ }
        }

        val skip = Regex("blitz|rapid|bullet|speed|titled|online|armageddon|blind|simul", RegexOption.IGNORE_CASE)
        val files = File(root, "data-cache/pgnmentor/$folder").walk().filter { it.name.endsWith(".pgn") }.toList()
        val pool = Executors.newFixedThreadPool(6)
        val found = files.map { file ->
            pool.submit<List<Found>> {
                file.readText(Charsets.ISO_8859_1).split(Regex("\n(?=\\[Event )")).mapNotNull { block ->
                    val headers = Regex("\\[(\\w+) \"([^\"]*)\"]").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
                    if (skip.containsMatchIn(headers["Event"].orEmpty())) return@mapNotNull null
                    val body = block.lines().filterNot { it.startsWith("[") }.joinToString(" ").replace(Regex("\\{[^}]*\\}"), " ")
                    val sans = body.split(Regex("\\s+")).map { it.replace(Regex("^\\d+\\.+"), "") }
                        .filter { it.isNotBlank() && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }
                    val (line, reach) = reached(sans) ?: return@mapNotNull null
                    Found(line, reach, headers, sans, block.trim())
                }
            }
        }.flatMap { it.get() }
        pool.shutdown()

        val masters = found.groupBy { it.line }
        File(out, "$folder-lines.tsv").writeText(lines.indices.sortedByDescending { club[it] }.joinToString("") { i ->
            "${i + 1}\t${club[i]}\t${masters[i]?.size ?: 0}\t${lines[i].joinToString(" ")}\n"
        })
        val win = if (white) "1-0" else "0-1"
        val cand = StringBuilder()
        val pgn = StringBuilder()
        for (i in lines.indices) {
            val games = masters[i].orEmpty()
                .filter { it.sans.size >= it.reach + 16 && it.sans.size <= 140 }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<Found> { it.result == win }.thenByDescending { minOf(it.whiteElo ?: 2300, it.blackElo ?: 2300) })
                .take(12)
            for (g in games) {
                cand.append(listOf(i + 1, g.id, g.white, g.black, g.whiteElo ?: "", g.blackElo ?: "", g.year, g.headers["Event"].orEmpty(), g.result, g.sans.size, g.reach, g.sans.joinToString(" ")).joinToString("\t")).append('\n')
                pgn.append("PGN\t${g.id}\t${g.pgn.replace("\r", "").replace("\n", "\\n")}\n")
            }
        }
        File(out, "$folder-cand.tsv").writeText(cand.toString())
        File(out, "$folder-pgn.tsv").writeText(pgn.toString())
    }
}
