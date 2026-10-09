package app.chessnut.otb.engine

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stockfish shipped as `lib/arm64-v8a/libstockfish.so`. Android only lets an
 * app execute files from its native library directory, which is why the
 * binary is packaged as a library and extracted at install time.
 */
class StockfishProcess private constructor(private val process: Process) : EngineProcess {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lines = MutableSharedFlow<String>(extraBufferCapacity = 4096)
    override val output: SharedFlow<String> = lines.asSharedFlow()
    private val writer = process.outputStream.bufferedWriter()
    private val writeLock = Mutex()

    init {
        scope.launch {
            try {
                process.inputStream.bufferedReader().useLines { all -> all.forEach { lines.emit(it) } }
            } catch (_: IOException) {
                // destroy() closes the pipe under the reader; that is the normal way out.
            }
        }
    }

    override suspend fun send(line: String) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    override fun close() {
        runCatching {
            writer.write("quit\n")
            writer.flush()
        }
        process.destroy()
        scope.cancel()
    }

    companion object {
        fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

        fun start(context: Context): StockfishProcess {
            val binary = binary(context)
            if (!binary.exists()) throw EngineException("Stockfish is not installed for this device's CPU (${binary.path}).")
            val process = ProcessBuilder(binary.path)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()
            return StockfishProcess(process)
        }
    }
}
