package app.chessnut.otb.repertoire

import java.io.File

/**
 * The moves you have picked over the book's main move, per opening and side,
 * keyed by the moves before them. One row per choice in a small file.
 */
class MoveChoices(private val file: File) {
    private data class Choice(val opening: String, val userWhite: Boolean, val before: List<String>, val move: String)

    private val rows: MutableList<Choice> = load()

    @Synchronized
    fun of(opening: String, userWhite: Boolean): Map<List<String>, String> =
        rows.filter { it.opening == opening && it.userWhite == userWhite }.associate { it.before to it.move }

    @Synchronized
    fun set(opening: String, userWhite: Boolean, before: List<String>, move: String) {
        rows.removeAll { it.opening == opening && it.userWhite == userWhite && it.before == before }
        rows += Choice(opening, userWhite, before, move)
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(rows.joinToString("") { c ->
            "${c.opening.replace('\t', ' ')}\t${if (c.userWhite) "w" else "b"}\t${c.before.joinToString(" ")}\t${c.move}\n"
        })
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun load(): MutableList<Choice> = runCatching {
        if (!file.exists()) return@runCatching mutableListOf()
        file.readLines().mapNotNull { row ->
            val p = row.split('\t')
            if (p.size != 4) null else Choice(p[0], p[1] == "w", p[2].split(' ').filter { it.isNotBlank() }, p[3])
        }.toMutableList()
    }.getOrDefault(mutableListOf())
}
