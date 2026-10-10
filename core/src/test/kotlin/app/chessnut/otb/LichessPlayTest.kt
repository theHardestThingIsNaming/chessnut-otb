package app.chessnut.otb

import app.chessnut.otb.lichess.LichessEvent
import app.chessnut.otb.lichess.LichessOAuth
import app.chessnut.otb.lichess.LichessPlay
import app.chessnut.otb.lichess.OnlineControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LichessPlayTest {
    @Test
    fun pkceMatchesTheSpecVector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", LichessOAuth.challenge(verifier))
        val url = LichessOAuth.authorizeUrl("abc", LichessOAuth.challenge(verifier))
        assertTrue(url.startsWith("https://lichess.org/oauth?"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("scope=board%3Aplay"))
        assertTrue(url.contains("redirect_uri=app.chessnut.otb%3A%2F%2Foauth"))
    }

    @Test
    fun aLoginReplyKeepsTheTokenAndTheName() {
        assertEquals("lip_abc", LichessOAuth.accessToken("""{"token_type":"Bearer","access_token":"lip_abc"}"""))
        val account = LichessOAuth.account("""{"id":"me","username":"Me"}""")
        assertEquals("me", account.id)
        assertEquals("Me", account.username)
        val back = LichessOAuth.redirect("app.chessnut.otb://oauth?code=xyz&state=abc")
        assertEquals("xyz", back.code)
        assertEquals("abc", back.state)
    }

    @Test
    fun longerGamesAreTheOnesTheBoardApiAllows() {
        assertTrue(OnlineControl.Presets.all { it.minutes >= 10 })
        assertEquals(
            "rated=false&time=10&increment=5&variant=standard",
            LichessPlay.seekForm(OnlineControl.Default, rated = false),
        )
    }

    @Test
    fun aGameStreamRebuildsThePositionAndTheClocks() {
        val full = LichessPlay.parse(
            """
            {"type":"gameFull","id":"abcdefgh","white":{"id":"me","name":"Me"},"black":{"id":"opp","name":"Opp"},
            "initialFen":"startpos","state":{"type":"gameState","moves":"e2e4 e7e5","wtime":590000,"btime":600000,
            "winc":5000,"binc":5000,"status":"started"}}
            """.trimIndent().replace("\n", ""),
            accountId = "me",
        )
        val table = assertIs<LichessEvent.Full>(full).table
        assertTrue(table.userWhite)
        assertEquals(listOf("e2e4", "e7e5"), table.moves)
        assertEquals("e4", LichessPlay.position("startpos", table.moves).second.first())
        assertEquals(590_000L, table.whiteMillis)
        assertEquals(5_000L, table.incrementMillis)
        assertTrue(table.fen.contains("4P3"))

        val started = LichessPlay.parse(
            """{"type":"gameStart","game":{"gameId":"abcdefgh","color":"black"}}""",
            accountId = "me",
        )
        assertEquals("black", assertIs<LichessEvent.GameStarted>(started).color)
        assertNull(LichessPlay.parse("", "me"))
        assertEquals("1-0", LichessPlay.result("mate", "white"))
        assertEquals("1/2-1/2", LichessPlay.result("draw", null))
    }

    @Test
    fun ongoingGamesSkipCorrespondence() {
        val json = """{"nbMyTurn":1,"nowPlaying":[
            {"gameId":"abcd1234","fullId":"abcd1234wxyz","color":"black","fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR","hasMoved":false,"isMyTurn":true,"lastMove":"e2e4","opponent":{"id":"bob","username":"Bob","rating":1500},"perf":"rapid","rated":false,"rating":1600,"secondsLeft":600,"source":"lobby","speed":"rapid","variant":{"key":"standard","name":"Standard"}},
            {"gameId":"corr0001","fullId":"corr0001wxyz","color":"white","fen":"8/8/8/8/8/8/8/8","hasMoved":true,"isMyTurn":false,"lastMove":"","opponent":{"id":"amy","username":"Amy"},"perf":"correspondence","rated":true,"rating":1600,"secondsLeft":86400,"source":"friend","speed":"correspondence","variant":{"key":"standard","name":"Standard"}}
        ]}"""
        val games = app.chessnut.otb.lichess.LichessPlay.ongoing(json)
        kotlin.test.assertEquals(1, games.size)
        val game = games.single()
        kotlin.test.assertEquals("abcd1234", game.gameId)
        kotlin.test.assertEquals("Bob", game.opponent)
        kotlin.test.assertEquals(false, game.userWhite)
        kotlin.test.assertEquals(true, game.isMyTurn)
        kotlin.test.assertEquals(600L, game.secondsLeft)
        kotlin.test.assertTrue(app.chessnut.otb.lichess.LichessPlay.canAbort(1))
        kotlin.test.assertFalse(app.chessnut.otb.lichess.LichessPlay.canAbort(2))
    }
}
