package app.chessnut.otb.live

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

enum class LiveEnd {
    Checkmate,
    Stalemate,
    Draw,
    WhiteResign,
    BlackResign,
    WhiteFlag,
    BlackFlag,
}

data class LiveSnapshot(
    val started: Boolean = false,
    val ended: Boolean = false,
    val toMoveWhite: Boolean = true,
    val sanMoves: List<String> = emptyList(),
    val lastUci: String? = null,
    val whiteMillis: Long = TimeControl.Default.initialMillis,
    val blackMillis: Long = TimeControl.Default.initialMillis,
    val flaggedWhite: Boolean = false,
    val flaggedBlack: Boolean = false,
    val result: String = "*",
    val end: LiveEnd? = null,
    val canSave: Boolean = false,
    val control: TimeControl = TimeControl.Default,
    val whiteOfferedDraw: Boolean = false,
    val blackOfferedDraw: Boolean = false,
)

sealed class LiveEvent {
    data object Ignored : LiveEvent()
    data class Illegal(val squares: List<String> = emptyList()) : LiveEvent()
    data class Move(val san: String, val check: Boolean = false, val squares: List<String> = emptyList()) : LiveEvent()
    data class Finished(
        val result: String,
        val sanMoves: List<String>,
        val placements: List<String>,
        val end: LiveEnd? = null,
        val squares: List<String> = emptyList(),
    ) : LiveEvent()
}

/**
 * Turns the Air's live placement stream into moves and a countdown clock.
 * Piece-lift frames are skipped. Checkmate (including a king taken off the
 * board), stalemate, flag, and resign all stop the clocks.
 */
class LiveGameTracker(control: TimeControl = TimeControl.Default) {
    private val start: String = placementOf(Board().fen)
    private var board = Board()
    private val moves = mutableListOf<String>()
    private val placements = mutableListOf<String>()
    private var lastUci: String? = null
    private var lastPlacement: String? = null
    private var started = false
    private var ended = false
    private var saved = false
    private var result = "*"
    private var end: LiveEnd? = null
    var control: TimeControl = control
        private set
    private var whiteMillis = control.initialMillis
    private var blackMillis = control.initialMillis
    private var runningSince: Long? = null
    private var flaggedWhite = false
    private var flaggedBlack = false
    private var whiteOfferedDraw = false
    private var blackOfferedDraw = false
    /** White's clock stays frozen until Black is to move. */
    private var whitePressed = false

    fun reset() {
        board = Board()
        moves.clear()
        placements.clear()
        lastUci = null
        lastPlacement = null
        started = false
        ended = false
        saved = false
        result = "*"
        end = null
        whiteMillis = this.control.initialMillis
        blackMillis = this.control.initialMillis
        runningSince = null
        flaggedWhite = false
        flaggedBlack = false
        whiteOfferedDraw = false
        blackOfferedDraw = false
        whitePressed = false
    }

    fun markSaved() {
        saved = true
    }

    fun setControl(control: TimeControl, now: Long) {
        this.control = control
        if (!started || ended || moves.isEmpty()) {
            whiteMillis = control.initialMillis
            blackMillis = control.initialMillis
            if (started && !ended && whitePressed) runningSince = now
        }
    }

    fun snapshot(now: Long): LiveSnapshot {
        val white = remaining(white = true, now)
        val black = remaining(white = false, now)
        return LiveSnapshot(
            started = started,
            ended = ended,
            toMoveWhite = board.sideToMove == Side.WHITE,
            sanMoves = moves.toList(),
            lastUci = lastUci,
            whiteMillis = white,
            blackMillis = black,
            flaggedWhite = flaggedWhite,
            flaggedBlack = flaggedBlack,
            result = result,
            end = end,
            canSave = moves.isNotEmpty() && !saved,
            control = control,
            whiteOfferedDraw = whiteOfferedDraw,
            blackOfferedDraw = blackOfferedDraw,
        )
    }

    fun poll(now: Long): LiveEvent = flagIfExpired(now) ?: LiveEvent.Ignored

    fun resign(white: Boolean, now: Long): LiveEvent {
        if (!started || ended) return LiveEvent.Ignored
        deduct(now)
        ended = true
        runningSince = null
        if (white) {
            result = "0-1"
            end = LiveEnd.WhiteResign
        } else {
            result = "1-0"
            end = LiveEnd.BlackResign
        }
        return if (moves.isEmpty()) LiveEvent.Ignored else LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
    }

    fun offerDraw(white: Boolean, now: Long): LiveEvent {
        if (!started || ended) return LiveEvent.Ignored
        val opponentOffered = if (white) blackOfferedDraw else whiteOfferedDraw
        if (opponentOffered) {
            deduct(now)
            ended = true
            runningSince = null
            result = "1/2-1/2"
            end = LiveEnd.Draw
            whiteOfferedDraw = false
            blackOfferedDraw = false
            return if (moves.isEmpty()) LiveEvent.Ignored else LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
        }
        if (white) whiteOfferedDraw = !whiteOfferedDraw else blackOfferedDraw = !blackOfferedDraw
        return LiveEvent.Ignored
    }

    fun onPlacement(raw: String, now: Long): LiveEvent {
        flagIfExpired(now)?.let { return it }
        val placement = placementOf(raw)
        if (sameSquares(placement, start)) {
            lastPlacement = placement
            if (started && !ended && moves.isEmpty()) return LiveEvent.Ignored
            val finished = finishIfUnsaved(now)
            begin(now)
            placements += placement
            return finished ?: LiveEvent.Ignored
        }
        if (placement == lastPlacement) return LiveEvent.Ignored
        lastPlacement = placement
        if (!started || ended) return LiveEvent.Ignored
        if (sameSquares(placement, placementOf(board.fen))) return LiveEvent.Ignored

        val move = board.legalMoves().firstOrNull { matches(board, it, placement) }
        if (move != null) {
            val san = sanOf(board, move)
            val flagged = deduct(now)
            if (flagged != null) return flagged
            val wasWhite = board.sideToMove == Side.WHITE
            if (whitePressed) {
                if (wasWhite) whiteMillis += control.incrementMillis else blackMillis += control.incrementMillis
            }
            board.doMove(move)
            moves += san
            lastUci = move.toString().lowercase()
            placements += placement
            if (!whitePressed) whitePressed = true
            runningSince = now
            whiteOfferedDraw = false
            blackOfferedDraw = false
            terminalOf(board)?.let { return stop(it.first, it.second, now, if (it.second == LiveEnd.Checkmate) checkLights() else emptyList()) }
            return LiveEvent.Move(san, check = board.isKingAttacked, squares = if (board.isKingAttacked) checkLights() else emptyList())
        }

        // A piece in the air is not a result. King-lifts during a move (castling,
        // stepping out of check) used to be scored as taking the king.
        if (isLift(placementOf(board.fen), placement)) return LiveEvent.Ignored
        kingTaken(placement)?.let {
            placements += placement
            val missingWhite = 'K' !in placement
            return stop(it, LiveEnd.Checkmate, now, lightsOf(kingAlgebraic(placementOf(board.fen), missingWhite)))
        }
        mateOnBoard(placement)?.let {
            placements += placement
            return stop(it, LiveEnd.Checkmate, now, checkLights())
        }
        return LiveEvent.Illegal(mismatchedSquares(placementOf(board.fen), placement))
    }

    fun finishNow(now: Long): LiveEvent.Finished? = finishIfUnsaved(now)

    private fun stop(gameResult: String, kind: LiveEnd, now: Long, squares: List<String> = emptyList()): LiveEvent.Finished {
        deduct(now)
        result = gameResult
        end = kind
        ended = true
        runningSince = null
        return LiveEvent.Finished(gameResult, moves.toList(), placements.toList(), kind, squares)
    }

    private fun flagIfExpired(now: Long): LiveEvent.Finished? {
        if (!started || ended || !whitePressed) return null
        val extra = runningExtra(now)
        val remaining = (if (board.sideToMove == Side.WHITE) whiteMillis else blackMillis) - extra
        if (remaining > 0) return null
        if (board.sideToMove == Side.WHITE) {
            whiteMillis = 0
            flaggedWhite = true
            result = "0-1"
            end = LiveEnd.WhiteFlag
        } else {
            blackMillis = 0
            flaggedBlack = true
            result = "1-0"
            end = LiveEnd.BlackFlag
        }
        ended = true
        runningSince = null
        if (moves.isEmpty()) return null
        return LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
    }

    private fun finishIfUnsaved(now: Long): LiveEvent.Finished? {
        if (!started || ended || moves.isEmpty() || saved) return null
        deduct(now)
        ended = true
        runningSince = null
        return LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
    }

    private fun begin(now: Long) {
        board = Board()
        moves.clear()
        placements.clear()
        lastUci = null
        started = true
        ended = false
        saved = false
        result = "*"
        end = null
        whiteMillis = control.initialMillis
        blackMillis = control.initialMillis
        runningSince = null
        whitePressed = false
        flaggedWhite = false
        flaggedBlack = false
        whiteOfferedDraw = false
        blackOfferedDraw = false
    }

    private fun deduct(now: Long): LiveEvent.Finished? {
        val extra = runningExtra(now)
        if (board.sideToMove == Side.WHITE) {
            whiteMillis -= extra
            if (whiteMillis <= 0) {
                whiteMillis = 0
                flaggedWhite = true
                result = "0-1"
                end = LiveEnd.WhiteFlag
                ended = true
                runningSince = null
                return if (moves.isEmpty()) null else LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
            }
        } else {
            blackMillis -= extra
            if (blackMillis <= 0) {
                blackMillis = 0
                flaggedBlack = true
                result = "1-0"
                end = LiveEnd.BlackFlag
                ended = true
                runningSince = null
                return if (moves.isEmpty()) null else LiveEvent.Finished(result, moves.toList(), placements.toList(), end)
            }
        }
        runningSince = now
        return null
    }

    private fun remaining(white: Boolean, now: Long): Long {
        val base = if (white) whiteMillis else blackMillis
        val running = started && !ended && whitePressed && runningSince != null &&
            ((white && board.sideToMove == Side.WHITE) || (!white && board.sideToMove == Side.BLACK))
        val extra = if (running) runningExtra(now) else 0
        return (base - extra).coerceAtLeast(0)
    }

    private fun runningExtra(now: Long): Long {
        val from = runningSince ?: return 0
        return (now - from).coerceAtLeast(0)
    }

    private fun kingTaken(placement: String): String? {
        val whiteKing = 'K' in placement
        val blackKing = 'k' in placement
        return when {
            whiteKing && !blackKing -> "1-0"
            blackKing && !whiteKing -> "0-1"
            else -> null
        }
    }

    /** True when pieces only left squares — a lift, not a completed move or capture. */
    private fun isLift(from: String, to: String): Boolean {
        val a = squares(from)
        val b = squares(to)
        var removed = 0
        for (i in 0 until 64) {
            if (b[i] != '.' && b[i] != a[i]) return false
            if (a[i] != '.' && b[i] == '.') removed++
        }
        return removed > 0
    }

    private fun sameSquares(a: String, b: String): Boolean = squares(a).contentEquals(squares(b))

    private fun checkLights(): List<String> =
        lightsOf(kingAlgebraic(placementOf(board.fen), board.sideToMove == Side.WHITE))

    private fun lightsOf(king: String?): List<String> =
        (uciSquares(lastUci) + listOfNotNull(king)).distinct()

    private fun mismatchedSquares(expected: String, actual: String): List<String> {
        val a = squares(expected)
        val b = squares(actual)
        return (0 until 64).filter { a[it] != b[it] }.map { algebraic(it) }
    }

    private fun kingAlgebraic(placement: String, white: Boolean): String? {
        val piece = if (white) 'K' else 'k'
        val i = squares(placement).indexOf(piece)
        return if (i >= 0) algebraic(i) else null
    }

    private fun uciSquares(uci: String?): List<String> {
        val move = uci ?: return emptyList()
        if (move.length < 4) return emptyList()
        return listOf(move.substring(0, 2), move.substring(2, 4))
    }

    private fun algebraic(index: Int): String = "${'a' + index % 8}${8 - index / 8}"

    private fun squares(placement: String): CharArray {
        val out = CharArray(64) { '.' }
        var i = 0
        for (c in placement) {
            when {
                c == '/' -> continue
                c.isDigit() -> i += c - '0'
                i < 64 -> out[i++] = c
            }
        }
        return out
    }

    /** Mate on the physical board even if the last ply did not match a single legal move. */
    private fun mateOnBoard(placement: String): String? {
        for (side in listOf(Side.WHITE, Side.BLACK)) {
            val fen = "$placement ${if (side == Side.WHITE) "w" else "b"} - - 0 1"
            val loaded = Board()
            if (runCatching { loaded.loadFromFen(fen) }.isFailure) continue
            if (isCheckmate(loaded)) return if (side == Side.WHITE) "0-1" else "1-0"
        }
        return null
    }

    private fun terminalOf(board: Board): Pair<String, LiveEnd>? = when {
        isCheckmate(board) -> (if (board.sideToMove == Side.WHITE) "0-1" else "1-0") to LiveEnd.Checkmate
        isStalemate(board) -> "1/2-1/2" to LiveEnd.Stalemate
        else -> null
    }

    private fun isCheckmate(board: Board): Boolean =
        board.isMated || (board.legalMoves().isEmpty() && board.isKingAttacked)

    private fun isStalemate(board: Board): Boolean =
        board.isStaleMate || (board.legalMoves().isEmpty() && !board.isKingAttacked)

    private fun matches(board: Board, move: Move, target: String): Boolean {
        val next = board.clone()
        next.doMove(move)
        return sameSquares(placementOf(next.fen), target)
    }

    private fun sanOf(board: Board, move: Move): String {
        val list = MoveList(board.fen)
        list.add(move)
        return list.toSan().trim()
    }

    private fun placementOf(fen: String): String = fen.trim().substringBefore(' ')
}
