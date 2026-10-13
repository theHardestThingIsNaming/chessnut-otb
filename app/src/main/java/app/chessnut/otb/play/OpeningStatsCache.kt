package app.chessnut.otb.play

import android.util.Log
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.lichess.LichessHttp
import app.chessnut.otb.lichess.LichessHttpException
import app.chessnut.otb.openings.OpeningRecord
import app.chessnut.otb.openings.OpeningStats
import java.io.File
import kotlinx.coroutines.delay

/**
 * Explorer results per opening, kept in a file so the list sorts instantly.
 * Entries older than a month are fetched again.
 */
class OpeningStatsCache(private val file: File, private val http: LichessHttp) {
    private val records = mutableMapOf<String, Pair<OpeningRecord, Long>>()

    init {
        runCatching {
            if (file.exists()) {
                file.readLines().forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size == 5) {
                        val record = OpeningRecord(parts[1].toLong(), parts[2].toLong(), parts[3].toLong())
                        records[parts[0]] = record to parts[4].toLong()
                    }
                }
            }
        }
    }

    @Synchronized
    fun record(name: String): OpeningRecord? = records[name]?.first

    /**
     * [lines], the ones club players reach most first, from the counts already here.
     * Lines without a count keep their order, after the counted ones.
     */
    fun byPlays(lines: List<List<String>>): List<List<String>> {
        // Each line's count once: worked out inside the sort, it was replayed for every comparison.
        val played = lines.map { line -> lineKey(line)?.let { record(it)?.total } ?: -1L }
        return lines.indices.sortedWith(compareByDescending<Int> { played[it] }.thenBy { it }).map { lines[it] }
    }

    @Synchronized
    private fun stale(name: String, now: Long): Boolean = records[name]?.let { now - it.second > MAX_AGE_MS } ?: true

    /**
     * Fetches openings that are missing or stale, one at a time, and calls
     * [onProgress] every few so the list can re-sort. Stops on a rate limit.
     */
    suspend fun fill(names: List<String>, onProgress: () -> Unit) =
        fillPlays(names.mapNotNull { name -> OpeningStats.play(name)?.let { name to it } }, onProgress)

    /** Whether [key] needs fetching. */
    @Synchronized
    fun missing(key: String): Boolean = stale(key, System.currentTimeMillis())

    /** Like [fill], for explorer positions given directly as (cache key, UCI moves), such as one line of an opening. */
    suspend fun fillPlays(entries: List<Pair<String, String>>, onProgress: () -> Unit) {
        val now = System.currentTimeMillis()
        var fetched = 0
        for ((name, play) in entries.filter { stale(it.first, now) }) {
            val record = try {
                OpeningStats.parse(http.explorer(play, OpeningStats.QUERY))
            } catch (e: LichessHttpException) {
                Log.w(TAG, "explorer $name: ${e.status}")
                if (e.status == 429 || e.status == 401) break
                null
            } catch (e: Exception) {
                Log.w(TAG, "explorer $name", e)
                break
            } ?: continue
            synchronized(this) { records[name] = record to now }
            fetched++
            if (fetched % 8 == 0) {
                save()
                onProgress()
            }
            delay(PAUSE_MS)
        }
        if (fetched > 0) {
            save()
            onProgress()
        }
    }

    @Synchronized
    private fun save() {
        runCatching {
            file.writeText(records.entries.joinToString("") { (name, value) ->
                val (r, at) = value
                "$name\t${r.white}\t${r.draws}\t${r.black}\t$at\n"
            })
        }
    }

    private companion object {
        /** The explorer cache key for [line]'s end position: "line:" and its UCI moves. */
        fun lineKey(line: List<String>): String? =
            runCatching { "line:" + ChessLines.replay(line).uci.joinToString(",") }.getOrNull()

        const val TAG = "OpeningStats"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        /** The explorer is shared by everyone; one request at a time, gently. */
        const val PAUSE_MS = 400L
    }
}
