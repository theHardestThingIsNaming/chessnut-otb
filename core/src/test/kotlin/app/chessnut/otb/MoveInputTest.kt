package app.chessnut.otb

import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.chess.squareIndex
import app.chessnut.otb.chess.squareName
import app.chessnut.otb.chess.uciSquares
import com.github.bhlangonijr.chesslib.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveInputTest {
    private val start = Board().fen

    @Test
    fun squaresRoundTripAndReadUci() {
        assertEquals(0, squareIndex("a1"))
        assertEquals(63, squareIndex("H8"))
        assertEquals("e4", squareName(28))
        assertEquals(12 to 28, uciSquares("e2e4"))
        assertNull(uciSquares("e2"))
        assertNull(uciSquares(null))
    }

    @Test
    fun tapPicksUpOwnPiecesOnlyAndPlaysLegalMoves() {
        val e2 = squareIndex("e2")
        assertEquals(MoveInput.Tap.Select(e2), MoveInput.tap(start, null, e2))
        assertEquals(MoveInput.Tap.Select(null), MoveInput.tap(start, null, squareIndex("e7")))
        assertEquals(MoveInput.Tap.Select(null), MoveInput.tap(start, e2, e2))
        assertEquals(MoveInput.Tap.Play("e2e4"), MoveInput.tap(start, e2, squareIndex("e4")))
        assertEquals(MoveInput.Tap.Select(squareIndex("d2")), MoveInput.tap(start, e2, squareIndex("d2")))
        assertEquals(MoveInput.Tap.Select(null), MoveInput.tap(start, e2, squareIndex("e5")))
    }

    @Test
    fun targetsAndDropsFollowTheRulesAndPromoteToQueen() {
        assertEquals(setOf(squareIndex("e3"), squareIndex("e4")), MoveInput.targets(start, squareIndex("e2")))
        assertEquals(emptySet(), MoveInput.targets(start, null))
        assertEquals("g1f3", MoveInput.drop(start, squareIndex("g1"), squareIndex("f3")))
        assertNull(MoveInput.drop(start, squareIndex("g1"), squareIndex("g3")))
        assertEquals("a7a8q", MoveInput.drop("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", squareIndex("a7"), squareIndex("a8")))
    }
}
