package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.MasterGames
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Walks a shipped master game (MASTER_GAME id) with Stockfish at depth 18:
 * for each move, the score before it (White's view), the engine's best move, and how much the
 * played move gave away, plus the board every few moves. For writing a game's story and review points.
 */
class MasterEvalTest {
    @Test
    fun walk() {
        val id = System.getenv("MASTER_GAME") ?: return
        val game = MasterGames.byId(id) ?: error("no game $id")
        val replay = ChessLines.replay(game.moves)
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 2")
        fun eval(fen: String): Pair<Int, List<String>> {
            send("position fen $fen"); send("go depth 18")
            var cp = 0; var pv = emptyList<String>()
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " pv " in l && " score " in l) {
                    val t = l.split(" "); val i = t.indexOf("score")
                    cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                    pv = t.drop(t.indexOf("pv") + 1)
                }
                if (l.startsWith("bestmove")) break
            }
            // White's view.
            return (if (fen.split(' ')[1] == "w") cp else -cp) to pv
        }
        val out = StringBuilder("${game.title} ${game.result}\n")
        var before = eval(replay.fens[0])
        for (ply in game.moves.indices) {
            val after = eval(replay.fens[ply + 1])
            val white = ply % 2 == 0
            val loss = if (white) before.first - after.first else after.first - before.first
            val best = ChessLines.sanOf(replay.fens[ply], before.second.take(1)).firstOrNull()
            val pv = runCatching { ChessLines.sanOf(replay.fens[ply + 1], after.second.take(5)).joinToString(" ") }.getOrDefault("")
            val label = "${ply / 2 + 1}${if (white) "." else "..."} ${game.moves[ply]}"
            out.append("$ply\t$label\teval ${after.first}\tloss $loss\tbest $best\tthen $pv\n")
            if (ply % 6 == 5 || loss >= 80) {
                val board = com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(replay.fens[ply + 1]) }
                out.append(board.toString().lines().take(8).joinToString("\n") { "      $it" }).append("\n")
            }
            before = after
        }
        send("quit")
        File(System.getenv("MASTER_OUT")).writeText(out.toString())
    }
}
