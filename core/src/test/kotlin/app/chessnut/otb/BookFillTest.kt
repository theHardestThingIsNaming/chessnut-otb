package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. With BOOK_FILL_OUT set, asks a local Stockfish for your
 * answer wherever an opening's book line stops on the opponent's move, and
 * writes the completed lines in the custom book's format for review.
 */
class BookFillTest {
    @Test
    fun fillOpenEnds() {
        val out = System.getenv("BOOK_FILL_OUT") ?: return
        val engine = ProcessBuilder(System.getenv("STOCKFISH") ?: "stockfish").redirectErrorStream(true).start()
        val input = engine.outputStream.bufferedWriter()
        val output = engine.inputStream.bufferedReader()
        fun send(line: String) {
            input.write(line + "\n")
            input.flush()
        }
        fun waitFor(prefix: String): String {
            while (true) {
                val line = output.readLine() ?: error("engine closed")
                if (line.startsWith(prefix)) return line
            }
        }
        send("uci"); waitFor("uciok")
        send("setoption name Threads value 4")
        send("isready"); waitFor("readyok")
        val rows = linkedSetOf<String>()
        val names = OpeningCatalog.browse("").flatMap { it.names }.distinct()
        for (name in names) {
            val white = OpeningBook.side(name) == OpeningSide.White
            for (line in OpeningTree.openEnds(name, white)) {
                val fen = ChessLines.replay(line).fens.last()
                if (ChessLines.terminalOf(fen) != null) continue
                send("position fen $fen")
                send("go depth 20")
                val uci = waitFor("bestmove").split(' ')[1]
                val san = ChessLines.sanOf(fen, listOf(uci)).firstOrNull() ?: continue
                val full = line + san
                val view = OpeningBook.view(full)
                val pgn = full.mapIndexed { i, m -> if (i % 2 == 0) "${i / 2 + 1}. $m" else m }.joinToString(" ")
                rows += "${view.eco ?: "A00"}\t${view.name ?: name}\t$pgn"
            }
        }
        send("quit")
        File(out).writeText(rows.joinToString("\n", postfix = "\n"))
    }
}
