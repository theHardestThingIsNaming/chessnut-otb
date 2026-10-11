package app.chessnut.otb.openings

import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board

/** How often club players fall for a trap: the mistake's games out of all games in the position. */
data class TrapRecord(val mistakeGames: Long, val totalGames: Long) {
    val percent: Int get() = if (totalGames == 0L) 0 else Math.round(mistakeGames * 100.0 / totalGames).toInt()

    /** Too few games to say. */
    val thin: Boolean get() = totalGames < MIN_GAMES

    val frequency: TrapFrequency get() = when {
        percent >= 10 -> TrapFrequency.Common
        percent >= 3 -> TrapFrequency.Occasional
        else -> TrapFrequency.Rare
    }

    companion object {
        const val MIN_GAMES = 30L
    }
}

enum class TrapFrequency { Common, Occasional, Rare }

/**
 * Trap frequencies from the Lichess opening explorer, at the rating band of a
 * typical club: blitz, rapid and classical games between [BAND].
 */
object TrapStats {
    const val BAND = "1400–2000"

    /** Lichess rating buckets 1400, 1600 and 1800 cover 1400 to 1999. */
    const val QUERY = "variant=standard&speeds=blitz,rapid,classical&ratings=1400,1600,1800&moves=30&topGames=0&recentGames=0"

    /** The trap's starting position as UCI moves for the explorer's `play` parameter. */
    fun play(trap: Trap): String? {
        var fen = Board().fen
        val ucis = mutableListOf<String>()
        for (san in trap.before) {
            val uci = PlayPosition.uciOfSan(fen, san) ?: return null
            ucis += uci
            fen = PlayPosition.after(fen, uci)?.second ?: return null
        }
        return ucis.joinToString(",")
    }

    /** How often the trap's mistake was played, from the explorer's answer for its position. */
    fun parse(json: String, trap: Trap): TrapRecord? {
        val root = runCatching { JsonLite.parse(json) as? Json.Obj }.getOrNull() ?: return null
        fun games(node: Json?) = listOf("white", "draws", "black").sumOf { node?.long(it) ?: 0L }
        val total = games(root)
        val moves = root.fields["moves"] as? Json.Arr ?: return TrapRecord(0, total)
        val mistake = trap.moves.first()
        val played = moves.items.firstOrNull { it.str("san") == mistake }
        return TrapRecord(games(played), total)
    }
}
