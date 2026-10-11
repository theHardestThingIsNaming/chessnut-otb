package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.play.PlayPosition
import java.io.File
import kotlin.test.Test

/** Not a check: a tool. With TRAP_IN set, scores each candidate's last move with a local Stockfish. */
class TrapCheckTest {
    @Test
    fun scoreCandidates() {
        val input = System.getenv("TRAP_IN") ?: return
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        fun go(fen: String): Pair<Int, List<String>> {
            send("position fen $fen"); send("go depth 20")
            var cp = 0; var pv = emptyList<String>()
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " pv " in l) {
                    val t = l.split(" ")
                    val i = t.indexOf("score")
                    cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                    pv = t.drop(t.indexOf("pv") + 1)
                }
                if (l.startsWith("bestmove")) return cp to pv
            }
        }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 4")
        val out = StringBuilder()
        for (row in File(input).readLines().filter { it.isNotBlank() }) {
            val (label, sans) = row.split("|")
            val moves = sans.split(" ")
            val fens = ChessLines.replay(moves).fens
            val before = fens[fens.size - 2]
            val after = fens.last()
            val (bestCp, bestPv) = go(before)
            val (afterCp, afterPv) = go(after)
            // Both from the mistake-maker's view: before is theirs to move, after is the opponent's.
            val drop = bestCp + afterCp
            out.append("$label: loses ${drop}cp. best was ${ChessLines.sanOf(before, bestPv.take(1)).joinToString()}; refutation ${ChessLines.sanOf(after, afterPv.take(9)).joinToString(" ")}\n")
        }
        send("quit")
        File(System.getenv("TRAP_OUT")).writeText(out.toString())
    }
}
