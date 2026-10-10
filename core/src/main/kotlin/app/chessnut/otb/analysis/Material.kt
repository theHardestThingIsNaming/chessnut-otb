package app.chessnut.otb.analysis

/**
 * Piece-count difference for the position in [fen]. [points] is White's lead
 * (pawn 1, knight 3, bishop 3, rook 5, queen 9). [whiteExtra] and [blackExtra]
 * are the surplus pieces, most valuable first, as FEN letters.
 */
data class MaterialBalance(val points: Int, val whiteExtra: String, val blackExtra: String) {
    val even: Boolean get() = whiteExtra.isEmpty() && blackExtra.isEmpty()
}

object Material {
    private val order = listOf('Q', 'R', 'B', 'N', 'P')
    private val value = mapOf('Q' to 9, 'R' to 5, 'B' to 3, 'N' to 3, 'P' to 1)

    fun balance(fen: String): MaterialBalance {
        val counts = mutableMapOf<Char, Int>()
        for (symbol in fen.substringBefore(' ')) {
            if (symbol.isLetter()) counts[symbol] = (counts[symbol] ?: 0) + 1
        }
        val white = StringBuilder()
        val black = StringBuilder()
        var points = 0
        for (type in order) {
            val diff = (counts[type] ?: 0) - (counts[type.lowercaseChar()] ?: 0)
            points += diff * value.getValue(type)
            val extra = if (diff > 0) white else black
            repeat(kotlin.math.abs(diff)) { extra.append(if (diff > 0) type else type.lowercaseChar()) }
        }
        return MaterialBalance(points, white.toString(), black.toString())
    }
}
