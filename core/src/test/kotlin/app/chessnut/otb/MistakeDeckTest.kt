package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.PositionEval
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.mistakes.MistakeDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MistakeDeckTest {
    // 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6?? 4. Qxf7#: Black's third move is the blunder.
    private val moves = listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#")
    private val fens = ChessLines.replay(moves).fens
    private val scores = listOf(30, 30, 0, 20, 30, 30, 1000, 1000)
    private val best = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g7g6", "h5f7", null)
    private val analysis = GameAnalysis(
        "test",
        0,
        fens.mapIndexed { i, fen ->
            PositionEval(fen, listOfNotNull(best[i]?.let { EngineLine(Score(cp = scores[i]), listOf(it), 20) }))
        },
    )

    private fun game(white: String, black: String, practise: String = "") =
        SavedGame("g1", 0, GameSource.Board, moves, true, null, white = white, black = black, practise = practise)

    @Test
    fun collectsTheBlunderWithTheEnginesMove() {
        val found = MistakeDeck.collect(game("", ""), analysis, emptyList(), anySide = true)
        val blunder = found.single { it.playedSan == "Nf6" }
        assertEquals(MoveClass.Blunder, blunder.classification)
        assertEquals("g7g6", blunder.bestUci)
        assertEquals("3... Nf6", blunder.moveLabel)
        assertFalse(blunder.whiteToMove)
        assertEquals("f1c4", blunder.lastUci)
    }

    @Test
    fun onlyMySideWhenMyNameIsAPlayer() {
        assertTrue(MistakeDeck.collect(game("Ciaran", "Bob"), analysis, listOf("Ciaran")).none { !it.whiteToMove })
        assertTrue(MistakeDeck.collect(game("Bob", "Ciaran"), analysis, listOf("Ciaran")).any { it.playedSan == "Nf6" })
        // An online game names the Lichess account, not the app's name.
        val online = MistakeDeck.collect(game("JoseRobertoGomes", "CaptainTimeout"), analysis, listOf("Ciaran", "CaptainTimeout"))
        assertTrue(online.isNotEmpty() && online.all { !it.whiteToMove })
    }

    @Test
    fun aBoardGameAddsNothingUntilYouSayWhichSideYouWere() {
        assertTrue(MistakeDeck.collect(game("", ""), analysis, listOf("Ciaran")).isEmpty())
        assertTrue(MistakeDeck.collect(game("", "", practise = MistakeDeck.PRACTISE_WHITE), analysis, emptyList()).none { !it.whiteToMove })
        assertTrue(MistakeDeck.collect(game("", "", practise = MistakeDeck.PRACTISE_BLACK), analysis, emptyList()).any { it.playedSan == "Nf6" })
        assertTrue(MistakeDeck.collect(game("", "", practise = MistakeDeck.PRACTISE_BOTH), analysis, emptyList()).any { it.playedSan == "Nf6" })
    }

    @Test
    fun answersCloseToBestCount() {
        assertTrue(MistakeDeck.solved("g7g6", "g7g6", null, null))
        assertTrue(MistakeDeck.solved("d8e7", "g7g6", 48.0, 45.0))
        assertFalse(MistakeDeck.solved("a7a6", "g7g6", 48.0, 10.0))
    }
}

