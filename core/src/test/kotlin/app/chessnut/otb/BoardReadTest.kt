package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.play.BoardRead
import app.chessnut.otb.play.BoardReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BoardReadTest {
    private fun fenAfter(san: String) = ChessLines.replay(san.split(' ').filter { it.isNotBlank() }).fens.last()
    private fun board(fen: String) = fen.substringBefore(' ')

    @Test
    fun theSamePositionAndALegalMove() {
        val start = fenAfter("")
        assertEquals(BoardReading.Same, BoardRead.read(start, board(start)))
        assertEquals(BoardReading.Move("e2e4"), BoardRead.read(start, board(fenAfter("e4"))))
    }

    @Test
    fun aCaptureUnderWayIsAPieceInTheHand() {
        // 11.Bxc6 in Nakamura-Onischuk: the bishop lifted, then the pawn, before the bishop lands.
        val before = fenAfter("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 d5 Bb5 Ne4 Nxd4 Bc5 Be3 O-O Nxc6 bxc6 Bxc5 Nxc5")
        val bishopUp = board(before).replace("1B", "2")
        val reading = BoardRead.read(before, bishopUp)
        assertIs<BoardReading.InHand>(reading)
        assertEquals(listOf("b5"), reading.lifted)
        // Then the c6 pawn comes off too: still nothing new on the board.
        val bothUp = board(fenAfter("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 d5 Bb5 Ne4 Nxd4 Bc5 Be3 O-O Nxc6 bxc6 Bxc5 Nxc5"))
            .replace("1B", "2").replace("2p5", "8")
        assertIs<BoardReading.InHand>(BoardRead.read(before, bothUp))
        // And the bishop lands: the move.
        assertEquals(BoardReading.Move("b5c6"), BoardRead.read(before, board(fenAfter("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 d5 Bb5 Ne4 Nxd4 Bc5 Be3 O-O Nxc6 bxc6 Bxc5 Nxc5 Bxc6"))))
    }

    @Test
    fun castlingHalfDoneIsUnexpectedWithItsSquares() {
        val before = fenAfter("e4 e5 Nf3 Nc6 Bc4 Bc5")
        // The king is already on g1, the rook still on h1.
        val kingOnly = board(before).replace("RNBQK2R", "RNBQ2KR")
        val reading = BoardRead.read(before, kingOnly)
        assertIs<BoardReading.Unexpected>(reading)
        assertEquals(listOf("e1", "g1"), reading.squares)
        assertEquals(BoardReading.Move("e1g1"), BoardRead.read(before, board(fenAfter("e4 e5 Nf3 Nc6 Bc4 Bc5 O-O"))))
    }

    @Test
    fun tooManyWrongSquaresLightNothing() {
        val start = fenAfter("")
        val reading = BoardRead.read(start, "8/8/8/8/8/8/8/8")
        assertIs<BoardReading.InHand>(reading)
        val scrambled = BoardRead.read(start, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR".reversed())
        assertIs<BoardReading.Unexpected>(scrambled)
        assertEquals(emptyList(), BoardRead.lightsFor(scrambled))
    }

    @Test
    fun anIllegalMoveIsNamedButHalfMovesAreNot() {
        val start = fenAfter("")
        // A rook through its own pawn, a bishop through one, a pawn three squares.
        assertEquals("e2e5", BoardRead.illegalMove(start, "rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR"))
        assertEquals("f1c4", BoardRead.illegalMove(start, "rnbqkbnr/pppppppp/8/8/2B5/8/PPPPPPPP/RNBQK1NR"))
        // Legal, the same position, or the other side's piece: not an illegal move.
        assertEquals(null, BoardRead.illegalMove(start, board(fenAfter("e4"))))
        assertEquals(null, BoardRead.illegalMove(start, board(start)))
        assertEquals(null, BoardRead.illegalMove(start, "rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR"))
        // A piece lifted, nothing put down: a move under way.
        assertEquals(null, BoardRead.illegalMove(start, "rnbqkbnr/pppppppp/8/8/8/8/PPPP1PPP/RNBQKBNR"))
        // Castling, king first: the king's step alone is not illegal.
        val castle = fenAfter("e4 e5 Nf3 Nc6 Bc4 Bc5")
        assertEquals(null, BoardRead.illegalMove(castle, "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ2KR"))
        // En passant, the pawn moved before the captured pawn is lifted.
        val ep = fenAfter("e4 a6 e5 d5")
        assertEquals(null, BoardRead.illegalMove(ep, "rnbqkbnr/1pp1pppp/p2P4/3pP3/8/8/PPPP1PPP/RNBQKBNR".replace("3pP3", "3p4")))
        // A pawn set on the last rank before it becomes a queen.
        val promote = "8/4P3/8/8/8/8/k7/4K3 w - - 0 1"
        assertEquals(null, BoardRead.illegalMove(promote, "4P3/8/8/8/8/8/k7/4K3"))
    }
}
