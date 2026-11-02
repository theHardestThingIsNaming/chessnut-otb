package app.chessnut.otb

import app.chessnut.otb.live.LogReplay
import app.chessnut.otb.protocol.FakeChessnutBoard
import app.chessnut.otb.protocol.PositionCodec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogReplayTest {
    private fun hex(placement: String) = PositionCodec.encodePlacement(placement).joinToString(" ") { "%02x".format(it) }

    private fun log(vararg frames: Pair<String, String>) =
        frames.joinToString("\n") { (time, placement) -> "$time <- fen ${hex(placement)}" }

    @Test
    fun replaysAQuickExchangeTheWayTheAppSawIt() {
        val p = FakeChessnutBoard.placementsFromSan("1. e4 e5 2. Nf3 Nc6")
        val out = LogReplay.replay(
            log(
                "10:00:00.000" to p[0], "10:00:01.000" to p[1], "10:00:02.000" to p[2],
                // Two plies inside one settle window: the app sees only the later board.
                "10:00:03.000" to p[3], "10:00:03.100" to p[4], "10:00:05.000" to p[4],
            ),
        )
        assertTrue(out.any { it.endsWith("e4") }, out.toString())
        assertTrue(out.any { it.contains("Nf3 Nc6") || it.endsWith("Nc6") }, out.toString())
    }

    @Test
    fun aBoardThatStaysWrongIsReported() {
        val p = FakeChessnutBoard.placementsFromSan("1. e4")
        val out = LogReplay.replay(
            log(
                "10:00:00.000" to p[0], "10:00:01.000" to p[1],
                "10:00:02.000" to "rnbqkbnr/pppppppp/8/8/8/8/PPPPQPPP/RNB1KBNR", "10:00:06.000" to "rnbqkbnr/pppppppp/8/8/8/8/PPPPQPPP/RNB1KBNR",
            ),
        )
        assertEquals(1, out.count { it.contains("illegal") }, out.toString())
    }

    /** `BOARD_LOG=/path/board-....txt ./gradlew :core:test --tests '*LogReplayTest*' -i` prints what the app did with a real session. */
    @Test
    fun replaysARealLogWhenOneIsGiven() {
        val path = System.getenv("BOARD_LOG") ?: return
        LogReplay.replay(File(path).readText()).forEach { println("REPLAY $it") }
    }
}
