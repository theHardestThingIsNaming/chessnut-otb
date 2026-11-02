package app.chessnut.otb.play

/** The Learn step's board: the first moves of a line played out from the start, however far in you stepped. */
object LineWalk {
    /** [before] is the position the last move was played from (the start when there is none). */
    class Step(val fen: String, val san: List<String>, val uci: List<String>, val before: String)

    /** Stops early at a move that doesn't parse or isn't legal, so a damaged line shows as far as it goes. */
    fun to(start: String, line: List<String>, count: Int): Step {
        var fen = start
        var before = start
        val san = mutableListOf<String>()
        val uci = mutableListOf<String>()
        for (move in line.take(count.coerceIn(0, line.size))) {
            val played = PlayPosition.uciOfSan(fen, move) ?: break
            val next = PlayPosition.after(fen, played)?.second ?: break
            before = fen
            fen = next
            san += move
            uci += played
        }
        return Step(fen, san, uci, before)
    }

    /** True when [move] is the next move of one of [lines], given the moves already [played]: on a line, whatever the book says. */
    fun continues(lines: List<List<String>>, played: List<String>, move: String): Boolean =
        lines.any { it.size > played.size && it.take(played.size) == played && it[played.size] == move }

    /** True while [played] is still the start of one of [lines] (or all of one). */
    fun follows(lines: List<List<String>>, played: List<String>): Boolean =
        lines.any { it.size >= played.size && it.take(played.size) == played }
}
