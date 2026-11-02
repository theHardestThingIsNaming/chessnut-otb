package app.chessnut.otb.openings

/** How an opening's lines are grouped and ordered on its page. */
object LineGroups {
    /** The variation a line is named for in the book, without the opening's own name; null when it has none. */
    fun variationOf(name: String, line: List<String>): String? = OpeningBook.view(line).name
        ?.removePrefix("$name: ")
        ?.removePrefix(name)
        ?.trim(' ', ',', ':')
        ?.ifBlank { null }

    /**
     * Which group each of [lines] sits in on the page. Three or more lines of one named family ("Evans Gambit")
     * are a group of their own; the rest are grouped by the move where they part ("3...Bc5"). Blank when
     * the lines don't part at all, or when one group would hold most of them.
     */
    fun labels(name: String, lines: List<List<String>>): List<String> {
        val parting = lines.firstOrNull()?.let { first ->
            (0 until first.size).firstOrNull { ply -> lines.any { it.size <= ply || it[ply] != first[ply] } }
        }
        val named = lines.map { variationOf(name, it)?.substringBefore(", ")?.takeIf { f -> f.isNotBlank() } }
        // "Evans Gambit Declined" belongs with "Evans Gambit": a name that continues another is part of its family.
        val roots = named.filterNotNull().distinct().sortedBy { it.length }
        val family = named.map { f -> f?.let { n -> roots.firstOrNull { it != n && n.startsWith("$it ") } ?: n } }
        val counts = family.filterNotNull().groupingBy { it }.eachCount()
        val labels = lines.mapIndexed { i, line ->
            family[i]?.takeIf { (counts[it] ?: 0) >= 3 }
                ?: parting?.takeIf { it < line.size }?.let { ply -> "${ply / 2 + 1}${if (ply % 2 == 0) "." else "..."}${line[ply]}" }.orEmpty()
        }
        val biggest = labels.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        return if (biggest * 4 > lines.size * 3) List(lines.size) { "" } else labels
    }

    /**
     * The order lines are shown and trained in: best win rate first, lines without a rate after in their own
     * order. A long list is kept in its groups and sorted within each.
     */
    fun order(name: String, lines: List<List<String>>, rate: (List<String>) -> Int?): List<List<String>> {
        val labels = labels(name, lines)
        val byRate = compareByDescending<List<String>> { rate(it) ?: -1 }
        val groups = lines.indices.groupBy { labels[it] }
        return if (groups.size < 2 || lines.size < 6 || "" in groups) {
            lines.sortedWith(byRate)
        } else {
            groups.values.flatMap { rows -> rows.map { lines[it] }.sortedWith(byRate) }
        }
    }
}
