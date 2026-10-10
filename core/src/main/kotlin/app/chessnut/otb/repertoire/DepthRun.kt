package app.chessnut.otb.repertoire

import java.io.File

/**
 * How far you can carry an opening past the book: after the last book move,
 * each of your moves has to be one of the engine's top two, or as good as
 * them. The run is the number of such moves in a row.
 */
object DepthRun {
    /** Winning chances, in percentage points, a move may give away against the best and still count. */
    const val TOLERANCE = 2.0

    fun good(uci: String, topTwo: List<String>, winDrop: Double): Boolean =
        topTwo.take(2).any { it.equals(uci, ignoreCase = true) } || winDrop <= TOLERANCE
}

/** Best run past the book per opening and side, and the last one, in a small file. */
class DepthRecords(private val file: File) {
    data class Record(val best: Int, val last: Int)

    private val records: MutableMap<String, Record> = load()

    @Synchronized
    fun of(opening: String, userWhite: Boolean): Record? = records[key(opening, userWhite)]

    /** Saves a finished run and returns the record, with [Record.best] raised when it is beaten. */
    @Synchronized
    fun finish(opening: String, userWhite: Boolean, run: Int): Record {
        val previous = records[key(opening, userWhite)]
        val next = Record(best = maxOf(previous?.best ?: 0, run), last = run)
        records[key(opening, userWhite)] = next
        save()
        return next
    }

    private fun key(opening: String, userWhite: Boolean) = "${opening.replace('\t', ' ')}\t${if (userWhite) "w" else "b"}"

    private fun save() {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(records.entries.joinToString("") { (k, r) -> "$k\t${r.best}\t${r.last}\n" })
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun load(): MutableMap<String, Record> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf()
        file.readLines().mapNotNull { row ->
            val p = row.split('\t')
            if (p.size != 4) null else "${p[0]}\t${p[1]}" to Record(p[2].toInt(), p[3].toInt())
        }.toMap().toMutableMap()
    }.getOrDefault(mutableMapOf())
}
