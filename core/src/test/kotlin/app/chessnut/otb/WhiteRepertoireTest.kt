package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhiteRepertoireTest {
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
}
