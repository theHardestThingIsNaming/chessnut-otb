package app.chessnut.otb

import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.chessnut.otb.games.SavedGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sharing a game from anywhere it is shown: a Lichess link first, the PGN when there is no link. */
object GameShare {
    fun link(context: Context, app: OtbApp, game: SavedGame, scope: CoroutineScope) {
        if (app.lichessUrl(game.id) == null) Toast.makeText(context, "Putting the game on Lichess…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) { app.shareLink(game) }
            result.onSuccess { url ->
                val result = if (game.result == "*") "" else " (${game.result})"
                send(context, "${game.title}$result\n$url", "Share game link")
            }.onFailure { e ->
                Toast.makeText(context, "${e.message} Sharing the PGN instead.", Toast.LENGTH_LONG).show()
                pgn(context, game)
            }
        }
    }

    fun pgn(context: Context, game: SavedGame) = send(context, game.toPgn(), "Share PGN")

    private fun send(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, title))
    }
}
