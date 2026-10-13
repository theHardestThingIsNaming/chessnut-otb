package app.chessnut.otb.mistakes

import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.repertoire.LineReview

/**
 * Which mistakes to practise now, so the list never piles up. Mistakes already under way come
 * first, on their review dates; new ones fill the rest of [LIMIT], the worst first, a few per game.
 * The others wait their turn. Mistakes made in a position already lost are left out: there was
 * little left to save.
 */
object MistakeQueue {
    /** At most this many to practise at once. */
    const val LIMIT = 15

    /** At most this many new mistakes from one game, so one bad game can't fill the list. */
    const val PER_GAME = 3

    /** Below this chance of winning before the move, the game was already lost. */
    const val LOST = 15.0

    fun worthPractising(m: Mistake): Boolean = (m.winBefore ?: 100.0) >= LOST

    /**
     * The mistakes due now, at most [limit]. [all] comes newest game first; among equally bad new
     * mistakes, newer ones go first.
     */
    fun due(all: List<Mistake>, reviews: Map<String, LineReview>, now: Long, limit: Int = LIMIT): List<Mistake> {
        val due = all.filter { worthPractising(it) && (reviews[it.key]?.dueAt ?: 0L) <= now }
        val (started, fresh) = due.partition { it.key in reviews }
        val picked = started.sortedBy { reviews.getValue(it.key).dueAt }.take(limit).toMutableList()
        val perGame = mutableMapOf<String, Int>()
        val worstFirst = fresh.withIndex().sortedWith(
            compareByDescending<IndexedValue<Mistake>> { it.value.classification == MoveClass.Blunder }
                .thenByDescending { it.value.winDrop ?: 0.0 }
                .thenBy { it.index },
        ).map { it.value }
        for (m in worstFirst) {
            if (picked.size >= limit) break
            val n = perGame[m.gameId] ?: 0
            if (n >= PER_GAME) continue
            perGame[m.gameId] = n + 1
            picked += m
        }
        return picked
    }
}
