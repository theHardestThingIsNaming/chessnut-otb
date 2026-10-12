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
}
