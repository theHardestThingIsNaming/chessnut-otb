package app.chessnut.otb.play

/**
 * A game that began from a set-up position, as one id: the start, then the moves played from it
 * ("fen:<start>|e2e4 e7e5"). The review screen steps through it, as it does a saved game.
 */
object PositionGame {
    const val PREFIX = "fen:"

    class Replay(val start: String, val fens: List<String>, val uci: List<String>, val san: List<String>)

    fun id(start: String, uci: List<String>): String = PREFIX + start + if (uci.isEmpty()) "" else "|" + uci.joinToString(" ")

    /** Plays the moves out from the start; stops at the first one that isn't legal. Null if [id] isn't a position id. */
    fun parse(id: String): Replay? {
        if (!id.startsWith(PREFIX)) return null
        val body = id.removePrefix(PREFIX)
        val start = body.substringBefore('|')
        val moves = body.substringAfter('|', "").split(' ').filter { it.isNotEmpty() }
        val fens = mutableListOf(start)
        val played = mutableListOf<String>()
        val san = mutableListOf<String>()
        for (uci in moves) {
            val step = PlayPosition.after(fens.last(), uci) ?: break
            san += step.first
            fens += step.second
            played += uci
        }
        return Replay(start, fens, played, san)
    }
}
