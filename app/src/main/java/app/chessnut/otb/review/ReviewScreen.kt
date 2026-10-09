package app.chessnut.otb.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.squareIndex
import app.chessnut.otb.ui.BoardFrame
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubPanel
import app.chessnut.otb.ui.clubTextButtonColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(model: ReviewViewModel, onBack: () -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val game = ui.game
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = ui.exploring) { model.backToGame() }
    LifecycleResumeEffect(model) {
        model.resume()
        onPauseOrDispose { model.pause() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text(game?.title ?: "Game", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = clubAppBarColors(),
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Palette.Gold) } },
                actions = {
                    TextButton(onClick = model::flip) { Text("Flip", color = Palette.Gold) }
                    TextButton(onClick = { menu = true }) { Text("More", color = Palette.Gold) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit game info") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Analyse again") }, onClick = { menu = false; model.reanalyse() })
                        DropdownMenuItem(text = { Text("Delete game") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        if (game == null) {
            Text("This game is no longer saved.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccuracyRow(ui, onAnalyse = model::reanalyse)
            if (!game.isComplete) {
                Text(
                    "Board recording stopped early: ${game.stopReason.orEmpty()} Moves after that point are missing.",
                    color = Palette.Error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val bar = 18.dp
                val boardSize = maxWidth - bar - 10.dp
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvalBar(ui.engine?.score, ui.engine?.terminal, ui.fen, ui.flipped, Modifier.width(bar).height(boardSize))
                    BoardFrame(Modifier.size(boardSize)) {
                        ChessBoard(
                            fen = ui.fen,
                            flipped = ui.flipped,
                            lastMove = ui.lastMove,
                            selected = ui.selected,
                            targets = remember(ui.selected, ui.fen) { model.targets() },
                            arrows = boardArrows(ui),
                            highlights = gambitHighlights(ui),
                            onSquare = model::tapSquare,
                        )
                    }
                }
            }
            ArrowToggles(ui, onToggle = model::toggleArrow)
            NavigationRow(ui, model)
            OpeningBanner(ui)
            GambitRow(ui, onPlay = model::play)
            EnginePanel(ui, onPlay = model::play)
            MoveList(ui, onSelect = model::goTo)
        }
    }

    if (editing && game != null) {
        EditInfoDialog(game.white, game.black, game.event, game.date, game.result, onDismiss = { editing = false }) { w, b, e, d, r ->
            model.saveInfo(w, b, e, d, r)
            editing = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this game?") },
            text = { Text("It is removed from this phone. Games on the board are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.delete()
                    onBack()
                }) { Text("Delete", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Gold) } },
        )
    }
}

@Composable
private fun OpeningBanner(ui: ReviewState) {
    val line = shownSan(ui)
    val opening = remember(line) { OpeningBook.view(line) }
    val name = opening.name ?: return
    Text(
        listOfNotNull(opening.eco, name).joinToString("  "),
        color = Palette.Gold,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun GambitRow(ui: ReviewState, onPlay: (String) -> Unit) {
    val line = shownSan(ui)
    val gambits = remember(line) { OpeningBook.view(line).gambits }
    if (gambits.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Gambit available", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            gambits.take(4).forEach { gambit ->
                val uci = remember(line, gambit.name) {
                    runCatching { ChessLines.replay(line + gambit.san.last()).uci.last() }.getOrNull()
                }
                FilterChip(
                    selected = false,
                    onClick = { uci?.let(onPlay) },
                    colors = clubChipColors(),
                    label = { Text(gambit.name.substringAfterLast(": ").ifBlank { gambit.name }, fontSize = 12.sp, color = Palette.Text) },
                )
            }
        }
    }
}

private fun shownSan(ui: ReviewState): List<String> {
    val played = ui.game?.sanMoves?.take(ui.cursor).orEmpty()
    return played + ui.side.map { it.san }
}

private fun gambitHighlights(ui: ReviewState): Set<Int> {
    val line = shownSan(ui)
    return OpeningBook.view(line).gambits.mapNotNull { gambit ->
        val uci = runCatching { ChessLines.replay(line + gambit.san.last()).uci.last() }.getOrNull() ?: return@mapNotNull null
        if (uci.length < 4) null else squareIndex(uci.substring(2, 4))
    }.toSet()
}

@Composable
private fun AccuracyRow(ui: ReviewState, onAnalyse: () -> Unit) {
    val report = ui.report
    when {
        ui.progress != null -> Column {
            Text("Stockfish is analysing… ${(ui.progress * 100).toInt()}%", color = Palette.TextMuted)
            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
        }
        report != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AccuracyBadge("White", report.whiteAccuracy, Color.White, Color.Black)
            AccuracyBadge("Black", report.blackAccuracy, Color(0xFF2B2B2B), Color.White)
            val counts = report.moves.groupingBy { it.classification }.eachCount()
            Text(
                "?! ${counts[MoveClass.Inaccuracy] ?: 0}   ? ${counts[MoveClass.Mistake] ?: 0}   ?? ${counts[MoveClass.Blunder] ?: 0}",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not analysed yet.", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Button(onClick = onAnalyse) { Text("Analyse") }
        }
    }
}

@Composable
private fun AccuracyBadge(label: String, value: Double?, background: Color, text: Color) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–", color = text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("$label accuracy", color = text.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun EvalBar(score: Score?, terminal: Terminal?, fen: String, flipped: Boolean, modifier: Modifier) {
    val whiteShare = when {
        terminal == Terminal.Checkmate -> if (fen.split(' ')[1] == "w") 0f else 1f
        terminal == Terminal.Stalemate -> 0.5f
        score == null -> 0.5f
        else -> (score.whiteWinPercent / 100).toFloat()
    }
    val animated by animateFloatAsState(whiteShare, label = "eval")
    val dark = Color(0xFF2B2B2B)
    Box(modifier.clip(RoundedCornerShape(4.dp))) {
        Column(Modifier.fillMaxSize()) {
            val top = if (flipped) animated else 1 - animated
            if (top > 0f) Box(Modifier.fillMaxWidth().weight(top).background(if (flipped) Color.White else dark))
            if (1 - top > 0f) Box(Modifier.fillMaxWidth().weight(1 - top).background(if (flipped) dark else Color.White))
        }
        val label = when (terminal) {
            Terminal.Checkmate -> "#"
            Terminal.Stalemate -> "½"
            null -> score?.let { compactLabel(it) }.orEmpty()
        }
        Text(
            label,
            fontSize = 8.sp,
            color = if (whiteShare >= 0.5f) Color.Black else Color.White,
            modifier = Modifier.align(if ((whiteShare >= 0.5f) != flipped) Alignment.BottomCenter else Alignment.TopCenter).padding(vertical = 2.dp),
        )
    }
}

private fun compactLabel(score: Score): String {
    val mate = score.mate
    return if (mate != null) "M${kotlin.math.abs(mate)}" else String.format(Locale.ROOT, "%.1f", kotlin.math.abs(score.cappedCp) / 100.0)
}

private fun boardArrows(ui: ReviewState): List<Arrow> = buildList {
    if (ArrowKind.Threat in ui.arrows) {
        ui.threat?.takeIf { it.fen == ui.fen }?.let { add(Arrow.ofUci(it.uci, Palette.ArrowThreat)) }
    }
    if (ArrowKind.Better in ui.arrows) add(Arrow.ofUci(ui.betterThanPlayed?.bestUci, Palette.ArrowBetter))
    if (ArrowKind.Best in ui.arrows && ui.engine?.terminal == null) {
        add(Arrow.ofUci(ui.engine?.lines?.firstOrNull()?.firstUci, Palette.ArrowBest))
    }
}.filterNotNull()

@Composable
private fun ArrowToggles(ui: ReviewState, onToggle: (ArrowKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowChip("Best move", Palette.ArrowBest, ArrowKind.Best in ui.arrows) { onToggle(ArrowKind.Best) }
            ArrowChip("Better than played", Palette.ArrowBetter, ArrowKind.Better in ui.arrows) { onToggle(ArrowKind.Better) }
            ArrowChip("Threat", Palette.ArrowThreat, ArrowKind.Threat in ui.arrows) { onToggle(ArrowKind.Threat) }
        }
        val better = ui.betterThanPlayed
        if (ArrowKind.Better in ui.arrows && better != null) {
            val preferred = ChessLines.sanOf(ui.fens[ui.cursor - 1], listOfNotNull(better.bestUci)).firstOrNull()
            if (preferred != null) {
                Text(
                    "Blue: Stockfish preferred ${moveNumber(ui.cursor - 1)}$preferred to ${better.san}.",
                    color = Palette.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
        if (ArrowKind.Threat in ui.arrows) {
            val threat = ui.threat?.takeIf { it.fen == ui.fen }
            val passed = remember(ui.fen) { ChessLines.passFen(ui.fen) }
            val text = when {
                passed == null -> "Red: no threat shown while in check."
                threat == null -> "Red: looking for the threat…"
                else -> {
                    val san = ChessLines.sanOf(passed, listOf(threat.uci)).firstOrNull()
                    "Red: if it were their move, they would play ${san ?: threat.uci}."
                }
            }
            Text(text, color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ArrowChip(label: String, color: Color, on: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = on,
        onClick = onClick,
        colors = clubChipColors(),
        label = { Text(label, fontSize = 12.sp, color = if (on) Palette.OnGold else Palette.Text) },
        leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (on) color else Palette.Gold)) },
    )
}

@Composable
private fun NavigationRow(ui: ReviewState, model: ReviewViewModel) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavKey("|<", Modifier.weight(1f)) { model.goTo(0) }
            NavKey("<", Modifier.weight(1f)) { model.step(-1) }
            NavKey(">", Modifier.weight(1f), enabled = !ui.exploring) { model.step(1) }
            NavKey(">|", Modifier.weight(1f)) { model.goTo(ui.fens.lastIndex) }
        }
        if (ui.exploring) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Exploring: " + sideLineText(ui),
                    color = Palette.Gold,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = model::backToGame, colors = clubTextButtonColors()) { Text("Back to game", color = Palette.Gold) }
            }
        }
    }
}

@Composable
private fun NavKey(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(Palette.SurfaceHigh)
            .border(1.dp, if (enabled) Palette.Gold else Palette.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Palette.Gold else Palette.Text.copy(alpha = 0.35f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun sideLineText(ui: ReviewState): String {
    val startPly = ui.cursor
    return ui.side.mapIndexed { i, move ->
        val ply = startPly + i
        val number = ply / 2 + 1
        when {
            ply % 2 == 0 -> "$number. ${move.san}"
            i == 0 -> "$number… ${move.san}"
            else -> move.san
        }
    }.joinToString(" ")
}

@Composable
private fun EnginePanel(ui: ReviewState, onPlay: (String) -> Unit) {
    val engine = ui.engine ?: return
    Box(Modifier.fillMaxWidth().clubPanel()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val heading = when (engine.terminal) {
                Terminal.Checkmate -> "Checkmate"
                Terminal.Stalemate -> "Stalemate"
                null -> when {
                    engine.lines.isEmpty() && engine.live -> "Stockfish is thinking…"
                    engine.lines.isEmpty() -> "No engine lines for this position."
                    engine.live -> "Live Stockfish, depth ${engine.lines.first().depth}"
                    else -> "Best moves (depth ${engine.lines.first().depth})"
                }
            }
            Text(heading, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
            val playedHere = if (!ui.exploring) ui.uci.getOrNull(ui.cursor) else null
            engine.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = line.firstUci != null) { line.firstUci?.let(onPlay) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        line.score.label(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp),
                        color = if (line.firstUci == playedHere) Palette.Best else Palette.Text,
                    )
                    Text(line.san.joinToString(" "), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Palette.Text)
                }
            }
            if (!ui.exploring && ui.cursor < ui.fens.lastIndex) {
                val move = ui.report?.moves?.getOrNull(ui.cursor)
                if (move != null) {
                    val name = when (move.classification) {
                        MoveClass.Best -> "best move"
                        MoveClass.Good -> "good"
                        MoveClass.Inaccuracy -> "an inaccuracy"
                        MoveClass.Mistake -> "a mistake"
                        MoveClass.Blunder -> "a blunder"
                    }
                    Text(
                        "Played ${moveNumber(ui.cursor)}${move.san}: $name (accuracy ${move.accuracy.toInt()}).",
                        color = classColor(move.classification) ?: Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Tap a line or move a piece to explore.", color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

private fun moveNumber(ply: Int): String = if (ply % 2 == 0) "${ply / 2 + 1}. " else "${ply / 2 + 1}… "

private fun classColor(c: MoveClass): Color? = when (c) {
    MoveClass.Best -> Palette.Best
    MoveClass.Good -> null
    MoveClass.Inaccuracy -> Palette.Inaccuracy
    MoveClass.Mistake -> Palette.Mistake
    MoveClass.Blunder -> Palette.Blunder
}

private fun classSuffix(c: MoveClass?): String = when (c) {
    MoveClass.Inaccuracy -> "?!"
    MoveClass.Mistake -> "?"
    MoveClass.Blunder -> "??"
    else -> ""
}

@Composable
private fun MoveList(ui: ReviewState, onSelect: (Int) -> Unit) {
    val game = ui.game ?: return
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        game.sanMoves.forEachIndexed { ply, san ->
            if (ply % 2 == 0) {
                Text("${ply / 2 + 1}.", color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            val classification = ui.report?.moves?.getOrNull(ply)?.classification
            val current = !ui.exploring && ui.cursor == ply + 1
            Text(
                san + classSuffix(classification),
                fontSize = 14.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = classification?.let(::classColor) ?: Palette.Text,
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (current) Palette.GoldDim else Color.Transparent)
                    .clickable { onSelect(ply + 1) }
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            )
        }
        if (game.result != "*") {
            Text(game.result, color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
    }
}

@Composable
private fun EditInfoDialog(
    white: String,
    black: String,
    event: String,
    date: String,
    result: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var w by remember { mutableStateOf(white) }
    var b by remember { mutableStateOf(black) }
    var e by remember { mutableStateOf(event) }
    var d by remember { mutableStateOf(date) }
    var r by remember { mutableStateOf(result) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(w, { w = it }, label = { Text("White") }, singleLine = true)
                OutlinedTextField(b, { b = it }, label = { Text("Black") }, singleLine = true)
                OutlinedTextField(e, { e = it }, label = { Text("Event") }, singleLine = true)
                OutlinedTextField(d, { d = it }, label = { Text("Date (yyyy.mm.dd)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1-0", "0-1", "1/2-1/2", "*").forEach { value ->
                        FilterChip(
                            selected = r == value,
                            onClick = { r = value },
                            colors = clubChipColors(),
                            label = { Text(if (value == "*") "Unknown" else value, color = if (r == value) Palette.OnGold else Palette.Text) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(w, b, e, d, r) }, colors = clubTextButtonColors()) { Text("Save", color = Palette.Gold) } },
        dismissButton = { TextButton(onClick = onDismiss, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Gold) } },
    )
}
