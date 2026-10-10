package app.chessnut.otb.openings

import com.github.bhlangonijr.chesslib.Bitboard
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * What a move does on the board, in plain words: what it attacks, pins,
 * uncovers, defends or escapes, worked out from the position before and
 * after it. Used for moves without a written note, so the explanation fits
 * the game instead of repeating one line per piece type.
 */
internal object MoveFeatures {
    /** Up to [limit] short sentences, most important first. Empty when nothing stands out. */
    fun of(fenBefore: String, uci: String, limit: Int = 2): List<String> {
        val before = Board().apply { loadFromFen(fenBefore) }
        val move = before.legalMoves().firstOrNull { it.toString().equals(uci, ignoreCase = true) } ?: return emptyList()
        val mover = before.sideToMove
        val opponent = mover.flip()
        val moved = before.getPiece(move.from)
        val type = moved.pieceType ?: return emptyList()
        val after = Board().apply { loadFromFen(fenBefore) }
        after.doMove(move)

        val found = mutableListOf<Pair<Int, String>>()
        check(before, after, move, mover, opponent)?.let { found += 100 to it }
        // A piece that is simply lost or traded off next move threatens nothing worth saying.
        // A pawn that attacks a piece forces an answer even when it can be taken, as in a gambit.
        val loose = type != PieceType.PAWN && !after.isKingAttacked && loose(after, move.to, type, mover, opponent)
        val targets = if (loose) emptyList() else newTargets(before, after, move, type, mover, opponent)
        val pin = if (loose) null else pin(after, move.to, type, opponent)
        when {
            targets.size >= 2 -> found += 90 to "Forks ${both(targets[0], targets[1])}."
            targets.size == 1 && targets[0].type != PieceType.KING -> found += 70 to threatPhrase(targets[0])
        }
        pin?.let { found += 80 to it }
        uncovered(before, after, move, mover, opponent)?.let { found += 60 to it }
        if (!loose) pileUp(before, after, move, mover, opponent)?.let { found += 50 to it }
        defends(before, after, move, mover, opponent)?.let { found += 40 to it }
        escapes(before, move, type, mover, opponent)?.let { found += 30 to it }
        return found.sortedByDescending { it.first }.map { it.second }.distinct().take(limit)
    }

    /** The piece on [square] can be taken for free, or by something cheaper. */
    private fun loose(after: Board, square: Square, type: PieceType, mover: Side, opponent: Side): Boolean {
        val attackers = Bitboard.bbToSquareList(after.squareAttackedBy(square, opponent))
        if (attackers.isEmpty()) return false
        if (after.squareAttackedBy(square, mover) == 0L) return true
        val value = VALUE[type] ?: 0
        return attackers.any { (VALUE[after.getPiece(it).pieceType] ?: 100) < value }
    }

    /** A piece the moved piece can now win or that is worth more than it, or the king when it gives check. */
    data class Target(val type: PieceType, val square: Square, val undefended: Boolean)

    private fun check(before: Board, after: Board, move: Move, mover: Side, opponent: Side): String? {
        if (before.isKingAttacked) {
            val type = before.getPiece(move.from).pieceType
            return when {
                type == PieceType.KING -> "Steps the king out of check."
                before.getPiece(move.to) != Piece.NONE -> "Takes the checking piece."
                else -> "Blocks the check."
            }
        }
        if (!after.isKingAttacked) return null
        val king = after.getKingSquare(opponent)
        val checkers = Bitboard.bbToSquareList(after.squareAttackedBy(king, mover))
        val direct = move.to in checkers
        val others = checkers.filter { it != move.to }
        return when {
            direct && others.isNotEmpty() -> "Double check, so the king has to move."
            direct -> null
            else -> others.firstOrNull()?.let { "Discovered check from the ${name(after, it)} on ${it.label}." }
        }
    }

    private fun newTargets(before: Board, after: Board, move: Move, type: PieceType, mover: Side, opponent: Side): List<Target> {
        val now = attacks(after, move.to, type, mover)
        val was = attacks(before, move.from, type, mover)
        val value = VALUE[type] ?: 0
        return Bitboard.bbToSquareList(now and after.getBitboard(opponent))
            .filter { (was and it.bitboard) == 0L || type == PieceType.PAWN }
            .mapNotNull { square ->
                val target = after.getPiece(square).pieceType ?: return@mapNotNull null
                if (target == PieceType.KING) return@mapNotNull Target(target, square, undefended = false)
                val undefended = after.squareAttackedBy(square, opponent) == 0L
                val worth = VALUE[target] ?: 0
                if (undefended || worth > value) Target(target, square, undefended) else null
            }
            .sortedByDescending { VALUE[it.type] ?: 100 }
    }

    private fun threatPhrase(target: Target): String = when {
        target.undefended -> "Attacks the undefended ${target.type.label} on ${target.square.label}."
        else -> "Threatens the ${target.type.label} on ${target.square.label}."
    }

    private fun both(a: Target, b: Target): String {
        fun say(t: Target) = if (t.type == PieceType.KING) "the king" else "the ${t.type.label} on ${t.square.label}"
        return "${say(a)} and ${say(b)}"
    }

    /** A bishop, rook or queen move that pins a piece to the king or queen behind it. */
    private fun pin(after: Board, at: Square, type: PieceType, opponent: Side): String? {
        if (type != PieceType.BISHOP && type != PieceType.ROOK && type != PieceType.QUEEN) return null
        val occupied = after.bitboard
        val direct = slider(type, occupied, at)
        for (square in Bitboard.bbToSquareList(direct and after.getBitboard(opponent))) {
            val pinned = after.getPiece(square).pieceType ?: continue
            // Pinned pawns are too common to be worth saying.
            if (pinned == PieceType.KING || pinned == PieceType.PAWN) continue
            val behind = slider(type, occupied and square.bitboard.inv(), at) and direct.inv()
            for (back in Bitboard.bbToSquareList(behind and after.getBitboard(opponent))) {
                if (!aligned(at, square, back)) continue
                val prize = after.getPiece(back).pieceType ?: continue
                if (prize == PieceType.KING) return "Pins the ${pinned.label} on ${square.label} to the king."
                if ((VALUE[prize] ?: 0) > (VALUE[pinned] ?: 0)) return "Pins the ${pinned.label} on ${square.label} to the ${prize.label}."
            }
        }
        return null
    }

    /** Moving off a line lets a bishop, rook or queen behind it attack something new. */
    private fun uncovered(before: Board, after: Board, move: Move, mover: Side, opponent: Side): String? {
        for (square in Bitboard.bbToSquareList(after.getBitboard(mover))) {
            if (square == move.to) continue
            val type = after.getPiece(square).pieceType ?: continue
            if (type != PieceType.BISHOP && type != PieceType.ROOK && type != PieceType.QUEEN) continue
            val gained = slider(type, after.bitboard, square) and slider(type, before.bitboard, square).inv()
            for (target in Bitboard.bbToSquareList(gained and after.getBitboard(opponent))) {
                val victim = after.getPiece(target).pieceType ?: continue
                if (victim == PieceType.KING) continue
                val undefended = after.squareAttackedBy(target, opponent) == 0L
                if (undefended || (VALUE[victim] ?: 0) > (VALUE[type] ?: 0)) {
                    return "Opens the ${type.label} on ${square.label} onto the ${victim.label} on ${target.label}."
                }
            }
        }
        return null
    }

    /** More attackers on a defended enemy piece than before, counting the weak f7 and f2 squares. */
    private fun pileUp(before: Board, after: Board, move: Move, mover: Side, opponent: Side): String? {
        val mine = attacks(after, move.to, after.getPiece(move.to).pieceType ?: return null, mover)
        for (square in Bitboard.bbToSquareList(mine and after.getBitboard(opponent))) {
            val victim = after.getPiece(square).pieceType ?: continue
            if (victim == PieceType.KING) continue
            val count = java.lang.Long.bitCount(after.squareAttackedBy(square, mover))
            val was = java.lang.Long.bitCount(before.squareAttackedBy(square, mover))
            if (count < 2 || count <= was) continue
            val defenders = Bitboard.bbToSquareList(after.squareAttackedBy(square, opponent))
            val onlyKing = defenders.size == 1 && after.getPiece(defenders[0]).pieceType == PieceType.KING
            val nth = ORDINAL.getOrElse(count) { "another" }
            val guards = when (defenders.size) {
                1 -> "only one defender"
                else -> "only ${NUMBER.getOrElse(defenders.size) { defenders.size.toString() }} defenders"
            }
            return when {
                onlyKing -> "Adds a $nth attacker to ${square.label}, which only the king defends."
                count > defenders.size -> "Puts a $nth attacker on the ${victim.label} on ${square.label}, which has $guards."
                else -> "Adds a $nth attacker to the ${victim.label} on ${square.label}."
            }
        }
        return null
    }

    /** Covers one of your pieces that was under attack and short of defenders. */
    private fun defends(before: Board, after: Board, move: Move, mover: Side, opponent: Side): String? {
        val type = after.getPiece(move.to).pieceType ?: return null
        val mine = attacks(after, move.to, type, mover)
        for (square in Bitboard.bbToSquareList(mine and after.getBitboard(mover))) {
            val piece = after.getPiece(square).pieceType ?: continue
            if (piece == PieceType.KING) continue
            val attackers = java.lang.Long.bitCount(before.squareAttackedBy(square, opponent))
            val defenders = java.lang.Long.bitCount(before.squareAttackedBy(square, mover))
            if (attackers > 0 && attackers > defenders) return "Defends the ${piece.label} on ${square.label}."
        }
        return null
    }

    /** The moved piece was attacked and could have been lost. */
    private fun escapes(before: Board, move: Move, type: PieceType, mover: Side, opponent: Side): String? {
        // A capture is its own reason; it is not running away.
        if (type == PieceType.KING || type == PieceType.PAWN || before.isKingAttacked || before.getPiece(move.to) != Piece.NONE) return null
        val attackers = Bitboard.bbToSquareList(before.squareAttackedBy(move.from, opponent))
        if (attackers.isEmpty()) return null
        val defended = before.squareAttackedBy(move.from, mover) != 0L
        val cheaper = attackers.any { (VALUE[before.getPiece(it).pieceType] ?: 100) < (VALUE[type] ?: 0) }
        return if (!defended || cheaper) "Moves the ${type.label} out of attack." else null
    }

    private fun attacks(board: Board, square: Square, type: PieceType, side: Side): Long = when (type) {
        PieceType.PAWN -> Bitboard.getPawnAttacks(side, square)
        PieceType.KNIGHT -> Bitboard.getKnightAttacks(square, -1L)
        PieceType.KING -> Bitboard.getKingAttacks(square, -1L)
        else -> slider(type, board.bitboard, square)
    }

    private fun slider(type: PieceType, occupied: Long, square: Square): Long = when (type) {
        PieceType.BISHOP -> Bitboard.getBishopAttacks(occupied, square)
        PieceType.ROOK -> Bitboard.getRookAttacks(occupied, square)
        PieceType.QUEEN -> Bitboard.getQueenAttacks(occupied, square)
        else -> 0L
    }

    /** [b] lies between [a] and [c] on one straight line. */
    private fun aligned(a: Square, b: Square, c: Square): Boolean {
        val (af, ar) = a.file.ordinal to a.rank.ordinal
        val (bf, br) = b.file.ordinal to b.rank.ordinal
        val (cf, cr) = c.file.ordinal to c.rank.ordinal
        val d1 = Integer.signum(bf - af) to Integer.signum(br - ar)
        val d2 = Integer.signum(cf - bf) to Integer.signum(cr - br)
        return d1 == d2
    }

    private fun name(board: Board, square: Square) = board.getPiece(square).pieceType?.label ?: "piece"

    private val PieceType.label: String get() = name.lowercase()

    private val Square.label: String get() = name.lowercase()

    private val ORDINAL = mapOf(2 to "second", 3 to "third", 4 to "fourth")

    private val NUMBER = mapOf(2 to "two", 3 to "three", 4 to "four")

    private val VALUE = mapOf(
        PieceType.PAWN to 1,
        PieceType.KNIGHT to 3,
        PieceType.BISHOP to 3,
        PieceType.ROOK to 5,
        PieceType.QUEEN to 9,
    )
}
