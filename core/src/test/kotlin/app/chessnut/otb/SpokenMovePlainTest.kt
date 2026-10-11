package app.chessnut.otb

import app.chessnut.otb.play.SpokenMove
import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenMovePlainTest {
    @Test
    fun namesThePieceAndTheSquare() {
        assertEquals("pawn to e6", SpokenMove.plain("e6"))
        assertEquals("rook to f7", SpokenMove.plain("Rf7"))
        assertEquals("pawn takes d5", SpokenMove.plain("exd5"))
        assertEquals("bishop takes f7", SpokenMove.plain("Bxf7+"))
        assertEquals("knight to d7", SpokenMove.plain("Nbd7"))
        assertEquals("castling kingside", SpokenMove.plain("O-O"))
        assertEquals("pawn to e8, promoting to queen", SpokenMove.plain("e8=Q"))
    }
}
