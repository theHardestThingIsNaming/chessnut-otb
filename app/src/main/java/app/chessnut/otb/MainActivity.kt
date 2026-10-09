package app.chessnut.otb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chessnut.otb.board.BoardScreen
import app.chessnut.otb.board.BoardViewModel
import app.chessnut.otb.games.PgnImport
import app.chessnut.otb.review.ReviewScreen
import app.chessnut.otb.review.ReviewViewModel
import app.chessnut.otb.ui.ClubAtmosphere
import app.chessnut.otb.ui.ClubGamesTheme

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as OtbApp
        sharedText = readShared(intent)
        setContent {
            ClubGamesTheme {
                ClubAtmosphere {
                var screen by rememberSaveable { mutableStateOf("games") }
                var afterReview by rememberSaveable { mutableStateOf("games") }

                LaunchedEffect(sharedText) {
                    val text = sharedText ?: return@LaunchedEffect
                    sharedText = null
                    when (val result = importPgn(app, text)) {
                        is Imported.Game -> screen = "review:${result.id}"
                        is Imported.Failed -> Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }

                BackHandler(enabled = screen != "games") {
                    if (screen.startsWith("review:")) {
                        screen = afterReview
                        afterReview = "games"
                    } else {
                        screen = "games"
                    }
                }
                when {
                    screen == "board" -> BoardScreen(
                        viewModel<BoardViewModel>(),
                        onBack = { screen = "games" },
                        onAnalyze = { id ->
                            afterReview = "board"
                            screen = "review:$id"
                        },
                    )
                    screen == "about" -> AboutScreen(onBack = { screen = "games" })
                    screen.startsWith("review:") -> {
                        val id = screen.removePrefix("review:")
                        ReviewScreen(
                            viewModel<ReviewViewModel>(key = id, factory = ReviewViewModel.Factory(id)),
                            onBack = {
                                screen = afterReview
                                afterReview = "games"
                            },
                        )
                    }
                    else -> GamesScreen(
                        app = app,
                        onOpen = {
                            afterReview = "games"
                            screen = "review:$it"
                        },
                        onBoard = { screen = "board" },
                        onAbout = { screen = "about" },
                        onImport = { text ->
                            when (val result = importPgn(app, text)) {
                                is Imported.Game -> {
                                    screen = "review:${result.id}"
                                    null
                                }
                                is Imported.Failed -> result.message
                            }
                        },
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = readShared(intent)
    }

    private sealed interface Imported {
        data class Game(val id: String) : Imported
        data class Failed(val message: String) : Imported
    }

    private fun importPgn(app: OtbApp, text: String): Imported = try {
        val game = PgnImport.parse(text, System.currentTimeMillis())
        if (app.store.addIfNew(game)) {
            app.refreshGames()
            app.analyse(game.id)
        }
        Imported.Game(game.id)
    } catch (e: IllegalArgumentException) {
        Imported.Failed(e.message ?: "That text is not a PGN this app can read.")
    }

    private fun readShared(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return it }
        val uri = intent.streamUri() ?: return null
        return contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_SHARE_BYTES)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private companion object {
        const val MAX_SHARE_BYTES = 256_000
    }
}
