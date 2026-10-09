package app.chessnut.otb.protocol

/**
 * Chessnut Air LED and buzzer commands.
 *
 * BLE firmware expects a 34-byte write on the operation characteristic
 * (Chessnut_eBoards). Bytes after the length-prefixed payload are padding.
 *
 * LEDs: `[0x0A, 0x08, rank8..rank1, …]`. Within a rank byte, bit 7 is file a
 * and bit 0 is h — the same packing as EasyLink `cl_led` / `bitset<8>(row)`.
 *
 * Buzzer: `[0x0B, 0x04, freqHi, freqLo, durHi, durLo, …]` as EasyLink `cl_beep`.
 */
object LedCodec {
    const val FRAME_SIZE = 34

    val ALL: List<String> = buildList {
        for (rank in 1..8) for (file in 'a'..'h') add("$file$rank")
    }

    val OFF: ByteArray = encode(emptyList())

    fun encode(squares: Collection<String>): ByteArray {
        val packet = ByteArray(FRAME_SIZE)
        packet[0] = 0x0A
        packet[1] = 0x08
        for (name in squares) {
            if (name.length < 2) continue
            val file = name[0].lowercaseChar() - 'a'
            val rank = name[1].digitToIntOrNull() ?: continue
            if (file !in 0..7 || rank !in 1..8) continue
            val row = 8 - rank
            packet[2 + row] = (packet[2 + row].toInt() or (1 shl (7 - file))).toByte()
        }
        return packet
    }

    /** The winner's four ranks: 1–4 for White, 5–8 for Black. */
    fun winnerHalf(towardWhite: Boolean): List<String> {
        val ranks = if (towardWhite) 1..4 else 5..8
        return buildList {
            for (rank in ranks) for (file in 'a'..'h') add("$file$rank")
        }
    }

    fun beep(frequencyHz: Int, durationMs: Int): ByteArray {
        val freq = frequencyHz.coerceIn(0, 0xFFFF)
        val duration = durationMs.coerceIn(0, 0xFFFF)
        val packet = ByteArray(FRAME_SIZE)
        packet[0] = 0x0B
        packet[1] = 0x04
        packet[2] = (freq shr 8).toByte()
        packet[3] = freq.toByte()
        packet[4] = (duration shr 8).toByte()
        packet[5] = duration.toByte()
        return packet
    }
}
