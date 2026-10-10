package app.chessnut.otb.mistakes

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.KeepScreenOn
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubTextButtonColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(model: MistakesViewModel, onBack: () -> Unit, onOpenGame: (String) -> Unit) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text("Learn from mistakes") },
                colors = clubAppBarColors(),
                navigationIcon = {
                    TextButton(onClick = onBack, colors = clubTextButtonColors()) { Text("Back", color = Palette.Gold) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.loading) {
                CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), color = Palette.Gold)
                return@Column
            }
            ui.summary?.let {
                Summary(it, model, onBack)
                return@Column
            }
            val mistake = ui.mistake
            if (mistake != null) {
                Text(
                    "${ui.index + 1} of ${ui.total} · ${mistake.gameTitle}",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ui.status, color = if (ui.solved) Palette.Gold else Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ui.checking) CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Gold, strokeWidth = 2.dp)
            }
            if (mistake == null) return@Column
            ChessBoard(
                fen = ui.fen,
                flipped = !mistake.whiteToMove,
                lastUci = ui.lastUci,
                selected = ui.selected,
                targets = ui.targets,
                arrows = listOfNotNull(
                    Arrow.ofUci(ui.bestUci, Palette.ArrowBest),
                    Arrow.ofUci(ui.wrongUci, Palette.Blunder),
                ),
                onSquare = model::tap,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ui.solved) {
                    Button(
                        onClick = model::hint,
                        enabled = !ui.checking && ui.bestUci == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                    ) { Text("Hint") }
                }
                TextButton(onClick = model::next, colors = clubTextButtonColors()) {
                    Text(if (ui.solved) "Next" else "Skip", color = Palette.Gold)
                }
                TextButton(onClick = { onOpenGame(mistake.gameId) }, colors = clubTextButtonColors()) {
                    Text("Open game", color = Palette.Gold)
                }
            }
        }
    }
}

@Composable
private fun Summary(summary: SessionSummary, model: MistakesViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            if (summary.nothingDue) "Nothing due right now" else "Session complete",
            color = Palette.Gold,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!summary.nothingDue) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tally(summary.firstTry, "solved first time", Palette.Best)
                Tally(summary.helped, "solved with a hint or another try", Palette.Inaccuracy)
                Tally(summary.skipped, "skipped", Palette.TextMuted)
            }
        }
        val next = when {
            summary.stillDue == 1 -> "1 position is still due."
            summary.stillDue > 1 -> "${summary.stillDue} positions are still due."
            else -> summary.nextDueAt?.let { "Next position due ${whenDue(it)}." }
        }
        next?.let { Text(it, color = Palette.Text, style = MaterialTheme.typography.bodyLarge) }
        val missed = summary.helped + summary.skipped
        if (missed > 0) {
            Button(
                onClick = model::retryMissed,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Retry the $missed I missed") }
        }
        Button(
            onClick = model::practiseAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (missed > 0) Palette.SurfaceHigh else Palette.Gold,
                contentColor = if (missed > 0) Palette.Gold else Palette.OnGold,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (summary.nothingDue) "Practise anyway (${summary.all})" else "Practise all ${summary.all}") }
        TextButton(onClick = onDone, colors = clubTextButtonColors(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Done", color = Palette.Gold)
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

private fun whenDue(at: Long): String {
    val days = ((at - System.currentTimeMillis()) / (24L * 60 * 60 * 1000.0)).let { Math.ceil(it).toInt() }
    return when {
        days <= 0 -> "now"
        days == 1 -> "tomorrow"
        else -> "in $days days"
    }
}
