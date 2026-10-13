package app.chessnut.otb.board

import androidx.compose.ui.text.style.TextAlign
import app.chessnut.otb.openings.OpeningBook
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.live.LiveEnd
import app.chessnut.otb.live.LiveSnapshot
import app.chessnut.otb.live.LowTime
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.ClockMark
import app.chessnut.otb.ui.FlagMark
import app.chessnut.otb.ui.HalfMark
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubPanel
import app.chessnut.otb.ui.clubTextButtonColors
import app.chessnut.otb.ui.KeepScreenOn
import app.chessnut.otb.ui.GameAction
import app.chessnut.otb.ui.ActionTone
import app.chessnut.otb.ui.ClockFace
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import app.chessnut.otb.ui.BackButton
import androidx.compose.ui.platform.LocalDensity
import app.chessnut.otb.ui.PositionDialog
import app.chessnut.otb.ui.MoreMark
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun BoardLinkSection(model: BoardViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) model.autoConnect()
    }
    val connected = ui.connected
    // On the home screen the board is one line; the full panel opens only to change how it connects or to pick a board.
    var open by remember { mutableStateOf(false) }
    val needsPanel = connected == null && (ui.needsPermission || (ui.found.isNotEmpty() && !ui.scanning && ui.busy == null))
    if (compact && !open && !needsPanel) {
        val busy = ui.scanning || ui.busy != null
        Row(
            modifier.fillMaxWidth().clubPanel().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when {
                        connected != null -> Palette.Accent
                        busy -> Palette.Gold
                        else -> Palette.Outline
                    },
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    connected?.name?.trim() ?: "Chessnut Air",
                    color = Palette.Text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when {
                    connected != null -> listOfNotNull(ui.batteryPercent?.let { "Battery $it%" }, ui.message).joinToString(" · ").ifBlank { "Connected" }
                    busy -> ui.busy?.let { "$it…" } ?: "Looking for your board…"
                    else -> ui.message ?: "Not connected"
                }
                Text(
                    detail,
                    color = if (connected != null && (ui.batteryPercent ?: 100) <= 15) Palette.Error else Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                connected != null -> TextButton(onClick = model::disconnect, colors = clubTextButtonColors()) { Text("Disconnect", color = Palette.TextMuted) }
                busy -> CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), color = Palette.Accent, strokeWidth = 2.dp)
                else -> {
                    TextButton(onClick = model::autoConnect, colors = clubTextButtonColors()) { Text("Connect", color = Palette.Accent) }
                    if (model.options.size > 1) {
                        TextButton(onClick = { open = true }, colors = clubTextButtonColors()) { Text("⋯", color = Palette.TextMuted) }
                    }
                }
            }
        }
        return
    }
    Column(modifier.fillMaxWidth().clubPanel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Chessnut Air", color = Palette.Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (connected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    connected.name.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ui.batteryPercent?.let { percent ->
                    Text("$percent%", color = if (percent <= 15) Palette.Error else Palette.Accent, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = model::disconnect, colors = clubTextButtonColors()) { Text("Disconnect", color = Palette.Accent) }
            }
            ui.message?.let { Text(it, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium) }
        } else {
            when {
                ui.needsPermission -> {
                    Text("Allow Bluetooth so the app can connect to your board.", color = Palette.Text)
                    Button(onClick = { permissionLauncher.launch(bluetoothPermissions) }) { Text("Allow Bluetooth") }
                }
                ui.scanning || ui.busy != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), color = Palette.Accent, strokeWidth = 2.dp)
                        Text(ui.busy?.let { "$it…" } ?: "Looking for your board…", color = Palette.Text)
                    }
                }
                ui.found.isNotEmpty() -> {
                    ui.message?.let { Text(it, color = Palette.Text) }
                    Text("Tap your board to connect.", color = Palette.Text)
                    ui.found.forEach { board ->
                        Column(Modifier.fillMaxWidth().clickable { model.connect(board) }.padding(vertical = 6.dp)) {
                            Text(board.name.trim(), color = Palette.Text, style = MaterialTheme.typography.titleMedium)
                            Text("${board.address}  ${board.rssi?.let { "$it dBm" }.orEmpty()}", color = Palette.TextMuted)
                        }
                    }
                }
                else -> {
                    Text(ui.message ?: "Not connected.", color = Palette.Text)
                    Button(onClick = model::autoConnect, enabled = ui.busy == null) { Text("Try again") }
                }
            }
            if (compact && open) {
                TextButton(onClick = { open = false }, colors = clubTextButtonColors()) { Text("Done", color = Palette.Accent) }
            }
            if (model.options.size > 1 && ui.busy == null && !ui.scanning) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.options.forEachIndexed { index, option ->
                        FilterChip(
                            selected = index == ui.optionIndex,
                            onClick = { model.select(index) },
                            colors = clubChipColors(),
                            label = { Text(option.label, color = if (index == ui.optionIndex) Palette.OnAccent else Palette.Text) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    model: BoardViewModel,
    onBack: () -> Unit,
    onAnalyze: (String) -> Unit = {},
    onPositionAnalyse: (fen: String) -> Unit = {},
    onPositionPlay: (fen: String, userWhite: Boolean) -> Unit = { _, _ -> },
) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var position by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    position?.let { placement ->
        PositionDialog(
            placement,
            onDismiss = { position = null },
            onAnalyse = { position = null; onPositionAnalyse(it) },
            onPlay = { fen, white -> position = null; onPositionPlay(fen, white) },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text("Board") },
                colors = clubAppBarColors(),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Only for a position set up by hand: from the normal start, there's nothing to start "from".
                    ui.livePlacement?.takeIf { ui.connected != null && it.substringBefore(' ') != START_PLACEMENT }?.let { placement ->
                        TextButton(onClick = { position = placement }, colors = clubTextButtonColors()) { Text("From here", color = Palette.Accent) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More" }) { MoreMark(Palette.Text) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Share connection log") }, onClick = {
                                menu = false
                                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, model.logText())
                                context.startActivity(Intent.createChooser(share, "Share board log"))
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            // During a game the board runs edge to edge; everything else keeps the usual margins.
            Modifier.fillMaxSize().padding(padding).padding(horizontal = if (ui.connected == null) 16.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.connected == null) {
                BoardLinkSection(model)
                var showLog by remember { mutableStateOf(false) }
                TextButton(onClick = { showLog = !showLog }, colors = clubTextButtonColors()) {
                    Text(if (showLog) "Hide connection log" else "Show connection log", color = Palette.TextMuted)
                }
                if (showLog) LogView(ui.log, Modifier.weight(1f))
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
                )
            }
            if (ui.connected != null) {
                ui.busy?.let { Text("$it…", color = Palette.Accent, modifier = Modifier.padding(horizontal = 16.dp)) }
                ui.message?.let { Text(it, color = Palette.Text, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
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
) {
    val live = ui.live
    val playing = live.started && !live.ended
    val showActions = !live.ended
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ui.connected!!.name.trim(),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ui.batteryPercent?.let { percent ->
                Text("$percent%", color = if (percent <= 15) Palette.Error else Palette.Accent, fontWeight = FontWeight.SemiBold)
            }
            TimeControlChip(
                live.control,
                enabled = !playing || live.sanMoves.isEmpty(),
                onSelect = onTimeControl,
            )
            TextButton(onClick = onDisconnect, colors = clubTextButtonColors()) { Text("Disconnect", color = Palette.Accent) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // The board takes what it can while leaving each clock a usable strip; the strips share the rest.
            val boardSide = minOf(maxWidth, maxHeight - MIN_STRIP * 2 - 32.dp).coerceAtLeast(120.dp)
            val strips = Modifier.padding(horizontal = 16.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerStrip(
                    name = "Black",
                    millis = live.blackMillis.takeUnless { live.control.untimed },
                    lowMillis = LowTime.threshold(live.control.initialMillis),
                    running = playing && !live.toMoveWhite,
                    flagged = live.flaggedBlack,
                    light = false,
                    facingAway = true,
                    playing = showActions,
                    drawLabel = drawLabel(offered = live.blackOfferedDraw, opponentOffered = live.whiteOfferedDraw),
                    onDraw = onDrawBlack,
                    onResign = onResignBlack,
                    modifier = strips.weight(1f),
                )
                ChessBoard(
                    fen = ui.livePlacement ?: "8/8/8/8/8/8/8/8",
                    modifier = Modifier.size(boardSide).align(Alignment.CenterHorizontally),
                    lastUci = live.lastUci,
                )
                // The game so far on the left, the opening it's in on the right.
                val opening = remember(live.sanMoves) { OpeningBook.view(live.sanMoves).name }
                Row(strips.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusLine(live),
                        color = if (live.ended) Palette.Accent else Palette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (live.ended) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    opening?.let {
                        Text(
                            it,
                            color = Palette.Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                PlayerStrip(
                    name = "White",
                    millis = live.whiteMillis.takeUnless { live.control.untimed },
                    lowMillis = LowTime.threshold(live.control.initialMillis),
                    running = playing && live.toMoveWhite,
                    flagged = live.flaggedWhite,
                    light = true,
                    facingAway = false,
                    playing = showActions,
                    drawLabel = drawLabel(offered = live.whiteOfferedDraw, opponentOffered = live.blackOfferedDraw),
                    onDraw = onDrawWhite,
                    onResign = onResignWhite,
                    modifier = strips.weight(1f),
                )
            }
        }
        if (live.ended && ui.lastSavedGameId != null) {
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Analyze this game") }
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
        else -> if (live.control.untimed) "Set up the starting position to start." else "Set up the starting position to start ${live.control.label}."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeControlChip(current: TimeControl, enabled: Boolean, onSelect: (TimeControl) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }) {
        Row(
            Modifier
                .menuAnchor()
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.AccentDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ClockMark(if (enabled) Palette.Accent else Palette.TextMuted)
            Text(
                "${current.label} ▾",
                color = if (enabled) Palette.Accent else Palette.TextMuted,
                fontWeight = FontWeight.Bold,
            )
        }
        ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            (TimeControl.Presets + TimeControl.Untimed).forEach { preset ->
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

private val MIN_STRIP = 76.dp

private const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

/** One player's side of the table: a clock that grows with the room it has, and their draw and resign buttons. */
@Composable
private fun PlayerStrip(
    name: String,
    /** Null when the game has no clock. */
    millis: Long?,
    /** The clock turns red at or below this. */
    lowMillis: Long,
    running: Boolean,
    flagged: Boolean,
    light: Boolean,
    facingAway: Boolean,
    playing: Boolean,
    drawLabel: String,
    onDraw: () -> Unit,
    onResign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Slim rows: the clock stays big enough to read across the table, the buttons take no more than they need.
        val rowHeight = maxHeight.coerceIn(MIN_STRIP, 84.dp)
        val clockSize = with(LocalDensity.current) { (rowHeight * 0.42f).toSp() }
        Row(
            Modifier.fillMaxWidth().height(rowHeight).then(if (facingAway) Modifier.rotate(180f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // No "White"/"Black" label: which clock is whose is plain from where it sits.
            Column(Modifier.weight(1.6f).fillMaxHeight().semantics { contentDescription = "$name clock" }) {
                if (millis != null) {
                    ClockFace(
                        millis,
                        light,
                        running,
                        Modifier.fillMaxWidth().weight(1f),
                        flagged = flagged,
                        fontSize = clockSize,
                        low = millis <= lowMillis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (playing) {
                GameAction(
                    drawLabel,
                    light,
                    onDraw,
                    Modifier.weight(0.6f).fillMaxHeight(),
                    tone = if (drawLabel != "Draw") ActionTone.Active else ActionTone.Plain,
                    icon = { HalfMark(it) },
                )
                GameAction("Resign", light, onResign, Modifier.weight(0.6f).fillMaxHeight(), tone = ActionTone.Danger, icon = { FlagMark(it) })
            }
        }
    }
}

/** The last three moves, "12. Nf3 Nc6 13. Bb5", so the newest is always in view. */
private fun moveLine(sans: List<String>): String = buildString {
    val from = (sans.size - 6).coerceAtLeast(0).let { it - it % 2 }
    for (i in from until sans.size) {
        if (i % 2 == 0) append("${i / 2 + 1}. ")
        append(sans[i]).append(' ')
    }
}.trim()

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
