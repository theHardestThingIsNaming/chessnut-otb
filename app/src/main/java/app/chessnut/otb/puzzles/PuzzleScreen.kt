package app.chessnut.otb.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.GameAction
import app.chessnut.otb.ui.KeepScreenOn
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PrimaryAction
import app.chessnut.otb.ui.ResultMark
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubTextButtonColors

/** Middlegame tactics or an endgame type, one puzzle at a time, in sessions of ten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(model: PuzzleViewModel, onBack: () -> Unit) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    val tone = if (model.endgame) Palette.Endgame else Palette.Accent
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = { TopAppBar(title = { Text(ui.title) }, colors = clubAppBarColors(), navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.summary?.let {
                Summary(it, model, tone, onBack)
                return@Column
            }
            val puzzle = ui.puzzle ?: return@Column
            SessionBar(ui.index, ui.total, tone)
            Text(if (model.endgame) "${ui.index + 1} of ${ui.total}" else "${ui.index + 1} of ${ui.total} · ${puzzle.origin}", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Text(ui.status, color = if (ui.solved) tone else Palette.Text, style = MaterialTheme.typography.titleMedium)
            ChessBoard(
                fen = ui.fen,
                flipped = !puzzle.solverWhite,
                lastUci = ui.lastUci,
                selected = ui.selected,
                targets = ui.targets,
                arrows = listOfNotNull(Arrow.ofUci(ui.hintUci, Palette.ArrowBest), Arrow.ofUci(ui.wrongUci, Palette.Blunder)),
                onSquare = model::tap,
            )
            ui.pass?.let { ResultMark(it, ui.mark.orEmpty()) }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.solved) {
                    PrimaryAction("Next", Modifier.weight(1f).fillMaxHeight(), color = tone, onClick = model::next)
                } else {
                    GameAction("Skip", light = false, onClick = model::next, modifier = Modifier.weight(1f).fillMaxHeight())
                    PrimaryAction("Hint", Modifier.weight(1.4f).fillMaxHeight(), color = tone, enabled = !ui.busy && ui.hintUci == null, onClick = model::hint)
                }
            }
        }
    }
}

@Composable
private fun Summary(summary: PuzzleSummary, model: PuzzleViewModel, tone: Color, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (summary.nothingDue) "Nothing due right now" else "Session complete", color = tone, style = MaterialTheme.typography.headlineSmall)
        if (!summary.nothingDue) {
            Tally(summary.clean, "solved without help", Palette.Best)
            Tally(summary.helped, "solved with a hint or another try", Palette.Inaccuracy)
            Tally(summary.skipped, "skipped", Palette.TextMuted)
        }
        val missed = summary.helped + summary.skipped
        if (missed > 0) {
            Button(
                onClick = model::retryMissed,
                colors = ButtonDefaults.buttonColors(containerColor = tone, contentColor = Palette.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Retry the $missed I missed") }
        }
        Button(
            onClick = model::practiseAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (missed > 0) Palette.SurfaceHigh else tone,
                contentColor = if (missed > 0) tone else Palette.OnAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (summary.nothingDue) "Practise anyway" else "Keep going") }
        TextButton(onClick = onDone, colors = clubTextButtonColors(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Done", color = tone)
        }
    }
}

@Composable
private fun SessionBar(index: Int, total: Int, tone: Color) {
    if (total <= 1) return
    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(
                    when {
                        i < index -> tone
                        i == index -> Palette.Gold
                        else -> Palette.SurfaceHigh
                    },
                ),
            )
        }
    }
}

@Composable
private fun Tally(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$count", color = color, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
        Text(label, color = Palette.Text, style = MaterialTheme.typography.bodyLarge)
    }
}
