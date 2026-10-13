package app.chessnut.otb.review

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.chessnut.otb.games.GameSource
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.chessnut.otb.mistakes.MistakeDeck
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.analysis.MoveReport
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.FlipMark
import app.chessnut.otb.ui.GambitMark
import app.chessnut.otb.ui.MoreMark
import app.chessnut.otb.ui.Palette
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
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.PlayerBar
import androidx.compose.foundation.layout.Spacer
import app.chessnut.otb.ui.EvalBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    model: ReviewViewModel,
    onBack: () -> Unit,
    onPractise: () -> Unit = {},
    onPlayFrom: (fen: String, userWhite: Boolean) -> Unit = { _, _ -> },
) {
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
            // Moves from a set-up position don't start from move one, so the opening book can't name them.
            val headline = if (ui.position) "Position" else OpeningBook.view(shownSan(ui)).name?.let(::shortOpening)
                ?: game?.title?.let(::shortOpening)
                ?: "Game"
            TopAppBar(
                title = { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = clubAppBarColors(),
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    if (game != null && !ui.position) {
                        IconButton(
                            onClick = { GameShare.link(context, model.app, game, scope) },
                            modifier = Modifier.semantics { contentDescription = "Share game" },
                        ) { ShareMark(Palette.Text) }
                    }
                    IconButton(onClick = model::flip, modifier = Modifier.semantics { contentDescription = "Flip board" }) {
                        FlipMark(Palette.Text)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More" }) {
                            MoreMark(Palette.Text)
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
                        DropdownMenuItem(text = { Text("Play the bot from here") }, onClick = {
                            menu = false
                            onPlayFrom(ui.fen, ui.fen.split(' ').getOrNull(1) != "b")
                        })
                        if (ui.position) {
                            // A bare position has no saved game behind it.
                        } else if (lichessLink != null) {
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
                        if (game != null && !ui.position) {
                            DropdownMenuItem(text = { Text("Share PGN") }, onClick = { menu = false; GameShare.pgn(context, game) })
                            DropdownMenuItem(text = { Text("Practise this game's mistakes") }, onClick = { menu = false; onPractise() })
                            DropdownMenuItem(text = { Text("Edit game info") }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text("Analyse again") }, onClick = { menu = false; model.reanalyse() })
                            DropdownMenuItem(text = { Text("Delete game") }, onClick = { menu = false; confirmDelete = true })
                        }
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
            if (!ui.position) AccuracyRow(ui, onAnalyse = model::reanalyse)
            if (ui.report != null && model.asksForSide()) PractiseCard(ui.game?.practise.orEmpty(), model::setPractise)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewPlayer(game.black, white = false, ui, Modifier, show = !ui.flipped)
                ReviewPlayer(game.white, white = true, ui, Modifier, show = ui.flipped)
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
                ReviewPlayer(game.white, white = true, ui, Modifier, show = !ui.flipped)
                ReviewPlayer(game.black, white = false, ui, Modifier, show = ui.flipped)
                EvalBar(ui.engine?.score, ui.engine?.terminal, ui.fen)
            }
            NavigationRow(ui, model)
            EnginePanel(ui, onPlay = model::play)
            if (!ui.position) MoveList(ui, onSelect = model::goTo)
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
    if (ui.position) return emptySet()
    val line = shownSan(ui)
    return OpeningBook.view(line).gambits.mapNotNull { gambit ->
        val uci = runCatching { ChessLines.replay(line + gambit.san.last()).uci.last() }.getOrNull() ?: return@mapNotNull null
        uciSquares(uci)?.second
    }.toSet()
}

@Composable
private fun AccuracyRow(ui: ReviewState, onAnalyse: () -> Unit) {
    val report = ui.report
    // The same height whether analysing or done, so the board doesn't jump when the report arrives.
    val steady = Modifier.fillMaxWidth().heightIn(min = 60.dp)
    when {
        ui.progress != null -> Column(steady, verticalArrangement = Arrangement.Center) {
            Text("Stockfish is analysing… ${(ui.progress * 100).toInt()}%", color = Palette.TextMuted)
            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
        }
        report != null -> Column(steady, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            SideSummary("White", report.whiteAccuracy, light = true, report.moves.filter { it.whiteMoved })
            SideSummary("Black", report.blackAccuracy, light = false, report.moves.filter { !it.whiteMoved })
        }
        else -> Row(steady, verticalAlignment = Alignment.CenterVertically) {
            Text("Not analysed yet.", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Button(onClick = onAnalyse) { Text("Analyse") }
        }
    }
}

/** A player above or below the board: name, what they have taken, and their accuracy once analysed. */
@Composable
private fun ReviewPlayer(name: String, white: Boolean, ui: ReviewState, modifier: Modifier, show: Boolean) {
    if (!show) return
    val accuracy = ui.report?.let { if (white) it.whiteAccuracy else it.blackAccuracy }
    PlayerBar(
        name = name.ifBlank { if (white) "White" else "Black" },
        white = white,
        fen = ui.fen,
        millis = null,
        running = false,
        modifier = modifier,
        badge = accuracy?.let { String.format(Locale.ROOT, "%.1f", it) },
    )
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
        Spacer(Modifier.width(8.dp))
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
                TextButton(onClick = model::backToGame, colors = clubTextButtonColors()) {
                    Text(if (ui.position) "Back to start" else "Back to game", color = Palette.Accent)
                }
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
    // The panel keeps its size while Stockfish works: a heading, three line slots and the note, always.
    val engine = ui.engine ?: EngineView(null, null, emptyList(), live = true)
    Box(Modifier.fillMaxWidth().clubPanel()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val heading = when (engine.terminal) {
                Terminal.Checkmate -> "Checkmate"
                Terminal.Stalemate -> "Stalemate"
                null -> when {
                    engine.lines.isEmpty() && engine.live -> "Stockfish is thinking…"
                    engine.lines.isEmpty() -> "No engine lines for this position."
                    engine.live -> "Live Stockfish, depth ${engine.lines.first().depth}"
                    else -> "Stockfish, depth ${engine.lines.first().depth}"
                }
            }
            Text(heading, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            val playedHere = if (!ui.exploring) ui.uci.getOrNull(ui.cursor) else null
            val gambitMoves = OpeningBook.view(shownSan(ui)).gambits.map { it.san.last() }.toSet()
            for (slot in 0 until maxOf(ENGINE_SLOTS, engine.lines.size)) {
                val line = engine.lines.getOrNull(slot)
                if (line == null) {
                    Text("…", color = Palette.TextMuted, fontSize = 16.sp, modifier = Modifier.height(ENGINE_ROW).padding(horizontal = 4.dp))
                    continue
                }
                val gambit = line.san.firstOrNull() in gambitMoves
                Row(
                    Modifier.fillMaxWidth().height(ENGINE_ROW).clip(RoundedCornerShape(6.dp))
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
                        minLines = 2,
                        maxLines = 2,
                    )
                } else {
                    Text("", fontSize = 16.sp, minLines = 2)
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

/**
 * The move table colours the engine's top moves, brilliant ones, mistakes and blunders. Excellent,
 * good and slightly inexact moves stay plain, so the table stays calm. Inaccuracies keep their "?!".
 */
private fun tableColor(c: MoveClass?): Color = when (c) {
    MoveClass.Best, MoveClass.Brilliant, MoveClass.Mistake, MoveClass.Blunder -> classColor(c)
    else -> Palette.Text
}

private fun classSuffix(c: MoveClass?): String = when (c) {
    MoveClass.Brilliant -> "!!"
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
        color = tableColor(classification),
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

/** Engine lines shown at once, and each one's height, so the panel never changes size. */
private const val ENGINE_SLOTS = 3
private val ENGINE_ROW = 30.dp

/**
 * For a game that doesn't say which side you were, such as one on the board:
 * its mistakes join your practice only once you say whose to keep.
 */
@Composable
private fun PractiseCard(practise: String, onChoose: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clubPanel().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (practise) {
                MistakeDeck.PRACTISE_WHITE -> "White's mistakes are in your practice."
                MistakeDeck.PRACTISE_BLACK -> "Black's mistakes are in your practice."
                MistakeDeck.PRACTISE_BOTH -> "Both sides' mistakes are in your practice."
                else -> "Add this game's mistakes for later review?"
            },
            color = Palette.Text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((side, label) in listOf(MistakeDeck.PRACTISE_WHITE to "I was White", MistakeDeck.PRACTISE_BLACK to "I was Black", MistakeDeck.PRACTISE_BOTH to "Both")) {
                FilterChip(
                    selected = practise == side,
                    onClick = { onChoose(if (practise == side) "" else side) },
                    label = { Text(label) },
                    colors = clubChipColors(),
                )
            }
        }
    }
}
