package app.chessnut.otb

import app.chessnut.otb.engine.EngineProcess
import app.chessnut.otb.engine.UciEngine
import com.github.bhlangonijr.chesslib.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * A slow engine: the first search runs on until it's stopped or its time is
 * up. Its time runs out while the next commands arrive, so, like Stockfish,
 * its late "bestmove" lands in the middle of whatever comes next.
 */
private class SlowEngineProcess : EngineProcess {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val output = flow
    private var searches = 0
    private var pending = false

    override suspend fun send(line: String) {
        if (pending && line.startsWith("go")) {
            pending = false
            flow.emit("bestmove a2a3")
        }
        when {
            line == "uci" -> {
                flow.emit("id name Slow 1")
                flow.emit("uciok")
            }
            line == "isready" -> flow.emit("readyok")
            line.startsWith("go") -> {
                searches++
                if (searches > 1) {
                    flow.emit("info depth 12 multipv 1 score cp 40 nodes 10 pv d2d4")
                    flow.emit("bestmove d2d4")
                } else {
                    pending = true
                    flow.emit("info depth 3 multipv 1 score cp 10 nodes 10 pv a2a3")
                }
            }
            line == "stop" && pending -> {
                pending = false
                flow.emit("bestmove a2a3")
            }
        }
    }

    override fun close() = Unit
}

class EngineSettleTest {
    @Test
    fun aCancelledSearchDoesNotAnswerTheNextOne() = runTest {
        val engine = UciEngine(SlowEngineProcess())
        engine.start()
        val first = async(start = CoroutineStart.UNDISPATCHED) { engine.analyseMillis(Board().fen, 60_000, 1) }
        yield()
        delay(10)
        first.cancel()
        // Without settling, this search would take the first one's late "bestmove a2a3" as its own.
        val second = engine.analyseMillis(Board().fen, 100, 1)
        assertEquals(listOf("d2d4"), second.map { it.uci.first() })
    }
}
