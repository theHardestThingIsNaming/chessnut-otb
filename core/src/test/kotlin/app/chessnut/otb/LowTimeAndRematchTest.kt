package app.chessnut.otb

import app.chessnut.otb.lichess.LichessEvent
import app.chessnut.otb.lichess.LichessPlay
import app.chessnut.otb.live.LowTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowTimeAndRematchTest {
    @Test
    fun theClockTurnsRedInTheLastFifteenthOfTheGame() {
        assertEquals(20_000, LowTime.threshold(5 * 60_000L))
        assertEquals(40_000, LowTime.threshold(10 * 60_000L))
        assertEquals(10_000, LowTime.threshold(60_000L))
        assertEquals(60_000, LowTime.threshold(30 * 60_000L))
        assertTrue(LowTime.low(19_000, 5 * 60_000L))
        assertFalse(LowTime.low(21_000, 5 * 60_000L))
    }

    @Test
    fun aRematchSwapsColoursAndKeepsTheClockAndRating() {
        val full = """{"type":"gameFull","id":"abc12345","rated":true,"speed":"rapid","clock":{"initial":600000,"increment":5000},
            "white":{"id":"me","name":"Me","rating":1612},"black":{"id":"them","name":"Them","rating":1688},
            "initialFen":"startpos","state":{"type":"gameState","moves":"e2e4","wtime":600000,"btime":600000,"winc":5000,"binc":5000,"status":"resign","winner":"white"}}"""
        val table = (LichessPlay.parse(full, "me") as LichessEvent.Full).table
        assertEquals(600_000, table.initialMillis)
        assertEquals("rated=true&clock.limit=600&clock.increment=5&color=black&keepAliveStream=true", LichessPlay.rematchForm(table))
    }
}
