package app.chessnut.otb.masters

import androidx.activity.compose.BackHandler
import app.chessnut.otb.ui.FlagControl
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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

/** Guess the master's moves from where the game reached your line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterGameScreen(model: MasterGameViewModel, onBack: () -> Unit, onAnalyse: (String) -> Unit = {}) {
    KeepScreenOn()
    val ui by model.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val game = ui.game ?: return
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = clubAppBarColors(),
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Text(ui.status, color = if (ui.done) Palette.Accent else Palette.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val score = if (ui.guessed > 0) "${ui.matched}/${ui.guessed}" else null
                score?.let { Text(it, color = Palette.Accent, style = MaterialTheme.typography.titleMedium) }
                // The move just played: ask for a closer look at it.
                val played = remember(ui.fen) { model.movesTo(ui.fen) }
                FlagControl(played, "master", game.title, listOfNotNull(ui.yourNote, ui.theirNote).joinToString(" "), Modifier.height(28.dp))
            }
            // The players, as on a scoresheet: the opponent above the board, the master you guess below.
            PlayerLine(game.player(!game.userWhite))
            ChessBoard(
                fen = ui.fen,
                flipped = !game.userWhite,
                lastUci = ui.lastUci,
                selected = ui.selected,
                targets = ui.targets,
                arrows = listOfNotNull(
                    Arrow.ofUci(ui.threatUci, Palette.ArrowThreat),
                    Arrow.ofUci(ui.masterUci, Palette.ArrowBest),
                    Arrow.ofUci(ui.wrongUci, Palette.Blunder),
                    Arrow.ofUci(ui.guideUci, Palette.ArrowBest),
                ) + ui.review?.arrows.orEmpty().mapNotNull { (uci, colour) ->
                    Arrow.ofUci(uci, when (colour) { 'R' -> Palette.ArrowThreat; 'B' -> Palette.ArrowBetter; else -> Palette.ArrowBest })
                },
                onSquare = model::tap,
            )
            PlayerLine(game.player(game.userWhite))
            // Your last guess and why the master played what he did, then the reply and its threat.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.boardNote?.let { Text(it, color = Palette.Gold, style = MaterialTheme.typography.bodyMedium) }
                // A key moment: the game waits here until you continue.
                ui.review?.let { review ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
                            .border(1.dp, Palette.Gold, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(review.title, color = Palette.Gold, style = MaterialTheme.typography.titleSmall)
                        Text(review.text, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ui.intro?.let { NoteCard(it, Palette.TextMuted) }
                ui.pass?.let { ResultMark(it, ui.mark.orEmpty()) }
                ui.yourNote?.let { NoteCard(it, Palette.Text) }
                ui.theirNote?.let { NoteCard(it, Palette.Text) }
                if (ui.threatUci != null) Text("Red arrow: the threat.", color = Palette.ArrowThreat, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(0.dp))
            Row(Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAction("Restart", light = false, onClick = model::restart, modifier = Modifier.weight(1f).fillMaxHeight())
                if (ui.review != null) {
                    PrimaryAction("Continue", Modifier.weight(1.4f).fillMaxHeight(), onClick = model::continueOn)
                } else if (ui.done && !ui.over) {
                    PrimaryAction(if (ui.watching) (if (ui.onBoard) "Play the lit moves" else "Playing out…") else "Play out the game", Modifier.weight(1.4f).fillMaxHeight(), enabled = !ui.watching, onClick = model::watchRest)
                } else if (ui.done) {
                    // The whole game, with the engine, from your games' Master games folder.
                    GameAction("Analyse", light = false, onClick = { model.saveForAnalysis()?.let(onAnalyse) }, modifier = Modifier.weight(1f).fillMaxHeight())
                    PrimaryAction("Done", Modifier.weight(1.4f).fillMaxHeight(), onClick = onBack)
                } else {
                    PrimaryAction("Hint", Modifier.weight(1.4f).fillMaxHeight(), enabled = !ui.busy, onClick = model::hint)
                }
            }
        }
    }
}

@Composable
private fun NoteCard(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(12.dp),
    )
}

@Composable
private fun PlayerLine(text: String) {
    Text(text, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
