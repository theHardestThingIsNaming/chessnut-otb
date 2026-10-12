package app.chessnut.otb

import app.chessnut.otb.live.LiveEnd
import app.chessnut.otb.live.LiveEvent
import app.chessnut.otb.live.LiveGameTracker
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.protocol.FakeChessnutBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LiveGameTest {
    @Test
    fun startPositionWithSplitEmptyFilesStillStartsTheClocks() {
        val tracker = LiveGameTracker()
        tracker.onPlacement("rnbqkbnr/pppppppp/44/44/44/44/PPPPPPPP/RNBQKBNR", 0)
        assertTrue(tracker.snapshot(0).started)
        assertFalse(tracker.snapshot(0).ended)
    }

    @Test
    fun clocksCountDownAndGainIncrementAfterAMove() {
        val tracker = LiveGameTracker(TimeControl(initialMillis = 60_000, incrementMillis = 5_000))
        val start = FakeChessnutBoard.START_PLACEMENT
        tracker.onPlacement(start, 0)
        assertEquals(60_000, tracker.snapshot(0).whiteMillis)
        assertEquals(60_000, tracker.snapshot(10_000).whiteMillis)
        assertEquals(60_000, tracker.snapshot(10_000).blackMillis)

        tracker.onPlacement(after("1. e4"), 10_000)
        val afterWhite = tracker.snapshot(10_000)
        assertEquals(listOf("e4"), afterWhite.sanMoves)
        assertEquals(60_000, afterWhite.whiteMillis)
        assertEquals(60_000, afterWhite.blackMillis)
        assertFalse(afterWhite.toMoveWhite)

        tracker.onPlacement(after("1. e4 e5"), 13_000)
        val afterBlack = tracker.snapshot(13_000)
        assertEquals(listOf("e4", "e5"), afterBlack.sanMoves)
        assertEquals(60_000, afterBlack.whiteMillis)
        assertEquals(62_000, afterBlack.blackMillis)
        assertTrue(afterBlack.toMoveWhite)
    }

    @Test
    fun whitesClockDoesNotRunUntilTheFirstMove() {
        val tracker = LiveGameTracker(TimeControl(initialMillis = 3_000, incrementMillis = 0))
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        assertIs<LiveEvent.Ignored>(tracker.poll(60_000))
        assertFalse(tracker.snapshot(60_000).ended)
        assertEquals(3_000, tracker.snapshot(60_000).whiteMillis)
    }

    @Test
    fun flagEndsTheGameOnTheClockTick() {
        val tracker = LiveGameTracker(TimeControl(initialMillis = 3_000, incrementMillis = 0))
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement(after("1. e4"), 1_000)
        val flag = tracker.poll(1_000 + 60_000)
        assertIs<LiveEvent.Finished>(flag)
        assertEquals("1-0", flag.result)
        assertTrue(tracker.snapshot(1_000 + 60_000).flaggedBlack)
        assertEquals(0, tracker.snapshot(1_000 + 60_000).blackMillis)
    }

    @Test
    fun noClockNeverFlags() {
        val tracker = LiveGameTracker(TimeControl.Untimed)
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement(after("1. e4"), 1_000)
        assertIs<LiveEvent.Ignored>(tracker.poll(10 * 60 * 60_000L))
        tracker.onPlacement(after("1. e4 e5"), 10 * 60 * 60_000L)
        val snapshot = tracker.snapshot(20 * 60 * 60_000L)
        assertFalse(snapshot.ended)
        assertEquals(listOf("e4", "e5"), snapshot.sanMoves)
        assertEquals("No clock", TimeControl.fromLabel("No clock").label)
    }

    @Test
    fun droppingAPawnOnE5IsIllegal() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        val onE5 = "rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR"
        val event = tracker.onPlacement(onE5, 1_000)
        assertIs<LiveEvent.Illegal>(event)
        assertTrue("e2" in event.squares)
        assertTrue("e5" in event.squares)
    }

    @Test
    fun aCheckingMoveIsMarkedCheck() {
        val tracker = LiveGameTracker()
        var event: LiveEvent = LiveEvent.Ignored
        var now = 0L
        for (placement in FakeChessnutBoard.placementsFromSan("1. e4 e5 2. Qh5 Nc6 3. Qxe5+")) {
            event = tracker.onPlacement(placement, now)
            now += 1_000
        }
        assertIs<LiveEvent.Move>(event)
        assertTrue(event.check)
        assertTrue(event.san.endsWith("+"))
        assertTrue("e8" in event.squares)
        assertTrue("e5" in event.squares)
    }

    @Test
    fun pieceLiftsDoNotCountAsMoves() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement("rnbqkbnr/pppppppp/8/8/8/8/PPPP1PPP/RNBQKBNR", 1_000)
        assertEquals(emptyList(), tracker.snapshot(1_000).sanMoves)
        val event = tracker.onPlacement(after("1. e4"), 2_000)
        assertIs<LiveEvent.Move>(event)
        assertEquals("e4", event.san)
    }

    @Test
    fun takingTheKingOffTheBoardIsCheckmate() {
        val tracker = LiveGameTracker()
        var now = 0L
        for (placement in FakeChessnutBoard.placementsFromSan("1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6")) {
            tracker.onPlacement(placement, now)
            now += 1_000
        }
        // Queen captures the king on e8 (over-the-board mate, not a legal chess capture).
        val kingCaptured = "r1bqQb1r/pppp1ppp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR"
        val event = tracker.onPlacement(kingCaptured, now)
        assertIs<LiveEvent.Finished>(event)
        assertEquals("1-0", event.result)
        assertEquals(LiveEnd.Checkmate, tracker.snapshot(now).end)
        assertTrue(tracker.snapshot(now).ended)
    }

    @Test
    fun aFlickerWhileTheKingIsUpIsNotCheckmate() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        val flicker = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPPPPPP/RNBQ1BNR"
        val event = tracker.onPlacement(flicker, 1_000)
        assertIs<LiveEvent.Illegal>(event)
        assertEquals(null, tracker.snapshot(1_000).end)
        assertFalse(tracker.snapshot(1_000).ended)
        assertEquals(emptyList(), tracker.snapshot(1_000).sanMoves)
    }

    @Test
    fun kingsTouchingAreNotCheckmate() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        val touching = "rnbq1bnr/pppppppp/8/4k3/4K3/8/PPPPPPPP/RNBQ1BNR"
        val event = tracker.onPlacement(touching, 1_000)
        assertIs<LiveEvent.Illegal>(event)
        assertFalse(tracker.snapshot(1_000).ended)
    }

    @Test
    fun liftingTheKingIsNotCheckmate() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        val kingLifted = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR"
        val event = tracker.onPlacement(kingLifted, 1_000)
        assertIs<LiveEvent.Ignored>(event)
        assertEquals(null, tracker.snapshot(1_000).end)
        assertFalse(tracker.snapshot(1_000).ended)
    }

    @Test
    fun whiteResigns() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement(after("1. e4"), 1_000)
        val event = tracker.resign(white = true, now = 2_000)
        assertIs<LiveEvent.Finished>(event)
        assertEquals("0-1", event.result)
        assertEquals(LiveEnd.WhiteResign, tracker.snapshot(2_000).end)
        assertTrue(tracker.snapshot(2_000).ended)
    }

    @Test
    fun blackAcceptsWhitesDrawOffer() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement(after("1. e4"), 1_000)
        tracker.offerDraw(white = true, now = 1_500)
        assertTrue(tracker.snapshot(1_500).whiteOfferedDraw)
        val event = tracker.offerDraw(white = false, now = 2_000)
        assertIs<LiveEvent.Finished>(event)
        assertEquals("1/2-1/2", event.result)
        assertEquals(LiveEnd.Draw, tracker.snapshot(2_000).end)
    }

    @Test
    fun checkmateFinishesTheGame() {
        val tracker = LiveGameTracker()
        var now = 0L
        for (placement in FakeChessnutBoard.placementsFromSan("1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#")) {
            val event = tracker.onPlacement(placement, now)
            now += 1_000
            if (event is LiveEvent.Finished) {
                assertEquals("1-0", event.result)
                assertEquals("Qxf7#", event.sanMoves.last())
                assertTrue("e8" in event.squares)
                assertTrue("f7" in event.squares)
                return
            }
        }
        error("expected mate")
    }

    @Test
    fun returningToTheStartSavesThePreviousGame() {
        val tracker = LiveGameTracker()
        tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 0)
        tracker.onPlacement(after("1. e4"), 1_000)
        tracker.onPlacement(after("1. e4 e5"), 2_000)
        val event = tracker.onPlacement(FakeChessnutBoard.START_PLACEMENT, 3_000)
        assertIs<LiveEvent.Finished>(event)
        assertEquals(listOf("e4", "e5"), event.sanMoves)
        assertEquals("*", event.result)
        assertTrue(tracker.snapshot(3_000).started)
        assertEquals(emptyList(), tracker.snapshot(3_000).sanMoves)
    }

    @Test
    fun realAirPlacementsPlayAsALiveGame() {
        val tracker = LiveGameTracker()
        val placements = javaClass.getResource("/chessnut-air-game.txt")!!.readText().trim().lines()
        placements.forEachIndexed { i, p -> tracker.onPlacement(p, i * 1_000L) }
        val snap = tracker.snapshot(placements.size * 1_000L)
        assertEquals("e4", snap.sanMoves.first())
        assertTrue("O-O" in snap.sanMoves, snap.sanMoves.toString())
        assertTrue(snap.sanMoves.size >= 10)
    }

    private fun after(san: String): String = FakeChessnutBoard.placementsFromSan(san).last()
}
