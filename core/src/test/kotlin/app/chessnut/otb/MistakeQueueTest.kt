package app.chessnut.otb

import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.mistakes.Mistake
import app.chessnut.otb.mistakes.MistakeQueue
import app.chessnut.otb.repertoire.LineReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MistakeQueueTest {
    private fun m(game: String, ply: Int, cls: MoveClass = MoveClass.Mistake, drop: Double = 12.0, before: Double = 50.0) =
        Mistake(game, ply, "fen", "e4", null, cls, true, game, winBefore = before, winDrop = drop)

    @Test
    fun neverMoreThanFifteenAndNoGameFillsTheList() {
        // 73 new mistakes over 10 games: 15 to practise, at most 3 from any game.
        val all = (0 until 73).map { m("g${it % 10}", it) }
        val due = MistakeQueue.due(all, emptyMap(), now = 1000)
        assertEquals(15, due.size)
        assertTrue(due.groupBy { it.gameId }.values.all { it.size <= MistakeQueue.PER_GAME })
    }

    @Test
    fun mistakesUnderWayComeFirstThenTheWorstNewOnes() {
        val started = m("old", 1)
        val blunder = m("a", 2, MoveClass.Blunder, drop = 40.0)
        val bigMistake = m("b", 3, drop = 14.0)
        val smallMistake = m("c", 4, drop = 10.5)
        val reviews = mapOf(started.key to LineReview(0, 500))
        val due = MistakeQueue.due(listOf(smallMistake, bigMistake, blunder, started), reviews, now = 1000, limit = 3)
        assertEquals(listOf(started, blunder, bigMistake), due)
    }

    @Test
    fun notYetDueAndAlreadyLostAreLeftOut() {
        val later = m("a", 1)
        val lost = m("b", 2, before = 8.0)
        val unknown = Mistake("c", 3, "fen", "e4", null, MoveClass.Mistake, true, "c")
        val due = MistakeQueue.due(listOf(later, lost, unknown), mapOf(later.key to LineReview(1, 5000)), now = 1000)
        assertEquals(listOf(unknown), due)
    }
}
