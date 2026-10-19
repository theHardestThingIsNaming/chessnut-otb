package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.MiddlegamePlans
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhiteRepertoireTest {
    companion object {
        /** The lines of a bundled White chapter, in drilled order. */
        fun lines(chapter: String): List<List<String>> =
            WhiteRepertoireTest::class.java.classLoader.getResourceAsStream("white-repertoire.tsv")!!.bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
                .filter { it[0] == chapter }.map { it[2].split(' ') }
    }

    private val rows = WhiteRepertoireTest::class.java.classLoader.getResourceAsStream("white-repertoire.tsv")!!
        .bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    @Test
    fun everyLineIsLegalAndStartsWithE4() {
        assertTrue(rows.size > 100)
        for ((chapter, side, line) in rows) {
            val moves = line.split(' ')
            assertEquals("w", side, chapter)
            assertEquals("e4", moves.first(), "$chapter starts with e4")
            assertEquals(moves.size, ChessLines.replay(moves).uci.size, "$chapter: $line replays")
            assertTrue(moves.size % 2 == 0, "$chapter ends after Black's reply: $line")
        }
        assertEquals(rows.size, rows.map { it[2] }.toSet().size, "no repeated lines")
    }

    @Test
    fun everyLineLeadsIntoALegalPlan() {
        val plans = MiddlegamePlans.all.filter { it.opening.startsWith("Repertoire: ") }
        for (plan in plans) {
            assertEquals(plan.moves.size, ChessLines.replay(plan.before + plan.moves).uci.size - plan.before.size, "${plan.name} replays")
            assertTrue(plan.before.size % 2 == 1, "${plan.name} starts after White's move")
        }
        for ((chapter, _, line) in rows) {
            val moves = line.split(' ')
            assertTrue(
                plans.any { it.opening == "Repertoire: $chapter" && moves.size >= it.before.size && moves.take(it.before.size) == it.before },
                "$chapter: no plan after $line",
            )
        }
    }
}
