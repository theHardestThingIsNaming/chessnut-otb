package app.chessnut.otb

import app.chessnut.otb.openings.LineGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineGroupsTest {
    private fun line(vararg moves: String) = moves.toList()

    @Test
    fun linesAreShownBestRateFirstAndUnratedLast() {
        val a = line("e4", "e5", "Nf3", "Nc6", "Bb5")
        val b = line("e4", "e5", "Nf3", "Nc6", "Bc4")
        val c = line("e4", "e5", "Nf3", "Nc6", "d4")
        val d = line("e4", "e5", "Nf3", "Nc6", "Nc3")
        val rates = mapOf(a to 48, b to 55, c to 52)
        val order = LineGroups.order("Test", listOf(a, b, c, d)) { rates[it] }
        assertEquals(listOf(b, c, a, d), order)
    }

    @Test
    fun equalRatesKeepTheirOwnOrder() {
        val a = line("e4", "e5", "Nf3", "Nc6", "Bb5")
        val b = line("e4", "e5", "Nf3", "Nc6", "Bc4")
        assertEquals(listOf(a, b), LineGroups.order("Test", listOf(a, b)) { 50 })
    }

    @Test
    fun oneGroupHoldingMostLinesStaysFlat() {
        // Seven lines go 2...Nc6 and one goes 2...Nf6: grouping by the parting move would give one big group.
        val lines = (1..7).map { line("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O".repeat(1), "Be7".repeat(it % 2 + 1).take(3)) } +
            listOf(line("e4", "e5", "Nf3", "Nf6"))
        val labels = LineGroups.labels("Test", lines)
        assertTrue(labels.all { it.isEmpty() })
    }

    @Test
    fun aFamilyOfThreeOrMoreLinesFormsItsOwnGroup() {
        val evans = listOf(
            line("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4", "Bxb4", "c3", "Ba5", "d4", "exd4", "O-O"),
            line("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4", "Bxb4", "c3", "Ba5", "d4", "exd4", "Qb3"),
            line("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4", "Bxb4", "c3", "Ba5", "d4", "Bb6"),
        )
        val others = listOf(
            line("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "Ng5"),
            line("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "d4"),
            line("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"),
            line("e4", "e5", "Nf3", "Nc6", "d4", "exd4"),
        )
        val labels = LineGroups.labels("Italian Game", evans + others)
        assertEquals(1, labels.take(3).toSet().size)
        assertTrue(labels[0].isNotEmpty())
    }
}
