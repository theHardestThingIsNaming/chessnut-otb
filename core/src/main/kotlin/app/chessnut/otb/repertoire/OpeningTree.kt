package app.chessnut.otb.repertoire

import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog

/**
 * Turns a book opening into a repertoire to drill, the way a coach would
 * write one out: on your moves, your chosen move from [choices] (by the moves
 * before it), else the book's main move; on the opponent's, each of their
 * most common book replies. Every path to the end of the book (or
 * [maxPlies]) is one line. Lines end on your move, since a line that ends
 * with the opponent's move has nothing left for you to answer.
 *
 * Named variations grouped under the opening, such as the Scotch's Göring
 * Gambit, add their own move for your side where they branch off, so the
 * whole family is covered.
 */
object OpeningTree {
    fun lines(
        family: String,
        userWhite: Boolean,
        choices: Map<List<String>, String> = emptyMap(),
        maxPlies: Int = 16,
        maxReplies: Int = OpeningCatalog.breadth(family),
        maxLines: Int = 40,
        variations: List<List<String>> = OpeningCatalog.variationsOf(family).mapNotNull { OpeningBook.line(it)?.san },
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
            val mine = choices[line]?.takeIf { it in replies } ?: replies.first()
            val branches = if (yourMove) {
                // Your move, plus the move of any grouped variation that branches off here.
                (listOf(mine) + variations.filter { it.size > line.size && it.take(line.size) == line }.map { it[line.size] })
                    .filter { it in replies }
                    .distinct()
            } else {
                replies.take(maxReplies)
            }
            for (reply in branches) walk(line + reply)
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
