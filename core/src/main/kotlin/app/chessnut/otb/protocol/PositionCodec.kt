package app.chessnut.otb.protocol

/**
 * Chessnut board-position frames. Bytes 2..33 pack 64 squares, two per byte,
 * using the same walk as EasyLinkSDK `ChessLink::toFen`.
 *
 * Nibbles: 0 empty, 1 q, 2 k, 3 b, 4 p, 5 n, 6 R, 7 P, 8 r, 9 B, 10 N, 11 Q, 12 K.
 * A value above 12 makes the frame invalid.
 */
object PositionCodec {
    private val pieces = charArrayOf('0', 'q', 'k', 'b', 'p', 'n', 'R', 'P', 'r', 'B', 'N', 'Q', 'K')

    fun decodePlacement(packet: ByteArray): String? {
        if (packet.size < 34 || packet[0] != 0x01.toByte()) return null
        val fen = StringBuilder()
        var empty = 0
        for (rank in 0 until 8) {
            for (fileIndex in 7 downTo 0) {
                val raw = packet[(rank * 8 + fileIndex) / 2 + 2].toInt() and 0xFF
                val nibble = if (fileIndex % 2 == 0) raw and 0x0F else raw shr 4
                if (nibble > 12) return null
                val piece = pieces[nibble]
                if (piece == '0') {
                    empty++
                } else {
                    if (empty > 0) {
                        fen.append(empty)
                        empty = 0
                    }
                    fen.append(piece)
                }
            }
            if (empty > 0) {
                fen.append(empty)
                empty = 0
            }
            if (rank < 7) fen.append('/')
        }
        return fen.toString()
    }

    fun encodePlacement(placement: String): ByteArray {
        val squares = expand(placement)
        val packet = ByteArray(36)
        packet[0] = 0x01
        packet[1] = 0x22
        var cursor = 0
        for (rank in 0 until 8) {
            for (fileIndex in 7 downTo 0) {
                val code = pieces.indexOf(squares[cursor])
                require(code >= 0) { "Unknown piece '${squares[cursor]}'" }
                val index = (rank * 8 + fileIndex) / 2 + 2
                val current = packet[index].toInt() and 0xFF
                val mask = if (fileIndex % 2 == 0) code else code shl 4
                packet[index] = (current or mask).toByte()
                cursor++
            }
        }
        return packet
    }

    private fun expand(placement: String): CharArray {
        val squares = CharArray(64)
        var cursor = 0
        for (symbol in placement) {
            when {
                symbol == '/' -> Unit
                symbol.isDigit() -> repeat(symbol.digitToInt()) {
                    check(cursor < 64) { "Placement is longer than 64 squares" }
                    squares[cursor++] = '0'
                }
                else -> {
                    check(cursor < 64) { "Placement is longer than 64 squares" }
                    squares[cursor++] = symbol
                }
            }
        }
        check(cursor == 64) { "Placement does not cover 64 squares" }
        return squares
    }
}
