package app.chessnut.otb.puzzles

import app.chessnut.otb.mistakes.ReviewFile
import app.chessnut.otb.repertoire.LineReview
import java.io.File

/** When each puzzle comes round again, on the same 1, 3, 7, 14, 30 day steps as lines. */
class PuzzleProgress(filesDir: File) {
    private val reviews = ReviewFile(File(filesDir, "puzzles/reviews.tsv"))

    fun all(): Map<String, LineReview> = reviews.all()

    fun grade(id: String, clean: Boolean, now: Long): LineReview = reviews.grade(id, clean, now)

    /** Played through without a slip: done for good. */
    fun master(id: String) = reviews.retire(id)

    fun mastered(id: String): Boolean = all()[id]?.dueAt == Long.MAX_VALUE

    /** A set's numbers for its tile: how many puzzles, solved at least once, and due now. */
    fun counts(key: String, now: Long): Counts {
        val set = PuzzleSets.of(key)
        val known = all()
        return Counts(
            total = set.size,
            solved = set.count { known[it.id]?.let { r -> r.box > 0 || r.dueAt > now } == true },
            due = set.count { known[it.id]?.let { r -> r.dueAt <= now } == true },
        )
    }

    /**
     * The next session: puzzles due again first, then ones not yet tried,
     * easiest first, up to [size].
     */
    fun session(key: String, now: Long, size: Int = SESSION): List<Puzzle> {
        val set = PuzzleSets.of(key)
        val known = all()
        val due = set.filter { known[it.id]?.let { r -> r.dueAt <= now } == true }.sortedBy { known.getValue(it.id).dueAt }
        val fresh = set.filter { it.id !in known }
        return (due + fresh).take(size)
    }

    data class Counts(val total: Int, val solved: Int, val due: Int)

    companion object {
        const val SESSION = 10
    }
}
