package app.chessnut.otb

import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.AnalysisFormat
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.GameAnalyzer
import app.chessnut.otb.analysis.Material
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.PositionEval
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.engine.EngineProcess
import app.chessnut.otb.engine.UciEngine
import app.chessnut.otb.games.GameStore
import app.chessnut.otb.games.PgnImport
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.protocol.FakeChessnutBoard
import com.github.bhlangonijr.chesslib.Board
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/** Answers UCI like Stockfish would, with the first legal move as its line and a score from [cpFor]. */
private class FakeEngineProcess(private val cpFor: (String) -> Int = { 20 }) : EngineProcess {
    val sent = mutableListOf<String>()
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    override val output = flow
    private var fen = ""

    override suspend fun send(line: String) {
        sent += line
        when {
            line == "uci" -> {
                flow.emit("id name FakeFish 1")
                flow.emit("uciok")
            }
            line == "isready" -> flow.emit("readyok")
            line.startsWith("position fen ") -> fen = line.removePrefix("position fen ")
            line.startsWith("go") -> {
                val moves = Board().apply { loadFromFen(fen) }.legalMoves().map { it.toString().lowercase() }
                moves.take(3).forEachIndexed { index, move ->
                    flow.emit("info depth 12 seldepth 14 multipv ${index + 1} score cp ${cpFor(fen) - index * 30} nodes 1000 pv $move")
                }
                if (!line.contains("infinite")) flow.emit("bestmove ${moves.firstOrNull() ?: "(none)"}")
            }
            line == "stop" -> flow.emit("bestmove 0000")
        }
    }

    override fun close() = Unit
}

class AnalysisTest {
    @Test
    fun infoScoresAreTurnedToWhitesPointOfView() {
        val line = "info depth 20 seldepth 28 multipv 2 score cp 35 nodes 12 nps 1 pv e7e5 g1f3"
        val (index, parsed) = UciEngine.parseInfo(line, sideToMoveIsWhite = false)!!
        assertEquals(2, index)
        assertEquals(Score(cp = -35), parsed.score)
        assertEquals(listOf("e7e5", "g1f3"), parsed.uci)
        assertEquals(Score(mate = 3), UciEngine.parseInfo("info depth 9 score mate 3 pv a1a8", true)!!.second.score)
        assertNull(UciEngine.parseInfo("info depth 9 score cp 10 lowerbound pv a1a8", true))
    }

    @Test
    fun analyserMarksCheckmateAndReturnsEveryPosition() = runTest {
        val process = FakeEngineProcess()
        val engine = UciEngine(process)
        val moves = listOf("f3", "e5", "g4", "Qh4#")

        val analysis = GameAnalyzer(engine, nodes = 1000).analyse(moves)

        assertEquals("FakeFish 1", analysis.engine)
        assertEquals(5, analysis.positions.size)
        assertEquals(Terminal.Checkmate, analysis.positions.last().terminal)
        assertEquals(Score.WhiteMated, analysis.positions.last().score)
        assertEquals(3, analysis.positions.first().lines.size)
        assertTrue(process.sent.contains("go nodes 1000"))
    }

    @Test
    fun accuracyIsHighForSteadyPlayAndBlundersAreFlagged() {
        val replay = ChessLines.replay(listOf("e4", "e5", "Qh5", "Ke7"))
        val cps = listOf(20, 20, 20, 20, 900)
        val analysis = GameAnalysis(
            "test",
            1,
            replay.fens.mapIndexed { i, fen -> PositionEval(fen, listOf(EngineLine(Score(cp = cps[i]), listOf("a2a3"), 1))) },
        )

        val report = AccuracyCalculator.report(listOf("e4", "e5", "Qh5", "Ke7"), replay.uci, analysis)

        assertEquals(MoveClass.Blunder, report.moves[3].classification)
        assertTrue(report.whiteAccuracy!! > 95, "white ${report.whiteAccuracy}")
        assertTrue(report.blackAccuracy!! < report.whiteAccuracy!!)
        assertEquals(100.0, AccuracyCalculator.moveAccuracy(50.0, 60.0))
    }

    @Test
    fun soundSacrificesAreBrilliantAndCloseMovesAreExcellent() {
        assertEquals(MoveClass.Best, AccuracyCalculator.classify(0.0, playedBest = true))
        assertEquals(MoveClass.Brilliant, AccuracyCalculator.classify(0.0, playedBest = true, sacrifice = true))
        assertEquals(MoveClass.Brilliant, AccuracyCalculator.classify(8.0, playedBest = false, sacrifice = true))
        assertEquals(MoveClass.Blunder, AccuracyCalculator.classify(40.0, playedBest = false, sacrifice = true))
        assertEquals(MoveClass.Excellent, AccuracyCalculator.classify(1.0, playedBest = false))
        assertEquals(MoveClass.Good, AccuracyCalculator.classify(6.0, playedBest = false))

        val gambit = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
        assertTrue(AccuracyCalculator.isSacrifice(gambit, "f2f4"))
        assertFalse(AccuracyCalculator.isSacrifice(gambit, "g1f3"))
        assertFalse(AccuracyCalculator.isSacrifice("3qk3/8/8/8/8/8/8/3QK3 w - - 0 1", "d1d8"))
        val checked = Board()
        listOf("e2e4", "e7e5", "f2f4", "e5f4", "d1h5", "g8f6", "h5e5").forEach { uci ->
            checked.doMove(checked.legalMoves().first { it.toString().lowercase() == uci })
        }
        assertFalse(AccuracyCalculator.isSacrifice(checked.fen, "f8e7"))

        val after = Board().apply {
            loadFromFen(gambit)
            doMove(com.github.bhlangonijr.chesslib.move.Move(com.github.bhlangonijr.chesslib.Square.F2, com.github.bhlangonijr.chesslib.Square.F4))
        }.fen
        val analysis = GameAnalysis(
            "test",
            1,
            listOf(
                PositionEval(gambit, listOf(EngineLine(Score(cp = 30), listOf("f2f4"), 1))),
                PositionEval(after, listOf(EngineLine(Score(cp = 20), listOf("e5f4"), 1))),
            ),
        )
        val report = AccuracyCalculator.report(listOf("f4"), listOf("f2f4"), analysis)
        assertEquals(MoveClass.Brilliant, report.moves.single().classification)
    }

    @Test
    fun materialNamesTheExtraPiecesAndTheirPoints() {
        val even = Material.balance(Board().fen)
        assertEquals(0, even.points)
        assertTrue(even.even)

        val pawn = Material.balance("rnbqkbnr/ppppppp1/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals(1, pawn.points)
        assertEquals("P", pawn.whiteExtra)
        assertEquals("", pawn.blackExtra)

        val set = Material.balance("4k3/8/8/8/8/8/P7/Q3K3 w - - 0 1")
        assertEquals(10, set.points)
        assertEquals("QP", set.whiteExtra)

        val blackPawn = Material.balance("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPP1/RNBQKBNR w KQkq - 0 1")
        assertEquals(-1, blackPawn.points)
        assertEquals("p", blackPawn.blackExtra)
    }

    @Test
    fun liveAnalysisStopsTheSearchWhenCancelled() = runTest {
        val process = FakeEngineProcess()
        val engine = UciEngine(process)
        engine.start()

        val first = engine.analyseLive(Board().fen, multiPv = 3).first()

        assertTrue(first.isNotEmpty())
        assertEquals("stop", process.sent.last())
        val second = engine.analyseLive(Board().fen, multiPv = 1).take(1).toList()
        assertEquals(1, second.size)
    }

    @Test
    fun passingHandsTheMoveToTheOtherSide() {
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1", ChessLines.passFen(afterE4))
        val checked = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
        assertNull(ChessLines.passFen(checked))
    }

    @Test
    fun pvLinesBecomeSan() {
        assertEquals(listOf("e4", "e5", "Nf3"), ChessLines.sanOf(Board().fen, listOf("e2e4", "e7e5", "g1f3")))
        assertEquals(listOf("e4"), ChessLines.sanOf(Board().fen, listOf("e2e4", "e2e4")))
    }

    @Test
    fun analysisTextRoundTrips() {
        val analysis = GameAnalysis(
            "Stockfish 18",
            1000,
            listOf(
                PositionEval(Board().fen, listOf(EngineLine(Score(cp = 31), listOf("e2e4", "e7e5"), 20), EngineLine(Score(mate = -2), emptyList(), 5))),
                PositionEval("8/8/8/8/8/8/8/k1K5 b - - 0 1", emptyList(), Terminal.Stalemate),
            ),
        )
        assertEquals(analysis, AnalysisFormat.read(AnalysisFormat.write(analysis)))
    }

    @Test
    fun storeKeepsOneCopyOfEachGameAndItsAnalysis() {
        val dir = File.createTempFile("games", "").apply {
            delete()
            mkdirs()
        }
        val placements = FakeChessnutBoard.placementsFromSan("1. e4 e5 2. Nf3")
        val game = SavedGame.fromBoard(placements, now = 5, date = "2026.10.09")
        val store = GameStore(dir)

        assertTrue(store.addIfNew(game))
        assertFalse(GameStore(dir).addIfNew(game.copy(createdAt = 9)))
        assertEquals(game, GameStore(dir).load(game.id))
        assertEquals(listOf("e4", "e5", "Nf3"), store.all().single().sanMoves)

        val analysis = GameAnalysis("x", 1, listOf(PositionEval(Board().fen, emptyList())))
        store.saveAnalysis(game.id, analysis)
        assertEquals(analysis, GameStore(dir).loadAnalysis(game.id))
        store.delete(game.id)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun openingNameFollowsTheLineAndOffersAGambit() {
        val start = OpeningBook.view(emptyList())
        assertEquals(null, start.name)
        val afterE4 = OpeningBook.view(listOf("e4"))
        assertTrue(afterE4.gambits.any { it.name.contains("gambit", ignoreCase = true) })

        val italian = OpeningBook.view(listOf("e4", "e5", "Nf3", "Nc6", "Bc4"))
        assertEquals("Italian Game", italian.name)
        assertEquals("C50", italian.eco)

        val scotch = OpeningBook.view(listOf("e4", "e5", "Nf3", "Nc6", "d4"))
        assertTrue(scotch.name!!.contains("Scotch"))
    }

    @Test
    fun namedFoldersHoldGamesAndALabelIsKept() {
        val dir = File.createTempFile("games", "").apply {
            delete()
            mkdirs()
        }
        val game = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan("1. e4 e5"), now = 5, date = "2026.10.09")
        val store = GameStore(dir)
        store.addIfNew(game)
        assertEquals("Tuesday", store.createFolder("Tuesday"))
        store.moveToFolder(game.id, "Tuesday")
        assertEquals("Tuesday", store.all().single().folder)
        assertTrue(File(dir, "folders/Tuesday/${game.id}").isDirectory)
        store.save(store.load(game.id)!!.copy(label = "Rapid vs Sam"))
        assertEquals("Rapid vs Sam", store.load(game.id)!!.title)
        store.moveToGames(game.id)
        assertEquals("", store.all().single().folder)
        store.deleteFolder("Tuesday")
        assertTrue(store.folders().isEmpty())
    }

    @Test
    fun anOldArchiveFolderIsPickedUpAsANamedFolder() {
        val dir = File.createTempFile("games", "").apply {
            delete()
            mkdirs()
        }
        val game = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan("1. d4"), now = 5, date = "2026.10.09")
        val legacy = File(dir, "archive/${game.id}").apply { mkdirs() }
        val props = java.util.Properties().apply {
            setProperty("id", game.id)
            setProperty("createdAt", "5")
            setProperty("source", "Board")
            setProperty("moves", "d4")
            setProperty("complete", "false")
            setProperty("date", "2026.10.09")
            setProperty("result", "*")
        }
        File(legacy, "game.properties").writer().use { props.store(it, null) }
        val store = GameStore(dir)
        assertEquals(listOf("Archive"), store.folders())
        assertEquals("Archive", store.all().single().folder)
    }

    @Test
    fun pgnImportDropsCommentsAndVariations() {
        val pgn = """
            [Event "Club"]
            [White "Ada"]
            [Black "?"]
            [Result "1-0"]

            1. e4 {best by test} e5 (1... c5 2. Nf3) 2. Nf3 $1 Nc6 3. Bb5 a6?! 1-0
        """.trimIndent()
        val game = PgnImport.parse(pgn, now = 1)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), game.sanMoves)
        assertEquals("Ada", game.white)
        assertEquals("", game.black)
        assertEquals("1-0", game.result)
        assertNotNull(game.id)
    }

    @Test
    fun unfiledGamesPastFiveOrThirtyDaysMoveToEarlier() {
        val dir = File.createTempFile("games", "").apply {
            delete()
            mkdirs()
        }
        val store = GameStore(dir)
        val now = 40L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val lines = listOf("1. e4", "1. d4", "1. c4", "1. Nf3", "1. e4 e5", "1. d4 d5")
        lines.forEachIndexed { index, san ->
            val game = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan(san), now = now - index * 1000, date = "2026.10.09")
            store.addIfNew(game)
        }
        val filed = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan("1. e4 c5"), now = now - 31 * day, date = "2026.09.01")
        store.addIfNew(filed)
        store.moveToFolder(filed.id, "Tuesday")
        val old = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan("1. d4 Nf6"), now = now - 31 * day, date = "2026.09.01")
        store.addIfNew(old)

        store.shelveUnfiled(now)

        val loose = store.all().filter { it.folder.isBlank() }
        assertEquals(5, loose.size)
        assertTrue(loose.all { now - it.createdAt <= 30L * day })
        assertEquals("Earlier", store.load(old.id)!!.folder)
        assertEquals("Tuesday", store.load(filed.id)!!.folder)
        val sixth = store.all().first { it.sanMoves == listOf("d4", "d5") }
        assertEquals("Earlier", sixth.folder)
    }
}
