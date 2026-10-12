package app.chessnut.otb

import app.chessnut.otb.chesscom.ChessComGames
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChessComGamesTest {
    private val now = LocalDate.of(2026, 10, 12).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun picksTheMonthsCoveringTheLastThirtyDays() {
        val archives = ChessComGames.archives(
            """{"archives":["https://api.chess.com/pub/player/me/games/2026/08","https://api.chess.com/pub/player/me/games/2026/09","https://api.chess.com/pub/player/me/games/2026/10"]}""",
        )
        assertEquals(3, archives.size)
        assertEquals(listOf("2026/09", "2026/10"), ChessComGames.recentArchives(archives, now).map { it.takeLast(7) })
    }

    @Test
    fun keepsOnlyThisOpeningFromYourSide() {
        val scotch = "[White \"Me\"]\n[Black \"Them\"]\n[Event \"Live Chess\"]\n\n1. e4 {[%clk 0:09:59]} e5 2. Nf3 Nc6 3. d4 exd4 4. Nxd4 Nf6 1-0"
        val json = """{"games":[
            {"url":"https://www.chess.com/game/live/1","pgn":${quote(scotch)},"end_time":${now / 1000 - 3600},"rules":"chess","white":{"username":"Me"},"black":{"username":"Them"}},
            {"url":"https://www.chess.com/game/live/2","pgn":${quote(scotch)},"end_time":${now / 1000 - 40L * 86400},"rules":"chess","white":{"username":"Me"},"black":{"username":"Them"}},
            {"url":"https://www.chess.com/game/live/3","pgn":"x","end_time":${now / 1000},"rules":"chess960","white":{"username":"Me"},"black":{"username":"Them"}}]}"""
        val games = ChessComGames.games(json, since = now - 30L * 86_400_000)
        assertEquals(listOf("https://www.chess.com/game/live/1"), games.map { it.url })
        val saved = assertNotNull(ChessComGames.saved(games.single(), "Scotch Game", userWhite = true, username = "me"))
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "d4", "exd4", "Nxd4", "Nf6"), saved.sanMoves)
        assertNull(ChessComGames.saved(games.single(), "Scotch Game", userWhite = false, username = "me"), "you had White, not Black")
        assertNull(ChessComGames.saved(games.single(), "Pirc Defense", userWhite = true, username = "me"), "not a Pirc")
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
