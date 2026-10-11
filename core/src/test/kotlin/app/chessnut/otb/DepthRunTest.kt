package app.chessnut.otb

import app.chessnut.otb.repertoire.DepthRecords
import app.chessnut.otb.repertoire.DepthRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepthRunTest {
    @Test
    fun topTwoOrEquallyGoodMovesCount() {
        assertTrue(DepthRun.good("e2e4", listOf("e2e4", "d2d4"), winDrop = 5.0))
        assertTrue(DepthRun.good("d2d4", listOf("e2e4", "d2d4"), winDrop = 5.0))
        assertTrue(DepthRun.good("c2c4", listOf("e2e4", "d2d4"), winDrop = 1.0))
        assertFalse(DepthRun.good("a2a4", listOf("e2e4", "d2d4"), winDrop = 6.0))
    }

    @Test
    fun recordsKeepTheBestPerOpeningAndSide() {
        val file = File.createTempFile("depth", ".tsv").apply { delete() }
        val records = DepthRecords(file)
        assertNull(records.of("Scotch Game", true))
        assertEquals(DepthRecords.Record(best = 7, last = 7), records.finish("Scotch Game", true, 7))
        assertEquals(DepthRecords.Record(best = 7, last = 3), records.finish("Scotch Game", true, 3))
        records.finish("Scotch Game", false, 2)
        val reread = DepthRecords(file)
        assertEquals(DepthRecords.Record(7, 3), reread.of("Scotch Game", true))
        assertEquals(DepthRecords.Record(2, 2), reread.of("Scotch Game", false))
    }

    @Test
    fun eachLineKeepsItsOwnBest() {
        val records = DepthRecords(File.createTempFile("depth", ".tsv").apply { delete() })
        val mieses = listOf("e4", "e5", "Nf3", "Nc6", "d4", "exd4", "Nxd4", "Nf6", "Nxc6", "bxc6", "e5")
        val lolli = listOf("e4", "e5", "Nf3", "Nc6", "d4", "Nxd4", "Nxd4", "exd4", "Qxd4")
        records.finishLine("Scotch Game", true, mieses, 5)
        records.finishLine("Scotch Game", true, mieses, 2)
        records.finishLine("Scotch Game", true, lolli, 8)
        assertEquals(DepthRecords.Record(5, 2), records.ofLine("Scotch Game", true, mieses))
        assertEquals(8, records.ofLine("Scotch Game", true, lolli)?.best)
        assertNull(records.ofLine("Scotch Game", false, mieses))
        // A line's record doesn't change the opening's own.
        assertNull(records.of("Scotch Game", true))
    }
}
