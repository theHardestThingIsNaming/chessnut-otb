package app.chessnut.otb.play

/** Turns a SAN move into words a phone can say, such as "knight f 6". */
object SpokenMove {
    private val marks = Regex("""[!?]+""")
    private val move = Regex("""^([NBRQK])?([a-h])?([1-8])?(x)?([a-h][1-8])(?:=([NBRQ]))?$""")

    fun phrase(san: String, mate: Boolean = false): String {
        var text = san.trim().replace(marks, "")
        val hash = text.endsWith("#")
        val plus = text.endsWith("+")
        text = text.removeSuffix("#").removeSuffix("+").trim()
        val enPassant = text.endsWith("e.p.")
        text = text.removeSuffix("e.p.").trim()
        val body = when (text) {
            "O-O", "0-0" -> "castle kingside"
            "O-O-O", "0-0-0" -> "castle queenside"
            else -> words(text) ?: text
        }
        val spoken = if (enPassant) "$body, en passant" else body
        return when {
            mate || hash -> "$spoken, checkmate"
            plus -> "$spoken, check"
            else -> spoken
        }
    }

    /** A move in plain words for reading and saying, such as "pawn to e6" or "bishop takes f7". */
    fun plain(san: String): String {
        val text = san.trim().replace(marks, "").removeSuffix("#").removeSuffix("+")
        if (text == "O-O" || text == "0-0") return "castling kingside"
        if (text == "O-O-O" || text == "0-0-0") return "castling queenside"
        val match = move.matchEntire(text) ?: return san
        val piece = match.groupValues[1].ifEmpty { "P" }
        val capture = match.groupValues[4].isNotEmpty()
        val promo = match.groupValues[6]
        val base = "${pieceName(piece)} ${if (capture) "takes" else "to"} ${match.groupValues[5]}"
        return if (promo.isEmpty()) base else "$base, promoting to ${pieceName(promo)}"
    }

    private fun words(san: String): String? {
        val match = move.matchEntire(san) ?: return null
        val piece = match.groupValues[1]
        val from = match.groupValues[2] + match.groupValues[3]
        val capture = match.groupValues[4].isNotEmpty()
        val dest = match.groupValues[5]
        val promo = match.groupValues[6]
        val parts = mutableListOf<String>()
        if (piece.isNotEmpty()) parts += pieceName(piece)
        if (from.isNotEmpty()) parts += from
        if (from.isNotEmpty()) parts += if (capture) "takes" else "to"
        else if (capture) parts += "takes"
        parts += square(dest)
        if (promo.isNotEmpty()) parts += listOf("promotes", "to", pieceName(promo))
        return parts.joinToString(" ")
    }

    private fun square(square: String) = "${square[0]} ${square[1]}"

    private fun pieceName(piece: String) = when (piece) {
        "N" -> "knight"
        "B" -> "bishop"
        "R" -> "rook"
        "Q" -> "queen"
        "K" -> "king"
        "P" -> "pawn"
        else -> piece
    }
}
