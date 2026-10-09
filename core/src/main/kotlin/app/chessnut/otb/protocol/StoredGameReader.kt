package app.chessnut.otb.protocol

data class StoredBoardGame(val placements: List<String>)

data class StoredReadResult(
    val boardCount: Int,
    val games: List<StoredBoardGame>,
    /** The board sent the same game twice, so it did not advance without a delete. */
    val repeatedGame: Boolean,
)

class ProtocolException(message: String) : Exception(message)

/**
 * Reads onboard games using the EasyLink opcodes. Write pacing belongs to the
 * transport.
 *
 * `39 01 00` deletes the current game from the board (official SDK). It is only
 * sent when [readGames] is asked to clear, and only after `onGame` has returned
 * for that game, so a failed save leaves the board copy in place.
 */
class StoredGameReader(private val transport: BoardTransport) {

    suspend fun count(): Int {
        transport.write(QUERY_COUNT)
        repeat(MAX_SKIPPED) {
            val reply = transport.read()
            if (reply.size >= 3 && reply[0] == 0x32.toByte() && reply[1] == 0x01.toByte()) {
                return reply[2].toInt() and 0xFF
            }
        }
        throw ProtocolException("No 32 01 <count> reply to 31 01 00.")
    }

    suspend fun readGames(
        limit: Int = Int.MAX_VALUE,
        clearAfterSave: Boolean = false,
        onGame: suspend (StoredBoardGame) -> Unit = {},
    ): StoredReadResult {
        val count = count()
        val games = mutableListOf<StoredBoardGame>()
        var repeated = false
        if (count == 0) return StoredReadResult(0, games, repeatedGame = false)
        try {
            transport.write(UPLOAD_MODE)
            for (index in 0 until minOf(count, limit)) {
                transport.write(READY)
                transport.write(START)
                val game = readOneGame()
                if (!clearAfterSave && games.isNotEmpty() && games.last() == game) {
                    repeated = true
                    break
                }
                onGame(game)
                games += game
                if (clearAfterSave) transport.write(FILE_DONE)
            }
        } finally {
            runCatching { transport.write(REALTIME) }
        }
        return StoredReadResult(count, games, repeated)
    }

    private suspend fun readOneGame(): StoredBoardGame {
        val placements = mutableListOf<String>()
        var inside = false
        while (true) {
            val frame = transport.read()
            when {
                isFlag(frame, BEGIN) -> {
                    inside = true
                    placements.clear()
                }
                isFlag(frame, END) -> if (inside) return StoredBoardGame(placements)
                inside -> PositionCodec.decodePlacement(frame)?.let { placements += it }
            }
        }
    }

    private fun isFlag(frame: ByteArray, marker: Byte): Boolean =
        frame.size >= 3 && frame[0] == 0x37.toByte() && frame[1] == 0x01.toByte() && frame[2] == marker

    companion object {
        private const val MAX_SKIPPED = 32
        val REALTIME = byteArrayOf(0x21, 0x01, 0x00)
        val UPLOAD_MODE = byteArrayOf(0x21, 0x01, 0x01)
        val QUERY_COUNT = byteArrayOf(0x31, 0x01, 0x00)
        val READY = byteArrayOf(0x33, 0x01, 0x00)
        val START = byteArrayOf(0x34, 0x01, 0x01)
        val FILE_DONE = byteArrayOf(0x39, 0x01, 0x00)
        private val BEGIN: Byte = 0xBE.toByte()
        private val END: Byte = 0xED.toByte()
    }
}
