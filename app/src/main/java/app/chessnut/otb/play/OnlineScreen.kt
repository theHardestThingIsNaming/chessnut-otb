package app.chessnut.otb.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.lichess.LichessSignIn
import app.chessnut.otb.lichess.OnlineControl
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.FlagMark
import app.chessnut.otb.ui.HalfMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PlayerBar
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubTextButtonColors
import app.chessnut.otb.ui.KeepScreenOn
import app.chessnut.otb.ui.moveSquares
import app.chessnut.otb.ui.GameAction
import app.chessnut.otb.ui.ActionTone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnlineScreen(
    viewModel: OnlineViewModel,
    onBack: () -> Unit,
    onAnalyse: (String) -> Unit,
    oauthRedirect: String? = null,
    onOAuthHandled: () -> Unit = {},
) {
    KeepScreenOn()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.engage() }
    LaunchedEffect(oauthRedirect) {
        if (!oauthRedirect.isNullOrBlank()) {
            viewModel.finishLogin(oauthRedirect)
            onOAuthHandled()
        }
    }
    BackHandler {
        if (ui.live) confirmLeave = true else {
            viewModel.leave()
            onBack()
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this game?") },
            text = {
                Text(if (ui.canAbort) "Nobody has moved twice yet, so the game will be aborted." else "Lichess will count it as a resignation.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.resignAndLeave()
                    onBack()
                }) { Text(if (ui.canAbort) "Abort" else "Resign", color = Palette.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay", color = Palette.Accent) }
            },
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text(ui.username?.let { "Lichess · $it" } ?: "Lichess", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = clubAppBarColors(),
                navigationIcon = {
                    TextButton(onClick = {
                        if (ui.live) confirmLeave = true else {
                            viewModel.leave()
                            onBack()
                        }
                    }, colors = clubTextButtonColors()) { Text("Back", color = Palette.Accent) }
                },
                actions = {
                    if (ui.username != null && !ui.playing && !ui.seeking) {
                        TextButton(onClick = viewModel::logout, colors = clubTextButtonColors()) { Text("Log out", color = Palette.Accent) }
                    }
                },
            )
        },
    ) { padding ->
        if (ui.playing) {
            Table(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse)
        } else {
            Lobby(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lobby(ui: OnlineUi, viewModel: OnlineViewModel, modifier: Modifier) {
    val context = LocalContext.current
    LaunchedEffect(ui.username) {
        if (ui.username == null) LichessSignIn.warmUp(context)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ui.status, color = Palette.Text, style = MaterialTheme.typography.titleMedium)
        if (ui.username == null) {
            Text(
                "Lichess opens over this screen. Approve it once, and the next game starts without asking.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { LichessSignIn.open(context, viewModel.loginUrl()) },
                enabled = !ui.thinking,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.thinking) "Signing in…" else "Continue with Lichess") }
        } else {
            Text("10 minutes and longer.", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineControl.Presets.forEach { control ->
                    FilterChip(
                        selected = ui.control == control,
                        onClick = { viewModel.setControl(control) },
                        label = { Text(control.label) },
                        colors = clubChipColors(),
                        enabled = !ui.seeking,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.rated,
                    onCheckedChange = viewModel::setRated,
                    enabled = !ui.seeking,
                    colors = CheckboxDefaults.colors(checkedColor = Palette.Accent, uncheckedColor = Palette.AccentDim),
                )
                Text("Rated", color = Palette.Text)
            }
            ui.resumable.forEach { game ->
                Button(
                    onClick = { viewModel.resume(game.gameId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Resume vs ${game.opponent}" + if (game.isMyTurn) " · your move" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = if (ui.seeking) viewModel::cancelSeek else viewModel::seek,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.seeking) "Cancel" else "Find a game") }
            if (ui.seeking) {
                CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.CenterHorizontally), color = Palette.Accent, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun Table(ui: OnlineUi, viewModel: OnlineViewModel, modifier: Modifier, onAnalyse: (String) -> Unit) {
    val topMillis = if (ui.userWhite) ui.blackMillis else ui.whiteMillis
    val bottomMillis = if (ui.userWhite) ui.whiteMillis else ui.blackMillis
    val topRunning = if (ui.userWhite) ui.blackRunning else ui.whiteRunning
    val bottomRunning = if (ui.userWhite) ui.whiteRunning else ui.blackRunning
    val opponentLight = !ui.userWhite
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ui.status,
                color = Palette.Text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.thinking) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Accent, strokeWidth = 2.dp)
            }
        }
        PlayerBar(ui.opponent, opponentLight, ui.fen, topMillis, topRunning)
        ChessBoard(
            fen = ui.fen,
            flipped = !ui.userWhite,
            lastUci = ui.lastUci,
            selected = ui.selected,
            targets = ui.targets,
            arrows = listOfNotNull(Arrow.ofUci(ui.promptUci, Palette.ArrowBest)),
            highlights = moveSquares(ui.promptUci),
            onSquare = viewModel::tap,
        )
        PlayerBar(ui.username ?: "You", ui.userWhite, ui.fen, bottomMillis, bottomRunning)
        if (ui.finished == null) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAction(if (ui.drawOffered) "Accept draw" else "Draw", ui.userWhite, viewModel::draw, Modifier.weight(1f).fillMaxHeight()) {
                    HalfMark(it, Modifier.size(16.dp))
                }
                if (ui.drawOffered) {
                    GameAction("Decline", ui.userWhite, viewModel::declineDraw, Modifier.weight(1f).fillMaxHeight())
                }
                GameAction(
                    if (ui.canAbort) "Abort" else "Resign",
                    ui.userWhite,
                    viewModel::resign,
                    Modifier.weight(1f).fillMaxHeight(),
                    tone = ActionTone.Danger,
                ) { FlagMark(it, Modifier.size(16.dp)) }
            }
        } else {
            Button(
                onClick = viewModel::again,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Find a game") }
            Button(
                onClick = {
                    viewModel.archiveId()?.let(onAnalyse)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Analyse") }
            ui.gameId?.let { id ->
                val context = LocalContext.current
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lichess.org/$id")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open on Lichess") }
            }
        }
    }
}

