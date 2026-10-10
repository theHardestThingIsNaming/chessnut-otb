package app.chessnut.otb.play

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.lichess.LichessSignIn
import app.chessnut.otb.lichess.OnlineControl
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BoardFrame
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.FlagMark
import app.chessnut.otb.ui.HalfMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubTextButtonColors
import app.chessnut.otb.ui.formatClock
import app.chessnut.otb.ui.squareIndex
import app.chessnut.otb.ui.KeepScreenOn

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
                TextButton(onClick = { confirmLeave = false }) { Text("Stay", color = Palette.Gold) }
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
                    }, colors = clubTextButtonColors()) { Text("Back", color = Palette.Gold) }
                },
                actions = {
                    if (ui.username != null && !ui.playing && !ui.seeking) {
                        TextButton(onClick = viewModel::logout, colors = clubTextButtonColors()) { Text("Log out", color = Palette.Gold) }
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
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
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
                    colors = CheckboxDefaults.colors(checkedColor = Palette.Gold, uncheckedColor = Palette.GoldDim),
                )
                Text("Rated", color = Palette.Text)
            }
            ui.resumable.forEach { game ->
                Button(
                    onClick = { viewModel.resume(game.gameId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Gold),
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
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.seeking) "Cancel" else "Find a game") }
            if (ui.seeking) {
                CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.CenterHorizontally), color = Palette.Gold, strokeWidth = 2.dp)
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Gold, strokeWidth = 2.dp)
            }
        }
        BoardFrame(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ChessBoard(
                fen = ui.fen,
                flipped = !ui.userWhite,
                lastMove = ui.lastUci?.let { uci ->
                    if (uci.length < 4) null else squareIndex(uci.substring(0, 2)) to squareIndex(uci.substring(2, 4))
                },
                selected = ui.selected,
                targets = viewModel.targets(),
                arrows = listOfNotNull(Arrow.ofUci(ui.promptUci, Palette.ArrowBest)),
                highlights = moveSquares(ui.promptUci),
                onSquare = viewModel::tap,
            )
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlineClock(ui.opponent, topMillis, topRunning, opponentLight, Modifier.weight(1f).fillMaxHeight())
            OnlineClock("You", bottomMillis, bottomRunning, ui.userWhite, Modifier.weight(1f).fillMaxHeight())
        }
        if (ui.finished == null) {
            val youLight = ui.userWhite
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(
                    if (ui.drawOffered) "Accept draw" else "Draw",
                    { HalfMark(if (youLight) Palette.Ink else Palette.Gold, Modifier.size(16.dp)) },
                    viewModel::draw,
                    fill = if (youLight) Color(0xFFF7F0E4) else Color(0xFF2A241C),
                    text = if (youLight) Palette.Ink else Palette.Gold,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                )
                if (ui.drawOffered) {
                    Action(
                        "Decline",
                        {},
                        viewModel::declineDraw,
                        fill = if (youLight) Color(0xFFF7F0E4) else Color(0xFF2A241C),
                        text = if (youLight) Palette.Ink else Palette.Gold,
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                    )
                }
                Action(
                    if (ui.canAbort) "Abort" else "Resign",
                    { FlagMark(Palette.Error, Modifier.size(16.dp)) },
                    viewModel::resign,
                    fill = if (youLight) Color(0xFFF3D4C8) else Color(0xFF3A221C),
                    text = Palette.Error,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                )
            }
        } else {
            Button(
                onClick = viewModel::again,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Find a game") }
            Button(
                onClick = {
                    viewModel.archiveId()?.let(onAnalyse)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Gold),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Analyse") }
            ui.gameId?.let { id ->
                val context = LocalContext.current
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lichess.org/$id")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Gold),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open on Lichess") }
            }
        }
    }
}

@Composable
private fun OnlineClock(name: String, millis: Long, running: Boolean, light: Boolean, modifier: Modifier) {
    val background = if (light) Palette.Paper else Color(0xFF1A1610)
    val clockColor = when {
        running -> if (light) Color(0xFF8A5A12) else Palette.Gold
        light -> Palette.Ink
        else -> Palette.Text
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(2.dp, if (running) Palette.Gold else Palette.GoldDim, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, color = if (light) Color(0xFF5C5348) else Palette.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatClock(millis), fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = clockColor, maxLines = 1)
    }
}

@Composable
private fun Action(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    fill: Color,
    text: Color,
    modifier: Modifier,
) {
    Box(modifier.background(fill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, color = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

private fun moveSquares(uci: String?): Set<Int> {
    if (uci == null || uci.length < 4) return emptySet()
    return setOf(squareIndex(uci.substring(0, 2)), squareIndex(uci.substring(2, 4)))
}
