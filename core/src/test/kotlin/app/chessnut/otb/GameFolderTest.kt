package app.chessnut.otb

import app.chessnut.otb.games.GameStore
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.protocol.FakeChessnutBoard
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameFolderTest {
    @Test
    fun aDotFolderNameCanNeverEatAGame() {
        val dir = File.createTempFile("games", "").apply {
            delete()
            mkdirs()
        }
        val game = SavedGame.fromBoard(FakeChessnutBoard.placementsFromSan("1. e4 e5"), now = 5, date = "2026.10.09")
        val store = GameStore(dir)
        store.addIfNew(game)
        assertNull(store.createFolder(".."))
        assertNull(store.createFolder("."))
        store.moveToFolder(game.id, "..")
        store.moveToFolder(game.id, ".")
        assertNotNull(store.load(game.id), "the game is still there")
    }
}
