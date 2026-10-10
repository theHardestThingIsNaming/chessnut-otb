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

    private val start = mapOf('P' to 8, 'N' to 2, 'B' to 2, 'R' to 2, 'Q' to 1)

    /**
     * The pieces each side has taken off the board in [fen], cheapest first:
     * the first string is what White captured (black letters), the second what
     * Black captured (white letters). Promotions can leave a count above the
     * starting one; those simply count as nothing captured.
     */
    fun captured(fen: String): Pair<String, String> {
        val counts = mutableMapOf<Char, Int>()
        for (symbol in fen.substringBefore(' ')) {
            if (symbol.isLetter()) counts[symbol] = (counts[symbol] ?: 0) + 1
        }
        val byWhite = StringBuilder()
        val byBlack = StringBuilder()
        for (type in order.reversed()) {
            val full = start.getValue(type)
            repeat((full - (counts[type.lowercaseChar()] ?: 0)).coerceAtLeast(0)) { byWhite.append(type.lowercaseChar()) }
            repeat((full - (counts[type] ?: 0)).coerceAtLeast(0)) { byBlack.append(type) }
        }
        return byWhite.toString() to byBlack.toString()
    }
}
