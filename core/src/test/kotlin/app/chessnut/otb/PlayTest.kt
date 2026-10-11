package app.chessnut.otb

import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.PositionEval
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningFilter
import app.chessnut.otb.openings.OpeningSide
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.play.BotChooser
import app.chessnut.otb.play.OpeningScores
import app.chessnut.otb.play.PlayClock
import app.chessnut.otb.play.PlayPosition
import app.chessnut.otb.play.SpokenMove
import app.chessnut.otb.play.TrainerGate
import app.chessnut.otb.play.botAcceptsDraw
import com.github.bhlangonijr.chesslib.Board
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayTest {
    @Test
    fun italianRepliesStayInTheOpening() {
        assertEquals(listOf("e4"), OpeningBook.replies(emptyList(), "Italian Game"))
        val played = listOf("e4", "e5", "Nf3", "Nc6")
        assertTrue("Bc4" in OpeningBook.replies(played, "Italian Game"))
        assertTrue(OpeningBook.featured().any { it.name == "Italian Game" })
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bc4"), OpeningBook.line("Italian Game")?.san)
        val italian = OpeningBook.rankedReplies(listOf("e4", "e5", "Nf3", "Nc6"), "Italian Game")
        assertTrue(italian.isNotEmpty())
        assertEquals("Bc4", italian.first())
    }

    @Test
    fun gambitsHaveTheirOwnSection() {
        val sections = OpeningCatalog.browse("")
        assertEquals(listOf("Openings", "Gambits", "Meme openings"), sections.map { it.title })
        val gambits = sections.first { it.title == "Gambits" }.names
        assertTrue(gambits.size >= 20, "gambits ${gambits.size}")
        // A gambit of a listed opening lives inside that opening instead.
        assertFalse("Italian Game: Evans Gambit" in gambits)
        assertTrue("Italian Game: Evans Gambit" in OpeningCatalog.variationsOf("Italian Game"))
        assertTrue("King's Gambit" in gambits)
        assertTrue("Sicilian Defense: Smith-Morra Gambit" in OpeningCatalog.variationsOf("Sicilian Defense"))
        assertTrue(gambits.all { OpeningBook.line(it) != null })
        assertEquals(OpeningSide.White, OpeningBook.side("Italian Game"))
        assertEquals(OpeningSide.Black, OpeningBook.side("Sicilian Defense"))
        assertEquals(OpeningSide.White, OpeningBook.side("King's Gambit"))
        assertEquals(OpeningSide.White, OpeningBook.side("King's Gambit Accepted"))
        assertEquals(OpeningSide.White, OpeningBook.side("Queen's Gambit Accepted"))
        assertEquals(OpeningSide.White, OpeningBook.side("Queen's Gambit Declined"))
        assertEquals(OpeningSide.Black, OpeningBook.side("King's Gambit Declined: Falkbeer Countergambit"))
        assertEquals(OpeningSide.Black, OpeningBook.side("Benko Gambit"))
        val falkbeer = "King's Gambit Declined: Falkbeer Countergambit"
        assertTrue("e4" in OpeningBook.replies(emptyList(), falkbeer))
        assertEquals("e2e4", PlayPosition.uciOfSan(Board().fen, "e4"))
        assertTrue("e5" in OpeningBook.replies(listOf("e4"), falkbeer))
        assertTrue(OpeningBook.replies(listOf("e3"), falkbeer).isEmpty())
        assertTrue(OpeningCatalog.matches("King's Gambit", 80, OpeningFilter(gambits = true)))
        assertFalse(OpeningCatalog.matches("Italian Game", null, OpeningFilter(gambits = true)))
        assertTrue(OpeningCatalog.matches("Italian Game", null, OpeningFilter(white = true)))
        assertFalse(OpeningCatalog.matches("Sicilian Defense", null, OpeningFilter(white = true)))
        assertTrue(OpeningCatalog.matches("French Defense", null, OpeningFilter(black = true)))
        assertTrue(OpeningCatalog.matches("London System", null, OpeningFilter(unrated = true)))
        assertTrue(OpeningCatalog.matches("London System", 0, OpeningFilter(unrated = true)))
        assertFalse(OpeningCatalog.matches("London System", 80, OpeningFilter(unrated = true)))
        assertTrue(OpeningCatalog.matches("Bongcloud Attack", null, OpeningFilter(memes = true)))
        assertFalse(OpeningCatalog.matches("Italian Game", null, OpeningFilter(memes = true)))
        assertTrue(OpeningCatalog.matches("Irish Gambit", null, OpeningFilter(gambits = true, memes = true)))
    }

    @Test
    fun highestAccuracyIsKeptForTheOpeningYouPlayed() {
        val line = OpeningBook.line("King's Gambit")
        assertNotNull(line)
        val game = SavedGame(
            id = "g",
            createdAt = 0,
            source = GameSource.Pgn,
            sanMoves = line.san,
            isComplete = true,
            stopReason = null,
            white = "Ciaran",
            black = "Opponent",
        )
        val analysis = GameAnalysis(
            "test",
            1,
            (0..line.san.size).map { ply ->
                PositionEval(
                    fen = "8/8/8/8/8/8/8/8 ${if (ply % 2 == 0) "w" else "b"} - - 0 1",
                    lines = listOf(EngineLine(Score(cp = 0), listOf("e2e4"), 1)),
                )
            },
        )
        val scores = OpeningScores.best(listOf(game to analysis), listOf("King's Gambit", "Italian Game"), "Ciaran")
        assertEquals(100, scores["King's Gambit"])
        assertFalse("Italian Game" in scores)
        assertTrue(OpeningScores.best(listOf(game to analysis), listOf("King's Gambit"), "Someone Else").isEmpty())
    }

    @Test
    fun botCanLeaveTheTopLine() {
        val lines = listOf(
            EngineLine(Score(cp = 40), listOf("e2e4"), 10),
            EngineLine(Score(cp = 30), listOf("d2d4"), 10),
        )
        assertEquals("d2d4", BotChooser.pick(lines, 1400, whiteToMove = true, Coin(0.99, 0)))
        assertEquals("e2e4", BotChooser.pick(lines, 2000, whiteToMove = true, Coin(0.1, 0)))
    }

    @Test
    fun trainerKeepsBookAndSolidMoves() {
        assertTrue(TrainerGate.judge(book = true, playedBest = false, winDrop = 80.0).accepted)
        assertTrue(TrainerGate.judge(book = false, playedBest = true, winDrop = 40.0).accepted)
        assertTrue(TrainerGate.judge(book = false, playedBest = false, winDrop = 9.0).accepted)
        assertTrue(TrainerGate.judge(book = false, playedBest = false, winDrop = 1.0).accepted)
        assertFalse(TrainerGate.judge(book = false, playedBest = false, winDrop = 15.0).accepted)
        assertFalse(TrainerGate.judge(book = false, playedBest = false, winDrop = 35.0).accepted)
    }

    @Test
    fun movesAreSpokenAsWords() {
        assertEquals("e 4", SpokenMove.phrase("e4"))
        assertEquals("knight f 6", SpokenMove.phrase("Nf6"))
        assertEquals("bishop takes c 6", SpokenMove.phrase("Bxc6"))
        assertEquals("e takes d 5", SpokenMove.phrase("exd5"))
        assertEquals("knight b to d 2", SpokenMove.phrase("Nbd2"))
        assertEquals("castle kingside", SpokenMove.phrase("O-O"))
        assertEquals("castle queenside", SpokenMove.phrase("O-O-O"))
        assertEquals("e 8 promotes to queen", SpokenMove.phrase("e8=Q"))
        assertEquals("queen h 5, check", SpokenMove.phrase("Qh5+"))
        assertEquals("queen takes f 7, checkmate", SpokenMove.phrase("Qxf7#"))
        assertEquals("e 4, checkmate", SpokenMove.phrase("e4", mate = true))
    }

    @Test
    fun aPlacementFindsTheMoveThatProducedIt() {
        val start = Board().fen
        val played = PlayPosition.after(start, "e2e4")
        assertNotNull(played)
        assertEquals("e4", played.first)
        assertEquals("e2e4", PlayPosition.uciMatching(start, PlayPosition.placementOf(played.second)))
        assertEquals("e2e4", PlayPosition.uciOfSan(start, "e4"))
    }

    @Test
    fun aBotClockLocksAfterTheFirstMoveAndFlags() {
        val clock = PlayClock(TimeControl.minutes(1, 1))
        assertTrue(clock.setControl(TimeControl.minutes(5, 3), now = 0))
        assertEquals(5 * 60_000L, clock.whiteMillis)
        clock.begin(now = 1_000)
        assertEquals(null, clock.moved(now = 2_000))
        assertEquals(1, clock.moveCount)
        assertFalse(clock.setControl(TimeControl.minutes(1), now = 3_000))
        assertEquals(5 * 60_000L - 1_000 + 3_000, clock.whiteMillis)
        assertTrue(clock.whiteToMove.not())
        val flagged = clock.poll(now = 3_000 + clock.blackMillis + 1)
        assertEquals("1-0", flagged)
        assertTrue(clock.flaggedBlack)
    }

    @Test
    fun aClockMoveCanBeRestoredWhenASlideContinues() {
        val clock = PlayClock(TimeControl.minutes(5, 3))
        clock.begin(now = 1_000)
        val before = clock.shot()
        clock.moved(now = 2_000)
        clock.moved(now = 3_000)
        clock.restore(before)
        assertEquals(0, clock.moveCount)
        assertTrue(clock.whiteToMove)
        assertEquals(5 * 60_000L, clock.whiteMillis)
        assertEquals(5 * 60_000L, clock.blackMillis)
    }

    @Test
    fun theBotTakesADrawOnlyWhenThePositionIsClose() {
        assertTrue(botAcceptsDraw(Score(cp = 40)))
        assertFalse(botAcceptsDraw(Score(cp = 200)))
        assertFalse(botAcceptsDraw(Score(mate = 3)))
    }

    private class Coin(private val doubleValue: Double, private val intValue: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = doubleValue
        override fun nextInt(until: Int): Int = intValue.coerceIn(0, until - 1)
    }
}
