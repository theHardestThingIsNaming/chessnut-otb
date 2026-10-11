package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Reads MISTAKE_IN (lines `opening \t SAN moves \t explorer JSON`)
 * and, for every move club players play in at least 3% of the games there, asks a
 * local Stockfish how much it loses. Writes the costly ones to MISTAKE_OUT as
 * `loss \t share% \t games \t position \t move \t refutation`, most played first.
 */
class MistakeMineTest {
    @Test
    fun mine() {
        val input = System.getenv("MISTAKE_IN") ?: return
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 6")
        send("setoption name MultiPV value 1")
        // Score after a move, from the mover's side, with the engine's reply line.
        fun score(fen: String, moves: List<String>): Pair<Int, List<String>> {
            send("position fen $fen moves ${moves.joinToString(" ")}".trimEnd()); send("go depth 20")
            var cp = 0; var pv = emptyList<String>()
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " pv " in l && " score " in l) {
                    val t = l.split(" ")
                    val i = t.indexOf("score")
                    cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                    pv = t.drop(t.indexOf("pv") + 1)
                }
                if (l.startsWith("bestmove")) break
            }
            return cp to pv
        }
        val out = mutableListOf<Pair<Double, String>>()
        for (row in File(input).readLines().filter { it.isNotBlank() }) {
            val (opening, position, json) = row.split("\t", limit = 3)
            val root = JsonLite.parse(json) as? Json.Obj ?: continue
            fun games(n: Json?) = listOf("white", "draws", "black").sumOf { n?.long(it) ?: 0L }
            val total = games(root)
            if (total < 30) continue
            val before = position.split(" ").filter { it.isNotBlank() }
            val fen = ChessLines.replay(before).fens.last()
            val best = score(fen, emptyList()).first // side to move's score
            for (m in (root.fields["moves"] as? Json.Arr)?.items.orEmpty()) {
                val played = games(m)
                val share = played * 100.0 / total
                if (share < 3 || played < 30) continue
                val uci = m.str("uci") ?: continue
                val (reply, pv) = score(fen, listOf(uci)) // opponent's view after the move
                val loss = best + reply
                if (loss < 150) continue
                val line = ChessLines.sanOf(fen, listOf(uci) + pv.take(7)).joinToString(" ")
                out += share to "$loss\t${"%.1f".format(share)}%\t$played\t$opening: $position\t${m.str("san")}\t$line"
            }
        }
        send("quit")
        File(System.getenv("MISTAKE_OUT")).writeText(out.sortedByDescending { it.first }.joinToString("\n") { it.second })
    }
}
