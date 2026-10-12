package app.chessnut.otb.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One Stockfish process for a screen, started on first use. Everything that
 * asks at once gets the same engine; [close] stops it, and the next [get]
 * starts a fresh one.
 */
class EngineHolder(private val context: Context, private val threads: Int = 2) {
    private val lock = Mutex()
    @Volatile private var engine: UciEngine? = null

    suspend fun get(): UciEngine = lock.withLock {
        engine ?: withContext(Dispatchers.IO) { UciEngine(StockfishProcess.start(context), threads = threads) }
            .also {
                it.start()
                engine = it
            }
    }

    fun close() {
        val closing = engine
        engine = null
        closing?.close()
    }
}
