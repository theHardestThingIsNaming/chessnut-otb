package app.chessnut.otb

import app.chessnut.otb.flags.FlagKey
import app.chessnut.otb.flags.FlagStore
import app.chessnut.otb.flags.MoveFlag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MoveFlagsTest {
    @Test
    fun aMoveIsNamedByThePositionBeforeIt() {
        // The same position reached by a different move order gives the same key.
        val a = FlagKey.of(listOf("g1f3", "g8f6", "b1c3", "b8c6", "e2e4"))
        val b = FlagKey.of(listOf("b1c3", "b8c6", "g1f3", "g8f6", "e2e4"))
        assertNotNull(a)
        assertEquals(a, b)
        assertNull(FlagKey.of(emptyList()))
        assertNull(FlagKey.of(listOf("e2e5", "e7e5")))
    }

    @Test
    fun flagsSurviveARestartWithTheirText() {
        val file = File.createTempFile("flags", ".tsv").apply { deleteOnExit() }
        val key = FlagKey.of(listOf("e2e4", "e7e5"))!!
        val flag = MoveFlag(key, listOf("e2e4", "e7e5"), "master", "Ruy Lopez\tline 2", "Black mirrors.\nA second line", "why not ...c5?", 123L)
        FlagStore(file).put(flag)
        val back = FlagStore(file)
        assertEquals(flag, back.get(key))
        back.remove(key)
        assertNull(FlagStore(file).get(key))
    }
}
