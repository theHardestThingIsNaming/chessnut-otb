package app.chessnut.otb

import app.chessnut.otb.play.PositionGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PositionGameTest {
    private val start = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"

    @Test
    fun aBarePositionHasOneBoard() {
        val replay = PositionGame.parse(PositionGame.id(start, emptyList()))!!
        assertEquals(listOf(start), replay.fens)
        assertEquals(emptyList(), replay.uci)
    }

    @Test
    fun movesPlayedFromThePositionCanBeSteppedThrough() {
        val id = PositionGame.id(start, listOf("e2e4", "e8e7", "e1e2"))
        val replay = PositionGame.parse(id)!!
        assertEquals(4, replay.fens.size)
        assertEquals(listOf("e4", "Ke7", "Ke2"), replay.san)
        assertEquals(start, replay.fens.first())
    }

    @Test
    fun anIllegalMoveEndsTheReplayThere() {
        val replay = PositionGame.parse(PositionGame.id(start, listOf("e2e4", "e8e5", "e1e2")))!!
        assertEquals(listOf("e2e4"), replay.uci)
    }

    @Test
    fun otherIdsAreNotPositions() {
        assertNull(PositionGame.parse("2026-10-29-abc"))
    }
}
