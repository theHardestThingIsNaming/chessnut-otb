package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.play.PlayPosition
import java.io.File
import kotlin.test.Test

/** Not a check: a tool. With TRAP_IN set (a file of `label|moves`, or `all`), scores each candidate's last move with a local Stockfish. */
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
        // TRAP_IN=all checks every shipped trap: its mistake, and the punishing move after it.
        val rows = if (input == "all") {
            app.chessnut.otb.openings.OpeningTraps.all.map { "${it.name}|${(it.before + it.moves.take(2)).joinToString(" ")}|2" }
        } else {
            File(input).readLines().filter { it.isNotBlank() }
        }
        for (row in rows) {
            val parts = row.split("|")
            val label = parts[0]
            val punish = parts.getOrNull(2) == "2"
            val all = parts[1].split(" ")
            val moves = if (punish) all.dropLast(1) else all
            val fens = ChessLines.replay(moves).fens
            val before = fens[fens.size - 2]
            val after = fens.last()
            val (bestCp, bestPv) = go(before)
            val (afterCp, afterPv) = go(after)
            // Both from the mistake-maker's view: before is theirs to move, after is the opponent's.
            val drop = bestCp + afterCp
            out.append("$label: loses ${drop}cp. best was ${ChessLines.sanOf(before, bestPv.take(1)).joinToString()}; refutation ${ChessLines.sanOf(after, afterPv.take(9)).joinToString(" ")}")
            if (punish) {
                // The punishing move, against the engine's best there: what it gives back.
                // A mating punish is the best there is: a mated position has no score to read.
                val back = if (all.last().endsWith("#")) 0 else afterCp + go(ChessLines.replay(all).fens.last()).first
                out.append(" | punish ${all.last()} gives back ${back}cp")
            }
            out.append("\n")
        }
        send("quit")
        File(System.getenv("TRAP_OUT")).writeText(out.toString())
    }
}
