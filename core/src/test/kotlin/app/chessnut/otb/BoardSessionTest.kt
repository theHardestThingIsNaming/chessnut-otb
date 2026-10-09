package app.chessnut.otb

import app.chessnut.otb.protocol.ChessnutSession
import app.chessnut.otb.protocol.ChessnutUuids
import app.chessnut.otb.protocol.FakeChessnutBoard
import app.chessnut.otb.protocol.PositionCodec
import app.chessnut.otb.protocol.StoredGameReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

class BoardSessionTest {
    private suspend fun CoroutineScope.open(board: FakeChessnutBoard, gap: Long = 0): ChessnutSession =
        ChessnutSession(board, this, writeGap = gap.milliseconds).also { it.open("FA:KE") }

    @Test
    fun readsEveryGameAndLeavesThemOnTheBoard() = runTest {
        val board = FakeChessnutBoard()
        val session = backgroundScope.open(board)

        val result = StoredGameReader(session).readGames()

        assertEquals(2, result.boardCount)
        assertEquals(board.stored, result.games.map { it.placements })
        assertFalse(result.repeatedGame)
        assertEquals(2, board.stored.size)
        assertFalse(board.written.any { it[0] == 0x39.toByte() })
        assertEquals(
            listOf(ChessnutUuids.FEN_NOTIFY, ChessnutUuids.OP_RESPONSE, ChessnutUuids.FILE_NOTIFY),
            board.subscribed,
        )
    }

    @Test
    fun stopsWhenTheBoardRepeatsAGame() = runTest {
        val board = FakeChessnutBoard(advancesWithoutDelete = false)
        val session = backgroundScope.open(board)

        val result = StoredGameReader(session).readGames()

        assertTrue(result.repeatedGame)
        assertEquals(1, result.games.size)
        assertEquals(2, board.stored.size)
    }

    @Test
    fun clearsOnlyAfterEachGameIsSaved() = runTest {
        val board = FakeChessnutBoard(advancesWithoutDelete = false)
        val first = board.stored[0]
        val session = backgroundScope.open(board)
        val saved = mutableListOf<List<String>>()

        val result = StoredGameReader(session).readGames(clearAfterSave = true) { saved += it.placements }

        assertEquals(2, result.games.size)
        assertEquals(first, saved.first())
        assertTrue(board.stored.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheGameOnTheBoard() = runTest {
        val board = FakeChessnutBoard(advancesWithoutDelete = false)
        val session = backgroundScope.open(board)

        assertFailsWith<IllegalStateException> {
            StoredGameReader(session).readGames(clearAfterSave = true) { error("disk full") }
        }

        assertEquals(2, board.stored.size)
        assertEquals(StoredGameReader.REALTIME.toList(), board.written.last().toList())
    }

    @Test
    fun gameFramesOnTheFenCharacteristicAreReadInUploadMode() = runTest {
        val board = FakeChessnutBoard(gameFramesOn = ChessnutUuids.FEN_NOTIFY)
        val session = backgroundScope.open(board)

        val result = StoredGameReader(session).readGames()

        assertEquals(board.stored, result.games.map { it.placements })
    }

    @Test
    fun livePositionReassemblesSplitNotifications() = runTest {
        val board = FakeChessnutBoard()
        val log = mutableListOf<String>()
        val session = ChessnutSession(board, backgroundScope, writeGap = 0.milliseconds, log = { log += it })
            .also { it.open("FA:KE") }
        val placement = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
        val frame = PositionCodec.encodePlacement(placement)

        board.emit(ChessnutUuids.FEN_NOTIFY, frame.copyOfRange(0, 20))
        board.emit(ChessnutUuids.FEN_NOTIFY, frame.copyOfRange(20, frame.size))
        session.startLive()
        testScheduler.runCurrent()

        assertEquals(FakeChessnutBoard.START_PLACEMENT, session.livePlacement.value, log.joinToString("\n"))
        board.emit(ChessnutUuids.FEN_NOTIFY, frame)
        testScheduler.runCurrent()
        assertEquals(placement, session.livePlacement.value)
    }

    @Test
    fun writesAreSpacedByTheGap() = runTest {
        val board = FakeChessnutBoard()
        val session = backgroundScope.open(board, gap = 200)
        val start = testScheduler.currentTime

        repeat(3) { session.write(StoredGameReader.REALTIME) }

        assertTrue(testScheduler.currentTime - start >= 400)
    }

    @Test
    fun setLedsWritesTheClassicAirCommand() = runTest {
        val board = FakeChessnutBoard()
        val session = backgroundScope.open(board)

        session.setLeds(listOf("c4"))

        val last = board.written.last()
        assertEquals(34, last.size)
        assertEquals(0x0A, last[0].toInt() and 0xFF)
        assertEquals(0x08, last[1].toInt() and 0xFF)
        assertEquals(0x20, last[6].toInt() and 0xFF)
    }

    @Test
    fun beepWritesTheClassicAirBuzzerCommand() = runTest {
        val board = FakeChessnutBoard()
        val session = backgroundScope.open(board)

        session.beep(1000, 200)

        val last = board.written.last()
        assertEquals(0x0B, last[0].toInt() and 0xFF)
        assertEquals(0x04, last[1].toInt() and 0xFF)
        assertEquals(0x03, last[2].toInt() and 0xFF)
        assertEquals(0xE8, last[3].toInt() and 0xFF)
    }

    @Test
    fun batteryReplyUpdatesThePercent() = runTest {
        val board = FakeChessnutBoard()
        val session = backgroundScope.open(board)

        session.requestBattery()
        testScheduler.runCurrent()

        assertEquals(75, session.batteryPercent.value)
    }

    @Test
    fun aBoardWithoutTheFileServiceStillConnects() = runTest {
        val board = FakeChessnutBoard().apply { missingCharacteristics = setOf(ChessnutUuids.FILE_NOTIFY) }
        val session = backgroundScope.open(board)

        assertFalse(session.fileNotifyAvailable)
    }
}
