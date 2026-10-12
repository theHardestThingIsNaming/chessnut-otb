package app.chessnut.otb.chesscom

import app.chessnut.otb.games.PgnImport
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.pgn.GameFingerprint
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** One finished game from a Chess.com monthly archive. */
data class ChessComGame(
    val url: String,
    val pgn: String,
    /** When it ended, in milliseconds. */
    val endedAt: Long,
    val white: String,
    val black: String,
)

/**
 * Chess.com's published data (api.chess.com/pub, no login): a player's
 * monthly game archives, read and filtered down to one opening.
 */
object ChessComGames {
    val USERNAME = Regex("^[A-Za-z0-9_-]{3,25}$")

    fun archivesUrl(username: String): String {
        require(USERNAME.matches(username)) { "That doesn't look like a Chess.com username." }
        return "https://api.chess.com/pub/player/${username.lowercase()}/games/archives"
    }

    /** The monthly archive URLs from `/games/archives`. */
    fun archives(json: String): List<String> {
        val root = JsonLite.parse(json) as? Json.Obj ?: return emptyList()
        return (root.fields["archives"] as? Json.Arr)?.items.orEmpty().mapNotNull { (it as? Json.Str)?.text }
    }

    /** The archives covering the last [days] days: this month and, early in a month, the one before. */
    fun recentArchives(archives: List<String>, now: Long, days: Int = 30): List<String> {
        val from = YearMonth.from(Instant.ofEpochMilli(now - days * DAY_MS).atZone(ZoneOffset.UTC))
        val to = YearMonth.from(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC))
        return archives.filter { url ->
            val parts = url.trimEnd('/').split('/')
            val month = runCatching { YearMonth.of(parts[parts.size - 2].toInt(), parts.last().toInt()) }.getOrNull()
            month != null && !month.isBefore(from) && !month.isAfter(to)
        }
    }

    /** Standard chess games in a monthly archive that ended at or after [since]. */
    fun games(json: String, since: Long): List<ChessComGame> {
        val root = JsonLite.parse(json) as? Json.Obj ?: return emptyList()
        return (root.fields["games"] as? Json.Arr)?.items.orEmpty().mapNotNull { item ->
            val game = item as? Json.Obj ?: return@mapNotNull null
            if ((game.str("rules") ?: "chess") != "chess") return@mapNotNull null
            val endedAt = (game.long("end_time") ?: return@mapNotNull null) * 1000
            if (endedAt < since) return@mapNotNull null
            ChessComGame(
                url = game.str("url") ?: return@mapNotNull null,
                pgn = game.str("pgn") ?: return@mapNotNull null,
                endedAt = endedAt,
                white = game.obj("white")?.str("username").orEmpty(),
                black = game.obj("black")?.str("username").orEmpty(),
            )
        }
    }

    /**
     * The game as a saved game, if it's [opening] and you ([username]) had the
     * opening's side ([userWhite]). Keyed by its Chess.com page, so a second
     * import skips it.
     */
    fun saved(game: ChessComGame, opening: String, userWhite: Boolean, username: String): SavedGame? {
        val mine = if (userWhite) game.white else game.black
        if (!mine.equals(username, ignoreCase = true)) return null
        val parsed = runCatching { PgnImport.parse(game.pgn, game.endedAt) }.getOrNull() ?: return null
        val name = OpeningBook.view(parsed.sanMoves.take(OPENING_PLIES)).name ?: return null
        if (name != opening && !name.startsWith("$opening:")) return null
        return parsed.copy(id = GameFingerprint.of("chesscom ${game.url}"), event = parsed.event.ifBlank { "Chess.com" })
    }

    private const val OPENING_PLIES = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000
}
