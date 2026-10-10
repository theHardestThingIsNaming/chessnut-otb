package app.chessnut.otb

import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineProgress
import app.chessnut.otb.repertoire.MoveChoices
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpeningTreeTest {
    @Test
    fun linesGiveYouOneMoveAndTheOpponentSeveral() {
        val lines = OpeningTree.lines("Italian Game", userWhite = true)
        assertTrue(lines.size > 3, "the Italian should branch: $lines")
        val italian = OpeningBook.line("Italian Game")!!.san
        assertTrue(lines.all { it.take(italian.size) == italian })
        // Every line ends on a White move, and each White position has one answer.
        assertTrue(lines.all { it.size % 2 == 1 })
        val answers = mutableMapOf<List<String>, MutableSet<String>>()
        for (line in lines) for (ply in line.indices step 2) answers.getOrPut(line.take(ply)) { mutableSetOf() } += line[ply]
        assertTrue(answers.values.all { it.size == 1 }, "one White answer per position")
        // No line is just the start of another.
        assertFalse(lines.any { a -> lines.any { b -> b.size > a.size && b.take(a.size) == a } })
    }

    @Test
    fun blackLinesEndOnBlackMoves() {
        val lines = OpeningTree.lines("Caro-Kann Defense", userWhite = false)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.size % 2 == 0 })
        assertEquals("c6", lines.first()[1])
    }

    @Test
    fun acceptedAndDeclinedLinesBelongToTheirGambit() {
        assertTrue("exf4" in OpeningBook.replies(listOf("e4", "e5", "f4"), "King's Gambit"))
        assertTrue(OpeningTree.lines("King's Gambit", userWhite = true).size > 3)
        assertTrue(OpeningTree.lines("Queen's Gambit", userWhite = true).size > 3)
    }

    @Test
    fun yourChoiceReplacesTheMainMove() {
        val before = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        val main = OpeningTree.lines("Italian Game", userWhite = true)
        assertTrue(main.filter { it.take(6) == before }.all { it[6] == "b4" }, "the book's main move after 3... Bc5")
        val file = File.createTempFile("choices", ".tsv").apply { delete() }
        MoveChoices(file).set("Italian Game", true, before, "c3")
        val choices = MoveChoices(file).of("Italian Game", true)
        val mine = OpeningTree.lines("Italian Game", userWhite = true, choices = choices)
        val giuoco = mine.filter { it.take(6) == before }
        assertTrue(giuoco.isNotEmpty() && giuoco.all { it[6] == "c3" })
        // A choice that isn't a book move is ignored.
        assertEquals(main, OpeningTree.lines("Italian Game", userWhite = true, choices = mapOf(before to "a3")))
    }

    @Test
    fun progressIsKeptPerOpeningAndSide() {
        val file = File.createTempFile("progress", ".tsv").apply { delete() }
        val progress = LineProgress(file)
        val line = listOf("e4", "e5", "Nf3", "Nc6", "Bc4")
        progress.grade("Italian Game", true, line, clean = true, now = 0)
        progress.grade("Italian Game", true, listOf("e4", "e5", "Nf3", "Nf6", "Nxe5"), clean = false, now = 0)

        val reread = LineProgress(file)
        assertEquals(setOf(Drill.key(line)), reread.learned("Italian Game", true))
        assertEquals(2, reread.reviews("Italian Game", true).size)
        assertTrue(reread.reviews("Italian Game", false).isEmpty())
        assertEquals(setOf("Italian Game" to true), reread.started())
    }
}
