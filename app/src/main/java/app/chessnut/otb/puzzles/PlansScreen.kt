package app.chessnut.otb.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.openings.MiddlegamePlan
import app.chessnut.otb.openings.MiddlegamePlans
import app.chessnut.otb.ui.Arrow
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.GameAction
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PrimaryAction
import app.chessnut.otb.ui.clubAppBarColors
import kotlinx.coroutines.delay

/**
 * An opening's middlegame plans: the list, then one plan stepped through on
 * the board with a note per move, ending with "Try it against the bot".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(opening: String, white: Boolean, onBack: () -> Unit, onTry: (fen: String, white: Boolean) -> Unit) {
    val plans = remember(opening, white) { MiddlegamePlans.of(opening, white) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val plan = plans.firstOrNull { it.name == open }
    BackHandler { if (plan != null) open = null else onBack() }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text(plan?.name ?: "Middlegame plans") },
                colors = clubAppBarColors(),
                navigationIcon = { BackButton { if (plan != null) open = null else onBack() } },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)
        if (plan == null) {
            LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        "What to aim for once your $opening lines end. Step through each plan, then try it against the bot.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(plans, key = { it.name }) { item ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).clickable { open = item.name }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.name, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(item.summary, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        } else {
            PlanViewer(plan, modifier, onTry)
        }
    }
}

@Composable
private fun PlanViewer(plan: MiddlegamePlan, modifier: Modifier, onTry: (fen: String, white: Boolean) -> Unit) {
    val replay = remember(plan) { ChessLines.replay(plan.before + plan.moves) }
    val start = plan.before.size
    var step by rememberSaveable(plan.name) { mutableIntStateOf(0) }
    var playing by remember(plan) { mutableStateOf(false) }
    LaunchedEffect(playing, step) {
        if (!playing) return@LaunchedEffect
        if (step >= plan.moves.size) {
            playing = false
            return@LaunchedEffect
        }
        delay(if (step == 0) 600 else STEP_MS)
        step++
    }
    val ply = start + step
    val lastUci = replay.uci.getOrNull(ply - 1)
    // Your side's moves in green, your opponent's in blue.
    val mover = (ply - 1) % 2 == 0
    val arrow = if (step > 0) Arrow.ofUci(lastUci, if (mover == plan.white) Palette.ArrowBest else Palette.ArrowBetter) else null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (step == 0) plan.summary else "${moveNumber(ply - 1)}${plan.moves[step - 1]}  ${plan.notes[step - 1]}",
            color = if (step == 0) Palette.Gold else Palette.Text,
            style = MaterialTheme.typography.bodyLarge,
            // Two to four lines: the board below stays put as the notes change length.
            minLines = 3,
            modifier = Modifier.heightIn(min = 72.dp),
        )
        ChessBoard(fen = replay.fens[ply], flipped = !plan.white, lastUci = lastUci, arrows = listOfNotNull(arrow))
        Text("Move ${step} of ${plan.moves.size}", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameAction("|<", light = false, onClick = { playing = false; step = 0 }, modifier = Modifier.weight(1f).fillMaxHeight())
            GameAction("<", light = false, onClick = { playing = false; if (step > 0) step-- }, modifier = Modifier.weight(1f).fillMaxHeight())
            GameAction(if (playing) "Pause" else "Play", light = false, onClick = { if (step >= plan.moves.size) step = 0; playing = !playing }, modifier = Modifier.weight(1.2f).fillMaxHeight())
            GameAction(">", light = false, onClick = { playing = false; if (step < plan.moves.size) step++ }, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        PrimaryAction("Try it against the bot", Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp)) {
            onTry(replay.fens[start], plan.white)
        }
    }
}

private fun moveNumber(ply: Int): String = "${ply / 2 + 1}${if (ply % 2 == 0) ". " else "… "}"

private const val STEP_MS = 2200L
