package app.chessnut.otb.chess

/** Square index 0..63 with a1 = 0, h8 = 63. */
fun squareName(index: Int): String = "${'a' + index % 8}${index / 8 + 1}"

fun squareIndex(name: String): Int = (name[1].lowercaseChar() - '1') * 8 + (name[0].lowercaseChar() - 'a')

/** The from and to squares of a UCI move such as "e2e4", or null when it is too short to read. */
fun uciSquares(uci: String?): Pair<Int, Int>? {
    if (uci == null || uci.length < 4) return null
    return squareIndex(uci.substring(0, 2)) to squareIndex(uci.substring(2, 4))
}

/** Expands a FEN (or just its placement) into 64 squares, a1 first; '.' is empty. */
fun squaresOf(fen: String): CharArray {
    val squares = CharArray(64) { '.' }
    val ranks = fen.substringBefore(' ').split('/')
    for ((row, rank) in ranks.withIndex()) {
        var file = 0
        for (c in rank) {
            if (c.isDigit()) {
                file += c.digitToInt()
            } else {
                if (file < 8 && row < 8) squares[(7 - row) * 8 + file] = c
                file++
            }
        }
    }
    return squares
}
