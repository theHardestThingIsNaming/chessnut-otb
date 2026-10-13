package app.chessnut.otb.board

import app.chessnut.otb.OtbApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The board's lights, for every screen that plays on it. The newest request always wins, even when
 * an older write is still on its way, and a stalled radio can't hold anything up for more than two seconds.
 */
class BoardLights(private val app: OtbApp, private val scope: CoroutineScope, private val log: (String) -> Unit = {}) {
    private val lock = Mutex()
    private var seq = 0

    fun show(squares: List<String>) {
        val session = app.boardSession ?: return
        val mine = synchronized(this) { ++seq }
        // A screen turns the lights off as it closes, when its own scope has already stopped:
        // that last request still has to reach the board.
        val runner = if (scope.isActive) scope else LEFTOVER
        runner.launch(Dispatchers.IO) {
            lock.withLock {
                if (mine != synchronized(this@BoardLights) { seq }) return@withLock
                log("leds ${squares.joinToString().ifEmpty { "off" }}")
                val wrote = withTimeoutOrNull(2.seconds) {
                    runCatching { session.setLeds(squares) }.onFailure { log("leds failed: ${it.message}") }.isSuccess
                }
                if (wrote == null) log("leds timed out")
            }
        }
    }

    /** A move's two squares, or off for null. */
    fun move(uci: String?) = show(if (uci == null || uci.length < 4) emptyList() else listOf(uci.substring(0, 2), uci.substring(2, 4)))

    fun off() = show(emptyList())

    private companion object {
        val LEFTOVER = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
