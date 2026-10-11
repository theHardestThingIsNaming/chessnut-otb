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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import app.chessnut.otb.ui.clubOutlinedButtonColors
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.PrimaryAction
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.unit.sp
import app.chessnut.otb.ui.EvalBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(viewModel: PlayViewModel, onBack: () -> Unit, mode: PlayKind = PlayKind.Bot, onAnalyse: (String) -> Unit = {}) {
    KeepScreenOn()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.setKind(mode) }
    // Back closes an opening's page first, then leaves.
    val onPage = ui.kind == PlayKind.Trainer && !ui.playing && ui.showDetail && ui.detail != null
    BackHandler {
        if (onPage) {
            viewModel.closeDetail()
        } else {
            viewModel.leave()
            onBack()
        }
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
                            onPage -> ui.detail?.name ?: "Openings"
                            else -> "Openings"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = clubAppBarColors(),
                navigationIcon = {
                    BackButton {
                        if (onPage) {
                            viewModel.closeDetail()
                        } else {
                            viewModel.leave()
                            onBack()
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.kind == PlayKind.Bot -> BotGame(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse)
            onPage -> OpeningPage(ui, ui.detail!!, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp))
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
            if (ui.openingsStarted > 0) ProgressCard(ui, viewModel::startReview)
            Text(
                if (ui.explain) {
                    "Learning: arrows show the book moves. Turn off Explain to test yourself, one line at a time."
                } else {
                    "Testing: play each line from memory. Each one is scheduled for review: further away after a clean run, tomorrow after a miss."
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
                                .clickable { viewModel.openDetail(row.name) }
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

        }
    }
}

/** How much you know across every opening, and the day's review when lines are due. */
@Composable
private fun ProgressCard(ui: PlayUi, onReview: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${ui.learnedTotal} ${if (ui.learnedTotal == 1) "line" else "lines"} learned",
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "In ${ui.openingsStarted} ${if (ui.openingsStarted == 1) "opening" else "openings"}" +
                    if (ui.dueTotal > 0) " · ${ui.dueTotal} due today" else " · nothing due",
                color = if (ui.dueTotal > 0) Palette.Gold else Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (ui.dueTotal > 0) {
            PrimaryAction("Review ${ui.dueTotal}", Modifier.height(40.dp).width(116.dp), onClick = onReview)
        }
    }
}

/** Past the book: how many engine-approved moves in a row, against your best for this opening. */
@Composable
private fun DepthStrip(run: Int, best: Int?, over: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Survival.copy(alpha = 0.12f))
            .border(1.dp, Palette.Survival.copy(alpha = if (over) 0.3f else 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            run.toString(),
            color = if (over) Palette.TextMuted else Palette.Survival,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (over) "Survival over" else "Survival",
                color = Palette.Survival,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (over) "Last move wasn't in the engine's top two." else "Good moves past the line: book or the engine's top two.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        best?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text("Best", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(it.toString(), color = Palette.Survival, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Survival's own card, in its red: what it is and your best and last run. */
@Composable
private fun SurvivalCard(detail: OpeningDetail) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Survival.copy(alpha = 0.12f))
            .border(1.dp, Palette.Survival.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(detail.depthBest?.toString() ?: "–", color = Palette.Survival, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text("best", color = Palette.Survival, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Survival", color = Palette.Survival, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                if (detail.learned > 0) {
                    "One of your learned lines, then the engine's best moves. How many good moves can you find past the line?"
                } else {
                    "Learn a line in Train to unlock Survival: play it, then keep finding the engine's best moves."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            detail.depthLast?.let { Text("Last run: $it", color = Palette.Text, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** One opening from one side: what you know of it, its lines and its history, with training a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningPage(ui: PlayUi, detail: OpeningDetail, viewModel: PlayViewModel, modifier: Modifier) {
    Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "head") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChessBoard(fen = detail.fen, modifier = Modifier.size(104.dp).clip(RoundedCornerShape(6.dp)), flipped = !ui.userWhite, coordinates = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (detail.moves.isNotBlank()) {
                            Text(detail.moves, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!detail.study) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
                                FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
                            }
                        }
                    }
                }
            }
            item(key = "scores") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${detail.learned}/${detail.total}", "Lines learned", Modifier.weight(1f), highlight = detail.total > 0 && detail.learned == detail.total)
                        StatTile(detail.due.toString(), "Due for review", Modifier.weight(1f), highlight = detail.due > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(detail.accuracy?.toString() ?: "–", "Your best accuracy", Modifier.weight(1f))
                        detail.winRate?.let { StatTile("$it%", "Wins on Lichess", Modifier.weight(1f)) }
                    }
                }
            }
            item(key = "survival") { SurvivalCard(detail) }
            item(key = "train-head") {
                Text("Train", color = Palette.Accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            item(key = "coach") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.explain, onClick = { viewModel.setExplain(!ui.explain) }, label = { Text("Explain") }, colors = clubChipColors())
                    FilterChip(selected = ui.ideas, onClick = { viewModel.setIdeas(!ui.ideas) }, label = { Text("Ideas") }, colors = clubChipColors())
                    FilterChip(selected = ui.history, onClick = { viewModel.setHistory(!ui.history) }, label = { Text("History") }, colors = clubChipColors())
                }
            }
            item(key = "mode") {
                Text(
                    if (ui.explain) {
                        "Learning: arrows show the book moves. Nothing is scored."
                    } else {
                        "Testing: play from memory. Each line is scheduled for review: 1 day after a clean run, then 3, 7, 14 and 30 days; " +
                            "tomorrow after a miss. After the line, play carries on with book moves."
                    },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (detail.choices.isNotEmpty()) {
                item(key = "choices") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text("Your moves", color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(detail.choices.joinToString(" · "), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = viewModel::resetChoices, colors = clubTextButtonColors()) { Text("Use main line", color = Palette.Accent) }
                    }
                }
            }
            item(key = "lines-head") {
                Text("Lines", color = Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            itemsIndexed(ui.lines, key = { index, _ -> "line-$index" }) { index, line ->
                LineRow(index, line) { viewModel.trainLine(index) }
            }
            detail.story?.let { story ->
                item(key = "story") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("History", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
                        Text(story, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Survival needs a line you know; until then training comes first.
            if (detail.learned > 0) {
                PrimaryAction("Survival", Modifier.weight(1f).fillMaxHeight(), color = Palette.Survival, onClick = viewModel::startSurvival)
            }
            PrimaryAction(
                if (detail.due > 0) "Review ${detail.due} due" else "Train",
                Modifier.weight(1.3f).fillMaxHeight(),
                onClick = viewModel::start,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = if (highlight) Palette.Gold else Palette.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

/** One line of the opening: its variation, its moves past the opening, how you're doing and when it's next due. */
@Composable
private fun LineRow(index: Int, line: LineView, onClick: () -> Unit) {
    val (label, tint) = when (line.status) {
        LineStatus.Learned -> "Learned" to Palette.Best
        LineStatus.Due -> "Due" to Palette.Gold
        LineStatus.Missed -> "Missed" to Palette.Error
        LineStatus.New -> "New" to Palette.TextMuted
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${index + 1}. ${line.name}", color = Palette.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
                line.next?.let { Text(it, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Text(line.moves, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        line.winRate?.let { rate ->
            Text("You win $rate% from here on Lichess", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
        }
        line.best?.let { best ->
            Text(
                "Survival best: $best ${if (best == 1) "move" else "moves"}",
                color = Palette.Survival,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** What the coach says: the idea behind the last move, and the opening's history as it comes up. */
@Composable
private fun CoachCard(ui: PlayUi) {
    var expanded by remember(ui.story) { mutableStateOf(false) }
    var cut by remember(ui.story) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            // The whole card opens and closes the story, not just its text.
            .clickable(enabled = ui.story != null && (cut || expanded)) { expanded = !expanded }
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.idea != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(ui.ideaMove.orEmpty(), color = Palette.Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(ui.idea, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ui.story?.let { story ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", color = Palette.Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    if (cut || expanded) {
                        Text(
                            if (expanded) "Less ▴" else "More ▾",
                            color = Palette.Accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Text(
                    story,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
                )
            }
        }
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
    val highlights = moveSquares(ui.hintUci) + moveSquares(ui.promptUci)
    Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // One line of fixed height, so the board never jumps: the status when there is one, otherwise the variation.
            val status = if (ui.status == ui.openingName || ui.status == ui.san.lastOrNull()) "" else ui.status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    status.ifEmpty { ui.variation ?: ui.openingName.orEmpty() },
                    color = when {
                        status.isEmpty() -> Palette.Accent
                        ui.retry -> Palette.Error
                        else -> Palette.Text
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    // Out of theory takes the line label's place, so the row never wraps and moves the board.
                    if (ui.san.isNotEmpty() && !ui.inTheory && !ui.survival) {
                        Text(
                            "Out of theory",
                            color = Palette.Gold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        ui.lineLabel?.let { label ->
                            Text(
                                label,
                                color = if (ui.survival) Palette.Survival else Palette.TextMuted,
                                fontWeight = if (ui.survival) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
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
            EvalBar(ui.evalScore, ui.evalTerminal, ui.fen)
            if (ui.bookSans.isNotEmpty()) BookKey(ui.bookSans, ui.explainedByEngine)
            ui.depthRun?.let { DepthStrip(it, ui.depthBest, ui.depthOver) }
            if (ui.idea != null || ui.story != null) CoachCard(ui)
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
            ui.retryMove?.let { move ->
                Button(
                    onClick = viewModel::retryLine,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Take back and play $move") }
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
            if (ui.survival) {
                if (ui.survivalOver) {
                    GameAction(
                        "Analyse",
                        light = false,
                        onClick = { viewModel.analyse()?.let(onAnalyse) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PrimaryAction("Play again", Modifier.weight(1.3f).fillMaxHeight(), color = Palette.Survival, onClick = viewModel::startSurvival)
                } else {
                    PrimaryAction("New line", Modifier.weight(1.3f).fillMaxHeight(), color = Palette.Survival, onClick = viewModel::startSurvival)
                }
            } else if (ui.lineDone) {
                val next = if (ui.reviewLast) "Finish review" else "Next line"
                GameAction(
                    "Analyse",
                    light = false,
                    onClick = { viewModel.analyse()?.let(onAnalyse) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                // After a miss, another go at the same line comes first.
                if (ui.lineClean) {
                    GameAction("Try again", light = false, onClick = viewModel::againLine, modifier = Modifier.weight(1f).fillMaxHeight())
                    PrimaryAction(next, Modifier.weight(1.3f).fillMaxHeight(), onClick = viewModel::nextLine)
                } else {
                    GameAction(next, light = false, onClick = viewModel::nextLine, modifier = Modifier.weight(1f).fillMaxHeight())
                    PrimaryAction("Try again", Modifier.weight(1.3f).fillMaxHeight(), onClick = viewModel::againLine)
                }
            } else {
                GameAction("Restart", light = false, onClick = viewModel::againLine, modifier = Modifier.weight(1f).fillMaxHeight())
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
        if (!ui.playing && ui.startFen != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "From the set-up position · ${if (ui.startFen.split(' ').getOrNull(1) == "b") "Black" else "White"} to move",
                    color = Palette.Gold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearStartPosition, colors = clubTextButtonColors()) { Text("Usual start", color = Palette.TextMuted) }
            }
        }
        // Before the game starts, show where it will begin.
        val shown = if (!ui.playing) ui.startFen ?: ui.fen else ui.fen
        PlayerBar("Bot ${ui.elo}", opponentWhite, shown, topMillis.takeIf { ui.timed }, topRunning, flagged = topFlagged)
        ChessBoard(
            fen = shown,
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
        PlayerBar("You", ui.userWhite, shown, bottomMillis.takeIf { ui.timed }, bottomRunning, flagged = bottomFlagged)
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
            DropdownMenuItem(
                text = { Text(NO_CLOCK) },
                onClick = {
                    onSelect(NO_CLOCK)
                    open = false
                },
            )
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
