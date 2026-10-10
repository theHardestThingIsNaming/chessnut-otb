package app.chessnut.otb.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.chessnut.otb.games.GameSource
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.Material
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.MoveReport
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BackMark
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.FlipMark
import app.chessnut.otb.ui.GambitMark
import app.chessnut.otb.ui.MoreMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PieceIcon
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubPanel
import app.chessnut.otb.ui.clubTextButtonColors
import java.util.Locale
import app.chessnut.otb.ui.KeepScreenOn
import app.chessnut.otb.chess.uciSquares
import app.chessnut.otb.ui.NavKey
import androidx.compose.runtime.rememberCoroutineScope
import app.chessnut.otb.GameShare
import app.chessnut.otb.ui.ShareMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(model: ReviewViewModel, onBack: () -> Unit, onPractise: () -> Unit = {}) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    val game = ui.game
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lichessLink by model.lichessLink.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = ui.exploring) { model.backToGame() }
    LifecycleResumeEffect(model) {
        model.resume()
        onPauseOrDispose { model.pause() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            val headline = OpeningBook.view(shownSan(ui)).name?.let(::shortOpening)
                ?: game?.title?.let(::shortOpening)
                ?: "Game"
            TopAppBar(
                title = { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = clubAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                        BackMark(Palette.Accent)
                    }
                },
                actions = {
                    if (game != null) {
                        IconButton(
                            onClick = { GameShare.link(context, model.app, game, scope) },
                            modifier = Modifier.semantics { contentDescription = "Share game" },
                        ) { ShareMark(Palette.Accent) }
                    }
                    IconButton(onClick = model::flip, modifier = Modifier.semantics { contentDescription = "Flip board" }) {
                        FlipMark(Palette.Accent)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More" }) {
                            MoreMark(Palette.Accent)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ArrowMenuItem("Best move", Palette.ArrowBest, ArrowKind.Best in ui.arrows) {
                            model.toggleArrow(ArrowKind.Best)
                        }
                        ArrowMenuItem("Better than played", Palette.ArrowBetter, ArrowKind.Better in ui.arrows) {
                            model.toggleArrow(ArrowKind.Better)
                        }
                        ArrowMenuItem("Threat", Palette.ArrowThreat, ArrowKind.Threat in ui.arrows) {
                            model.toggleArrow(ArrowKind.Threat)
                        }
                        if (lichessLink != null) {
                            DropdownMenuItem(text = { Text("Open on Lichess") }, onClick = {
                                menu = false
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lichessLink)))
                            })
                        } else if (game?.source != GameSource.Play) {
                            DropdownMenuItem(text = { Text("Upload to Lichess") }, onClick = {
                                menu = false
                                model.uploadToLichess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            })
                        }
                        if (game != null) {
                            DropdownMenuItem(text = { Text("Share PGN") }, onClick = { menu = false; GameShare.pgn(context, game) })
                        }
                        DropdownMenuItem(text = { Text("Practise this game's mistakes") }, onClick = { menu = false; onPractise() })
                        DropdownMenuItem(text = { Text("Edit game info") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Analyse again") }, onClick = { menu = false; model.reanalyse() })
                        DropdownMenuItem(text = { Text("Delete game") }, onClick = { menu = false; confirmDelete = true })
                        }
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
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChessBoard(
                    fen = ui.fen,
                    flipped = ui.flipped,
                    lastUci = ui.lastUci,
                    selected = ui.selected,
                    targets = remember(ui.selected, ui.fen) { ui.targets },
                    arrows = boardArrows(ui),
                    highlights = gambitHighlights(ui),
                    onSquare = model::tapSquare,
                    onDrag = model::drop,
                )
                EvalStrip(ui.engine?.score, ui.engine?.terminal, ui.fen)
            }
            NavigationRow(ui, model)
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
            dismissButton = { TextButton(onClick = { confirmDelete = false }, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Accent) } },
        )
    }
}

/** The specific variation, so a long book name fits the title. "Miles Gambit" stays; "Falkbeer Countergambit Accepted" drops the trailing Accepted. */
private fun shortOpening(name: String): String {
    val specific = name.substringAfterLast(": ").substringAfterLast(", ").trim().ifBlank { name }
    val base = specific.removeSuffix(" Accepted").removeSuffix(" Declined").trim()
    return if (base != specific && ("Countergambit" in base || base.length > 22)) base else specific
}

private fun shownSan(ui: ReviewState): List<String> {
    val played = ui.game?.sanMoves?.take(ui.cursor).orEmpty()
    return played + ui.side.map { it.san }
}

private fun gambitHighlights(ui: ReviewState): Set<Int> {
    val line = shownSan(ui)
    return OpeningBook.view(line).gambits.mapNotNull { gambit ->
        val uci = runCatching { ChessLines.replay(line + gambit.san.last()).uci.last() }.getOrNull() ?: return@mapNotNull null
        uciSquares(uci)?.second
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
        report != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SideSummary("White", report.whiteAccuracy, light = true, report.moves.filter { it.whiteMoved })
            SideSummary("Black", report.blackAccuracy, light = false, report.moves.filter { !it.whiteMoved })
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not analysed yet.", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Button(onClick = onAnalyse) { Text("Analyse") }
        }
    }
}

@Composable
private fun SideSummary(label: String, value: Double?, light: Boolean, moves: List<MoveReport>) {
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "$label accuracy ${value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "unknown"}"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (light) Color.White else Palette.Surface)
                .border(1.dp, if (light) Color.White else Palette.AccentDim, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–",
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 6.dp).width(52.dp),
        )
        listOf(
            MoveClass.Brilliant,
            MoveClass.Best,
            MoveClass.Excellent,
            MoveClass.Good,
            MoveClass.Inaccuracy,
            MoveClass.Mistake,
            MoveClass.Blunder,
        ).forEach { kind ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ClassCount(kind, moves.count { it.classification == kind })
            }
        }
    }
}

@Composable
private fun ClassCount(kind: MoveClass, count: Int) {
    val name = when (kind) {
        MoveClass.Brilliant -> "brilliant moves"
        MoveClass.Best -> "best moves"
        MoveClass.Excellent -> "excellent moves"
        MoveClass.Good -> "good moves"
        MoveClass.Inaccuracy -> "inaccuracies"
        MoveClass.Mistake -> "mistakes"
        MoveClass.Blunder -> "blunders"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics { contentDescription = "$count $name" },
    ) {
        ClassMark(kind, markTint(kind), Modifier.size(11.dp))
        Text(count.toString(), color = Palette.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ClassMark(kind: MoveClass, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        when (kind) {
            MoveClass.Brilliant -> drawPath(star(side, points = 4, inner = 0.22f), tint)
            MoveClass.Best -> drawPath(star(side, points = 5, inner = 0.42f), tint)
            MoveClass.Excellent -> drawPath(
                Path().apply {
                    moveTo(side / 2f, side * 0.06f)
                    lineTo(side * 0.94f, side / 2f)
                    lineTo(side / 2f, side * 0.94f)
                    lineTo(side * 0.06f, side / 2f)
                    close()
                },
                tint,
            )
            MoveClass.Good -> {
                val stroke = side * 0.16f
                drawLine(tint, Offset(side * 0.16f, side * 0.52f), Offset(side * 0.40f, side * 0.78f), stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(side * 0.40f, side * 0.78f), Offset(side * 0.86f, side * 0.22f), stroke, cap = StrokeCap.Round)
            }
            MoveClass.Inaccuracy -> drawCircle(tint, radius = side * 0.36f, style = Stroke(width = side * 0.18f))
            MoveClass.Mistake -> drawPath(
                Path().apply {
                    moveTo(side / 2f, side * 0.06f)
                    lineTo(side * 0.94f, side * 0.9f)
                    lineTo(side * 0.06f, side * 0.9f)
                    close()
                },
                tint,
            )
            MoveClass.Blunder -> drawCircle(tint, radius = side * 0.42f)
        }
    }
}

private fun star(side: Float, points: Int, inner: Float): Path {
    val path = Path()
    val center = side / 2f
    val outer = side * 0.48f
    val start = -PI / 2
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else side * inner
        val angle = start + i * PI / points
        val x = center + cos(angle).toFloat() * radius
        val y = center + sin(angle).toFloat() * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun markTint(kind: MoveClass): Color = when (kind) {
    MoveClass.Brilliant -> Palette.Brilliant
    MoveClass.Best -> Palette.Best
    MoveClass.Excellent -> Palette.Excellent
    MoveClass.Good -> Palette.Gold
    MoveClass.Inaccuracy -> Palette.Inaccuracy
    MoveClass.Mistake -> Palette.Mistake
    MoveClass.Blunder -> Palette.Blunder
}

@Composable
private fun EvalStrip(score: Score?, terminal: Terminal?, fen: String) {
    val balance = remember(fen) { Material.balance(fen) }
    val whiteShare = when {
        terminal == Terminal.Checkmate -> if (fen.split(' ')[1] == "w") 0f else 1f
        terminal == Terminal.Stalemate -> 0.5f
        score == null -> 0.5f
        else -> (score.whiteWinPercent / 100).toFloat().coerceIn(0f, 1f)
    }
    val animated by animateFloatAsState(whiteShare, label = "eval")
    val dark = Color(0xFF2B2B2B)
    val label = when (terminal) {
        Terminal.Checkmate -> "#"
        Terminal.Stalemate -> "½"
        null -> score?.let { compactLabel(it) }.orEmpty()
    }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Evaluation $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxSize()) {
                if (animated > 0f) Box(Modifier.fillMaxHeight().weight(animated).background(Color.White))
                if (1f - animated > 0f) Box(Modifier.fillMaxHeight().weight(1f - animated).background(dark))
            }
        }
        if (label.isNotEmpty()) {
            Text(label, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (!balance.even) {
            for (piece in balance.whiteExtra) PieceIcon(piece, Modifier.size(16.dp))
            for (piece in balance.blackExtra) PieceIcon(piece, Modifier.size(16.dp))
            Text(
                when {
                    balance.points > 0 -> "+${balance.points}"
                    else -> "−${-balance.points}"
                },
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

private fun compactLabel(score: Score): String {
    val mate = score.mate
    return if (mate != null) "M${kotlin.math.abs(mate)}" else String.format(Locale.ROOT, "%.1f", kotlin.math.abs(score.cappedCp) / 100.0)
}

@Composable
private fun ArrowMenuItem(label: String, color: Color, on: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (on) "$label  ·  On" else label) },
        leadingIcon = {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (on) color else Palette.AccentDim))
        },
        onClick = onClick,
    )
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
                    color = Palette.Accent,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = model::backToGame, colors = clubTextButtonColors()) { Text("Back to game", color = Palette.Accent) }
            }
        }
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
                    else -> null
                }
            }
            if (heading != null) Text(heading, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
            val playedHere = if (!ui.exploring) ui.uci.getOrNull(ui.cursor) else null
            val gambitMoves = OpeningBook.view(shownSan(ui)).gambits.map { it.san.last() }.toSet()
            engine.lines.forEach { line ->
                val gambit = line.san.firstOrNull() in gambitMoves
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = line.firstUci != null) { line.firstUci?.let(onPlay) }
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        line.score.label(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp),
                        color = if (line.firstUci == playedHere) Palette.Best else Palette.Text,
                    )
                    Text(
                        if (gambit) line.san.first() else line.san.take(4).joinToString(" "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        color = Palette.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (gambit) {
                        GambitMark(
                            Palette.Gold,
                            Modifier.size(16.dp).semantics { contentDescription = "Gambit" },
                        )
                    }
                }
            }
            if (!ui.exploring && ui.cursor < ui.fens.lastIndex) {
                val move = ui.report?.moves?.getOrNull(ui.cursor)
                if (move != null) {
                    val name = when (move.classification) {
                        MoveClass.Brilliant -> "a brilliant sacrifice"
                        MoveClass.Best -> "best move"
                        MoveClass.Excellent -> "excellent"
                        MoveClass.Good -> "good"
                        MoveClass.Inaccuracy -> "an inaccuracy"
                        MoveClass.Mistake -> "a mistake"
                        MoveClass.Blunder -> "a blunder"
                    }
                    Text(
                        "Played ${moveNumber(ui.cursor)}${move.san}: $name (accuracy ${move.accuracy.toInt()}).",
                        color = classColor(move.classification),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun moveNumber(ply: Int): String = if (ply % 2 == 0) "${ply / 2 + 1}. " else "${ply / 2 + 1}… "

private fun classColor(c: MoveClass): Color = when (c) {
    MoveClass.Brilliant -> Palette.Brilliant
    MoveClass.Best -> Palette.Best
    MoveClass.Excellent -> Palette.Excellent
    MoveClass.Good -> Palette.Gold
    MoveClass.Inaccuracy -> Palette.Inaccuracy
    MoveClass.Mistake -> Palette.Mistake
    MoveClass.Blunder -> Palette.Blunder
}

private fun classSuffix(c: MoveClass?): String = when (c) {
    MoveClass.Brilliant -> "!!"
    MoveClass.Excellent -> "!"
    MoveClass.Inaccuracy -> "?!"
    MoveClass.Mistake -> "?"
    MoveClass.Blunder -> "??"
    else -> ""
}

@Composable
private fun MoveList(ui: ReviewState, onSelect: (Int) -> Unit) {
    val game = ui.game ?: return
    val moves = game.sanMoves
    if (moves.isEmpty()) return
    val rowCount = (moves.size + 1) / 2
    val sheets = if (rowCount > 8) 2 else 1
    val perSheet = (rowCount + sheets - 1) / sheets
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(sheets) { sheet ->
                Column(Modifier.weight(1f)) {
                    val from = sheet * perSheet
                    val to = minOf(from + perSheet, rowCount)
                    for (row in from until to) {
                        val whitePly = row * 2
                        val blackPly = whitePly + 1
                        ScoreLine(
                            shaded = row % 2 == 1,
                            number = row + 1,
                            white = moves.getOrNull(whitePly),
                            black = moves.getOrNull(blackPly),
                            whiteClass = ui.report?.moves?.getOrNull(whitePly)?.classification,
                            blackClass = ui.report?.moves?.getOrNull(blackPly)?.classification,
                            whiteCurrent = !ui.exploring && ui.cursor == whitePly + 1,
                            blackCurrent = !ui.exploring && ui.cursor == blackPly + 1,
                            onWhite = { onSelect(whitePly + 1) },
                            onBlack = { if (blackPly < moves.size) onSelect(blackPly + 1) },
                        )
                    }
                }
            }
        }
        if (game.result != "*") Text(game.result, color = Palette.TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ScoreLine(
    shaded: Boolean,
    number: Int,
    white: String?,
    black: String?,
    whiteClass: MoveClass?,
    blackClass: MoveClass?,
    whiteCurrent: Boolean,
    blackCurrent: Boolean,
    onWhite: () -> Unit,
    onBlack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (shaded) Palette.SurfaceHigh else Color.Transparent)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.width(22.dp))
        ScoreMove(white, whiteClass, whiteCurrent, onWhite, Modifier.weight(1f))
        ScoreMove(black, blackClass, blackCurrent, onBlack, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreMove(san: String?, classification: MoveClass?, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (san == null) {
        Box(modifier)
        return
    }
    Text(
        san + classSuffix(classification),
        color = classification?.let(::classColor) ?: Palette.Text,
        fontSize = 14.sp,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (current) Palette.AccentDim else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
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
                            label = { Text(if (value == "*") "Unknown" else value, color = if (r == value) Palette.OnAccent else Palette.Text) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(w, b, e, d, r) }, colors = clubTextButtonColors()) { Text("Save", color = Palette.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Accent) } },
    )
}
