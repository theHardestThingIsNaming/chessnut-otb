package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. With TRAP_SCAN set to "Opening|w" or "Opening|b" (your
 * side), scores every opponent move in each position of your lines with a
 * local Stockfish and lists the ones that lose heavily, as trap candidates.
 */
class TrapScanTest {
    @Test
    fun scan() {
        val spec = System.getenv("TRAP_SCAN") ?: return
        val (opening, side) = spec.split("|")
        val white = side == "w"
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 6")
        send("setoption name MultiPV value 256")
        val out = StringBuilder()
        val seen = mutableSetOf<List<String>>()
        for (line in OpeningTree.lines(opening, white)) {
            for (ply in line.indices) {
                val before = line.take(ply)
                // The opponent's turn: White to move when you are Black.
                if ((ply % 2 == 0) == white || !seen.add(before)) continue
                val fen = ChessLines.replay(before).fens.last()
                send("position fen $fen"); send("go depth 16")
                val scores = mutableMapOf<String, Pair<Int, List<String>>>()
                while (true) {
                    val l = r.readLine() ?: error("closed")
                    if (l.startsWith("info") && " multipv " in l && " pv " in l) {
                        val t = l.split(" ")
                        if (t[t.indexOf("depth") + 1].toInt() < 16) continue
                        val i = t.indexOf("score")
                        val cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                        val pv = t.drop(t.indexOf("pv") + 1)
                        scores[pv.first()] = cp to pv
                    }
                    if (l.startsWith("bestmove")) break
                }
                val best = scores.values.maxOf { it.first }
                for ((uci, value) in scores) {
                    val drop = best - value.first
                    if (drop < 250 || value.first < -9000 && best < -9000) continue
                    val san = ChessLines.sanOf(fen, value.second.take(9)).joinToString(" ")
                    out.append("${drop}\t${before.joinToString(" ")}\t$san\n")
                }
            }
        }
        send("quit")
        File(System.getenv("TRAP_OUT")).writeText(out.lines().filter { it.isNotBlank() }.sortedBy { it.split("\t")[1].length }.joinToString("\n"))
    }
}
