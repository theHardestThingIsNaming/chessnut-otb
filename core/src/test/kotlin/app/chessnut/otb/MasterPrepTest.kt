package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Prepares master games for the guess-the-move mode.
 * MASTER_IN is the phone's masters-scan.tsv (PGN rows), MASTER_PICK lists "line:gameId" pairs for
 * MASTER_OPENING ("Scotch Game|w"). For each game it finds where the game reaches the line's last
 * position, then for the next MASTER_MOVES moves of your side (default 6) asks Stockfish for:
 * your side's top three moves with scores, and after each opponent move, the threat (its best move
 * if you passed) and what it would win. Writes a fact sheet to MASTER_OUT for writing the notes.
 */
class MasterPrepTest {
    private fun sans(pgn: String): List<String> =
        pgn.lines().filterNot { it.startsWith("[") }.joinToString(" ")
            .replace(Regex("\\{[^}]*\\}"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && !Regex("^\\d+\\.+$").matches(it) && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }
            .map { it.replace(Regex("^\\d+\\.+"), "") }

    @Test
    fun prepare() {
        val input = System.getenv("MASTER_IN") ?: return
        val (opening, side) = System.getenv("MASTER_OPENING").split('|')
        val white = side == "w"
        val count = System.getenv("MASTER_MOVES")?.toInt() ?: 6
        val pgns = File(input).readLines().filter { it.startsWith("PGN\t") }.associate { row ->
            val p = row.split('\t', limit = 3)
            p[1] to p[2].replace("\\n", "\n")
        }
        val lines = OpeningTree.lines(opening, white)
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val w = engine.outputStream.bufferedWriter()
        val r = engine.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        send("uci"); while (r.readLine() != "uciok") {}
        send("setoption name Threads value 2")
        /** Top [multi] moves from [fen], each with its score for the side to move. */
        fun top(fen: String, multi: Int): List<Pair<String, Int>> {
            send("setoption name MultiPV value $multi"); send("position fen $fen"); send("go depth 18")
            val best = sortedMapOf<Int, Pair<String, Int>>()
            while (true) {
                val l = r.readLine() ?: error("closed")
                if (l.startsWith("info") && " pv " in l && " multipv " in l) {
                    val t = l.split(" ")
                    val i = t.indexOf("score")
                    val cp = if (t[i + 1] == "mate") (if (t[i + 2].toInt() > 0) 10000 else -10000) else t[i + 2].toInt()
                    best[t[t.indexOf("multipv") + 1].toInt()] = t[t.indexOf("pv") + 1] to cp
                }
                if (l.startsWith("bestmove")) break
            }
            return best.values.toList()
        }
        val out = StringBuilder()
        for (pick in System.getenv("MASTER_PICK").split(',')) {
            val (lineNo, id) = pick.split(':')
            val line = lines[lineNo.toInt() - 1]
            val pgn = pgns[id] ?: error("no PGN for $id")
            val head = Regex("\\[(\\w+) \"([^\"]*)\"\\]").findAll(pgn).associate { it.groupValues[1] to it.groupValues[2] }
            val moves = sans(pgn)
            val replay = ChessLines.replay(moves)
            val lineEnd = ChessLines.replay(line).fens.last().split(' ').take(2)
            val start = replay.fens.indexOfFirst { it.split(' ').take(2) == lineEnd }
            check(start >= 0) { "$id never reaches line $lineNo" }
            out.append("GAME\t$lineNo\t$id\t${head["White"]}\t${head["Black"]}\t${head["Date"]?.take(4)}\t${head["Event"]}\t${head["Result"]}\t$start\t${moves.joinToString(" ")}\n")
            var mine = 0
            var ply = start
            while (ply < moves.size && mine < count) {
                val fen = replay.fens[ply]
                val yours = (ply % 2 == 0) == white
                val san = moves[ply]
                val uci = replay.uci[ply]
                // MASTER_BOARDS=1: the board before the move and the engine's line after it, to write the notes from.
                if (System.getenv("MASTER_BOARDS") != null) {
                    val board = com.github.bhlangonijr.chesslib.Board().apply { loadFromFen(fen) }
                    out.append("BOARD\t$ply\n").append(board.toString().lines().take(8).joinToString("\n") { "    $it" }).append("\n")
                    val after = replay.fens[ply + 1]
                    send("setoption name MultiPV value 1"); send("position fen $after"); send("go depth 18")
                    var pv = emptyList<String>()
                    while (true) {
                        val l = r.readLine() ?: error("closed")
                        if (l.startsWith("info") && " pv " in l) pv = l.split(" ").let { t -> t.drop(t.indexOf("pv") + 1) }
                        if (l.startsWith("bestmove")) break
                    }
                    out.append("PV\t$ply\t$san then ${runCatching { ChessLines.sanOf(after, pv.take(6)).joinToString(" ") }.getOrDefault("")}\n")
                }
                if (yours) {
                    val options = top(fen, 3)
                    val shown = options.map { (u, cp) -> "${ChessLines.sanOf(fen, listOf(u)).first()}=$u($cp)" }
                    out.append("YOU\t$ply\t$san\t$uci\t${shown.joinToString(" ")}\n")
                    mine++
                } else {
                    val after = replay.fens[ply + 1]
                    val threat = ChessLines.passFen(after)?.let { passed ->
                        val (u, passCp) = top(passed, 1).first()
                        val (_, nowCp) = top(after, 1).first()
                        // What the threat is worth: the opponent's score if you passed, against your score now.
                        "${ChessLines.sanOf(passed, listOf(u)).first()}=$u(${passCp + nowCp})"
                    } ?: "-"
                    out.append("THEM\t$ply\t$san\t$uci\tthreat $threat\n")
                }
                ply++
            }
        }
        send("quit")
        File(System.getenv("MASTER_OUT")).writeText(out.toString())
    }
}
