package app.chessnut.otb.repertoire

import app.chessnut.otb.openings.Opening
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import java.io.File

/**
 * The imported repertoire and its review dates, as two small files. Each
 * chapter becomes a trainer opening named [PREFIX] plus the chapter name.
 */
class RepertoireStore(directory: File) {
    private val root = directory.apply { mkdirs() }
    private val linesFile = File(root, "lines.tsv")
    private val reviewsFile = File(root, "reviews.tsv")
    /** Imported chapters; the ones that ship with the app are in [builtin]. */
    private var imported: List<StudyChapter> = load()
    private val builtin: List<StudyChapter> = shipped()
    private val chapters: List<StudyChapter> get() = builtin.filter { b -> imported.none { it.name == b.name } } + imported
    private val reviews: MutableMap<String, LineReview> = loadReviews()

    init {
        register()
    }

    @Synchronized
    fun names(): List<String> = chapters.map { PREFIX + it.name }

    @Synchronized
    fun chapter(name: String): StudyChapter? = chapters.firstOrNull { PREFIX + it.name == name }

    fun isRepertoire(name: String?): Boolean = name?.startsWith(PREFIX) == true

    @Synchronized
    fun reviews(): Map<String, LineReview> = reviews.toMap()

    @Synchronized
    fun due(name: String, now: Long): Int = chapter(name)?.let { Drill.due(it.lines, reviews, now) } ?: 0

    /** Adds the chapters, replacing any with the same name. Returns how many chapters and lines were added. */
    @Synchronized
    fun add(imported: List<StudyChapter>): Pair<Int, Int> {
        val names = imported.map { it.name }.toSet()
        this.imported = this.imported.filter { it.name !in names } + imported
        save()
        register()
        return imported.size to imported.sumOf { it.lines.size }
    }

    @Synchronized
    fun clear() {
        imported = emptyList()
        reviews.clear()
        linesFile.delete()
        reviewsFile.delete()
        register()
    }

    @Synchronized
    fun grade(line: List<String>, clean: Boolean, now: Long): LineReview {
        val key = Drill.key(line)
        val next = Drill.grade(reviews[key], clean, now)
        reviews[key] = next
        reviewsFile.writeText(reviews.entries.joinToString("") { (k, r) -> "$k\t${r.box}\t${r.dueAt}\n" })
        return next
    }

    /** Makes the chapters trainable: book lines for the trainer, and a section in its list. */
    private fun register() {
        val openings = chapters.flatMap { chapter -> chapter.lines.map { Opening("REP", PREFIX + chapter.name, it) } }
        val sides = chapters.associate { (PREFIX + it.name) to if (it.userWhite) OpeningSide.White else OpeningSide.Black }
        OpeningBook.setExtra(openings, sides)
        OpeningCatalog.repertoire = chapters.map { PREFIX + it.name }
    }

    private fun save() {
        linesFile.writeText(buildString {
            imported.forEach { chapter ->
                chapter.lines.forEach { line ->
                    append(chapter.name.replace('\t', ' ')).append('\t')
                    append(if (chapter.userWhite) "w" else "b").append('\t')
                    append(line.joinToString(" ")).append('\n')
                }
            }
        })
    }

    private fun load(): List<StudyChapter> = runCatching {
        if (!linesFile.exists()) return@runCatching emptyList()
        linesFile.readLines().mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size == 3) Triple(parts[0], parts[1] == "w", parts[2].split(' ')) else null
        }.groupBy { it.first }.map { (name, rows) -> StudyChapter(name, rows.first().second, rows.map { it.third }) }
    }.getOrDefault(emptyList())

    /** White's lines against Black's replies to 1.e4, from the app's own resources: chapter, w, line. */
    private fun shipped(): List<StudyChapter> = runCatching {
        val text = RepertoireStore::class.java.classLoader?.getResourceAsStream("white-repertoire.tsv")?.bufferedReader()?.readText().orEmpty()
        text.lines().mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size == 3 && !row.startsWith("#")) Triple(parts[0], parts[1] == "w", parts[2].trim().split(' ')) else null
        }.groupBy { it.first }.map { (name, rows) -> StudyChapter(name, rows.first().second, rows.map { it.third }) }
    }.getOrDefault(emptyList())

    private fun loadReviews(): MutableMap<String, LineReview> = runCatching {
        if (!reviewsFile.exists()) return@runCatching mutableMapOf()
        reviewsFile.readLines().mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size == 3) parts[0] to LineReview(parts[1].toInt(), parts[2].toLong()) else null
        }.toMap().toMutableMap()
    }.getOrDefault(mutableMapOf())

    companion object {
        const val PREFIX = "Repertoire: "

        /** The study id from a Lichess study link, or the id itself. */
        fun studyId(input: String): String? =
            Regex("""(?:study/)?([A-Za-z0-9]{8})(?:[/?#]|$)""").find(input.trim())?.groupValues?.get(1)
    }
}
