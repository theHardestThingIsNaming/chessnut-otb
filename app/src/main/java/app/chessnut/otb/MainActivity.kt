package app.chessnut.otb

import app.chessnut.otb.masters.MasterGameScreen
import app.chessnut.otb.masters.MasterGameViewModel
import app.chessnut.otb.mistakes.MistakesViewModel
import app.chessnut.otb.mistakes.MistakesScreen
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
import app.chessnut.otb.play.OnlineScreen
import app.chessnut.otb.play.OnlineViewModel
import app.chessnut.otb.play.PlayKind
import app.chessnut.otb.play.PlayScreen
import app.chessnut.otb.play.PlayViewModel
import app.chessnut.otb.puzzles.EndgamesScreen
import app.chessnut.otb.puzzles.EssentialScreen
import app.chessnut.otb.puzzles.EssentialViewModel
import app.chessnut.otb.puzzles.PlansScreen
import app.chessnut.otb.puzzles.PuzzleScreen
import app.chessnut.otb.puzzles.PuzzleViewModel
import app.chessnut.otb.review.ReviewScreen
import app.chessnut.otb.review.ReviewViewModel
import app.chessnut.otb.ui.ClubAtmosphere
import app.chessnut.otb.ui.ClubGamesTheme
import app.chessnut.otb.review.POSITION_PREFIX

class MainActivity : ComponentActivity() {
        private var sharedText by mutableStateOf<String?>(null)
        private var oauthRedirect by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as OtbApp
        sharedText = readShared(intent)
        oauthRedirect = readOAuth(intent)
        setContent {
            ClubGamesTheme {
                ClubAtmosphere {
                var screen by rememberSaveable { mutableStateOf("games") }
                var afterReview by rememberSaveable { mutableStateOf("games") }
                var playMode by rememberSaveable { mutableStateOf("bot") }
                /** Where a puzzle session returns to: an opening's page or the endgames list. */
                var afterPuzzles by rememberSaveable { mutableStateOf("games") }
                /** A plan to open straight away, when you came from its line. */
                var firstPlan by rememberSaveable { mutableStateOf<String?>(null) }
                val online = viewModel<OnlineViewModel>()

                LaunchedEffect(oauthRedirect) {
                    if (oauthRedirect != null) screen = "online"
                }
                val board = viewModel<BoardViewModel>()
                val play = viewModel<PlayViewModel>()
                val analysePosition = { fen: String, from: String ->
                    afterReview = from
                    screen = "review:$POSITION_PREFIX$fen"
                }
                val playPosition = { fen: String, white: Boolean ->
                    play.setStartPosition(fen, white)
                    playMode = "bot"
                    screen = "play"
                }

                LaunchedEffect(sharedText) {
                    val text = sharedText ?: return@LaunchedEffect
                    sharedText = null
                    when (val result = importPgn(app, text)) {
                        is Imported.Game -> screen = "review:${result.id}"
                        is Imported.Failed -> Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }

                BackHandler(enabled = screen != "games" && screen != "online") {
                    if (screen.startsWith("puzzles:")) {
                        screen = afterPuzzles
                    } else if (screen.startsWith("plans:") || screen.startsWith("master:")) {
                        screen = "play"
                    } else if (screen.startsWith("essential:")) {
                        screen = "endgames"
                    } else if (screen.startsWith("review:")) {
                        screen = afterReview
                        afterReview = "games"
                    } else {
                        screen = "games"
                    }
                }
                when {
                    screen == "board" -> BoardScreen(
                        board,
                        onBack = { screen = "games" },
                        onAnalyze = { id ->
                            afterReview = "board"
                            screen = "review:$id"
                        },
                        onPositionAnalyse = { analysePosition(it, "board") },
                        onPositionPlay = playPosition,
                    )
                    screen == "play" -> PlayScreen(
                        play,
                        onBack = { screen = "games" },
                        mode = if (playMode == "trainer") PlayKind.Trainer else PlayKind.Bot,
                        onAnalyse = { id ->
                            afterReview = "play"
                            screen = "review:$id"
                        },
                        onPuzzles = { key ->
                            afterPuzzles = "play"
                            screen = "puzzles:$key"
                        },
                        onMaster = { id -> screen = "master:$id" },
                        onPlans = { opening, white, plan ->
                            firstPlan = plan
                            screen = "plans:$opening:${if (white) "w" else "b"}"
                        },
                    )
                    screen == "online" -> OnlineScreen(
                        online,
                        onBack = { screen = "games" },
                        onAnalyse = { id ->
                            afterReview = "online"
                            screen = "review:$id"
                        },
                        oauthRedirect = oauthRedirect,
                        onOAuthHandled = { oauthRedirect = null },
                    )
                    screen == "about" -> AboutScreen(onBack = { screen = "games" })
                    screen.startsWith("master:") -> {
                        val id = screen.removePrefix("master:")
                        MasterGameScreen(
                            viewModel<MasterGameViewModel>(key = screen, factory = MasterGameViewModel.Factory(id)),
                            onBack = { screen = "play" },
                        )
                    }
                    screen.startsWith("plans:") -> {
                        val spec = screen.removePrefix("plans:")
                        PlansScreen(
                            opening = spec.substringBeforeLast(':'),
                            white = spec.endsWith(":w"),
                            firstPlan = firstPlan,
                            onBack = { screen = "play" },
                            onTry = { fen, white -> playPosition(fen, white) },
                        )
                    }
                    screen == "endgames" -> EndgamesScreen(
                        onBack = { screen = "games" },
                        onOpen = { key ->
                            afterPuzzles = "endgames"
                            screen = "puzzles:$key"
                        },
                        onEssential = { id -> screen = "essential:$id" },
                    )
                    screen.startsWith("essential:") -> {
                        val id = screen.removePrefix("essential:")
                        EssentialScreen(
                            viewModel<EssentialViewModel>(key = screen, factory = EssentialViewModel.Factory(id)),
                            onBack = { screen = "endgames" },
                        )
                    }
                    screen.startsWith("puzzles:") -> {
                        val key = screen.removePrefix("puzzles:")
                        PuzzleScreen(
                            viewModel<PuzzleViewModel>(key = screen, factory = PuzzleViewModel.Factory(key)),
                            onBack = { screen = afterPuzzles },
                        )
                    }
                    screen.startsWith("mistakes") -> {
                        val id = screen.removePrefix("mistakes").removePrefix(":").ifBlank { null }
                        MistakesScreen(
                            viewModel<MistakesViewModel>(key = screen, factory = MistakesViewModel.Factory(id)),
                            onBack = {
                                screen = if (id != null) "review:$id" else "games"
                            },
                            onOpenGame = { gameId ->
                                afterReview = screen
                                screen = "review:$gameId"
                            },
                        )
                    }
                    screen.startsWith("review:") -> {
                        val id = screen.removePrefix("review:")
                        ReviewScreen(
                            viewModel<ReviewViewModel>(key = id, factory = ReviewViewModel.Factory(id)),
                            onPractise = { screen = "mistakes:$id" },
                            onPlayFrom = playPosition,
                            onBack = {
                                screen = afterReview
                                afterReview = "games"
                            },
                        )
                    }
                    else -> GamesScreen(
                        app = app,
                        board = board,
                        onOpen = {
                            afterReview = "games"
                            screen = "review:$it"
                        },
                        onBoard = { screen = "board" },
                        onBot = { playMode = "bot"; screen = "play" },
                        onOnline = { screen = "online" },
                        onTrainer = { playMode = "trainer"; screen = "play" },
                        onAbout = { screen = "about" },
                        onMistakes = { screen = "mistakes" },
                        onEndgames = { screen = "endgames" },
                        onPositionAnalyse = { analysePosition(it, "games") },
                        onPositionPlay = playPosition,
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
        oauthRedirect = readOAuth(intent)
    }

    private fun readOAuth(intent: Intent): String? {
        val data = intent.data ?: return null
        if (data.scheme != "app.chessnut.otb") return null
        return data.toString()
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
