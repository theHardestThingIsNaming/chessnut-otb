package app.chessnut.otb.openings

import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board

/** Results of Lichess games that reached an opening's position. */
data class OpeningRecord(val white: Long, val draws: Long, val black: Long) {
    val total: Long get() = white + draws + black

    /** Percentage of games won by [side], or null with too few games to mean anything. */
    fun winPercent(side: OpeningSide): Int? {
        if (total < MIN_GAMES) return null
        val wins = if (side == OpeningSide.White) white else black
        return Math.round(wins * 100.0 / total).toInt()
    }

    companion object {
        const val MIN_GAMES = 50L
    }
}

/**
 * Win rates for the trainer's openings, from the Lichess opening explorer:
 * how often the side that chooses the opening goes on to win.
 */
object OpeningStats {
    /** Club-level rapid and classical games, closest to over-the-board play. */
    const val QUERY = "variant=standard&speeds=rapid,classical&ratings=1600,1800,2000,2200&moves=0&topGames=0&recentGames=0"

    /** The opening's moves in UCI, comma separated, for the explorer's `play` parameter. */
    fun play(name: String): String? {
        val san = OpeningBook.line(name)?.san ?: return null
        var fen = Board().fen
        val ucis = mutableListOf<String>()
        for (move in san) {
            val uci = PlayPosition.uciOfSan(fen, move) ?: return null
            ucis += uci
            fen = PlayPosition.after(fen, uci)?.second ?: return null
        }
        return ucis.joinToString(",")
    }

    fun parse(json: String): OpeningRecord? {
        val root = runCatching { JsonLite.parse(json) as? Json.Obj }.getOrNull() ?: return null
        val white = root.long("white") ?: return null
        val draws = root.long("draws") ?: return null
        val black = root.long("black") ?: return null
        return OpeningRecord(white, draws, black)
    }

    /** The win rate of the side the opening belongs to. */
    fun winPercent(name: String, record: OpeningRecord?): Int? {
        val side = OpeningBook.side(name) ?: return null
        return record?.winPercent(side)
    }

    /** Best win rate first. Openings without a rate keep their order, after the rest. */
    fun sorted(names: List<String>, rates: Map<String, Int?>): List<String> =
        names.withIndex()
            .sortedWith(compareBy<IndexedValue<String>> { rates[it.value] == null }.thenByDescending { rates[it.value] ?: 0 }.thenBy { it.index })
            .map { it.value }
}
