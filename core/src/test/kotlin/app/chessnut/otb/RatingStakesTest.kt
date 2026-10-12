package app.chessnut.otb

import app.chessnut.otb.lichess.LichessEvent
import app.chessnut.otb.lichess.LichessPlay
import app.chessnut.otb.lichess.RatingStakes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatingStakesTest {
    @Test
    fun evenPlayersRiskAboutAsMuchAsTheyStandToGain() {
        val stakes = RatingStakes.of(1600, 50, 1600, 50)
        assertEquals(stakes.win, -stakes.loss)
        assertEquals(0, stakes.draw)
        assertTrue(stakes.win in 5..8, "win ${stakes.win}")
        assertEquals("Win +${stakes.win} · Draw ±0 · Loss −${stakes.win}", stakes.label)
    }

    @Test
    fun aStrongerOpponentRisksLessAndAnUnsettledRatingMovesMore() {
        val up = RatingStakes.of(1500, 50, 1800, 50)
        assertTrue(up.win > -up.loss, "beating a stronger player gains more than losing to them costs")
        assertTrue(up.draw > 0)
        val newcomer = RatingStakes.of(1500, 150, 1500, 50)
        assertTrue(newcomer.win > RatingStakes.of(1500, 50, 1500, 50).win)
    }

    @Test
    fun readsRatingsFromTheGameAndTheDeviationFromTheProfile() {
        val full = """{"type":"gameFull","id":"abc12345","rated":true,"speed":"rapid",
            "white":{"id":"me","name":"Me","rating":1612,"provisional":false},
            "black":{"id":"them","name":"Them","rating":1688,"provisional":true},
            "initialFen":"startpos","state":{"type":"gameState","moves":"","wtime":600000,"btime":600000,"winc":5000,"binc":5000,"status":"started"}}"""
        val table = (LichessPlay.parse(full, "me") as LichessEvent.Full).table
        assertEquals(1612, table.userRating)
        assertEquals(1688, table.opponentRating)
        assertTrue(table.opponentProvisional)
        assertTrue(table.rated)
        assertEquals("rapid", table.speed)
        assertEquals(47, RatingStakes.deviation("""{"id":"me","perfs":{"rapid":{"games":120,"rating":1612,"rd":47,"prog":3}}}""", "rapid"))
    }
}

class JsonNumbersTest {
    @Test
    fun exponentsParse() {
        val parsed = app.chessnut.otb.lichess.JsonLite.parse("""{"a":1.5e3,"b":2}""") as app.chessnut.otb.lichess.Json.Obj
        assertEquals("1.5e3", (parsed.fields["a"] as app.chessnut.otb.lichess.Json.Num).raw)
        assertEquals(2L, parsed.long("b"))
    }
}
