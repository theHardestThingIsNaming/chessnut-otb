package app.chessnut.otb.mistakes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BoardFrame
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
            val mistake = ui.mistake
            if (mistake != null) {
                Text(
                    "${ui.index + 1} of ${ui.total} · ${ui.due} due · ${mistake.gameTitle}",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ui.status, color = if (ui.solved) Palette.Gold else Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ui.checking) CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Gold, strokeWidth = 2.dp)
            }
            if (mistake == null) return@Column
            BoardFrame(Modifier.fillMaxWidth().aspectRatio(1f)) {
                ChessBoard(
                    fen = ui.fen,
                    flipped = !mistake.whiteToMove,
                    selected = ui.selected,
                    targets = ui.targets,
                    arrows = listOfNotNull(
                        Arrow.ofUci(ui.bestUci, Palette.ArrowBest),
                        Arrow.ofUci(ui.wrongUci, Palette.Blunder),
                    ),
                    onSquare = model::tap,
                )
            }
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
