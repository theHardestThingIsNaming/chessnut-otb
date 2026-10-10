package app.chessnut.otb.analysis

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.exp
import kotlin.math.sqrt

enum class MoveClass { Brilliant, Best, Excellent, Good, Inaccuracy, Mistake, Blunder }

data class MoveReport(
    val ply: Int,
    val san: String,
    val whiteMoved: Boolean,
    /** Mover's win chance before and after the move, 0..100. */
    val winBefore: Double,
    val winAfter: Double,
    val accuracy: Double,
    val classification: MoveClass,
    /** Engine's preferred move in the position before, in UCI. */
    val bestUci: String?,
)

data class GameReport(val whiteAccuracy: Double?, val blackAccuracy: Double?, val moves: List<MoveReport>)

/**
 * Accuracy using the formulas Lichess publishes (win percentage from
 * centipawns, per-move accuracy from the drop in win percentage, and a game
 * score averaging a volatility-weighted mean with a harmonic mean). Written
 * from the formulas, not from lila's source. Numbers come out close to
 * Lichess, not identical: depth and engine build differ.
 *
 * Classification uses the mover's win-percentage drop. A sound sacrifice
 * (the opponent can win material they could not win before, and the move is
 * still best, excellent, or good) is brilliant. Otherwise the engine's move
 * is best, a drop of 2 or less is excellent, then good, and 10 / 20 / 30
 * are an inaccuracy, a mistake, and a blunder.
 */
object AccuracyCalculator {
    fun moveAccuracy(winBefore: Double, winAfter: Double): Double {
        if (winAfter >= winBefore) return 100.0
        val drop = winBefore - winAfter
        val raw = 103.1668100711649 * exp(-0.04354415386753951 * drop) - 3.166924740191411
        return (raw + 1).coerceIn(0.0, 100.0)
    }

    fun classify(winDrop: Double, playedBest: Boolean, sacrifice: Boolean = false): MoveClass {
        val sound = playedBest || winDrop < 10
        if (sacrifice && sound) return MoveClass.Brilliant
        if (playedBest) return MoveClass.Best
        return when {
            winDrop >= 30.0 -> MoveClass.Blunder
            winDrop >= 20.0 -> MoveClass.Mistake
            winDrop >= 10.0 -> MoveClass.Inaccuracy
            winDrop <= 2.0 -> MoveClass.Excellent
            else -> MoveClass.Good
        }
    }

    /**
     * True when [uci] gives the opponent a material gain they did not already
     * have: a piece left where it can be taken, or a defender moved away.
     * An even trade is not a sacrifice.
     */
    fun isSacrifice(fen: String, uci: String): Boolean {
        return try {
            val board = Board().apply { loadFromFen(fen) }
            val move = board.legalMoves().firstOrNull { it.toString().lowercase() == uci.lowercase() } ?: return false
        val taken = capturedValue(board, move)
        val before = opponentBoard(fen)?.let { bestCapture(it) } ?: 0
        if (!board.doMove(move)) return false
        bestCapture(board) - taken > before
        } catch (_: RuntimeException) {
            false
        }
    }

    /** [playedUci] has one entry per move; [analysis] has one more position than moves. */
    fun report(sanMoves: List<String>, playedUci: List<String>, analysis: GameAnalysis): GameReport {
        val positions = analysis.positions
        val count = minOf(sanMoves.size, positions.size - 1)
        if (count <= 0) return GameReport(null, null, emptyList())
        val whiteWins = positions.take(count + 1).map { it.score.whiteWinPercent }

        val moves = (0 until count).map { ply ->
            val whiteMoved = positions[ply].fen.split(' ')[1] == "w"
            val before = if (whiteMoved) whiteWins[ply] else 100 - whiteWins[ply]
            val after = if (whiteMoved) whiteWins[ply + 1] else 100 - whiteWins[ply + 1]
            val best = positions[ply].lines.firstOrNull()?.uci?.firstOrNull()
            val played = playedUci.getOrNull(ply)
            MoveReport(
                ply = ply,
                san = sanMoves[ply],
                whiteMoved = whiteMoved,
                winBefore = before,
                winAfter = after,
                accuracy = moveAccuracy(before, after),
                classification = classify(
                    before - after,
                    best != null && best == played,
                    sacrifice = played != null && isSacrifice(positions[ply].fen, played),
                ),
                bestUci = best,
            )
        }

        val windowSize = (count / 10).coerceIn(2, 8)
        val windows = buildList {
            repeat((windowSize.coerceAtMost(whiteWins.size) - 2).coerceAtLeast(0)) { add(whiteWins.take(windowSize)) }
            addAll(whiteWins.windowed(windowSize.coerceAtMost(whiteWins.size)))
        }
        val weights = windows.map { standardDeviation(it).coerceIn(0.5, 12.0) }

        fun sideAccuracy(white: Boolean): Double? {
            val mine = moves.mapIndexedNotNull { index, move ->
                if (move.whiteMoved == white) move.accuracy to weights.getOrElse(index) { weights.lastOrNull() ?: 1.0 } else null
            }
            if (mine.isEmpty()) return null
            val weighted = mine.sumOf { it.first * it.second } / mine.sumOf { it.second }
            val harmonic = mine.size / mine.sumOf { 1 / it.first.coerceAtLeast(1.0) }
            return (weighted + harmonic) / 2
        }
        return GameReport(sideAccuracy(true), sideAccuracy(false), moves)
    }

    /** The same position with the other side to move, so their captures can be measured. */
    private fun opponentBoard(fen: String): Board? {
        val parts = fen.split(' ').toMutableList()
        if (parts.size < 4) return null
        parts[1] = if (parts[1] == "w") "b" else "w"
        parts[3] = "-"
        return try {
            Board().apply { loadFromFen(parts.joinToString(" ")) }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun bestCapture(board: Board): Int {
        val squares = board.legalMoves().filter { capturedValue(board, it) > 0 }.map { it.to }.distinct()
        var best = 0
        for (square in squares) {
            val gain = see(board, square)
            if (gain > best) best = gain
        }
        return best
    }

    /** Material the side to move wins by capturing on [square], or a loss if they cannot. */
    private fun see(board: Board, square: Square): Int {
        val captures = board.legalMoves().filter { it.to == square && capturedValue(board, it) > 0 }
        if (captures.isEmpty()) return 0
        val move = captures.minBy { pieceValue(board.getPiece(it.from)) }
        val gained = capturedValue(board, move)
        if (!board.doMove(move)) return 0
        val reply = see(board, square).coerceAtLeast(0)
        board.undoMove()
        return gained - reply
    }

    private fun capturedValue(board: Board, move: Move): Int {
        val onTarget = board.getPiece(move.to)
        if (onTarget != Piece.NONE) return pieceValue(onTarget)
        val mover = board.getPiece(move.from)
        if (mover.pieceType == PieceType.PAWN && move.from.file != move.to.file) return 1
        return 0
    }

    private fun pieceValue(piece: Piece): Int = when (piece.pieceType) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT, PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 100
        else -> 0
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
