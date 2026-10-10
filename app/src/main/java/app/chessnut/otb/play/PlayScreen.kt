@file:OptIn(ExperimentalMaterial3Api::class)

package app.chessnut.otb.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.chessnut.otb.openings.OpeningCatalog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.ClockMark
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
import app.chessnut.otb.ui.NavKey
import androidx.compose.foundation.lazy.LazyRow
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.analysis.ChessLines
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import app.chessnut.otb.ui.clubOutlinedButtonColors
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.PrimaryAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(viewModel: PlayViewModel, onBack: () -> Unit, mode: PlayKind = PlayKind.Bot, onAnalyse: (String) -> Unit = {}) {
    KeepScreenOn()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.setKind(mode) }
    BackHandler {
        viewModel.leave()
        onBack()
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            ui.playing && ui.kind == PlayKind.Trainer -> ui.openingName ?: "Trainer"
                            ui.kind == PlayKind.Bot && ui.playing -> "Bot ${ui.elo}"
                            ui.kind == PlayKind.Bot -> "Bot"
                            else -> "Openings"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = clubAppBarColors(),
                navigationIcon = {
                    BackButton {
                        viewModel.leave()
                        onBack()
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.kind == PlayKind.Bot -> BotGame(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse)
            !ui.playing -> Setup(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp))
            else -> Playing(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(ui: PlayUi, viewModel: PlayViewModel, modifier: Modifier) {
    var importing by remember { mutableStateOf(false) }
    if (importing) StudyDialog(ui, viewModel, onDismiss = { importing = false })
    Column(modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
            FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
            if (ui.kind == PlayKind.Trainer) {
                Box(Modifier.width(8.dp))
                FilterChip(selected = ui.explain, onClick = { viewModel.setExplain(!ui.explain) }, label = { Text("Explain") }, colors = clubChipColors())
                FilterChip(selected = ui.ideas, onClick = { viewModel.setIdeas(!ui.ideas) }, label = { Text("Ideas") }, colors = clubChipColors())
                FilterChip(selected = ui.history, onClick = { viewModel.setHistory(!ui.history) }, label = { Text("History") }, colors = clubChipColors())
            }
        }
        if (ui.kind == PlayKind.Bot) {
            Text("Strength", color = Palette.TextMuted, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EloBracket.All.forEach { bracket ->
                    FilterChip(
                        selected = ui.elo == bracket.elo,
                        onClick = { viewModel.setElo(bracket.elo) },
                        label = { Text(bracket.label) },
                        colors = clubChipColors(),
                    )
                }
            }
            Text(
                "Higher ratings stay closer to the best line.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                ChessBoard(fen = START_FEN, modifier = Modifier.size(side), flipped = !ui.userWhite)
            }
        } else {
            Text(
                if (ui.explain) {
                    "Learning: arrows show the book moves. Turn off Explain to test yourself, one line at a time."
                } else {
                    "Testing: play each line from memory. Clean lines come back later, missed ones tomorrow."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = ui.openingQuery,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search openings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = ui.gambitsOnly, onClick = { viewModel.setGambitsOnly(!ui.gambitsOnly) }, label = { Text("Gambits") }, colors = clubChipColors()) }
                item { FilterChip(selected = ui.whiteOpenings, onClick = { viewModel.setWhiteOpenings(!ui.whiteOpenings) }, label = { Text("White's") }, colors = clubChipColors()) }
                item { FilterChip(selected = ui.blackOpenings, onClick = { viewModel.setBlackOpenings(!ui.blackOpenings) }, label = { Text("Black's") }, colors = clubChipColors()) }
                item { FilterChip(selected = ui.unratedOnly, onClick = { viewModel.setUnratedOnly(!ui.unratedOnly) }, label = { Text("Unplayed") }, colors = clubChipColors()) }
                item { FilterChip(selected = ui.memesOnly, onClick = { viewModel.setMemesOnly(!ui.memesOnly) }, label = { Text("Memes") }, colors = clubChipColors()) }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (ui.sections.isEmpty()) {
                    item { Text("No openings match.", color = Palette.TextMuted, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                if (ui.sections.any { section -> section.rows.any { it.winRate != null } }) {
                    item(key = "legend") {
                        Text(
                            "Best first: how often the opening's side wins on Lichess, club rapid and classical. Gold is your best accuracy.",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                ui.sections.forEach { section ->
                    if (section.title.isNotEmpty()) {
                        item(key = "head-${section.title}") {
                            Text(
                                section.title,
                                color = Palette.Accent,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(section.rows, key = { "${section.title}-${it.name}" }) { row ->
                        val selected = row.name == ui.openingName
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
                                .clickable { viewModel.selectOpening(row.name) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                row.name,
                                color = if (selected) Palette.Accent else Palette.Text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (row.learned != null && row.lines != null) {
                                Text(
                                    "${row.learned}/${row.lines} lines",
                                    color = if (row.learned == row.lines) Palette.Gold else Palette.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else if (row.due != null) {
                                Text(
                                    if (row.due > 0) "${row.due} due" else "done",
                                    color = if (row.due > 0) Palette.Gold else Palette.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                row.winRate?.let {
                                    Text("$it%", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text(
                                row.accuracy?.toString() ?: "–",
                                color = if (row.accuracy != null) Palette.Gold else Palette.TextMuted,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
                item(key = "import") {
                    TextButton(onClick = { importing = true }, colors = clubTextButtonColors()) {
                        Text("Import a Lichess study as your repertoire", color = Palette.Accent)
                    }
                }
            }
            ui.openingName?.let { name -> SelectedOpening(name, ui, viewModel::trainLine) }

        }
        Button(
            onClick = viewModel::start,
            enabled = ui.kind == PlayKind.Bot || ui.openingName != null,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ui.kind == PlayKind.Bot) "Play" else "Train") }
    }
}

/** The chosen opening: where it starts, its moves, and how much of it you know. */
@Composable
private fun SelectedOpening(name: String, ui: PlayUi, onTrainLine: (Int) -> Unit) {
    var showLines by remember(name) { mutableStateOf(false) }
    if (showLines) {
        LinesDialog(name, ui.lines, onDismiss = { showLines = false }) { index ->
            showLines = false
            onTrainLine(index)
        }
    }
    val line = remember(name) { OpeningBook.line(name)?.san.orEmpty() }
    val fen = remember(line) { runCatching { ChessLines.replay(line).fens.last() }.getOrDefault(START_FEN) }
    val row = ui.sections.flatMap { it.rows }.firstOrNull { it.name == name }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChessBoard(fen = fen, modifier = Modifier.size(84.dp), flipped = !ui.userWhite, coordinates = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = Palette.Accent, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line.isNotEmpty()) {
                Text(numbered(line), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val progress = when {
                row?.learned != null && row.lines != null -> "${row.learned} of ${row.lines} lines learned"
                row?.due != null -> if (row.due > 0) "${row.due} ${if (row.due == 1) "line" else "lines"} due" else "All lines reviewed"
                else -> "Not trained yet"
            }
            Text(progress, color = Palette.Text, style = MaterialTheme.typography.bodySmall)
            if (ui.lines.isNotEmpty()) {
                Text(
                    "See all ${ui.lines.size} lines",
                    color = Palette.Accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { showLines = true }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Every line of the opening with how you're doing on it. Tapping one drills it now. */
@Composable
private fun LinesDialog(name: String, lines: List<LineView>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text("Tap a line to drill it now.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(lines) { index, line ->
                    val (label, tint) = when (line.status) {
                        LineStatus.Learned -> "Learned" to Palette.Best
                        LineStatus.Due -> "Due" to Palette.Gold
                        LineStatus.Missed -> "Missed" to Palette.Error
                        LineStatus.New -> "New" to Palette.TextMuted
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(index) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${index + 1}. ${line.name}", color = Palette.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(line.moves, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = clubTextButtonColors()) { Text("Close", color = Palette.Accent) } },
    )
}

@Composable
private fun Playing(ui: PlayUi, viewModel: PlayViewModel, modifier: Modifier, onAnalyse: (String) -> Unit) {
    // Weakest first, so the main line is drawn on top where arrows overlap.
    val bookArrows = ui.bookUcis.mapIndexedNotNull { index, uci ->
        Arrow.ofUci(uci, bookArrowColor(index))
    }.asReversed()
    val arrows = bookArrows + listOfNotNull(
        Arrow.ofUci(ui.hintUci, Palette.ArrowBest),
        Arrow.ofUci(ui.promptUci, Palette.ArrowBetter),
        Arrow.ofUci(ui.rejectUci, Palette.Blunder),
    )
    val highlights = moveSquares(ui.hintUci) + moveSquares(ui.promptUci)
    Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (ui.status == ui.openingName) "" else ui.status,
                    color = if (ui.retry) Palette.Error else Palette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (ui.thinking) {
                    CircularProgressIndicator(
                        Modifier.padding(4.dp).size(22.dp),
                        color = Palette.Accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (ui.kind == PlayKind.Trainer) {
                FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ui.explain,
                        onClick = { viewModel.setExplain(!ui.explain) },
                        label = { Text("Explain") },
                        colors = clubChipColors(),
                    )
                    FilterChip(
                        selected = ui.ideas,
                        onClick = { viewModel.setIdeas(!ui.ideas) },
                        label = { Text("Ideas") },
                        colors = clubChipColors(),
                    )
                    FilterChip(
                        selected = ui.history,
                        onClick = { viewModel.setHistory(!ui.history) },
                        label = { Text("History") },
                        colors = clubChipColors(),
                    )
                    ui.lineLabel?.let { label ->
                        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (ui.san.isNotEmpty() && !ui.inTheory) {
                        Text(
                            "Out of theory",
                            color = Palette.OnAccent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .background(Palette.Accent, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            ChessBoard(
                fen = ui.fen,
                flipped = !ui.userWhite,
                lastUci = ui.lastUci,
                selected = ui.selected,
                targets = ui.targets,
                arrows = arrows,
                highlights = highlights,
                onSquare = viewModel::tap,
            )
            if (ui.bookSans.isNotEmpty()) BookKey(ui.bookSans, ui.explainedByEngine)
            ui.lineNote?.let { note ->
                Text(
                    note,
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, Palette.Accent, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            ui.choiceOffer?.let { move ->
                OutlinedButton(onClick = viewModel::adoptChoice, colors = clubOutlinedButtonColors(), modifier = Modifier.fillMaxWidth()) {
                    Text("Play $move here from now on", color = Palette.Accent)
                }
            }
            Text(numbered(ui.san), color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        // The actions stay along the bottom, under the thumb.
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameAction("Openings", light = false, onClick = viewModel::leave, modifier = Modifier.weight(1f).fillMaxHeight())
            if (ui.lineDone) {
                GameAction(
                    "Analyse",
                    light = false,
                    onClick = { viewModel.analyse()?.let(onAnalyse) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PrimaryAction("Next line", Modifier.weight(1.3f).fillMaxHeight(), onClick = viewModel::start)
            } else {
                GameAction("Restart", light = false, onClick = viewModel::start, modifier = Modifier.weight(1f).fillMaxHeight())
                PrimaryAction(
                    "Hint",
                    Modifier.weight(1.3f).fillMaxHeight(),
                    enabled = ui.finished == null && !ui.awaitingBoard,
                    onClick = viewModel::hint,
                )
            }
        }
    }
}

private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotGame(ui: PlayUi, viewModel: PlayViewModel, modifier: Modifier, onAnalyse: (String) -> Unit) {
    val opponentWhite = !ui.userWhite
    val topMillis = if (opponentWhite) ui.whiteMillis else ui.blackMillis
    val bottomMillis = if (opponentWhite) ui.blackMillis else ui.whiteMillis
    val topRunning = if (opponentWhite) ui.whiteRunning else ui.blackRunning
    val bottomRunning = if (opponentWhite) ui.blackRunning else ui.whiteRunning
    val topFlagged = if (opponentWhite) ui.flaggedWhite else ui.flaggedBlack
    val bottomFlagged = if (opponentWhite) ui.flaggedBlack else ui.flaggedWhite
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.playing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ui.status,
                color = when {
                    ui.browsing -> Palette.Accent
                    ui.retry -> Palette.Error
                    else -> Palette.Text
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.thinking) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Accent, strokeWidth = 2.dp)
            }
            BotTimeChip(ui.controlLabel, enabled = ui.san.isEmpty(), onSelect = viewModel::setTime)
        }
        if (!ui.playing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
                FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
                Box(Modifier.weight(1f))
                BotTimeChip(ui.controlLabel, enabled = true, onSelect = viewModel::setTime)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EloBracket.All) { bracket ->
                    FilterChip(
                        selected = ui.elo == bracket.elo,
                        onClick = { viewModel.setElo(bracket.elo) },
                        label = { Text(bracket.label) },
                        colors = clubChipColors(),
                    )
                }
            }
        }
        PlayerBar("Bot ${ui.elo}", opponentWhite, ui.fen, topMillis, topRunning, flagged = topFlagged)
        ChessBoard(
            fen = ui.fen,
            flipped = !ui.userWhite,
            lastUci = ui.lastUci,
            selected = ui.selected,
            targets = ui.targets,
            arrows = listOfNotNull(
                Arrow.ofUci(ui.hintUci, Palette.ArrowBest),
                Arrow.ofUci(ui.promptUci, Palette.ArrowBest),
            ),
            highlights = moveSquares(ui.hintUci) + moveSquares(ui.promptUci),
            onSquare = viewModel::tap,
        )
        PlayerBar("You", ui.userWhite, ui.fen, bottomMillis, bottomRunning, flagged = bottomFlagged)
        if (ui.playing && ui.finished == null) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAction("Draw", ui.userWhite, viewModel::offerDraw, Modifier.weight(1f).fillMaxHeight()) {
                    HalfMark(it, Modifier.size(16.dp))
                }
                GameAction("Resign", ui.userWhite, viewModel::resign, Modifier.weight(1f).fillMaxHeight(), tone = ActionTone.Danger) {
                    FlagMark(it, Modifier.size(16.dp))
                }
            }
        }
        if (ui.playing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavKey("|<", Modifier.weight(1f), ui.glancePly > 0, "Start") { viewModel.glanceTo(0) }
                NavKey("<", Modifier.weight(1f), ui.glancePly > 0, "Back") { viewModel.glanceBy(-1) }
                NavKey(">", Modifier.weight(1f), ui.browsing, "Forward") { viewModel.glanceBy(1) }
                NavKey(">|", Modifier.weight(1f), ui.browsing, "Current") { viewModel.glanceTo(ui.moveCount) }
            }
        }
        if (!ui.playing) {
            Button(
                onClick = viewModel::start,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Play") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::hint,
                    enabled = ui.finished == null && !ui.awaitingBoard,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                ) { Text("Hint") }
                TextButton(onClick = viewModel::start, colors = clubTextButtonColors()) { Text("New game", color = Palette.Accent) }
                TextButton(
                    onClick = { viewModel.analyse()?.let(onAnalyse) },
                    enabled = ui.san.isNotEmpty(),
                    colors = clubTextButtonColors(),
                ) { Text("Analyse", color = Palette.Accent) }
            }
        }
    }
}

/** Explain mode's arrow key: each book move with its colour, strongest first. */
@Composable
private fun BookKey(sans: List<String>, byEngine: Boolean) {
    val ranks = if (byEngine) ENGINE_RANKS else BOOK_RANKS
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (byEngine) Text("Engine", color = Palette.Accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        sans.forEachIndexed { rank, san ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(width = 18.dp, height = 6.dp).background(bookArrowColor(rank), RoundedCornerShape(3.dp)))
                Text(ranks.getOrElse(rank) { "" }, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    san,
                    color = Palette.Text,
                    fontWeight = if (rank == 0) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private val BOOK_RANKS = listOf("Main", "2nd", "3rd")
private val ENGINE_RANKS = listOf("Best", "2nd", "3rd")

private fun bookArrowColor(rank: Int) = when (rank) {
    0 -> Palette.ArrowBest
    1 -> Palette.ArrowBetter
    else -> Palette.ArrowBook
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotTimeChip(label: String, enabled: Boolean, onSelect: (String) -> Unit) {
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
            Text(label, color = if (enabled) Palette.Accent else Palette.TextMuted, fontWeight = FontWeight.Bold)
        }
        ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            TimeControl.Presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset.label)
                        open = false
                    },
                )
            }
        }
    }
}

private fun numbered(san: List<String>): String = buildString {
    san.forEachIndexed { index, move ->
        if (index % 2 == 0) append("${index / 2 + 1}. ")
        append(move)
        append(' ')
    }
}

/** Paste a Lichess study link; its chapters become the "My repertoire" section. */
@Composable
private fun StudyDialog(ui: PlayUi, viewModel: PlayViewModel, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hasRepertoire = ui.sections.any { it.title == OpeningCatalog.REPERTOIRE_SECTION }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a study") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a Lichess study link. Each chapter becomes a line to drill, played from its chapter's side. Lines come back for review on a schedule.",
                    color = Palette.TextMuted,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("lichess.org/study/…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Text(it, color = Palette.Text) }
                if (hasRepertoire) {
                    TextButton(onClick = { viewModel.clearRepertoire(); message = "Repertoire removed." }) {
                        Text("Remove my repertoire", color = Palette.Error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && link.isNotBlank(),
                onClick = {
                    busy = true
                    message = "Importing…"
                    viewModel.importStudy(link) {
                        busy = false
                        message = it
                    }
                },
            ) { Text("Import", color = Palette.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
