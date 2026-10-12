@file:OptIn(ExperimentalMaterial3Api::class)

package app.chessnut.otb.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.chessnut.otb.live.LowTime
import app.chessnut.otb.openings.MiddlegamePlans
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
import app.chessnut.otb.puzzles.PuzzleProgress
import app.chessnut.otb.puzzles.PuzzleSets
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.ClockMark
import app.chessnut.otb.ui.FlagMark
import app.chessnut.otb.ui.HalfMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PlayerBar
import app.chessnut.otb.ui.ResultMark
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
import app.chessnut.otb.repertoire.OpeningMedal
import app.chessnut.otb.ui.MedalMark
import app.chessnut.otb.ui.BoltMark
import app.chessnut.otb.ui.BookMark
import app.chessnut.otb.ui.PieceIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.BorderStroke
import app.chessnut.otb.openings.TrapFrequency
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    viewModel: PlayViewModel,
    onBack: () -> Unit,
    mode: PlayKind = PlayKind.Bot,
    onAnalyse: (String) -> Unit = {},
    onPuzzles: (String) -> Unit = {},
    onPlans: (opening: String, white: Boolean) -> Unit = { _, _ -> },
) {
    KeepScreenOn()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.setKind(mode) }
    // Back steps out one level: from a line to the opening's page (or the list), from the page to the list, then leaves.
    val onPage = ui.kind == PlayKind.Trainer && !ui.playing && ui.showDetail && ui.detail != null
    val back = {
        when {
            ui.kind == PlayKind.Trainer && ui.playing -> viewModel.leave()
            onPage -> viewModel.closeDetail()
            else -> {
                viewModel.leave()
                onBack()
            }
        }
    }
    BackHandler(onBack = back)
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
                    BackButton(back)
                },
            )
        },
    ) { padding ->
        when {
            ui.kind == PlayKind.Bot -> BotGame(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse)
            onPage -> OpeningPage(ui, ui.detail!!, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), onPuzzles, onPlans)
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
        if (ui.kind == PlayKind.Bot) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.userWhite, onClick = { viewModel.setUserWhite(true) }, label = { Text("White") }, colors = clubChipColors())
                FilterChip(selected = !ui.userWhite, onClick = { viewModel.setUserWhite(false) }, label = { Text("Black") }, colors = clubChipColors())
            }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The field keeps its own text: fed back from the view model, fast typing lost letters.
                var query by rememberSaveable { mutableStateOf(ui.openingQuery) }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.setQuery(it)
                    },
                    placeholder = { Text("Search openings") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                // The side you play: the stats and medals below are for it.
                SideToggle(ui.userWhite, viewModel::setUserWhite)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = ui.gambitsOnly, onClick = { viewModel.setGambitsOnly(!ui.gambitsOnly) }, label = { Text("Gambits") }, colors = clubChipColors()) }
                item { FilterChip(selected = ui.memesOnly, onClick = { viewModel.setMemesOnly(!ui.memesOnly) }, label = { Text("Memes") }, colors = clubChipColors()) }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (ui.sections.isEmpty()) {
                    item { Text("No openings match.", color = Palette.TextMuted, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                item(key = "legend") { Legend() }
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
                        OpeningListRow(row, selected = row.name == ui.openingName) { viewModel.openDetail(row.name) }
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

/** White or Black, as two small squares in the piece colours. */
@Composable
private fun SideToggle(white: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (side in listOf(true, false)) {
            val on = side == white
            // Each side on its own board square, so the black king shows against the dark page.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (side) Palette.LightSquare else Palette.DarkSquare)
                    .border(if (on) 3.dp else 0.dp, if (on) Palette.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .alpha(if (on) 1f else 0.45f)
                    .clickable { onChange(side) }
                    .semantics { contentDescription = if (side) "Play White" else "Play Black" },
                contentAlignment = Alignment.Center,
            ) {
                PieceIcon(if (side) 'K' else 'k', Modifier.size(34.dp), background = null)
            }
        }
    }
}

/** What the icons on each opening mean, with how medals are earned behind a tap. */
@Composable
private fun Legend() {
    var open by remember { mutableStateOf(false) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Medals") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedalLine(OpeningMedal.Gold, "Every line learned, and a Survival run of ${OpeningMedal.GOLD_RUN} or more.")
                    MedalLine(OpeningMedal.Silver, "Half the lines learned, and a Survival run of ${OpeningMedal.SILVER_RUN} or more.")
                    MedalLine(OpeningMedal.Bronze, "A line learned.")
                    Text("Medals are per side: White and Black each earn their own.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }, colors = clubTextButtonColors()) { Text("OK", color = Palette.Accent) } },
        )
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BookMark(Palette.TextMuted, Modifier.size(14.dp))
        Text("learned", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        BoltMark(Palette.Survival)
        Text("survival", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text("%", color = Palette.TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Lichess wins", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        MedalMark(medalColor(OpeningMedal.Gold), Modifier.size(16.dp))
        Text("medals ⓘ", color = Palette.Accent, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MedalLine(medal: OpeningMedal, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MedalMark(medalColor(medal))
        Text(text, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun medalColor(medal: OpeningMedal): Color = when (medal) {
    OpeningMedal.Gold -> Color(0xFFE2B656)
    OpeningMedal.Silver -> Color(0xFFC8CCD2)
    OpeningMedal.Bronze -> Color(0xFFC07A45)
}

/** One opening in the list: name, then icons for what you know of it, and its medal. */
@Composable
private fun OpeningListRow(row: OpeningRow, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, color = if (selected) Palette.Accent else Palette.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (row.learned != null && row.lines != null) {
                    BookMark(if (row.learned == row.lines) Palette.Gold else Palette.TextMuted, Modifier.size(13.dp))
                    Text("${row.learned}/${row.lines}", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.width(6.dp))
                }
                if (row.due != null && row.due > 0) {
                    ClockMark(Palette.Gold, Modifier.size(13.dp))
                    Text("${row.due}", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.width(6.dp))
                }
                row.depthBest?.let {
                    BoltMark(Palette.Survival, Modifier.size(13.dp))
                    Text("$it", color = Palette.Survival, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.width(6.dp))
                }
                row.winRate?.let { Text("$it%", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium) }
            }
        }
        val medal = row.medal
        if (medal != null) {
            MedalMark(medalColor(medal), Modifier.size(28.dp).semantics { contentDescription = "${medal.name} medal" })
        } else {
            Box(Modifier.size(28.dp))
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
                "${ui.openingsStarted} ${if (ui.openingsStarted == 1) "opening" else "openings"} started" +
                    if (ui.dueTotal > 0) " · ${ui.dueTotal} due today" else " · nothing due",
                color = if (ui.dueTotal > 0) Palette.Gold else Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Your medals, best first.
        for (medal in listOf(OpeningMedal.Gold, OpeningMedal.Silver, OpeningMedal.Bronze)) {
            val count = ui.medals[medal] ?: continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedalMark(medalColor(medal), Modifier.size(20.dp))
                Text("$count", color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (ui.dueTotal > 0) {
            PrimaryAction("Review ${ui.dueTotal}", Modifier.height(40.dp).width(108.dp), onClick = onReview)
        }
    }
}

/** A trap being shown: its name, progress through it, and what it wins once played out. */
@Composable
private fun TrapBanner(name: String, step: Int, length: Int, summary: String?, info: TrapInfo?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Mistake.copy(alpha = 0.12f))
            .border(1.dp, Palette.Mistake.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠  ${info?.kind ?: "Trap"}: $name", color = Palette.Mistake, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$step / $length", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        info?.label?.let { Text(it, color = frequencyColor(info.frequency), style = MaterialTheme.typography.labelMedium) }
        summary?.let { Text(it, color = Palette.Text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Common traps stand out; rare ones are muted, so you know which to expect. */
private fun frequencyColor(frequency: TrapFrequency?): Color = when (frequency) {
    TrapFrequency.Common -> Palette.Mistake
    TrapFrequency.Occasional -> Palette.Gold
    TrapFrequency.Rare, null -> Palette.TextMuted
}

/** Survival's counter: good moves in a row past the line, against your best on this line. */
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

/** Survival's own card, in its red: your best and last run, in one row. */
@Composable
private fun SurvivalCard(detail: OpeningDetail) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Survival.copy(alpha = 0.12f))
            .border(1.dp, Palette.Survival.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Survival", color = Palette.Survival, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                if (detail.learned > 0) "A learned line, then the engine's best moves" else "Learn a line in Train to unlock it",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail.depthLast?.let { Stat(it.toString(), "last", Modifier, Palette.Text) }
        Stat(detail.depthBest?.toString() ?: "–", "best", Modifier, Palette.Survival)
    }
}

/** Punish's own card, in its orange beside Survival's red: how many of the opening's mistakes you've punished without help. */
@Composable
private fun PunishCard(detail: OpeningDetail) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Mistake.copy(alpha = 0.12f))
            .border(1.dp, Palette.Mistake.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Punish", color = Palette.Mistake, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Common mistakes you punished without help",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Stat("${detail.punished}/${detail.punishable}", "punished", Modifier, Palette.Mistake)
    }
}

/** One opening from one side: what you know of it, its lines and its history, with training a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningPage(
    ui: PlayUi,
    detail: OpeningDetail,
    viewModel: PlayViewModel,
    modifier: Modifier,
    onPuzzles: (String) -> Unit,
    onPlans: (opening: String, white: Boolean) -> Unit,
) {
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
                // One strip of four, so the lines start higher up the page.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(vertical = 10.dp),
                ) {
                    Stat("${detail.learned}/${detail.total}", "learned", Modifier.weight(1f), if (detail.total > 0 && detail.learned == detail.total) Palette.Gold else Palette.Text)
                    Stat(detail.due.toString(), "due", Modifier.weight(1f), if (detail.due > 0) Palette.Gold else Palette.Text)
                    MedalStat(detail.medal, Modifier.weight(1f))
                    Stat(detail.winRate?.let { "$it%" } ?: "–", if (detail.winRate == null && !ui.lichessLinked) "Log in to Lichess" else "Lichess wins", Modifier.weight(1f))
                }
            }
            item(key = "survival") { SurvivalCard(detail) }
            if (detail.punishable > 0) item(key = "punish") { PunishCard(detail) }
            item(key = "coach") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Train", color = Palette.Accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp))
                    FilterChip(selected = ui.explain, onClick = { viewModel.setExplain(!ui.explain) }, label = { Text("Explain") }, colors = clubChipColors())
                    FilterChip(selected = ui.ideas, onClick = { viewModel.setIdeas(!ui.ideas) }, label = { Text("Ideas") }, colors = clubChipColors())
                    FilterChip(selected = ui.history, onClick = { viewModel.setHistory(!ui.history) }, label = { Text("History") }, colors = clubChipColors())
                }
            }
            item(key = "mode") {
                Text(
                    if (ui.explain) {
                        "Learning: the green arrow is this line's move, and a connected board lights it. Nothing is scored."
                    } else {
                        "Testing from memory. Clean lines come back in 1, 3, 7, 14, then 30 days; a miss comes back tomorrow."
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
            item(key = "beyond") {
                // A variation's page has its own lines; the plans, tactics and Punish live on the main opening's page.
                val family = OpeningCatalog.family(detail.name)
                if (family != null) {
                    FamilyLink(family) { viewModel.openDetail(family) }
                } else {
                    BeyondTheOpening(detail.name, ui.userWhite, onPuzzles, onPlans)
                }
            }
            item(key = "chess-com") { ChessComImport(ui, viewModel::importChessCom) }
            item(key = "lines-head") {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Lines", color = Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (ui.userWhite) "as White" else "as Black", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
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
            // Train, then Punish, then Survival: from learning the lines to testing them.
            PrimaryAction(
                if (detail.due > 0) "Review ${detail.due} due" else "Train",
                Modifier.weight(1.3f).fillMaxHeight(),
                onClick = viewModel::train,
            )
            if (detail.punishable > 0) {
                PrimaryAction("Punish", Modifier.weight(1f).fillMaxHeight(), color = Palette.Mistake, onClick = viewModel::startPunish)
            }
            // Survival needs a line you know; until then training comes first.
            if (detail.learned > 0) {
                PrimaryAction("Survival", Modifier.weight(1f).fillMaxHeight(), color = Palette.Survival, onClick = viewModel::startSurvival)
            }
        }
    }
}

/** The opening's medal in the stats strip, or a dash before the first line is learned. */
@Composable
private fun MedalStat(medal: OpeningMedal?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (medal != null) MedalMark(medalColor(medal), Modifier.size(26.dp)) else Text("–", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(medal?.name?.lowercase() ?: "medal", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, color: Color = Palette.Text) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${index + 1}. ${line.name}", color = Palette.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(line.moves, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        // Everything else about the line on one row: status and next review, Lichess result, Survival best.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.next?.let { "$label · ${it.replaceFirstChar { c -> c.lowercase() }}" } ?: label,
                color = tint,
                style = MaterialTheme.typography.labelMedium,
            )
            line.winRate?.let { Text("Wins $it%", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium) }
            Box(Modifier.weight(1f))
            line.best?.let { Text("Survival $it", color = Palette.Survival, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
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
    val showingTrap = ui.trapName != null
    // During a trap only its own move is drawn: the opponent's mistake in red, your punishment in green.
    val arrows = if (showingTrap) {
        listOfNotNull(Arrow.ofUci(ui.trapUci, if (ui.trapMistake) Palette.Blunder else Palette.ArrowBest))
    } else {
        bookArrows + listOfNotNull(
            Arrow.ofUci(ui.hintUci, Palette.ArrowBest),
            Arrow.ofUci(ui.branchUci, Palette.Gold),
            Arrow.ofUci(ui.promptUci, Palette.ArrowBetter),
            Arrow.ofUci(ui.rejectUci, Palette.Blunder),
        )
    }
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
                    if (ui.san.isNotEmpty() && !ui.inTheory && !ui.survival && !ui.punish) {
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
                                color = if (ui.survival) Palette.Survival else if (ui.punish) Palette.Mistake else Palette.TextMuted,
                                fontWeight = if (ui.survival || ui.punish) FontWeight.Bold else FontWeight.Normal,
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
            // In Punish mode the eval would give the mistake away, so it waits until you've answered.
            if (!showingTrap && !(ui.punish && !ui.lineDone)) EvalBar(ui.evalScore, ui.evalTerminal, ui.fen)
            if (!showingTrap) ui.punishPass?.let { ResultMark(it, ui.punishMark.orEmpty()) }
            ui.trapName?.let { TrapBanner(it, ui.trapStep, ui.trapLength, ui.trapSummary, ui.trapInfo) }
            if (!showingTrap) {
                for (info in ui.traps) {
                    OutlinedButton(
                        onClick = { viewModel.showTrap(info.name) },
                        border = BorderStroke(1.dp, Palette.Mistake),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠  Show ${if (info.kind == "Trap") "common trap" else "common mistake"}: ${info.name}", color = Palette.Mistake, fontWeight = FontWeight.SemiBold)
                            info.label?.let { Text(it, color = frequencyColor(info.frequency), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            if (ui.bookSans.isNotEmpty()) BookKey(ui.bookSans, ui.explainedByEngine, ui.bookFirstIsLine)
            if (!showingTrap) ui.depthRun?.let { DepthStrip(it, ui.depthBest, ui.depthOver) }
            if (ui.idea != null || ui.story != null) CoachCard(ui)
            ui.branchNote?.let { note ->
                Text(
                    "⑂  $note",
                    color = Palette.Gold,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, Palette.Gold, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            ui.lineNote?.let { note ->
                Text(
                    note,
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, if (ui.punish) Palette.Mistake else if (ui.survival) Palette.Survival else Palette.Accent, RoundedCornerShape(12.dp))
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
            if (!showingTrap) Text(numbered(ui.san), color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        // The actions stay along the bottom, under the thumb.
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameAction("Openings", light = false, onClick = viewModel::leave, modifier = Modifier.weight(1f).fillMaxHeight())
            if (showingTrap) {
                GameAction("Replay", light = false, onClick = viewModel::replayTrap, modifier = Modifier.weight(1f).fillMaxHeight())
                PrimaryAction("Back to the line", Modifier.weight(1.3f).fillMaxHeight(), onClick = viewModel::backToLine)
            } else if (ui.punish) {
                if (ui.lineDone) {
                    GameAction(
                        "Explain it",
                        light = false,
                        onClick = { ui.punishName?.let(viewModel::showTrap) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PrimaryAction("Next mistake", Modifier.weight(1.3f).fillMaxHeight(), color = Palette.Mistake, onClick = viewModel::startPunish)
                } else {
                    GameAction("Skip", light = false, onClick = viewModel::startPunish, modifier = Modifier.weight(1f).fillMaxHeight())
                    PrimaryAction(
                        "Hint",
                        Modifier.weight(1.3f).fillMaxHeight(),
                        color = Palette.Mistake,
                        enabled = ui.finished == null && !ui.awaitingBoard,
                        onClick = viewModel::hint,
                    )
                }
            } else if (ui.survival) {
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
        val lowMillis = LowTime.threshold(TimeControl.fromLabel(ui.controlLabel).initialMillis)
        PlayerBar("Bot ${ui.elo}", opponentWhite, shown, topMillis.takeIf { ui.timed }, topRunning, flagged = topFlagged, lowMillis = lowMillis)
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
        PlayerBar("You", ui.userWhite, shown, bottomMillis.takeIf { ui.timed }, bottomRunning, flagged = bottomFlagged, lowMillis = lowMillis)
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
private fun BookKey(sans: List<String>, byEngine: Boolean, lineFirst: Boolean) {
    val ranks = when {
        lineFirst -> LINE_RANKS
        byEngine -> ENGINE_RANKS
        else -> BOOK_RANKS
    }
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
private val LINE_RANKS = listOf("This line", "Also book", "Also book")
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

/** Brings in your recent Chess.com games of this opening, so their mistakes join your practice. */
@Composable
private fun ChessComImport(ui: PlayUi, onImport: (String) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { if (ui.chessComUser.isBlank()) asking = true else onImport(ui.chessComUser) },
            enabled = !ui.chessComBusy,
            colors = clubTextButtonColors(),
        ) {
            Text("Import my Chess.com games from the last 30 days", color = Palette.Accent)
        }
        ui.chessComNote?.let { Text(it, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall) }
        if (ui.chessComUser.isNotBlank() && !ui.chessComBusy) {
            Text(
                "As ${ui.chessComUser} · change",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { asking = true },
            )
        }
    }
    if (asking) {
        var name by remember { mutableStateOf(ui.chessComUser) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Your Chess.com username") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Username") })
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onImport(name)
                }, colors = clubTextButtonColors()) { Text("Import", color = Palette.Accent) }
            },
            dismissButton = { TextButton(onClick = { asking = false }, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.TextMuted) } },
        )
    }
}

/** On a variation's page: where its opening's middlegame plans, tactics and Punish mistakes are. */
@Composable
private fun FamilyLink(family: String, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Beyond the opening", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text("Middlegame plans, tactics and Punish are on the $family page.", color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
        Text("Open $family", color = Palette.Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Past the book: the opening's middlegame plans, and tactics from games that
 * began in your lines, so they're the ones your structures produce. Shown when
 * the opening has either.
 */
@Composable
private fun BeyondTheOpening(opening: String, white: Boolean, onPuzzles: (String) -> Unit, onPlans: (String, Boolean) -> Unit) {
    val key = PuzzleSets.middlegameKey(opening, white)
    val context = LocalContext.current
    // Read each time the page shows, so solving some updates the numbers.
    val counts = remember(key, LocalLifecycleOwner.current.lifecycle.currentState) { PuzzleProgress(context.filesDir).counts(key, System.currentTimeMillis()) }
    val plans = remember(opening, white) { MiddlegamePlans.of(opening, white).size }
    var showSolved by remember(key) { mutableStateOf(false) }
    if (counts.total == 0 && plans == 0) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(vertical = 6.dp),
    ) {
        Text("Beyond the opening", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        if (plans > 0) {
            BeyondRow("Middlegame plans", "What to aim for once your lines end", "$plans", Palette.Accent) { onPlans(opening, white) }
        }
        if (counts.total > 0 && (!counts.resting || showSolved)) {
            BeyondRow(
                "Middlegame tactics",
                "${counts.total} puzzles from games that began in your lines",
                if (counts.due > 0) "${counts.due} due" else "${counts.solved}/${counts.total}",
                if (counts.due > 0) Palette.Gold else Palette.Accent,
            ) { onPuzzles(key) }
        }
        if (counts.resting) {
            TextButton(onClick = { showSolved = !showSolved }, colors = clubTextButtonColors(), modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(if (showSolved) "Hide solved tactics" else "Show solved tactics", color = Palette.Accent)
            }
        }
    }
}

@Composable
private fun BeyondRow(title: String, detail: String, badge: String, badgeColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(badge, color = badgeColor, fontWeight = FontWeight.Bold)
    }
}
