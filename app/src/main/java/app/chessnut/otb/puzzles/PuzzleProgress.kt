package app.chessnut.otb.puzzles

import app.chessnut.otb.mistakes.ReviewFile
import app.chessnut.otb.repertoire.LineReview
import java.io.File

/** When each puzzle comes round again: once after three days, then it is finished. */
class PuzzleProgress(filesDir: File) {
    private val reviews = ReviewFile(File(filesDir, "puzzles/reviews.tsv"))

    fun all(): Map<String, LineReview> = reviews.all()

    fun grade(id: String, clean: Boolean, now: Long): LineReview {
        val next = PuzzleReview.grade(reviews.all()[id], clean, now)
        reviews.record(id, next)
        return next
    }

    /** Played through without a slip: done for good. */
    fun master(id: String) = reviews.retire(id)

    fun mastered(id: String): Boolean = all()[id]?.dueAt == Long.MAX_VALUE

    /** A set's numbers for its tile: how many puzzles, solved at least once, and due now. */
    fun counts(key: String, now: Long): Counts {
        val set = PuzzleSets.of(key)
        val known = all()
        return Counts(
            total = set.size,
            solved = set.count { PuzzleReview.solved(known[it.id]) },
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

    data class Counts(val total: Int, val solved: Int, val due: Int) {
        /** Every puzzle has been solved, and none is due yet. */
        val resting: Boolean get() = total > 0 && due == 0 && solved == total
    }

    companion object {
        const val SESSION = 10
    }
}
