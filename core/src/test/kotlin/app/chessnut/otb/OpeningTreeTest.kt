package app.chessnut.otb

import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
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
        val lines = OpeningTree.lines("Italian Game", userWhite = true, variations = emptyList())
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
    fun groupedVariationsJoinTheirOpening() {
        val scotch = OpeningTree.lines("Scotch Game", userWhite = true)
        val start = listOf("e4", "e5", "Nf3", "Nc6", "d4", "exd4")
        val fourth = scotch.filter { it.take(6) == start }.mapNotNull { it.getOrNull(6) }.toSet()
        assertTrue(setOf("Nxd4", "c3", "Bc4").all { it in fourth }, "main line, Göring and Scotch Gambit: $fourth")
        // The variations live inside the Scotch, not as their own openings in the list.
        val listed = OpeningCatalog.browse("").flatMap { it.names }
        assertFalse("Scotch Game: Göring Gambit" in listed)
        assertTrue("Scotch Game" in listed)
        assertEquals(listOf("Scotch Game: Scotch Gambit", "Scotch Game: Göring Gambit").sorted(), OpeningCatalog.variationsOf("Scotch Game").sorted())
    }

    @Test
    fun thePircCoversWhitesMainSystemsWithANoteForEveryMove() {
        assertTrue("Pirc Defense" in OpeningCatalog.browse("").flatMap { it.names })
        val lines = OpeningTree.lines("Pirc Defense", userWhite = false)
        val fourth = lines.mapNotNull { it.getOrNull(6) }.toSet()
        assertTrue(setOf("f4", "Nf3", "Be3", "Bg5", "Be2", "Bc4", "g3").all { it in fourth }, "White's 4th moves: $fourth")
        assertTrue(lines.all { it.size % 2 == 0 }, "every line ends on Black's move")
        val plies = lines.flatMap { line -> line.indices.map { line.take(it + 1).joinToString(" ") } }.toSet()
        assertEquals(emptyList(), plies.filter { it !in app.chessnut.otb.openings.MoveIdeas.noted })
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
        // Without the grouped variations (the Evans Gambit is one), your move is the only answer.
        val main = OpeningTree.lines("Italian Game", userWhite = true, variations = emptyList())
        assertTrue(main.filter { it.take(6) == before }.all { it[6] == "b4" }, "the book's main move after 3... Bc5")
        val file = File.createTempFile("choices", ".tsv").apply { delete() }
        MoveChoices(file).set("Italian Game", true, before, "c3")
        val choices = MoveChoices(file).of("Italian Game", true)
        val mine = OpeningTree.lines("Italian Game", userWhite = true, choices = choices, variations = emptyList())
        val giuoco = mine.filter { it.take(6) == before }
        assertTrue(giuoco.isNotEmpty() && giuoco.all { it[6] == "c3" })
        // A choice that isn't a book move is ignored.
        assertEquals(main, OpeningTree.lines("Italian Game", userWhite = true, choices = mapOf(before to "a3"), variations = emptyList()))
        // With them, your choice and the Evans Gambit both stay.
        val grouped = OpeningTree.lines("Italian Game", userWhite = true, choices = choices).filter { it.take(6) == before }.map { it[6] }.toSet()
        assertEquals(setOf("c3", "b4"), grouped)
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

    @Test
    fun theBishopsOpeningFollowsTheClubReplies() {
        val lines = OpeningTree.lines("Bishop's Opening", userWhite = true)
        assertEquals(4, lines.size)
        assertEquals(setOf("Nf6", "Nc6", "Bc5", "d6"), lines.map { it[3] }.toSet())
        assertTrue(lines.all { it.take(3) == listOf("e4", "e5", "Bc4") && it.size == 15 })
        assertEquals("Nc3", lines.first { it[3] == "Nf6" }[4])
        assertEquals("d3", lines.first { it[3] == "Nc6" }[4])
        assertEquals("Nf3", lines.first { it[3] == "Bc5" }[4])
    }
}
