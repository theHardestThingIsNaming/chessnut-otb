package app.chessnut.otb.play

import java.io.File

/**
 * Each analysed game's accuracies, kept on disk so the openings list needn't replay every game
 * to score it each time the app starts. An entry holds while the game's analysis is unchanged
 * ([stamp], its file's last write).
 */
class AccuracyCache(private val file: File) {
    /** null accuracies with [scored] false: the game couldn't be scored (no moves, say). */
    data class Entry(val stamp: Long, val scored: Boolean, val white: Double?, val black: Double?)

    private val entries = mutableMapOf<String, Entry>()
    private var dirty = false

    init {
        runCatching {
            if (file.exists()) file.readLines().forEach { row ->
                val p = row.split('\t')
                if (p.size == 5) entries[p[0]] = Entry(p[1].toLong(), p[2] == "1", p[3].toDoubleOrNull(), p[4].toDoubleOrNull())
            }
        }
    }

    @Synchronized
    fun get(id: String, stamp: Long): Entry? = entries[id]?.takeIf { it.stamp == stamp }

    @Synchronized
    fun put(id: String, entry: Entry) {
        entries[id] = entry
        dirty = true
    }

    /** Writes the entries for [keep] only, so games since let go drop out. Empty [keep] (games not loaded yet) drops nothing. */
    @Synchronized
    fun save(keep: Set<String>) {
        val gone = if (keep.isEmpty()) emptySet() else entries.keys - keep
        if (!dirty && gone.isEmpty()) return
        entries.keys.removeAll(gone)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(entries.entries.joinToString("") { (id, e) -> "$id\t${e.stamp}\t${if (e.scored) 1 else 0}\t${e.white ?: ""}\t${e.black ?: ""}\n" })
        }
        dirty = false
    }
}
