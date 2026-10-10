package app.chessnut.otb

import app.chessnut.otb.analysis.EngineLine
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.PositionEval
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.games.DigestStore
import app.chessnut.otb.games.GameDigest
import app.chessnut.otb.games.GameKeeper
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.GameStore
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.mistakes.Mistake
import app.chessnut.otb.play.OpeningScores
import app.chessnut.otb.play.ScoredGame
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameKeeperTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun tempDir(): File = File.createTempFile("keeper", "").apply {
        delete()
        mkdirs()
    }

    private fun game(san: String, createdAt: Long) = SavedGame(
        id = SavedGame.idFor(san.split(' '), emptyList()),
        createdAt = createdAt,
        source = GameSource.Play,
        sanMoves = san.split(' '),
        isComplete = true,
        stopReason = null,
        white = "Me",
        black = "Bot",
    )

    private fun flatAnalysis(plies: Int) = GameAnalysis(
        "test",
        1,
        (0..plies).map { ply ->
            PositionEval(
                fen = "8/8/8/8/8/8/8/8 ${if (ply % 2 == 0) "w" else "b"} - - 0 1",
                lines = listOf(EngineLine(Score(cp = 0), listOf("e2e4"), 1)),
            )
        },
    )

    @Test
    fun keepsTheNewestFiveAndFiledGamesAndDigestsTheRest() {
        val root = tempDir()
        val store = GameStore(File(root, "games"))
        val digests = DigestStore(File(root, "digests"))
        val newest = listOf("e4 e5", "d4 d5", "c4 e5", "Nf3 d5", "e4 c5").mapIndexed { i, san -> game(san, now - i * 1000) }
        newest.forEach(store::addIfNew)
        val analysed = game("e4 e5 f4", now - 10 * day).also(store::addIfNew)
        store.saveAnalysis(analysed.id, flatAnalysis(3))
        val waiting = game("d4 Nf6", now - 2 * day).also(store::addIfNew)
        val stale = game("c4 c5", now - 20 * day).also(store::addIfNew)
        val guarded = game("g3 d5", now - 30 * day).also(store::addIfNew)
        val filed = game("b3 e5", now - 40 * day).also(store::addIfNew)
        store.moveToFolder(filed.id, "Keepers")

        val removed = GameKeeper(store, digests).tidy(now, listOf("Me"), protect = setOf(guarded.id))

        assertEquals(setOf(analysed.id, stale.id), removed.toSet())
        assertTrue(newest.all { store.contains(it.id) })
        assertNotNull(store.load(waiting.id), "unanalysed games wait for their analysis")
        assertNotNull(store.load(guarded.id))
        assertEquals("Keepers", store.load(filed.id)!!.folder)
        assertNull(store.load(analysed.id))

        val kept = digests.all().single()
        assertEquals(analysed.id, kept.gameId)
        val scores = OpeningScores.bestOf(listOfNotNull(kept.score), listOf("King's Gambit"), "Me")
        assertEquals(100, scores["King's Gambit"])
    }

    @Test
    fun digestsRoundTripTheirMistakes() {
        val digests = DigestStore(tempDir())
        val mistake = Mistake(
            gameId = "abcdef0123456789",
            ply = 7,
            fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
            playedSan = "f6",
            bestUci = "b8c6",
            classification = MoveClass.Blunder,
            whiteToMove = false,
            gameTitle = "Me vs Bot",
            lastUci = "g1f3",
        )
        val digest = GameDigest(
            "abcdef0123456789",
            now,
            ScoredGame("Me", "Bot", listOf("e4", "e5", "Nf3"), 91.5, null),
            listOf(mistake),
        )
        digests.save(digest)
        assertEquals(digest, digests.all().single())
        assertEquals("abcdef0123456789:7", digests.all().single().mistakes.single().key)
    }
}
