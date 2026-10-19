package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.play.PlayPosition
import app.chessnut.otb.openings.Guess
import app.chessnut.otb.openings.MasterGames
import app.chessnut.otb.repertoire.OpeningTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MasterGamesTest {
    @Test
    fun everyGameReachesItsLineAndHasItsNotes() {
        assertTrue(MasterGames.all.isNotEmpty())
        for (game in MasterGames.all) {
            val replay = ChessLines.replay(game.moves)
            assertEquals(game.moves.size, replay.uci.size, "${game.id} replays")
            // The game reaches the line's last position, with the same side to move, at its start.
            val lineEnd = ChessLines.replay(game.line).fens.last().split(' ').take(2)
            assertEquals(lineEnd, replay.fens[game.start].split(' ').take(2), "${game.id} reaches its line at ply ${game.start}")
            if (game.opening.startsWith("Repertoire: ")) {
                val drilled = WhiteRepertoireTest.lines(game.opening.removePrefix("Repertoire: "))
                assertTrue(game.line in drilled, "${game.id} follows a line of its repertoire chapter")
            } else {
                assertTrue(game.line in OpeningTree.lines(game.opening, game.userWhite), "${game.id} follows a drilled line")
            }
            assertEquals(game, MasterGames.forLine(game.opening, game.userWhite, game.line))
            assertTrue(game.end in game.start + 1..game.moves.size, "${game.id} has a stretch to guess")
            assertTrue(game.yourPlies.isNotEmpty())
            for (ply in game.start until game.end) {
                assertNotNull(game.notes[ply], "${game.id} has a note at ply $ply")
            }
            for ((ply, moves) in game.good) {
                for (uci in moves) assertTrue(PlayPosition.after(replay.fens[ply], uci) != null, "${game.id} good move $uci at $ply")
            }
            for ((ply, uci) in game.threats) {
                val passed = ChessLines.passFen(replay.fens[ply + 1])
                assertNotNull(passed)
                assertTrue(PlayPosition.after(passed, uci) != null, "${game.id} threat $uci after ply $ply")
            }
        }
    }

    @Test
    fun theMastersMoveMatchesAndAnEqualOneIsGood() {
        val game = MasterGames.all.first { it.good.isNotEmpty() }
        val (ply, good) = game.good.entries.first()
        val master = ChessLines.replay(game.moves).uci[ply]
        assertEquals(Guess.Match, MasterGames.judge(game, ply, master, master))
        assertEquals(Guess.Good, MasterGames.judge(game, ply, good.first(), master))
        assertEquals(Guess.Miss, MasterGames.judge(game, ply, "zzzz", master))
    }
}
