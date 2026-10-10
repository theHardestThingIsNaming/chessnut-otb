package app.chessnut.otb.protocol

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList
import java.util.UUID

/**
 * A simulated Chessnut Air built on [FakeGattLink], answering the stored-game
 * opcodes the way the official SDK describes. Used by tests and by the app's
 * "simulated board" option, so the whole flow runs with no hardware.
 *
 * [advancesWithoutDelete] models the open question: whether the Air moves to
 * the next game when the host never sends `39 01 00`.
 */
class FakeChessnutBoard(
    games: List<List<String>> = listOf(placementsFromSan(SAMPLE_ONE), placementsFromSan(SAMPLE_TWO)),
    var advancesWithoutDelete: Boolean = true,
    private val gameFramesOn: UUID = ChessnutUuids.FILE_NOTIFY,
) : FakeGattLink() {
    val stored: MutableList<List<String>> = games.toMutableList()
    var livePlacement: String = START_PLACEMENT
    private var cursor = 0
    /** The game sent last, which `39 01 00` deletes, whether or not the cursor has moved past it. */
    private var current = 0

    init {
        onWrite = { bytes -> answer(bytes) }
    }

    private suspend fun answer(bytes: ByteArray) {
        if (bytes.size < 3) return
        when (bytes[0].toInt() and 0xFF) {
            0x29 -> emit(ChessnutUuids.OP_RESPONSE, byteArrayOf(0x2A, 0x02, 0x4B, 0x00))
            0x21 -> if (bytes[2] == 0x00.toByte()) {
                emit(ChessnutUuids.FEN_NOTIFY, PositionCodec.encodePlacement(livePlacement))
            }
            0x31 -> {
                cursor = 0
                emit(ChessnutUuids.OP_RESPONSE, byteArrayOf(0x32, 0x01, stored.size.toByte()))
            }
            0x34 -> {
                val game = stored.getOrNull(cursor) ?: return
                current = cursor
                emit(gameFramesOn, byteArrayOf(0x37, 0x01, 0xBE.toByte()))
                game.forEach { emit(gameFramesOn, PositionCodec.encodePlacement(it)) }
                emit(gameFramesOn, byteArrayOf(0x37, 0x01, 0xED.toByte()))
                if (advancesWithoutDelete) cursor++
            }
            0x39 -> if (current < stored.size) {
                stored.removeAt(current)
                cursor = current
            }
        }
    }

    companion object {
        const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        const val SAMPLE_ONE = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 Bb4+ " +
            "7. Bd2 Bxd2+ 8. Nbxd2 d5 9. exd5 Nxd5 10. Qb3 Nce7 11. O-O O-O 12. Rfe1 c6"
        const val SAMPLE_TWO = "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 O-O 6. Nf3 h6 7. Bh4 b6 " +
            "8. cxd5 Nxd5 9. Bxe7 Qxe7 10. Nxd5 exd5 11. Rc1 Be6 12. Qa4 c5 13. Qa3 Rc8"

        fun placementsFromSan(san: String): List<String> {
            val moves = MoveList().apply { loadFromSan(san) }
            val board = Board()
            val placements = mutableListOf(board.fen.substringBefore(' '))
            for (move in moves) {
                board.doMove(move)
                placements += board.fen.substringBefore(' ')
            }
            return placements
        }
    }
}
