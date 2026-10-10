@file:OptIn(ExperimentalMaterial3Api::class)

package app.chessnut.otb.play

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BoardFrame
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.ClockMark
import app.chessnut.otb.ui.FlagMark
import app.chessnut.otb.ui.HalfMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PlayerBar
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubOutlinedButtonColors
import app.chessnut.otb.ui.clubTextButtonColors
import app.chessnut.otb.ui.squareIndex
import app.chessnut.otb.ui.KeepScreenOn

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
                            else -> "Play"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = clubAppBarColors(),
                navigationIcon = {
                    TextButton(onClick = {
                        viewModel.leave()
                        onBack()
                    }, colors = clubTextButtonColors()) { Text("Back", color = Palette.Gold) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.kind == PlayKind.Bot,
                onClick = { viewModel.setKind(PlayKind.Bot) },
                label = { Text("Bot") },
                colors = clubChipColors(),
            )
            FilterChip(
                selected = ui.kind == PlayKind.Trainer,
                onClick = { viewModel.setKind(PlayKind.Trainer) },
                label = { Text("Opening trainer") },
                colors = clubChipColors(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
            FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
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
                BoardFrame(Modifier.size(side)) {
                    ChessBoard(fen = START_FEN, flipped = !ui.userWhite)
                }
            }
        } else {
            OpeningCheck("Explain the opening", ui.explain, viewModel::setExplain)
            Text(
                if (ui.explain) {
                    "Arrows show the book moves, strongest first. A move off the book has to be replayed."
                } else {
                    "Play from memory. Every move is scored, and the opening list keeps your accuracy."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            OpeningCheck("Speak the idea behind each move", ui.ideas, viewModel::setIdeas)
            OpeningCheck("Tell the opening's history", ui.history, viewModel::setHistory)
            TextButton(onClick = { importing = true }, colors = clubTextButtonColors()) {
                Text("Import a Lichess study as your repertoire", color = Palette.Gold)
            }
            OutlinedTextField(
                value = ui.openingQuery,
                onValueChange = viewModel::setQuery,
                label = { Text("Opening") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                OpeningCheck("Gambits", ui.gambitsOnly, viewModel::setGambitsOnly)
                OpeningCheck("White", ui.whiteOpenings, viewModel::setWhiteOpenings)
                OpeningCheck("Black", ui.blackOpenings, viewModel::setBlackOpenings)
                OpeningCheck("Unrated", ui.unratedOnly, viewModel::setUnratedOnly)
                OpeningCheck("Memes", ui.memesOnly, viewModel::setMemesOnly)
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
                                color = Palette.Gold,
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
                                color = if (selected) Palette.Gold else Palette.Text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (row.due != null) {
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
            }
        }
        Button(
            onClick = viewModel::start,
            enabled = ui.kind == PlayKind.Bot || ui.openingName != null,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ui.kind == PlayKind.Bot) "Play" else "Train") }
    }
}

@Composable
private fun OpeningCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(
                checkedColor = Palette.Gold,
                uncheckedColor = Palette.TextMuted,
                checkmarkColor = Palette.OnGold,
            ),
        )
        Text(label, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
    }
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
    val highlights = buildSet {
        for (uci in listOfNotNull(ui.hintUci, ui.promptUci)) {
            if (uci.length >= 4) {
                add(squareIndex(uci.substring(0, 2)))
                add(squareIndex(uci.substring(2, 4)))
            }
        }
    }
    val last = ui.lastUci?.let { uci ->
        if (uci.length < 4) null else squareIndex(uci.substring(0, 2)) to squareIndex(uci.substring(2, 4))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ui.status, color = if (ui.retry) Palette.Error else Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (ui.thinking) {
                CircularProgressIndicator(
                    Modifier.padding(4.dp).size(22.dp),
                    color = Palette.Gold,
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
                if (ui.san.isNotEmpty() && !ui.inTheory) {
                    Text(
                        "Out of theory",
                        color = Palette.OnGold,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(Palette.Gold, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
        BoardFrame(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ChessBoard(
                fen = ui.fen,
                flipped = !ui.userWhite,
                lastMove = last,
                selected = ui.selected,
                targets = viewModel.targets(),
                arrows = arrows,
                highlights = highlights,
                onSquare = viewModel::tap,
            )
        }
        if (ui.bookSans.isNotEmpty()) BookKey(ui.bookSans, ui.explainedByEngine)
        Text(numbered(ui.san), color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::hint,
                enabled = ui.finished == null && !ui.awaitingBoard,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
            ) { Text("Hint") }
            TextButton(onClick = viewModel::start, colors = clubTextButtonColors()) { Text("New game", color = Palette.Gold) }
            TextButton(
                onClick = { viewModel.analyse()?.let(onAnalyse) },
                enabled = ui.san.isNotEmpty(),
                colors = clubTextButtonColors(),
            ) { Text("Analyse", color = Palette.Gold) }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (ui.playing) ui.status else if (ui.userWhite) "You have White · ${ui.elo}" else "You have Black · ${ui.elo}",
                color = when {
                    ui.browsing -> Palette.Gold
                    ui.retry -> Palette.Error
                    else -> Palette.Text
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.thinking) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Gold, strokeWidth = 2.dp)
            }
            BotTimeChip(ui.controlLabel, enabled = ui.san.isEmpty(), onSelect = viewModel::setTime)
        }
        if (!ui.playing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
                FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EloBracket.All.forEach { bracket ->
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
        BoardFrame(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ChessBoard(
                fen = ui.fen,
                flipped = !ui.userWhite,
                lastMove = ui.lastUci?.let { uci ->
                    if (uci.length < 4) null else squareIndex(uci.substring(0, 2)) to squareIndex(uci.substring(2, 4))
                },
                selected = ui.selected,
                targets = if (ui.browsing) emptySet() else viewModel.targets(),
                arrows = listOfNotNull(
                    Arrow.ofUci(ui.hintUci, Palette.ArrowBest),
                    Arrow.ofUci(ui.promptUci, Palette.ArrowBest),
                ),
                highlights = moveSquares(ui.hintUci) + moveSquares(ui.promptUci),
                onSquare = viewModel::tap,
            )
        }
        PlayerBar("You", ui.userWhite, ui.fen, bottomMillis, bottomRunning, flagged = bottomFlagged)
        if (ui.playing && ui.finished == null) {
            val youLight = ui.userWhite
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotAction(
                    "Draw",
                    { HalfMark(if (youLight) Palette.Ink else Palette.Gold, Modifier.size(16.dp)) },
                    viewModel::offerDraw,
                    fill = if (youLight) Color(0xFFF7F0E4) else Color(0xFF2A241C),
                    text = if (youLight) Palette.Ink else Palette.Gold,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                )
                BotAction(
                    "Resign",
                    { FlagMark(Palette.Error, Modifier.size(16.dp)) },
                    viewModel::resign,
                    fill = if (youLight) Color(0xFFF3D4C8) else Color(0xFF3A221C),
                    text = Palette.Error,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        if (ui.playing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryKey("|<", "Start", ui.glancePly > 0, Modifier.weight(1f)) { viewModel.glanceTo(0) }
                HistoryKey("<", "Back", ui.glancePly > 0, Modifier.weight(1f)) { viewModel.glanceBy(-1) }
                HistoryKey(">", "Forward", ui.browsing, Modifier.weight(1f)) { viewModel.glanceBy(1) }
                HistoryKey(">|", "Current", ui.browsing, Modifier.weight(1f)) { viewModel.glanceTo(ui.moveCount) }
            }
        }
        if (!ui.playing) {
            Button(
                onClick = viewModel::start,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Play") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::hint,
                    enabled = ui.finished == null && !ui.awaitingBoard,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                ) { Text("Hint") }
                TextButton(onClick = viewModel::start, colors = clubTextButtonColors()) { Text("New game", color = Palette.Gold) }
                TextButton(
                    onClick = { viewModel.analyse()?.let(onAnalyse) },
                    enabled = ui.san.isNotEmpty(),
                    colors = clubTextButtonColors(),
                ) { Text("Analyse", color = Palette.Gold) }
            }
        }
    }
}

@Composable
private fun HistoryKey(label: String, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .semantics { contentDescription = description }
            .clip(shape)
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (enabled) Palette.Gold else Palette.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Palette.Gold else Palette.Text.copy(alpha = 0.35f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BotAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    fill: Color,
    text: Color,
    modifier: Modifier,
) {
    Box(
        modifier.background(fill).clickable(onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, color = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** Explain mode's arrow key: each book move with its colour, strongest first. */
@Composable
private fun BookKey(sans: List<String>, byEngine: Boolean) {
    val ranks = if (byEngine) ENGINE_RANKS else BOOK_RANKS
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (byEngine) Text("Engine", color = Palette.Gold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
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

private fun moveSquares(uci: String?): Set<Int> {
    if (uci == null || uci.length < 4) return emptySet()
    return setOf(squareIndex(uci.substring(0, 2)), squareIndex(uci.substring(2, 4)))
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
                .border(1.dp, Palette.GoldDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ClockMark(if (enabled) Palette.Gold else Palette.TextMuted)
            Text(label, color = if (enabled) Palette.Gold else Palette.TextMuted, fontWeight = FontWeight.Bold)
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
            ) { Text("Import", color = Palette.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
