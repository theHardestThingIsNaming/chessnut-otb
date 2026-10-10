package app.chessnut.otb.repertoire

import app.chessnut.otb.openings.OpeningBook

/**
 * Turns a book opening into a repertoire to drill, the way a coach would
 * write one out: on your moves, the book's main move; on the opponent's, each
 * of their most common book replies. Every path to the end of the book (or
 * [maxPlies]) is one line. Lines end on your move, since a line that ends
 * with the opponent's move has nothing left for you to answer.
 */
object OpeningTree {
    fun lines(
        family: String,
        userWhite: Boolean,
        maxPlies: Int = 16,
        maxReplies: Int = 3,
        maxLines: Int = 40,
    ): List<List<String>> {
        val found = linkedSetOf<List<String>>()
        fun walk(line: List<String>) {
            if (found.size >= maxLines) return
            val replies = if (line.size >= maxPlies) emptyList() else OpeningBook.rankedReplies(line, family)
            if (replies.isEmpty()) {
                trimmed(line, userWhite)?.let(found::add)
                return
            }
            val yourMove = (line.size % 2 == 0) == userWhite
            for (reply in if (yourMove) replies.take(1) else replies.take(maxReplies)) walk(line + reply)
        }
        walk(emptyList())
        // A shorter line that is the start of a longer one adds nothing to drill.
        return found.filter { short -> found.none { long -> long.size > short.size && long.take(short.size) == short } }
    }

    private fun trimmed(line: List<String>, userWhite: Boolean): List<String>? {
        var end = line.size
        while (end > 0 && ((end - 1) % 2 == 0) != userWhite) end--
        return line.take(end).takeIf { it.isNotEmpty() }
    }
}
