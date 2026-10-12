package app.chessnut.otb.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** An essential ending against perfect defence, every move checked by the tablebase. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssentialScreen(model: EssentialViewModel, onBack: () -> Unit) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val essential = ui.essential ?: return
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = { TopAppBar(title = { Text(essential.name) }, colors = clubAppBarColors(), navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui.status, color = if (ui.done) Palette.Endgame else Palette.Text, style = MaterialTheme.typography.titleMedium)
            // The lesson stays up while you play: the technique in view, and the board doesn't jump.
            Text(
                essential.lesson,
                color = Palette.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(12.dp),
            )
            ChessBoard(
                fen = ui.fen,
                flipped = !essential.white,
                lastUci = ui.lastUci,
                selected = ui.selected,
                targets = ui.targets,
                arrows = listOfNotNull(Arrow.ofUci(ui.hintUci, Palette.ArrowBest), Arrow.ofUci(ui.wrongUci, Palette.Blunder)),
                onSquare = model::tap,
            )
            ui.pass?.let { ResultMark(it, ui.mark.orEmpty()) }
            Text(
                "Perfect defence from the Lichess tablebase. Your moves: ${ui.yourMoves}",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAction("Restart", light = false, onClick = model::restart, modifier = Modifier.weight(1f).fillMaxHeight())
                if (ui.done) {
                    PrimaryAction("Done", Modifier.weight(1.4f).fillMaxHeight(), color = Palette.Endgame, onClick = onBack)
                } else {
                    PrimaryAction("Hint", Modifier.weight(1.4f).fillMaxHeight(), color = Palette.Endgame, enabled = !ui.busy && ui.hintUci == null, onClick = model::hint)
                }
            }
        }
    }
}
