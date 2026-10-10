package app.chessnut.otb.engine

import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.Score
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A running engine binary: lines in, lines out. [output] is hot and does not replay. */
interface EngineProcess {
    val output: Flow<String>

    suspend fun send(line: String)

    fun close()
}

class EngineException(message: String) : Exception(message)

/**
 * Minimal UCI client. One search at a time; a live search is stopped and its
 * `bestmove` consumed before the next command, so output never leaks between searches.
 */
class UciEngine(
    private val process: EngineProcess,
    private val threads: Int = 2,
    private val hashMb: Int = 64,
) {
    private val lock = Mutex()
    var name: String = "UCI engine"
        private set
    private var started = false

    suspend fun start() = lock.withLock {
        if (started) return@withLock
        val id = exchange("uci", 10.seconds) { it == "uciok" }
        id.firstOrNull { it.startsWith("id name ") }?.let { name = it.removePrefix("id name ") }
        process.send("setoption name Threads value $threads")
        process.send("setoption name Hash value $hashMb")
        exchange("isready", 10.seconds) { it == "readyok" }
        started = true
    }

    /** Full strength, or a Stockfish Elo cap. Null clears the cap. */
    suspend fun limitStrength(elo: Int?) = lock.withLock {
        if (elo == null) {
            process.send("setoption name UCI_LimitStrength value false")
        } else {
            process.send("setoption name UCI_LimitStrength value true")
            process.send("setoption name UCI_Elo value ${elo.coerceIn(1320, 3190)}")
        }
        exchange("isready", 10.seconds) { it == "readyok" }
    }

    /** A short timed search. Used for bot moves and for judging a trainer move. */
    suspend fun analyseMillis(fen: String, millis: Long, multiPv: Int): List<EngineLine> = lock.withLock {
        val sideToMoveIsWhite = fen.split(' ').getOrNull(1) != "b"
        process.send("setoption name MultiPV value $multiPv")
        process.send("position fen $fen")
        val lines = mutableMapOf<Int, EngineLine>()
        exchange("go movetime $millis", (millis + 15_000).milliseconds) { line ->
            parseInfo(line, sideToMoveIsWhite)?.let { (index, engineLine) -> lines[index] = engineLine }
            line.startsWith("bestmove")
        }
        lines.toSortedMap().values.toList()
    }

    /** Analyses [fen] to a fixed node count and returns the final [multiPv] lines, best first. */
    suspend fun analyse(fen: String, nodes: Long, multiPv: Int): List<EngineLine> = lock.withLock {
        val sideToMoveIsWhite = fen.split(' ').getOrNull(1) != "b"
        process.send("setoption name MultiPV value $multiPv")
        process.send("position fen $fen")
        val lines = mutableMapOf<Int, EngineLine>()
        exchange("go nodes $nodes", 5.minutes()) { line ->
            parseInfo(line, sideToMoveIsWhite)?.let { (index, engineLine) -> lines[index] = engineLine }
            line.startsWith("bestmove")
        }
        lines.toSortedMap().values.toList()
    }

    /**
     * Infinite search on [fen]; emits the current best lines as they improve.
     * Cancelling the collector stops the search.
     */
    fun analyseLive(fen: String, multiPv: Int): Flow<List<EngineLine>> = channelFlow {
        lock.withLock {
            val sideToMoveIsWhite = fen.split(' ').getOrNull(1) != "b"
            process.send("setoption name MultiPV value $multiPv")
            process.send("position fen $fen")
            val lines = mutableMapOf<Int, EngineLine>()
            var finished = false
            try {
                coroutineScope {
                    val reader = launch(start = CoroutineStart.UNDISPATCHED) {
                        process.output.takeWhile { !it.startsWith("bestmove") }.collect { line ->
                            val parsed = parseInfo(line, sideToMoveIsWhite) ?: return@collect
                            lines[parsed.first] = parsed.second
                            if (parsed.first >= lines.size) send(lines.toSortedMap().values.toList())
                        }
                        finished = true
                    }
                    process.send("go infinite")
                    reader.join()
                }
            } finally {
                if (!finished) {
                    withContext(NonCancellable) {
                        val done = async(start = CoroutineStart.UNDISPATCHED) {
                            process.output.first { it.startsWith("bestmove") }
                        }
                        process.send("stop")
                        withTimeout(5.seconds) { done.await() }
                    }
                }
            }
        }
    }

    fun close() {
        runCatching { process.close() }
    }

    private suspend fun exchange(command: String, timeout: Duration, isLast: (String) -> Boolean): List<String> =
        coroutineScope {
            val seen = mutableListOf<String>()
            val reader = async(start = CoroutineStart.UNDISPATCHED) {
                process.output.first { line ->
                    seen += line
                    isLast(line)
                }
            }
            process.send(command)
            try {
                withTimeout(timeout) { reader.await() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                reader.cancel()
                throw EngineException("No answer to '$command' within ${timeout.inWholeSeconds} s.")
            }
            seen
        }

    private fun Int.minutes(): Duration = (this * 60).seconds

    companion object {
        /** Parses `info ... multipv N score cp|mate X ... pv ...`; scores come back from White's side. */
        fun parseInfo(line: String, sideToMoveIsWhite: Boolean): Pair<Int, EngineLine>? {
            if (!line.startsWith("info ") || " pv " !in line || " score " !in line) return null
            if (" lowerbound" in line || " upperbound" in line) return null
            val parts = line.split(' ')
            var depth = 0
            var multipv = 1
            var score: Score? = null
            var pv: List<String> = emptyList()
            var i = 1
            while (i < parts.size) {
                when (parts[i]) {
                    "depth" -> depth = parts.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    "multipv" -> multipv = parts.getOrNull(i + 1)?.toIntOrNull() ?: 1
                    "score" -> {
                        val value = parts.getOrNull(i + 2)?.toIntOrNull() ?: return null
                        score = when (parts.getOrNull(i + 1)) {
                            "cp" -> Score(cp = value)
                            "mate" -> Score(mate = value)
                            else -> return null
                        }
                        i += 2
                    }
                    "pv" -> {
                        pv = parts.drop(i + 1)
                        break
                    }
                }
                i++
            }
            val relative = score ?: return null
            return multipv to EngineLine(relative.forMover(sideToMoveIsWhite), pv, depth)
        }
    }
}
