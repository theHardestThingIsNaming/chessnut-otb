package app.chessnut.otb

import app.chessnut.otb.play.AccuracyCache
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccuracyCacheTest {
    @Test
    fun keepsScoresUntilTheAnalysisChanges() {
        val file = Files.createTempDirectory("acc").resolve("accuracy.tsv").toFile()
        AccuracyCache(file).apply {
            put("a", AccuracyCache.Entry(10, true, 91.5, null))
            put("b", AccuracyCache.Entry(20, false, null, null))
            put("gone", AccuracyCache.Entry(30, true, 50.0, 60.0))
            save(setOf("a", "b"))
        }
        val read = AccuracyCache(file)
        assertEquals(AccuracyCache.Entry(10, true, 91.5, null), read.get("a", 10))
        assertEquals(AccuracyCache.Entry(20, false, null, null), read.get("b", 20))
        // Re-analysed: the stored score no longer holds.
        assertNull(read.get("a", 11))
        assertNull(read.get("gone", 30))
    }

    @Test
    fun gamesNotLoadedYetDropNothing() {
        val file = Files.createTempDirectory("acc").resolve("accuracy.tsv").toFile()
        AccuracyCache(file).apply { put("a", AccuracyCache.Entry(1, true, 80.0, 70.0)); save(setOf("a")) }
        AccuracyCache(file).apply { save(emptySet()) }
        assertEquals(80.0, AccuracyCache(file).get("a", 1)?.white)
    }
}
