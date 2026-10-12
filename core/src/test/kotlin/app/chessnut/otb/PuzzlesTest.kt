package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.puzzles.Puzzle
import app.chessnut.otb.puzzles.PuzzleReview
import app.chessnut.otb.puzzles.PuzzleRun
import app.chessnut.otb.puzzles.PuzzleSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PuzzleReviewTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aCleanSolveComesBackOnceThenFinishes() {
        val now = 1_000_000L
        val first = PuzzleReview.grade(null, clean = true, now)
        assertEquals(now + 3 * day, first.dueAt)
        val done = PuzzleReview.grade(first, clean = true, now + 3 * day)
        assertEquals(Long.MAX_VALUE, done.dueAt)
        val missed = PuzzleReview.grade(first, clean = false, now + 3 * day)
        assertEquals(now + 4 * day, missed.dueAt)
    }
}

class PuzzlesTest {
    @Test
    fun everyShippedPuzzleSolvesCleanly() {
        val openings = OpeningCatalog.browse("").first().names.map { it to (OpeningBook.side(it) == OpeningSide.White) }
        val keys = openings.map { (name, white) -> PuzzleSets.middlegameKey(name, white) } +
            PuzzleSets.ENDGAMES.keys.map(PuzzleSets::endgameKey)
        for (key in keys) {
            val set = PuzzleSets.of(key)
            // An opening with no tactic export yet is skipped.
            // The English has few games that start in your lines; the rest have dozens.
            if (set.isEmpty()) continue
            assertTrue(set.size >= 5, "$key has ${set.size}")
            for (puzzle in set) {
                val run = PuzzleRun(puzzle)
                run.start()
                while (!run.solved) {
                    val answer = run.answer(run.expected!!)
                    assertIs<PuzzleRun.Answer.Right>(answer, "${puzzle.id} in $key")
                }
            }
        }
        // Endgames are the shorter club-level ones: rating 1800 and up, no five-move races.
        for (type in PuzzleSets.ENDGAMES.keys) {
            val set = PuzzleSets.of(PuzzleSets.endgameKey(type))
            assertTrue(set.size in 8..16, "$type has ${set.size}")
            assertTrue(set.all { it.rating >= 1800 && "veryLong" !in it.themes }, type)
        }
        // Middlegame tactics are solved from your opening's side.
        assertTrue(PuzzleSets.of(PuzzleSets.middlegameKey("Scotch Game", true)).all { it.solverWhite })
        assertTrue(PuzzleSets.of(PuzzleSets.middlegameKey("Pirc Defense", false)).none { it.solverWhite })
    }

    @Test
    fun aWrongMoveIsTurnedBackAndAnyMateCounts() {
        // Scholar's mate set-up: after 1...Nf6??, 2.Qxf7# is the answer.
        val fen = ChessLines.replay("e4 e5 Bc4 Nc6 Qh5".split(" ")).fens.last()
        val run = PuzzleRun(Puzzle("t", fen, listOf("g8f6", "h5f7"), 600, listOf("mateIn1"), "Test"))
        run.start()
        assertIs<PuzzleRun.Answer.Wrong>(run.answer("a2a3"))
        assertEquals(PuzzleRun.Answer.Right(null, solved = true), run.answer("h5f7"))
        assertEquals(listOf("Mate in 1"), run.puzzle.ideas)
    }
}
