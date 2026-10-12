package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Reads PLAN_IN (lines `label|before|moves`, SAN, or
 * `all` for every shipped plan) and,
 * for every move of the plan, writes to PLAN_OUT how many centipawns it gives
 * away against Stockfish's best at depth 18, and the best move when it's a
 * lot, so a written plan can be checked move by move.
 */
class PlanCheckTest {
    @Test
    fun check() {
        val input = System.getenv("PLAN_IN") ?: return
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 6")
        fun score(fen: String): Pair<Int, String?> {
            send("position fen $fen"); send("go depth 18")
            var cp = 0
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " score " in l && " pv " in l) {
                    val t = l.split(" ")
                    val i = t.indexOf("score")
                    cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                }
                if (l.startsWith("bestmove")) return cp to l.split(" ").getOrNull(1)
            }
        }
        val out = StringBuilder()
        // PLAN_IN=all checks every plan shipped in MiddlegamePlans.
        val rows = if (input == "all") {
            app.chessnut.otb.openings.MiddlegamePlans.all.map { "${it.opening}: ${it.name}|${it.before.joinToString(" ")}|${it.moves.joinToString(" ")}" }
        } else {
            File(input).readLines().filter { '|' in it }
        }
        for (row in rows) {
            val (label, before, moves) = row.split('|').map { it.trim() }
            val start = before.split(' ').filter { it.isNotBlank() }
            val plan = moves.split(' ').filter { it.isNotBlank() }
            val fens = runCatching { ChessLines.replay(start + plan).fens }.getOrNull()
            if (fens == null || fens.size != start.size + plan.size + 1) {
                out.append("$label: ILLEGAL\n")
                continue
            }
            out.append(label).append(':')
            for ((i, san) in plan.withIndex()) {
                val at = start.size + i
                val (best, bestUci) = score(fens[at])
                val (after, _) = score(fens[at + 1])
                val loss = best + after
                val bestSan = bestUci?.let { ChessLines.sanOf(fens[at], listOf(it)).firstOrNull() }
                out.append(" $san(${loss}${if (loss >= 60) " best $bestSan" else ""})")
            }
            out.append('\n')
        }
        send("quit")
        File(System.getenv("PLAN_OUT")).writeText(out.toString())
    }
}
