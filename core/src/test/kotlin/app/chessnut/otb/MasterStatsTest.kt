package app.chessnut.otb

import app.chessnut.otb.openings.MiddlegamePlans
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Matches master games (PGN files in STATS_PGN_DIR, such as PGN Mentor's)
 * against an opening's middlegame plans by position, so other move orders count too.
 * For each plan move: how many master games reached the position before it, and how many
 * played the plan's move there. Run with STATS_OPENING="Scotch Game|w" STATS_OUT=<file>.
 */
class MasterStatsTest {
    /** The position's key: pieces, side to move, castling, en passant. */
    private fun key(board: Board) = board.fen.split(' ').take(4).joinToString(" ")

    @Test
    fun stats() {
        val dir = System.getenv("STATS_PGN_DIR") ?: return
        val (opening, side) = System.getenv("STATS_OPENING").split('|')
        val plans = MiddlegamePlans.of(opening, side == "w")
        // Positions the plans pass through, and the plan's move from each.
        val wanted = mutableMapOf<String, MutableSet<String>>()
        for (plan in plans) {
            val board = Board()
            for ((i, san) in (plan.before + plan.moves).withIndex()) {
                if (i >= plan.before.size) wanted.getOrPut(key(board)) { mutableSetOf() }.add(san)
                board.doMove(san)
            }
        }
        val reached = mutableMapOf<String, Int>()
        val played = mutableMapOf<Pair<String, String>, Int>()
        val skip = Regex("blitz|rapid|bullet|speed|titled|online|armageddon|blind", RegexOption.IGNORE_CASE)
        var games = 0
        for (file in File(dir).listFiles { f -> f.name.endsWith(".pgn") }.orEmpty()) {
            for (block in file.readText(Charsets.ISO_8859_1).split(Regex("\n(?=\\[Event )"))) {
                val event = Regex("\\[Event \"([^\"]*)\"").find(block)?.groupValues?.get(1).orEmpty()
                if (skip.containsMatchIn(event)) continue
                val body = block.lines().filterNot { it.startsWith("[") }.joinToString(" ").replace(Regex("\\{[^}]*\\}"), " ")
                val sans = body.split(Regex("\\s+")).map { it.replace(Regex("^\\d+\\.+"), "") }
                    .filter { it.isNotBlank() && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }
                val board = Board()
                games++
                for (san in sans.take(60)) {
                    val k = key(board)
                    val mine = wanted[k]
                    if (mine != null) {
                        reached[k] = (reached[k] ?: 0) + 1
                        val clean = san.trimEnd('+', '#', '!', '?')
                        mine.firstOrNull { it.trimEnd('+', '#') == clean }?.let { played[k to it] = (played[k to it] ?: 0) + 1 }
                    }
                    try { board.doMove(san) } catch (e: Exception) { break }
                }
            }
        }
        val out = StringBuilder("$games master games read\n")
        for (plan in plans) {
            val board = Board()
            plan.before.forEach { board.doMove(it) }
            out.append("${plan.name}:")
            for (san in plan.moves) {
                val k = key(board)
                out.append(" $san(${played[k to san] ?: 0}/${reached[k] ?: 0})")
                board.doMove(san)
            }
            out.append("\n")
        }
        File(System.getenv("STATS_OUT")).writeText(out.toString())
    }

    /**
     * STATS_CONSENSUS=1: from each plan's start, the masters' most played move, position by position,
     * for up to 14 plies while at least STATS_MIN (default 20) games go on. Written to STATS_OUT.
     */
    @Test
    fun consensus() {
        val dir = System.getenv("STATS_PGN_DIR") ?: return
        if (System.getenv("STATS_CONSENSUS") == null) return
        val (opening, side) = System.getenv("STATS_OPENING").split('|')
        val min = System.getenv("STATS_MIN")?.toInt() ?: 20
        val skip = Regex("blitz|rapid|bullet|speed|titled|online|armageddon|blind", RegexOption.IGNORE_CASE)
        // Every game as its list of (position, move, result).
        val games = mutableListOf<Pair<List<Pair<String, String>>, String>>()
        for (file in File(dir).listFiles { f -> f.name.endsWith(".pgn") }.orEmpty()) {
            for (block in file.readText(Charsets.ISO_8859_1).split(Regex("\n(?=\\[Event )"))) {
                val event = Regex("\\[Event \"([^\"]*)\"").find(block)?.groupValues?.get(1).orEmpty()
                if (skip.containsMatchIn(event)) continue
                val result = Regex("\\[Result \"([^\"]*)\"").find(block)?.groupValues?.get(1).orEmpty()
                val body = block.lines().filterNot { it.startsWith("[") }.joinToString(" ").replace(Regex("\\{[^}]*\\}"), " ")
                val sans = body.split(Regex("\\s+")).map { it.replace(Regex("^\\d+\\.+"), "") }
                    .filter { it.isNotBlank() && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }
                val board = Board()
                val steps = mutableListOf<Pair<String, String>>()
                for (san in sans.take(60)) {
                    val k = key(board)
                    try { board.doMove(san) } catch (e: Exception) { break }
                    steps += k to san.trimEnd('!', '?')
                }
                games += steps to result
            }
        }
        val out = StringBuilder()
        for (plan in MiddlegamePlans.of(opening, side == "w")) {
            val board = Board()
            plan.before.forEach { board.doMove(it) }
            var pool = games.mapNotNull { (steps, result) -> steps.indexOfFirst { it.first == key(board) }.takeIf { it >= 0 }?.let { Triple(steps, it, result) } }
            out.append("${plan.name} (${pool.size} games):")
            repeat(14) {
                val counts = pool.mapNotNull { (steps, at, _) -> steps.getOrNull(at)?.second }.groupingBy { it }.eachCount()
                val (move, n) = counts.maxByOrNull { it.value } ?: return@repeat
                if (n < min) return@repeat
                val white = pool.filter { it.first.getOrNull(it.second)?.second == move }.map { it.third }
                val score = white.sumOf { if (it == "1-0") 2 else if (it == "1/2-1/2") 1 else 0 } * 50 / maxOf(1, white.size)
                out.append(" $move($n/${pool.size}, W$score%)")
                pool = pool.filter { it.first.getOrNull(it.second)?.second == move }.map { Triple(it.first, it.second + 1, it.third) }
            }
            out.append("\n")
        }
        File(System.getenv("STATS_OUT")).writeText(out.toString())
    }
}
