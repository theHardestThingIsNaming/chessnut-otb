package app.chessnut.otb.board

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.live.LiveEnd
import app.chessnut.otb.live.LiveSnapshot
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.ui.BoardFrame
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubOutlinedButtonColors
import app.chessnut.otb.ui.clubPanel
import app.chessnut.otb.ui.clubTextButtonColors
import app.chessnut.otb.ui.squareIndex

private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(model: BoardViewModel, onBack: () -> Unit, onAnalyze: (String) -> Unit = {}) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) model.scan() else permissionMessage = "Bluetooth permission is needed to find the board."
    }

    fun startScan() {
        val missing = bluetoothPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (model.selected.needsPermissions && missing) permissionLauncher.launch(bluetoothPermissions) else model.scan()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text("Board") },
                colors = clubAppBarColors(),
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Palette.Gold) } },
                actions = {
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, model.logText())
                        context.startActivity(Intent.createChooser(share, "Share board log"))
                    }) { Text("Share log", color = Palette.Gold) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.connected == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.options.forEachIndexed { index, option ->
                        FilterChip(
                            selected = index == ui.optionIndex,
                            onClick = { model.select(index) },
                            colors = clubChipColors(),
                            label = { Text(option.label, color = if (index == ui.optionIndex) Palette.OnGold else Palette.Text) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = ::startScan, enabled = !ui.scanning && ui.busy == null) { Text("Find board") }
                    if (ui.scanning) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.height(24.dp).width(24.dp))
                    }
                }
                ui.found.forEach { board ->
                    Column(
                        Modifier.fillMaxWidth().clubPanel().clickable { model.connect(board) }.padding(14.dp),
                    ) {
                        Text(board.name.trim(), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text("${board.address}  ${board.rssi?.let { "$it dBm" }.orEmpty()}", color = Palette.TextMuted)
                    }
                }
            } else {
                LiveGamePanel(
                    ui,
                    modifier = Modifier.weight(1f),
                    onDisconnect = model::disconnect,
                    onTimeControl = model::setTimeControl,
                    onResignWhite = model::resignWhite,
                    onResignBlack = model::resignBlack,
                    onDrawWhite = model::offerDrawWhite,
                    onDrawBlack = model::offerDrawBlack,
                    onAnalyze = { ui.lastSavedGameId?.let(onAnalyze) },
                    onConfirmClear = { confirmClear = true },
                    onCount = model::countGames,
                    onReadOne = model::readOne,
                    onReadAll = model::readAll,
                    log = ui.log,
                )
            }
            ui.busy?.let { Text("$it…", color = Palette.Gold) }
            (permissionMessage ?: ui.message)?.let { Text(it, color = Palette.Text) }
            if (ui.connected == null) LogView(ui.log, Modifier.weight(1f))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear games from the board?") },
            text = {
                Text("Each game is saved on this phone first, then deleted from the board with the 39 01 00 command. Games that fail to save stay on the board.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    model.readAllAndClear()
                }) { Text("Read and clear", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LiveGamePanel(
    ui: BoardUiState,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit,
    onTimeControl: (TimeControl) -> Unit,
    onResignWhite: () -> Unit,
    onResignBlack: () -> Unit,
    onDrawWhite: () -> Unit,
    onDrawBlack: () -> Unit,
    onAnalyze: () -> Unit,
    onConfirmClear: () -> Unit,
    onCount: () -> Unit,
    onReadOne: () -> Unit,
    onReadAll: () -> Unit,
    log: List<String>,
) {
    val live = ui.live
    val lastMove = live.lastUci?.takeIf { it.length >= 4 }?.let {
        squareIndex(it.substring(0, 2)) to squareIndex(it.substring(2, 4))
    }
    val playing = live.started && !live.ended
    val showActions = !live.ended
    var tools by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ui.connected!!.name.trim(),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ui.batteryPercent?.let { percent ->
                Text("$percent%", color = if (percent <= 15) Palette.Error else Palette.Gold, fontWeight = FontWeight.SemiBold)
            }
            TimeControlChip(
                live.control,
                enabled = !playing || live.sanMoves.isEmpty(),
                onSelect = onTimeControl,
            )
            TextButton(onClick = onDisconnect, colors = clubTextButtonColors()) { Text("Disconnect", color = Palette.Gold) }
        }
        PlayerStrip(
            name = "Black",
            millis = live.blackMillis,
            running = playing && !live.toMoveWhite,
            flagged = live.flaggedBlack,
            light = false,
            facingAway = true,
            playing = showActions,
            drawLabel = drawLabel(offered = live.blackOfferedDraw, opponentOffered = live.whiteOfferedDraw),
            onDraw = onDrawBlack,
            onResign = onResignBlack,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.Center) {
            val size = minOf(maxWidth, maxHeight)
            BoardFrame(Modifier.width(size)) {
                ChessBoard(
                    fen = ui.livePlacement ?: "8/8/8/8/8/8/8/8",
                    lastMove = lastMove,
                )
            }
        }
        Text(
            statusLine(live),
            color = if (live.ended) Palette.Gold else Palette.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (live.ended) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PlayerStrip(
            name = "White",
            millis = live.whiteMillis,
            running = playing && live.toMoveWhite,
            flagged = live.flaggedWhite,
            light = true,
            facingAway = false,
            playing = showActions,
            drawLabel = drawLabel(offered = live.whiteOfferedDraw, opponentOffered = live.blackOfferedDraw),
            onDraw = onDrawWhite,
            onResign = onResignWhite,
        )
        if (live.ended && ui.lastSavedGameId != null) {
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) { Text("Analyze this game") }
        }
        TextButton(onClick = { tools = !tools }, modifier = Modifier.align(Alignment.CenterHorizontally), colors = clubTextButtonColors()) {
            Text(if (tools) "Hide board tools" else "Board tools", color = Palette.Gold)
        }
        if (tools) {
            val idle = ui.busy == null
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCount, enabled = idle, colors = clubOutlinedButtonColors()) { Text("Count games") }
                OutlinedButton(onClick = onReadOne, enabled = idle, colors = clubOutlinedButtonColors()) { Text("Read one game") }
                Button(onClick = onReadAll, enabled = idle) { Text("Read all games") }
                TextButton(onClick = onConfirmClear, enabled = idle) { Text("Read and clear board", color = Palette.Error) }
            }
            LogView(log, Modifier.height(140.dp))
        }
    }
}

private fun drawLabel(offered: Boolean, opponentOffered: Boolean): String = when {
    opponentOffered -> "Accept"
    offered -> "Offered"
    else -> "Draw"
}

private fun statusLine(live: LiveSnapshot): String = when (live.end) {
    LiveEnd.Checkmate -> "Checkmate. ${live.result}"
    LiveEnd.Stalemate -> "Stalemate. ${live.result}"
    LiveEnd.Draw -> "Draw. ${live.result}"
    LiveEnd.WhiteResign -> "White resigns. ${live.result}"
    LiveEnd.BlackResign -> "Black resigns. ${live.result}"
    LiveEnd.WhiteFlag, LiveEnd.BlackFlag -> "Flag. ${live.result}"
    null -> when {
        live.ended -> "Game over ${live.result}"
        live.started && live.sanMoves.isEmpty() -> "White to move"
        live.started -> moveLine(live.sanMoves)
        else -> "Set up the starting position to start ${live.control.label}."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeControlChip(current: TimeControl, enabled: Boolean, onSelect: (TimeControl) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }) {
        Text(
            "${current.label} ▾",
            color = if (enabled) Palette.Gold else Palette.TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .menuAnchor()
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.GoldDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            TimeControl.Presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerStrip(
    name: String,
    millis: Long,
    running: Boolean,
    flagged: Boolean,
    light: Boolean,
    facingAway: Boolean,
    playing: Boolean,
    drawLabel: String,
    onDraw: () -> Unit,
    onResign: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (light) Palette.Paper else Color(0xFF1A1610)
    val labelColor = if (light) Color(0xFF5C5348) else Palette.TextMuted
    val clockColor = when {
        flagged -> Palette.Error
        running -> if (light) Color(0xFF8A5A12) else Palette.Gold
        light -> Palette.Ink
        else -> Palette.Text
    }
    val border = when {
        flagged -> Palette.Error
        running -> Palette.Gold
        light -> Color(0xFFC4B8A4)
        else -> Palette.Gold
    }
    val drawActive = drawLabel != "Draw"
    val body = @Composable {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(background)
                .border(2.dp, border, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1.2f).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(name, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(formatClock(millis), fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = clockColor)
            }
            if (playing) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(border.copy(alpha = 0.7f)))
                StripAction(
                    label = drawLabel,
                    onClick = onDraw,
                    fill = when {
                        drawActive -> Palette.Gold
                        light -> Color(0xFFF7F0E4)
                        else -> Color(0xFF2A241C)
                    },
                    text = when {
                        drawActive -> Palette.OnGold
                        light -> Palette.Ink
                        else -> Palette.Gold
                    },
                    modifier = Modifier.weight(0.85f).fillMaxHeight(),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(border.copy(alpha = 0.7f)))
                StripAction(
                    label = "Resign",
                    onClick = onResign,
                    fill = if (light) Color(0xFFF3D4C8) else Color(0xFF3A221C),
                    text = Palette.Error,
                    modifier = Modifier.weight(0.85f).fillMaxHeight(),
                )
            }
        }
    }
    if (facingAway) Box(Modifier.fillMaxWidth().rotate(180f)) { body() } else body()
}

@Composable
private fun StripAction(
    label: String,
    onClick: () -> Unit,
    fill: Color,
    text: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(fill).clickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun formatClock(millis: Long): String {
    val ms = millis.coerceAtLeast(0)
    if (ms < 10_000) return String.format(Locale.ROOT, "0:%04.1f", ms / 1000.0)
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun moveLine(sans: List<String>): String = buildString {
    sans.forEachIndexed { i, san ->
        if (i % 2 == 0) append("${i / 2 + 1}. ")
        append(san).append(' ')
    }
}

@Composable
private fun LogView(lines: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Box(modifier.fillMaxWidth().padding(bottom = 12.dp).clubPanel()) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp), state = listState) {
            items(lines) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.TextMuted)
            }
        }
    }
}
