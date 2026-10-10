package app.chessnut.otb

import app.chessnut.otb.chess.PositionSetup
import app.chessnut.otb.play.PlayClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PositionSetupTest {
    @Test
    fun startingPlacementGetsFullCastlingRights() {
        val ok = assertIs<PositionSetup.Result.Ok>(PositionSetup.fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", whiteToMove = true))
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", ok.fen)
    }

    @Test
    fun castlingOnlyWhereKingAndRookAreHome() {
        val ok = assertIs<PositionSetup.Result.Ok>(PositionSetup.fen("r3k3/8/8/8/8/8/8/4K2R", whiteToMove = false))
        assertEquals("r3k3/8/8/8/8/8/8/4K2R b Kq - 0 1", ok.fen)
    }

    @Test
    fun impossiblePositionsAreExplained() {
        val noKing = assertIs<PositionSetup.Result.Problem>(PositionSetup.fen("8/8/8/8/8/8/8/4K3", true))
        assertTrue("king" in noKing.reason)
        val pawn = assertIs<PositionSetup.Result.Problem>(PositionSetup.fen("4k2P/8/8/8/8/8/8/4K3", true))
        assertTrue("pawn" in pawn.reason)
        // Black's king is attacked by the rook, so it can't be White to move.
        val check = assertIs<PositionSetup.Result.Problem>(PositionSetup.fen("4k3/8/8/8/8/8/8/4R1K1", true))
        assertTrue("Black is in check" in check.reason, check.reason)
        assertIs<PositionSetup.Result.Ok>(PositionSetup.fen("4k3/8/8/8/8/8/8/4R1K1", false))
    }

    @Test
    fun clockStartsWithTheSideToMove() {
        val clock = PlayClock()
        clock.begin(0, whiteFirst = false)
        assertEquals(false, clock.whiteToMove)
        // Black's thinking time comes off Black's clock.
        assertEquals(clock.control.initialMillis, clock.remaining(white = true, now = 2_000))
    }
}
