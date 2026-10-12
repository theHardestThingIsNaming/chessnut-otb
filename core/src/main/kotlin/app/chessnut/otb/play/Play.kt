package app.chessnut.otb.play

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.AccuracyCalculator
import com.github.bhlangonijr.chesslib.Board
import kotlin.random.Random

data class EloBracket(val elo: Int, val label: String) {
    companion object {
        val All = (1400..2000 step 100).map { EloBracket(it, it.toString()) }
        val Default = All.first { it.elo == 1800 }
    }
}

/** Picks a move from a MultiPV search so the bot is not glued to the first line. */
object BotChooser {
    fun pick(lines: List<EngineLine>, elo: Int, whiteToMove: Boolean, random: Random = Random.Default): String? {
        val ranked = lines.mapNotNull { line -> line.uci.firstOrNull()?.let { it to advantage(line, whiteToMove) } }
        if (ranked.isEmpty()) return null
        val best = ranked.maxOf { it.second }
        // 140cp at 1400, then 20cp tighter for each 100 Elo, down to 20cp at 2000.
        val steps = ((elo.coerceIn(1400, 2000) - 1400) / 100)
        val slack = 140 - steps * 20
        val pool = ranked.filter { best - it.second <= slack }.ifEmpty { listOf(ranked.first()) }
        if (pool.size == 1) return pool.first().first
        val topBias = ((elo - 1200) / 1600.0).coerceIn(0.35, 0.85)
        if (random.nextDouble() < topBias) return pool.maxBy { it.second }.first
        val rest = pool.filter { it.first != pool.maxBy { line -> line.second }.first }
        val choice = if (rest.isEmpty()) pool else rest
        return choice[random.nextInt(choice.size)].first
    }

    private fun advantage(line: EngineLine, whiteToMove: Boolean): Int =
        if (whiteToMove) line.score.cappedCp else -line.score.cappedCp
}

sealed class TrainerResult {
    data object Book : TrainerResult()
    data class Judged(val classification: MoveClass) : TrainerResult()

    val accepted: Boolean
        get() = this is Book || (this is Judged && when (classification) {
            MoveClass.Brilliant, MoveClass.Best, MoveClass.Excellent, MoveClass.Good -> true
            else -> false
        })

    val label: String
        get() = when (this) {
            Book -> "Book"
            is Judged -> when (classification) {
                MoveClass.Brilliant -> "Brilliant"
                MoveClass.Best -> "Best"
                MoveClass.Excellent -> "Excellent"
                MoveClass.Good -> "Good"
                MoveClass.Inaccuracy -> "Inaccuracy"
                MoveClass.Mistake -> "Mistake"
                MoveClass.Blunder -> "Blunder"
            }
        }
}

object TrainerGate {
    /** A book move always stands. Anything else has to be good or best. */
    fun judge(book: Boolean, playedBest: Boolean, winDrop: Double): TrainerResult =
        if (book) TrainerResult.Book else TrainerResult.Judged(AccuracyCalculator.classify(winDrop, playedBest))
}

/**
 * How long the board must stay still before a move counts. A slide reports
 * every square the piece crosses, and a hand can rest on one on the way, so a
 * short wait reads e2-e4 as e2-e3.
 */
const val BOARD_SETTLE_MS = 350L

object PlayPosition {
    fun placementOf(fen: String): String = fen.substringBefore(' ')

    fun matches(fen: String, placement: String): Boolean = same(placementOf(fen), placement)

    /** Squares that hold a piece in [fen] but are empty on the board's [placement]. */
    fun emptiedSquares(fen: String, placement: String): List<String> {
        val want = squares(placementOf(fen))
        val have = squares(placement)
        return (0 until 64).filter { want[it] != '.' && have[it] == '.' }.map { i -> "${'a' + i % 8}${8 - i / 8}" }
    }

    /** Squares where the board's [placement] differs from [fen], such as "c1", in board order. */
    fun differences(fen: String, placement: String): List<String> {
        val want = squares(placementOf(fen))
        val have = squares(placement)
        return (0 until 64).filter { want[it] != have[it] }.map { i -> "${'a' + i % 8}${8 - i / 8}" }
    }

    /** The legal move whose resulting piece placement is [target], if there is one. */
    fun uciMatching(fen: String, target: String): String? {
        val board = Board().apply { loadFromFen(fen) }
        return board.legalMoves().firstOrNull { move ->
            val next = board.clone()
            next.doMove(move)
            same(placementOf(next.fen), target)
        }?.toString()?.lowercase()
    }

    fun after(fen: String, uci: String): Pair<String, String>? {
        val board = Board().apply { loadFromFen(fen) }
        val move = board.legalMoves().firstOrNull { it.toString().lowercase() == uci.lowercase() } ?: return null
        val san = ChessLines.sanOf(fen, listOf(uci.lowercase())).firstOrNull() ?: return null
        board.doMove(move)
        return san to board.fen
    }

    fun uciOfSan(fen: String, san: String): String? {
        val board = Board().apply { loadFromFen(fen) }
        return board.legalMoves().firstOrNull {
            ChessLines.sanOf(board.fen, listOf(it.toString().lowercase())).firstOrNull() == san
        }?.toString()?.lowercase()
    }

    private fun same(a: String, b: String): Boolean = squares(a).contentEquals(squares(b))

    private fun squares(placement: String): CharArray {
        val out = CharArray(64) { '.' }
        var i = 0
        for (c in placement.substringBefore(' ')) {
            when {
                c == '/' -> continue
                c.isDigit() -> i += c - '0'
                i < 64 -> out[i++] = c
            }
        }
        return out
    }
}
