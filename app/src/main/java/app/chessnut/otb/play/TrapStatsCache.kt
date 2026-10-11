package app.chessnut.otb.play

import android.util.Log
import app.chessnut.otb.lichess.LichessHttp
import app.chessnut.otb.lichess.LichessHttpException
import app.chessnut.otb.openings.Trap
import app.chessnut.otb.openings.TrapRecord
import app.chessnut.otb.openings.TrapStats
import java.io.File
import kotlinx.coroutines.delay

/** How often club players fall for each trap, from the Lichess explorer, kept for a month. */
class TrapStatsCache(private val file: File, private val http: LichessHttp) {
    private val records = mutableMapOf<String, Pair<TrapRecord, Long>>()

    init {
        runCatching {
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val p = line.split('\t')
                    if (p.size == 4) records[p[0]] = TrapRecord(p[1].toLong(), p[2].toLong()) to p[3].toLong()
                }
            }
        }
    }

    @Synchronized
    fun record(trap: Trap): TrapRecord? = records[trap.name]?.first

    @Synchronized
    private fun stale(trap: Trap, now: Long) = records[trap.name]?.let { now - it.second > MAX_AGE_MS } ?: true

    /** Fetches the traps that are missing or stale, one at a time, then calls [onDone] if any arrived. */
    suspend fun fill(traps: List<Trap>, onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        var fetched = 0
        for (trap in traps.filter { stale(it, now) }) {
            val play = TrapStats.play(trap) ?: continue
            val record = try {
                TrapStats.parse(http.explorer(play, TrapStats.QUERY), trap)
            } catch (e: LichessHttpException) {
                Log.w(TAG, "explorer ${trap.name}: ${e.status}")
                if (e.status == 429 || e.status == 401) break
                null
            } catch (e: Exception) {
                Log.w(TAG, "explorer ${trap.name}", e)
                break
            } ?: continue
            synchronized(this) { records[trap.name] = record to now }
            fetched++
            delay(PAUSE_MS)
        }
        if (fetched > 0) {
            save()
            onDone()
        }
    }

    @Synchronized
    private fun save() {
        runCatching {
            file.writeText(records.entries.joinToString("") { (name, v) -> "$name\t${v.first.mistakeGames}\t${v.first.totalGames}\t${v.second}\n" })
        }
    }

    private companion object {
        const val TAG = "TrapStats"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val PAUSE_MS = 400L
    }
}
