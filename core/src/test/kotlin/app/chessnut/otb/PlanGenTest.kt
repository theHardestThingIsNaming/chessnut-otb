package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. From the end of each line in GEN_IN (`label|SAN moves`), plays Stockfish's
 * best move for both sides, one move at a time at depth 20, GEN_PLIES times (default 12).
 * Writes `label|line|continuation|scores` to GEN_OUT; each score is for the side to move before its move.
 * Run: GEN_IN=/tmp/gen-in.txt GEN_OUT=/tmp/gen-out.txt GEN_PLIES=14 STOCKFISH=/opt/homebrew/bin/stockfish \
 *   ./gradlew :core:test --tests '*PlanGenTest*' --rerun-tasks
 * GEN_THREADS sets Stockfish's threads (default 4).
 */
class PlanGenTest {
    @Test
    fun generate() {
        val input = System.getenv("GEN_IN") ?: return
        val plies = System.getenv("GEN_PLIES")?.toInt() ?: 12
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value ${System.getenv("GEN_THREADS") ?: "4"}")
        val out = StringBuilder()
        for (row in File(input).readLines().filter { '|' in it }) {
            val (label, before) = row.split('|')
            val moves = before.split(' ').toMutableList()
            val added = mutableListOf<String>()
            val scores = mutableListOf<String>()
            for (i in 0 until plies) {
                val fen = ChessLines.replay(moves).fens.last()
                send("position fen $fen"); send("go depth 20")
                var best: String? = null; var cp = ""
                while (true) {
                    val l = r.readLine() ?: error("closed")
                    if (l.startsWith("info") && " score " in l) { val t = l.split(" "); val at = t.indexOf("score"); cp = t[at + 1] + " " + t[at + 2] }
                    if (l.startsWith("bestmove")) { best = l.split(" ").getOrNull(1); break }
                }
                val uci = best?.takeIf { it != "(none)" } ?: break
                val san = ChessLines.sanOf(fen, listOf(uci)).first()
                moves += san; added += san; scores += cp
            }
            out.append("$label|$before|${added.joinToString(" ")}|${scores.joinToString(", ")}\n")
        }
        send("quit")
        File(System.getenv("GEN_OUT")).writeText(out.toString())
    }
}
