package app.chessnut.otb

import app.chessnut.otb.chess.StoredGameReconstructor
import app.chessnut.otb.lichess.ImportOutcome
import app.chessnut.otb.lichess.ImportedGame
import app.chessnut.otb.lichess.LichessImport
import app.chessnut.otb.lichess.LichessImporter
import app.chessnut.otb.pgn.GameFingerprint
import app.chessnut.otb.pgn.GameHeaders
import app.chessnut.otb.pgn.applyHeaders
import app.chessnut.otb.protocol.FakeTransport
import app.chessnut.otb.protocol.LedCodec
import app.chessnut.otb.protocol.PositionCodec
import app.chessnut.otb.protocol.StoredGameReader
import app.chessnut.otb.upload.FileGameMemory
import app.chessnut.otb.upload.ImportService
import app.chessnut.otb.upload.MemoryGameMemory
import app.chessnut.otb.upload.SubmitResult
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GameLogicTest {
    @Test
    fun ledCommandLightsC4AsEasyLinkDoes() {
        val c4 = LedCodec.encode(listOf("c4"))
        assertEquals(34, c4.size)
        assertEquals(listOf(0x0A, 0x08, 0, 0, 0, 0, 0x20, 0, 0, 0), c4.take(10).map { it.toInt() and 0xFF })
        assertEquals(
            listOf(0x0A, 0x08, 0, 0, 0, 0, 0, 0, 0, 0x80),
            LedCodec.encode(listOf("a1")).take(10).map { it.toInt() and 0xFF },
        )
        assertEquals(
            listOf(0x0A, 0x08, 1, 0, 0, 0, 0, 0, 0, 0),
            LedCodec.encode(listOf("h8")).take(10).map { it.toInt() and 0xFF },
        )
        assertEquals(listOf(0x0A, 0x08, 0, 0, 0, 0, 0, 0, 0, 0), LedCodec.OFF.take(10).map { it.toInt() and 0xFF })
    }

    @Test
    fun winnerHalfIsTheWinnersFourRanks() {
        val white = LedCodec.winnerHalf(towardWhite = true)
        assertEquals(32, white.size)
        assertTrue(white.all { it.last() in '1'..'4' })
        assertTrue("a1" in white && "h4" in white)
        assertTrue("e5" !in white)

        val black = LedCodec.winnerHalf(towardWhite = false)
        assertEquals(32, black.size)
        assertTrue(black.all { it.last() in '5'..'8' })
        assertTrue("a8" in black && "e5" in black)
        assertTrue("e4" !in black)
    }

    @Test
    fun boardBeepIsEasyLinkClBeep() {
        val packet = LedCodec.beep(1000, 200)
        assertEquals(34, packet.size)
        assertEquals(listOf(0x0B, 0x04, 0x03, 0xE8, 0x00, 0xC8), packet.take(6).map { it.toInt() and 0xFF })
    }

    @Test
    fun placementRoundTripsThroughTheBoardPacket() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        assertEquals(start, PositionCodec.decodePlacement(PositionCodec.encodePlacement(start)))
        val middlegame = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R"
        assertEquals(middlegame, PositionCodec.decodePlacement(PositionCodec.encodePlacement(middlegame)))
    }

    @Test
    fun reconstructsCastlingEnPassantAndPromotion() {
        assertReconstructs("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 O-O")
        assertReconstructs("1. e4 Nf6 2. e5 d5 3. exd6")
        assertReconstructs("1. e4 d5 2. exd5 c6 3. dxc6 Nf6 4. cxb7 Nc6 5. bxa8=Q")
    }

    @Test
    fun skipsRepeatedPlacementsAndPieceLifts() {
        val played = placementsOf("1. e4 e5")
        val withHeartbeat = listOf(played[0], played[0], played[1], played[1], played[2])
        val game = StoredGameReconstructor.reconstruct(withHeartbeat)
        assertTrue(game.isComplete)
        assertEquals(listOf("e4", "e5"), game.sanMoves)

        val liftE2 = "rnbqkbnr/pppppppp/8/8/8/8/PPPP1PPP/RNBQKBNR"
        val withLifts = listOf(played[0], liftE2, played[1], played[2], "8/8/8/8/8/8/8/8")
        val lifted = StoredGameReconstructor.reconstruct(withLifts)
        assertTrue(lifted.isComplete, lifted.stopReason)
        assertEquals(listOf("e4", "e5"), lifted.sanMoves)
    }

    @Test
    fun reconstructsARealAirRecordingWithPieceLifts() {
        val placements = javaClass.getResource("/chessnut-air-game.txt")!!.readText().trim().lines()
        val game = StoredGameReconstructor.reconstruct(placements)
        assertTrue(game.isComplete, game.stopReason ?: game.sanMoves.toString())
        assertEquals("e4", game.sanMoves.first())
        assertTrue("O-O" in game.sanMoves, game.sanMoves.toString())
    }

    @Test
    fun headersOverrideTagsAndEscapeQuotes() {
        val pgn = """
            [Event "Casual"]
            [White "Ada"]
            [Black "Grace"]

            1. e4 e5 *
        """.trimIndent()
        val rewritten = applyHeaders(
            pgn,
            GameHeaders(event = "League \"A\"", round = "3", result = "1-0"),
        )
        assertTrue(rewritten.contains("""[Event "League \"A\""]"""))
        assertTrue(rewritten.contains("""[Round "3"]"""))
        assertTrue(rewritten.contains("""[White "Ada"]"""))
        assertTrue(rewritten.trimEnd().endsWith("1. e4 e5 1-0"))
    }

    @Test
    fun fingerprintIgnoresHeaders() {
        val a = "[Event \"A\"]\n\n1. e4 e5 2. Nf3"
        val b = "[Event \"B\"]\n[White \"Me\"]\n\n1. e4 e5 2. Nf3"
        assertEquals(GameFingerprint.of(a), GameFingerprint.of(b))
    }

    @Test
    fun lichessBodyAndResponse() {
        val body = LichessImport.formBody("[Event \"A\"]\n\n1. e4")
        assertTrue(body.startsWith("pgn="))
        assertTrue(body.contains("%5BEvent+"))
        val game = LichessImport.parse("""{"id":"abcd1234","url":"https:\/\/lichess.org\/abcd1234"}""")
        assertEquals("abcd1234", game.id)
        assertEquals("https://lichess.org/abcd1234", game.url)
    }

    @Test
    fun readerDoesNotDeleteAndReturnsPlacements() = runTest {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
        val transport = FakeTransport()
        transport.enqueue(byteArrayOf(0x32, 0x01, 0x01))
        transport.enqueue(byteArrayOf(0x37, 0x01, 0xBE.toByte()))
        transport.enqueue(PositionCodec.encodePlacement(start))
        transport.enqueue(PositionCodec.encodePlacement(afterE4))
        transport.enqueue(byteArrayOf(0x37, 0x01, 0xED.toByte()))

        val result = StoredGameReader(transport).readGames()

        assertEquals(listOf(start, afterE4), result.games.single().placements)
        val written = transport.written.map { it.toList() }
        assertEquals(
            listOf(
                StoredGameReader.QUERY_COUNT,
                StoredGameReader.UPLOAD_MODE,
                StoredGameReader.READY,
                StoredGameReader.START,
                StoredGameReader.REALTIME,
            ).map { it.toList() },
            written,
        )
        assertFalse(transport.written.any { it[0] == 0x39.toByte() })
    }

    @Test
    fun uploadRemembersAGameAndQueuesWhenOffline() = runTest {
        val memory = MemoryGameMemory()
        var online = true
        val service = ImportService(
            importer = object : LichessImporter {
                override suspend fun import(pgn: String, token: String?): ImportOutcome {
                    if (!online) return ImportOutcome.Unavailable("No network.")
                    return ImportOutcome.Success(ImportedGame("abc", "https://lichess.org/abc"))
                }
            },
            memory = memory,
        )
        val pgn = "1. e4 e5\n"
        assertIs<SubmitResult.Uploaded>(service.submit(pgn, "token"))
        assertIs<SubmitResult.Duplicate>(service.submit(pgn, "token"))

        online = false
        val queued = service.submit("1. d4\n", "token")
        assertIs<SubmitResult.Queued>(queued)
        assertEquals(1, memory.pending().size)
    }

    @Test
    fun fileMemorySurvivesANewInstance() {
        val dir = File.createTempFile("otb", "").apply {
            delete()
            mkdirs()
        }
        val pgn = "1. e4 e5\n"
        FileGameMemory(dir).enqueue(pgn)
        assertEquals(listOf(pgn), FileGameMemory(dir).pending())
        FileGameMemory(dir).removePending(pgn)
        assertTrue(FileGameMemory(dir).pending().isEmpty())
    }

    private fun assertReconstructs(san: String) {
        val game = StoredGameReconstructor.reconstruct(placementsOf(san))
        assertTrue(game.isComplete, game.stopReason)
        val expected = MoveList().apply { loadFromSan(san) }
        assertEquals(expected.toSan().trim(), game.sanMoves.joinToString(" "))
    }

    private fun placementsOf(san: String): List<String> {
        val moves = MoveList().apply { loadFromSan(san) }
        val board = Board()
        val placements = mutableListOf(board.fen.substringBefore(' '))
        for (move in moves) {
            board.doMove(move)
            placements += board.fen.substringBefore(' ')
        }
        return placements
    }
}
