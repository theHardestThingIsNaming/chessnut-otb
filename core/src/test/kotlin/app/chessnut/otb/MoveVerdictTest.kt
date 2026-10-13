package app.chessnut.otb

import app.chessnut.otb.analysis.Score
import app.chessnut.otb.repertoire.MoveVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveVerdictTest {
    @Test
    fun aMoveDownTheListWithWhatItGaveAway() {
        // White to move: best keeps +0.6, the move played leaves -0.3.
        assertEquals(
            "You played Bd3, the engine's 4th choice: 0.9 pawns worse than Nc3.",
            MoveVerdict.describe("Bd3", "Nc3", 4, Score(cp = 60), Score(cp = -30), whiteMoved = true),
        )
        // Black to move: scores are White's view, so Black's best -0.5 and a move leaving +0.7 lose 1.2.
        assertEquals(
            "You played h6, outside the engine's top 5: 1.2 pawns worse than d5.",
            MoveVerdict.describe("h6", "d5", null, Score(cp = -50), Score(cp = 70), whiteMoved = false),
        )
    }

    @Test
    fun matesAreNamedNotCounted() {
        assertEquals(
            "You played Qe2, the engine's 2nd choice. That misses a forced mate.",
            MoveVerdict.describe("Qe2", "Qh7#", 2, Score(mate = 1), Score(cp = 400), whiteMoved = true),
        )
        assertEquals(
            "You played g4, outside the engine's top 5. That allows mate.",
            MoveVerdict.describe("g4", "Kg2", null, Score(cp = 0), Score(mate = -2), whiteMoved = true),
        )
    }
}
