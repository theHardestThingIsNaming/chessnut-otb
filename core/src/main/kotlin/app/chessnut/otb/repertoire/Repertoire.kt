package app.chessnut.otb.repertoire

/** Review state of one repertoire line. [box] climbs with each clean run; [dueAt] is when to drill it again. */
data class LineReview(val box: Int, val dueAt: Long)

/**
 * Spaced repetition over repertoire lines. A clean run moves a line to the
 * next box and a longer wait; a miss sends it back to the start for tomorrow.
 */
object Drill {
    /** Days to wait after a clean run from each box. */
    val INTERVAL_DAYS = listOf(1, 3, 7, 14, 30)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun key(line: List<String>): String = line.joinToString(" ")

    /**
     * The line to drill next: the most overdue review first, so nothing you
     * learned slips; then the first line never drilled; then whichever comes
     * due soonest.
     */
    fun next(lines: List<List<String>>, reviews: Map<String, LineReview>, now: Long): List<String>? {
        val overdue = lines.filter { reviews[key(it)]?.let { r -> r.dueAt <= now } == true }
        overdue.minByOrNull { reviews.getValue(key(it)).dueAt }?.let { return it }
        lines.firstOrNull { key(it) !in reviews }?.let { return it }
        return lines.minByOrNull { reviews[key(it)]?.dueAt ?: 0L }
    }

    /** Lines drilled before whose review date has come. */
    fun overdue(lines: List<List<String>>, reviews: Map<String, LineReview>, now: Long): Int =
        lines.count { reviews[key(it)]?.let { r -> r.dueAt <= now } == true }

    fun due(lines: List<List<String>>, reviews: Map<String, LineReview>, now: Long): Int =
        lines.count { (reviews[key(it)]?.dueAt ?: 0L) <= now }

    fun grade(previous: LineReview?, clean: Boolean, now: Long): LineReview {
        if (!clean) return LineReview(0, now + DAY_MS)
        val box = ((previous?.box ?: -1) + 1).coerceAtMost(INTERVAL_DAYS.lastIndex)
        return LineReview(box, now + INTERVAL_DAYS[box] * DAY_MS)
    }

    fun days(review: LineReview, now: Long): Int = ((review.dueAt - now + DAY_MS - 1) / DAY_MS).toInt()
}
