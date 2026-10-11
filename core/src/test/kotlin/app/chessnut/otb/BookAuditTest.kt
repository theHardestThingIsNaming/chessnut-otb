package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. With AUDIT_OUT set, scores every move of yours in the
 * main openings' drill lines with a local Stockfish and lists those that give
 * away at least 150 centipawns against the engine's best: book moves the
 * trainer shouldn't teach. Lines are `loss \t opening \t before \t move \t best`.
 */
class BookAuditTest {
    @Test
    fun audit() {
        val outPath = System.getenv("AUDIT_OUT") ?: return
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 6")
        fun score(fen: String, depth: Int): Pair<Int, String?> {
            send("position fen $fen"); send("go depth $depth")
            var cp = 0; var best: String? = null
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " score " in l && " pv " in l) {
                    val t = l.split(" ")
                    val i = t.indexOf("score")
                    cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                }
                if (l.startsWith("bestmove")) { best = l.split(" ").getOrNull(1); break }
            }
            return cp to best
        }
        val out = StringBuilder()
        val seen = mutableSetOf<List<String>>()
        for (name in OpeningCatalog.browse("").first().names) {
            val white = OpeningBook.side(name) == OpeningSide.White
            for (line in OpeningTree.lines(name, white)) {
                val fens = ChessLines.replay(line).fens
                for (ply in line.indices) {
                    if ((ply % 2 == 0) != white) continue
                    val upTo = line.take(ply + 1)
                    if (!seen.add(upTo)) continue
                    val (best, bestUci) = score(fens[ply], 16)
                    val (after, _) = score(fens[ply + 1], 16)
                    val loss = best + after
                    if (loss >= 150) {
                        val bestSan = bestUci?.let { ChessLines.sanOf(fens[ply], listOf(it)).firstOrNull() }
                        out.append("$loss\t$name\t${line.take(ply).joinToString(" ")}\t${line[ply]}\t$bestSan\n")
                    }
                }
            }
        }
        send("quit")
        File(outPath).writeText(out.toString())
    }
}
