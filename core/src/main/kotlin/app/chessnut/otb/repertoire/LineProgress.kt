package app.chessnut.otb.repertoire

import java.io.File

/** One drilled line: its spaced-repetition state, whether the last run was clean, and the opening and side it belongs to. */
data class LineRecord(val opening: String, val userWhite: Boolean, val line: List<String>, val review: LineReview, val clean: Boolean)

/**
 * Progress through book openings' lines, one row per line in a small file.
 * Imported studies keep their own reviews in the repertoire store.
 */
class LineProgress(private val file: File) {
    private val records: MutableMap<String, LineRecord> = load()

    @Synchronized
    fun reviews(opening: String, userWhite: Boolean): Map<String, LineReview> =
        records.values.filter { it.opening == opening && it.userWhite == userWhite }.associate { Drill.key(it.line) to it.review }

    /** Lines whose last run was clean. */
    @Synchronized
    fun learned(opening: String, userWhite: Boolean): Set<String> =
        records.values.filter { it.opening == opening && it.userWhite == userWhite && it.clean }.map { Drill.key(it.line) }.toSet()

    /** Openings with any drilled line, as (name, side). */
    @Synchronized
    fun started(): Set<Pair<String, Boolean>> = records.values.map { it.opening to it.userWhite }.toSet()

    @Synchronized
    fun grade(opening: String, userWhite: Boolean, line: List<String>, clean: Boolean, now: Long): LineReview {
        val key = id(opening, userWhite, line)
        val next = Drill.grade(records[key]?.review, clean, now)
        records[key] = LineRecord(opening, userWhite, line, next, clean)
        save()
        return next
    }

    private fun id(opening: String, userWhite: Boolean, line: List<String>) = "$opening|${if (userWhite) "w" else "b"}|${Drill.key(line)}"

    private fun save() {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(records.values.joinToString("") { r ->
            listOf(
                r.opening.replace('\t', ' '),
                if (r.userWhite) "w" else "b",
                Drill.key(r.line),
                r.review.box,
                r.review.dueAt,
                if (r.clean) "1" else "0",
            ).joinToString("\t") + "\n"
        })
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun load(): MutableMap<String, LineRecord> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf()
        file.readLines().mapNotNull { row ->
            val p = row.split('\t')
            if (p.size != 6) return@mapNotNull null
            val record = LineRecord(p[0], p[1] == "w", p[2].split(' '), LineReview(p[3].toInt(), p[4].toLong()), p[5] == "1")
            id(record.opening, record.userWhite, record.line) to record
        }.toMap().toMutableMap()
    }.getOrDefault(mutableMapOf())
}
