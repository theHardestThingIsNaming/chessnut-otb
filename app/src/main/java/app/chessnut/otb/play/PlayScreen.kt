@file:OptIn(ExperimentalMaterial3Api::class)

package app.chessnut.otb.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.chessnut.otb.live.LowTime
import app.chessnut.otb.openings.OpeningCatalog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.ui.FlagControl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.puzzles.PuzzleProgress
import app.chessnut.otb.puzzles.PuzzleSets
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BoardBanner
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
import app.chessnut.otb.ui.TargetMark
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
    onPlans: (opening: String, white: Boolean, plan: String?) -> Unit = { _, _, _ -> },
    onMaster: (id: String) -> Unit = {},
    onPlayOn: (fen: String, white: Boolean) -> Unit = { _, _ -> },
) {
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
                            ui.playing && ui.kind == PlayKind.Trainer -> ui.openingName?.removePrefix("Repertoire: ") ?: "Trainer"
                            ui.kind == PlayKind.Bot && ui.playing -> "Bot ${ui.elo}"
                            ui.kind == PlayKind.Bot -> "Bot"
                            onPage -> ui.detail?.name?.removePrefix("Repertoire: ") ?: "Openings"
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
            onPage -> OpeningPage(ui, ui.detail!!, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), onPuzzles, onPlans, onMaster)
            !ui.playing -> Setup(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp))
            else -> Playing(ui, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), onAnalyse, onPlans, onMaster, onPuzzles, onPlayOn)
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
                SideChips(ui.userWhite, viewModel::setUserWhite)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.gambitsOnly, onClick = { viewModel.setGambitsOnly(!ui.gambitsOnly) }, label = { Text("Gambits") }, colors = clubChipColors())
                FilterChip(selected = ui.memesOnly, onClick = { viewModel.setMemesOnly(!ui.memesOnly) }, label = { Text("Memes") }, colors = clubChipColors())
                FilterChip(selected = ui.knownOnly, onClick = { viewModel.setKnownOnly(!ui.knownOnly) }, label = { Text("Known") }, colors = clubChipColors())
                Box(Modifier.weight(1f))
                // What the icons and medals mean, behind a tap.
                Legend()
            }
            LazyColumn(Modifier.weight(1f)) {
                if (ui.sections.isEmpty()) {
                    item { Text("No openings match.", color = Palette.TextMuted, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                ui.sections.forEach { section ->
                    // Your chapters against each reply sit among the openings, under "vs ..." headings, with no section of their own.
                    if (section.title.isNotEmpty() && section.title != OpeningCatalog.REPERTOIRE_SECTION) {
                        item(key = "head-${section.title}") {
                            Text(
                                section.title,
                                color = Palette.Accent,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                    // Your repertoire's chapters are many: "Caro-Kann: Advance, Tal" and its sisters sit under one heading.
                    val families = if (section.title == OpeningCatalog.REPERTOIRE_SECTION) {
                        // The openings you meet most come first.
                        val order = listOf("Sicilian", "French", "Caro-Kann", "Scandinavian", "Pirc", "Modern", "Alekhine", "Philidor", "Nimzowitsch")
                        section.rows.groupBy { it.name.removePrefix("Repertoire: ").substringBefore(": ", "") }
                            .toList().sortedBy { (family, _) -> order.indexOf(family).let { if (it < 0) order.size else it } }.toMap()
                    } else {
                        mapOf("" to section.rows)
                    }
                    for ((family, rows) in families) {
                        if (family.isEmpty() || rows.size < 2) {
                            items(rows, key = { "${section.title}-${it.name}" }) { row ->
                                OpeningListRow(row, selected = row.name == ui.openingName, title = row.name.removePrefix("Repertoire: ")) { viewModel.openDetail(row.name) }
                            }
                        } else {
                            item(key = "family-$family") {
                                var open by rememberSaveable(family) { mutableStateOf(rows.any { it.name == ui.openingName }) }
                                val due = rows.sumOf { it.due ?: 0 }
                                Column(Modifier.animateContentSize()) {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(start = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("vs $family", color = Palette.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        Text("${rows.size} chapters", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                                        if (due > 0) ClockMark(Palette.Gold, Modifier.padding(start = 8.dp).size(13.dp))
                                        Box(Modifier.size(44.dp).semantics { contentDescription = if (open) "Hide chapters" else "Show chapters" }, contentAlignment = Alignment.Center) {
                                            Text(if (open) "▴" else "▾", color = Palette.TextMuted, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                    if (open) {
                                        for (row in rows) {
                                            OpeningListRow(
                                                row,
                                                selected = row.name == ui.openingName,
                                                title = row.name.removePrefix("Repertoire: ").removePrefix("$family: "),
                                            ) { viewModel.openDetail(row.name) }
                                        }
                                    }
                                }
                            }
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

/** The two chips that pick the side you play. */
@Composable
private fun SideChips(white: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = white, onClick = { onChange(true) }, label = { Text("White") }, colors = clubChipColors())
    FilterChip(selected = !white, onClick = { onChange(false) }, label = { Text("Black") }, colors = clubChipColors())
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
            title = { Text("Icons and medals") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendLine({ BookMark(Palette.TextMuted, Modifier.size(14.dp)) }, "Lines learned, of the opening's lines")
                    LegendLine({ ClockMark(Palette.Gold, Modifier.size(14.dp)) }, "Lines due for review")
                    LegendLine({ BoltMark(Palette.Survival, Modifier.size(14.dp)) }, "Survival best")
                    LegendLine({ Text("%", color = Palette.TextMuted, fontWeight = FontWeight.Bold) }, "How often your side wins on Lichess, club players")
                    MedalLine(OpeningMedal.Gold, "Every line learned, and a Survival run of ${OpeningMedal.GOLD_RUN} or more.")
                    MedalLine(OpeningMedal.Silver, "Half the lines learned, and a Survival run of ${OpeningMedal.SILVER_RUN} or more.")
                    MedalLine(OpeningMedal.Bronze, "A line learned.")
                    Text("Medals are per side: White and Black each earn their own.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }, colors = clubTextButtonColors()) { Text("OK", color = Palette.Accent) } },
        )
    }
    Text(
        "ⓘ",
        color = Palette.TextMuted,
        fontSize = 18.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(8.dp)
            .semantics { contentDescription = "What the icons and medals mean" },
    )
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
private fun OpeningListRow(row: OpeningRow, selected: Boolean, title: String = row.name, onClick: () -> Unit) {
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
            Text(title, color = if (selected) Palette.Accent else Palette.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (row.learned != null && row.lines != null) {
                    BookMark(if (row.learned == row.lines) Palette.Gold else Palette.TextMuted, Modifier.size(13.dp))
                    Text("${row.learned}/${row.lines}", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.width(6.dp))
                }
                if (row.due != null && row.due > 0) {
                    ClockMark(Palette.Gold, Modifier.size(13.dp))
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
                    if (ui.dueTotal > 0) "" else " · nothing due",
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
            PrimaryAction("Review", Modifier.height(40.dp).width(108.dp), onClick = onReview)
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

/** How long each move of the opening's preview stays, and how long the finished position does before it starts again. */
private const val PREVIEW_STEP_MS = 850L
private const val PREVIEW_END_MS = 3000L

/** One opening from one side: what you know of it, its lines and its history, with training a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningPage(
    ui: PlayUi,
    detail: OpeningDetail,
    viewModel: PlayViewModel,
    modifier: Modifier,
    onPuzzles: (String) -> Unit,
    onPlans: (opening: String, white: Boolean, plan: String?) -> Unit,
    onMaster: (id: String) -> Unit,
) {
    Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "head") {
                // The opening's moves played over and over on the board; a tap holds it where it is.
                val replay = remember(detail.san) { if (detail.san.isEmpty()) null else runCatching { ChessLines.replay(detail.san) }.getOrNull() }
                var step by remember(detail.name) { mutableIntStateOf(0) }
                var held by remember(detail.name) { mutableStateOf(false) }
                val total = detail.san.size
                LaunchedEffect(detail.name, held, total) {
                    if (replay == null || held || total == 0) return@LaunchedEffect
                    while (true) {
                        delay(if (step >= total) PREVIEW_END_MS else PREVIEW_STEP_MS)
                        step = if (step >= total) 0 else step + 1
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChessBoard(
                        fen = replay?.fens?.getOrNull(step) ?: detail.fen,
                        modifier = Modifier.size(244.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = replay != null) { held = !held },
                        flipped = !ui.userWhite,
                        coordinates = false,
                        slide = true,
                        lastUci = if (step > 0) replay?.uci?.getOrNull(step - 1) else null,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (detail.moves.isNotBlank()) {
                            // One numbered move pair to a line: "1. e4 e5" over "2. Nf3 Nc6", the move on the board lit.
                            val text = buildAnnotatedString {
                                detail.san.forEachIndexed { ply, san ->
                                    if (ply % 2 == 0) {
                                        if (ply > 0) append("\n")
                                        append("${ply / 2 + 1}. ")
                                    } else {
                                        append(" ")
                                    }
                                    if (ply == step - 1) withStyle(SpanStyle(color = Palette.Accent, fontWeight = FontWeight.Bold)) { append(san) } else append(san)
                                }
                            }
                            Text(
                                if (detail.san.isEmpty()) AnnotatedString(detail.moves) else text,
                                color = Palette.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        // The same two squares as the opening list: they take less room than two chips beside a bigger board.
                        if (!detail.study) SideToggle(ui.userWhite, viewModel::setUserWhite)
                    }
                }
            }
            // A variation's page has its own lines; the plans, tactics and Punish live on the main opening's page.
            val family = OpeningCatalog.family(detail.name)
            item(key = "scores") {
                PageStats(ui, detail, tactics = if (family == null) { { TacticsChip(detail.name, ui.userWhite, onPuzzles) } } else null)
            }
            if (family != null) item(key = "beyond") { FamilyLink(family) { viewModel.openDetail(family) } }
            item(key = "lines-head") {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Lines", color = Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
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
            // A long list is grouped under the move where its lines part; a short one stays flat.
            // The view model hands the lines over in page order: best win rate first, within a group when grouped.
            val groups = ui.lines.withIndex().groupBy { it.value.group }
            if (groups.size < 2 || ui.lines.size < 6 || "" in groups) {
                itemsIndexed(ui.lines, key = { index, _ -> "line-$index" }) { index, line ->
                    LineRow(index + 1, line, onPlan = { plan -> onPlans(detail.name, ui.userWhite, plan) }, onMaster = onMaster) { viewModel.trainLine(index) }
                }
            } else {
                for ((group, rows) in groups) {
                    item(key = "group-$group") {
                        // Closed to begin with, and left as you leave them; "N to do" on the heading says where something waits.
                        val groupKey = "${detail.name}|${ui.userWhite}|$group"
                        val open = groupKey in ui.openGroups
                        // A group of one is headed by the line's own name, so every row on the page opens the same way.
                        val single = rows.singleOrNull()?.value
                        LineGroupHeader(single?.name ?: group, single != null, rows.map { it.value }, open) { viewModel.toggleGroup(groupKey) }
                        if (open) {
                            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for ((index, line) in rows) {
                                    LineRow(index + 1, line.copy(name = withoutFamily(line.name, group)), onPlan = { plan -> onPlans(detail.name, ui.userWhite, plan) }, onMaster = onMaster) { viewModel.trainLine(index) }
                                }
                            }
                        }
                    }
                }
            }
            // The history and the Chess.com import are for reading and setting up, not for every visit.
            item(key = "more") {
                var more by rememberSaveable(detail.name) { mutableStateOf(false) }
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (detail.story != null) "History and import" else "Import games",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Text(if (more) "▴" else "▾", color = Palette.TextMuted, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (more) {
                        detail.story?.let { story ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("History", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
                                Text(story, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        ChessComImport(ui, viewModel::importChessCom)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Learn, Review or Practise by what the line it opens with needs; then Punish, then Survival: from learning the lines to testing them.
            PrimaryAction(ui.trainStep.name, Modifier.weight(1.3f).fillMaxHeight(), onClick = viewModel::train)
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

/**
 * What you know of the opening, in one row of icons: lines learned, due, medal, Lichess wins,
 * Survival best and mistakes punished. The ⓘ opens a legend that says what each one is.
 */
@Composable
private fun PageStats(ui: PlayUi, detail: OpeningDetail, tactics: (@Composable () -> Unit)? = null) {
    var legend by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).clickable { legend = !legend }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val allLearned = detail.total > 0 && detail.learned == detail.total
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            IconStat("${detail.learned}/${detail.total}", if (allLearned) Palette.Gold else Palette.Text) {
                BookMark(if (allLearned) Palette.Gold else Palette.TextMuted, Modifier.size(15.dp))
            }
            IconStat("${detail.due}", if (detail.due > 0) Palette.Gold else Palette.Text) { ClockMark(if (detail.due > 0) Palette.Gold else Palette.TextMuted, Modifier.size(15.dp)) }
            detail.medal?.let { MedalMark(medalColor(it), Modifier.size(22.dp)) }
            tactics?.invoke()
            Text("ⓘ", color = if (legend) Palette.Accent else Palette.TextMuted, fontSize = 16.sp)
        }
        if (legend) {
            // The rest of the numbers wait for a tap: the three above are what you check each visit.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconStat(detail.winRate?.let { "$it" } ?: "–", Palette.Text) {
                    Text("%", color = Palette.TextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconStat(detail.depthBest?.toString() ?: "–", Palette.Survival) { BoltMark(Palette.Survival, Modifier.size(15.dp)) }
                if (detail.punishable > 0) {
                    IconStat("${detail.punished}/${detail.punishable}", Palette.Mistake) { TargetMark(Palette.Mistake, Modifier.size(15.dp)) }
                }
            }
            LegendLine({ BookMark(Palette.TextMuted, Modifier.size(14.dp)) }, "Lines learned, of the opening's lines")
            LegendLine({ ClockMark(Palette.Gold, Modifier.size(14.dp)) }, "Lines due for review today")
            LegendLine({ MedalMark(medalColor(detail.medal ?: OpeningMedal.Bronze), Modifier.size(16.dp)) }, "Medal: bronze, silver or gold as you learn the lines")
            LegendLine({ Text("%", color = Palette.TextMuted, fontWeight = FontWeight.Bold) }, if (detail.winRate == null && !ui.lichessLinked) "Win rate on Lichess (log in to see it)" else "How often your side wins on Lichess")
            LegendLine({ BoltMark(Palette.Survival, Modifier.size(14.dp)) }, "Survival best" + (detail.depthLast?.let { ", last run $it" } ?: ""))
            if (detail.punishable > 0) LegendLine({ TargetMark(Palette.Mistake, Modifier.size(14.dp)) }, "Common mistakes you punished without help")
        }
    }
}

@Composable
private fun IconStat(value: String, color: Color, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        icon()
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun LegendLine(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { icon() }
        Text(text, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
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
@OptIn(ExperimentalLayoutApi::class)
/**
 * One line of an opening, collapsed to its name, moves and status. The chevron opens its stats, its
 * middlegame plans and its master game; tapping the row itself trains the line.
 */
@Composable
private fun LineRow(number: Int, line: LineView, onPlan: (String) -> Unit, onMaster: (String) -> Unit, onClick: () -> Unit) {
    val (label, tint) = when (line.status) {
        LineStatus.Learned -> "Learned" to Palette.Best
        LineStatus.Due -> "Due" to Palette.Gold
        LineStatus.Missed -> "Missed" to Palette.Error
        LineStatus.New -> "New" to Palette.TextMuted
    }
    var open by rememberSaveable(line.moves) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number. ${line.name}", color = Palette.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // How often your side wins from this line on Lichess, once fetched.
                    line.winRate?.let { Text("  $it%", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium) }
                }
                Text(
                    line.moves,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                // "Due · review now" said due twice.
                if (line.status == LineStatus.Due) "Due now" else label,
                color = tint,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Box(
                Modifier.size(44.dp).clickable { open = !open }.semantics { contentDescription = if (open) "Hide details" else "Show details" },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (open) "▴" else "▾", color = Palette.TextMuted, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (open) {
            Column(Modifier.padding(end = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Next review, Lichess result and Survival best on one row.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    line.next?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, color = tint, style = MaterialTheme.typography.labelMedium) }
                    Box(Modifier.weight(1f))
                    line.best?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            BoltMark(Palette.Survival, Modifier.size(12.dp))
                            Text("$it", color = Palette.Survival, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                // Where the line leads: its middlegame plans and a master game, side by side when they fit.
                if (line.plans.isNotEmpty() || line.master != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (plan in line.plans) LinkChip("Plan", plan, Palette.Accent) { onPlan(plan) }
                        line.master?.let { (id, title) -> LinkChip("Master game", title, Palette.Gold) { onMaster(id) } }
                    }
                }
            }
        }
    }
}

/** "Fontaine Countergambit" for "Evans Gambit, Fontaine Countergambit" under an "Evans Gambit" heading; "Declined, Lange Variation" for "Evans Gambit Declined, Lange Variation". */
private fun withoutFamily(name: String, group: String): String {
    val rest = name.removePrefix("$group, ").let { if (it == name) name.removePrefix("$group ") else it }
    return rest.ifBlank { name }
}

/** The heading over lines that part at the same move: the move, how many lines, and how many want you. */
@Composable
private fun LineGroupHeader(label: String, single: Boolean, lines: List<LineView>, open: Boolean, onToggle: () -> Unit) {
    val due = lines.count { it.status == LineStatus.Due || it.status == LineStatus.Missed }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One text for the name and the count, so the "to do" and the chevron stay at the right edge.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Palette.Accent, fontWeight = FontWeight.Bold)) { append(label) }
                if (!single) withStyle(SpanStyle(color = Palette.TextMuted)) { append("  ·  ${lines.size} lines") }
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (due > 0) Text("$due to do", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(44.dp).semantics { contentDescription = if (open) "Hide lines" else "Show lines" }, contentAlignment = Alignment.Center) {
            Text(if (open) "▴" else "▾", color = Palette.TextMuted, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A bordered link to something a line leads to: a kind in grey, its name in colour. */
@Composable
private fun LinkChip(kind: String, name: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(kind, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text("$name ›", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

/** A row of joined segments with one selected: the two ways of working on a line. */
@Composable
private fun Segmented(options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.height(36.dp).clip(shape).border(1.dp, Palette.Outline, shape).background(Palette.SurfaceHigh)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .background(if (on) Palette.Accent else Color.Transparent)
                    .clickable(enabled = enabled && !on) { onSelect(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Palette.OnAccent else if (enabled) Palette.Text else Palette.TextMuted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
private fun Playing(
    ui: PlayUi,
    viewModel: PlayViewModel,
    modifier: Modifier,
    onAnalyse: (String) -> Unit,
    onPlans: (opening: String, white: Boolean, plan: String?) -> Unit,
    onMaster: (id: String) -> Unit,
    onPuzzles: (String) -> Unit,
    onPlayOn: (fen: String, white: Boolean) -> Unit,
) {
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
            Arrow.ofUci(ui.threatUci, Palette.Blunder),
        )
    }
    val highlights = moveSquares(ui.hintUci) + moveSquares(ui.promptUci)
    val offer = FinishedLine.offer(ui.lineDone, ui.lineClean, ui.learning, ui.survival, ui.punish, showingTrap, ui.reviewing)
    Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.boardWrong) BoardBanner("Check the board", ui.status)
            if (ui.kind == PlayKind.Trainer) {
                // One header row of fixed height, so the board never jumps: the switch, one short thing to say, the spinner
                // while the opponent thinks (the line's label stays), and the flag for the move just played.
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Two ways to work on a line, one choice: Learn steps through it for you, Practise is you playing it.
                    // What comes after (plans, tactics, a master game) shows under the board once the line is done.
                    Segmented(listOf("Learn", "Practise"), if (ui.learning) 0 else 1, enabled = ui.canLearn) { index ->
                        if (index == 0) viewModel.learn() else viewModel.practise()
                    }
                    if (ui.learning) {
                        // What Learn says and shows is chosen where it happens: the notes spoken, and the opening's story.
                        FilterChip(selected = ui.ideas, onClick = { viewModel.setIdeas(!ui.ideas) }, label = { Text(if (ui.ideas) "🔊" else "🔇") }, colors = clubChipColors())
                        FilterChip(selected = ui.history, onClick = { viewModel.setHistory(!ui.history) }, label = { Text("📖") }, colors = clubChipColors())
                        Spacer(Modifier.weight(1f))
                    } else {
                        // What a move or the board has to say first (a rejected move, the board's state), then Out of theory,
                        // then which line this is. Plain "Your move" and the move's own name say nothing new.
                        val plain = ui.status == ui.openingName || ui.status == ui.san.lastOrNull() || ui.status == "Your move" || ui.status == "Your opponent's move"
                        val say = when {
                            !plain && ui.status.isNotEmpty() -> ui.status to (if (ui.retry) Palette.Error else Palette.Text)
                            ui.san.isNotEmpty() && !ui.inTheory && !ui.survival && !ui.punish && !ui.lineDone -> "Out of theory" to Palette.Gold
                            else -> ui.lineLabel.orEmpty() to (if (ui.survival) Palette.Survival else if (ui.punish) Palette.Mistake else Palette.TextMuted)
                        }
                        Text(
                            say.first,
                            color = say.second,
                            fontWeight = if (say.second == Palette.TextMuted) FontWeight.Normal else FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (ui.thinking) {
                        CircularProgressIndicator(Modifier.padding(4.dp).size(22.dp), color = Palette.Accent, strokeWidth = 2.dp)
                    }
                    // The move just played: ask for a closer look at it.
                    if (ui.san.isNotEmpty()) {
                        val played = remember(ui.san) { runCatching { ChessLines.replay(ui.san).uci }.getOrDefault(emptyList()) }
                        FlagControl(played, "train", ui.openingName.orEmpty(), ui.idea ?: ui.status, Modifier.height(28.dp))
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
                slide = true,
                onSquare = viewModel::tap,
            )
            // In Punish mode the eval would give the mistake away, so it waits until you've answered.
            if (!showingTrap && !(ui.punish && !ui.lineDone)) EvalBar(ui.evalScore, ui.evalTerminal, ui.fen)
            if (!showingTrap) ui.punishPass?.let { ResultMark(it, ui.punishMark.orEmpty()) }
            // The line is done and a middlegame plan starts where it ends: carry on into it, first thing under the board.
            val opening = ui.openingName
            // After a clean run, one "Play on" button opens what comes next; after a miss there is just Try again.
            if (opening != null && offer.playOn) {
                var more by remember(ui.fen) { mutableStateOf(false) }
                OutlinedButton(onClick = { more = !more }, colors = clubOutlinedButtonColors(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (more) "Play on ▴" else "Play on ▾", color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                }
                if (more) {
                    for (plan in ui.linePlans) {
                        OutlinedButton(
                            onClick = { onPlans(opening, ui.userWhite, plan) },
                            colors = clubOutlinedButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Plan: $plan", color = Palette.Accent) }
                    }
                    TacticsButton(opening, ui.userWhite, onPuzzles)
                    ui.lineMaster?.let { (id, title) ->
                        OutlinedButton(
                            onClick = { onMaster(id) },
                            colors = clubOutlinedButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Master game: $title", color = Palette.Gold) }
                    }
                    if (offer.engine) {
                        OutlinedButton(
                            onClick = { onPlayOn(ui.fen, ui.userWhite) },
                            colors = clubOutlinedButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Play it out against the engine", color = Palette.Accent) }
                    }
                    OutlinedButton(
                        onClick = { viewModel.analyse()?.let(onAnalyse) },
                        colors = clubOutlinedButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Analyse this game", color = Palette.Accent) }
                }
            }
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
            // The end of a line in Learn: nothing happens by itself; Practise is offered, and Back and Replay stay below.
            if (ui.learning && ui.learnTotal > 0 && ui.learnPly >= ui.learnTotal) {
                OutlinedButton(onClick = viewModel::practise, colors = clubOutlinedButtonColors(), modifier = Modifier.fillMaxWidth()) {
                    Text("That's the line. Practise it", color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
            ui.retryMove?.let { move ->
                Button(
                    onClick = viewModel::retryLine,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Take back and play $move") }
            }
            if (!showingTrap && !ui.lineDone) Text(numbered(ui.san), color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        // The actions stay along the bottom, under the thumb.
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A finished line has two buttons, Try again and Next line: the back arrow leaves.
            if (offer.openings) GameAction("Openings", light = false, onClick = viewModel::leave, modifier = Modifier.weight(1f).fillMaxHeight())
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
            } else if (ui.learning) {
                val atEnd = ui.learnPly >= ui.learnTotal
                GameAction("◀ Back", light = false, onClick = { viewModel.learnBy(-1) }, modifier = Modifier.weight(1f).fillMaxHeight())
                GameAction(
                    if (ui.learnPlaying) "Pause" else if (atEnd) "Replay" else "Play",
                    light = false,
                    onClick = { viewModel.learnPlay() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                // The button stays where the thumb has been tapping; at the end it just goes quiet, so a quick extra tap does nothing.
                PrimaryAction("Next ▶", Modifier.weight(1.3f).fillMaxHeight(), enabled = !atEnd, onClick = { viewModel.learnBy(1) })
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
                SideChips(ui.userWhite, viewModel::setUserWhite)
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
                    TextButton(onClick = { viewModel.clearRepertoire(); message = "Imported chapters removed." }) {
                        Text("Remove imported chapters", color = Palette.Error)
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

/** Middlegame tactics from games that began in your lines, as one chip on the stats row: what's solved, or that a review is next. */
@Composable
private fun TacticsChip(opening: String, white: Boolean, onPuzzles: (String) -> Unit) {
    val key = PuzzleSets.middlegameKey(opening, white)
    val context = LocalContext.current
    // Read each time the page shows, so solving some updates the numbers.
    val counts = remember(key, LocalLifecycleOwner.current.lifecycle.currentState) { PuzzleProgress(context.filesDir).counts(key, System.currentTimeMillis()) }
    if (counts.total == 0) return
    val color = if (counts.due > 0) Palette.Gold else Palette.Accent
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)).clickable { onPuzzles(key) }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Tactics", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(if (counts.due > 0) "Next ›" else "${counts.solved}/${counts.total} ›", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Under a finished line: the opening's middlegame tactics, when there are any. */
@Composable
private fun TacticsButton(opening: String, white: Boolean, onPuzzles: (String) -> Unit) {
    val key = PuzzleSets.middlegameKey(opening, white)
    val context = LocalContext.current
    val counts = remember(key) { PuzzleProgress(context.filesDir).counts(key, System.currentTimeMillis()) }
    if (counts.total == 0) return
    OutlinedButton(onClick = { onPuzzles(key) }, colors = clubOutlinedButtonColors(), modifier = Modifier.fillMaxWidth()) {
        Text("Middlegame tactics · ${if (counts.due > 0) "next is due" else "${counts.solved}/${counts.total} solved"}", color = Palette.Accent)
    }
}
