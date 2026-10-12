package app.chessnut.otb.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.chessnut.otb.ui.BackButton
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubTextButtonColors

/** Endgame puzzles by type, each with how far you've got. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndgamesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onEssential: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentState
    val rows = remember(lifecycle) {
        val progress = PuzzleProgress(context.filesDir)
        val now = System.currentTimeMillis()
        PuzzleSets.ENDGAMES.map { (type, name) -> Triple(type, name, progress.counts(PuzzleSets.endgameKey(type), now)) }
    }
    val mastered = remember(lifecycle) {
        val progress = PuzzleProgress(context.filesDir)
        Essentials.ALL.filter { progress.mastered(EssentialViewModel.PROGRESS_PREFIX + it.id) }.map { it.id }.toSet()
    }
    // Played through with a slip: worth another go.
    val slipped = remember(lifecycle) {
        val known = PuzzleProgress(context.filesDir).all()
        Essentials.ALL.map { it.id }.filter { it !in mastered && known.containsKey(EssentialViewModel.PROGRESS_PREFIX + it) }.toSet()
    }
    var showMastered by remember { mutableStateOf(false) }
    var showSolved by remember { mutableStateOf(false) }
    val essentials = Essentials.ALL.filter { showMastered || it.id !in mastered }
    val sections = rows.filter { showSolved || !it.third.resting }
    val solvedSections = rows.count { it.third.resting }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = { TopAppBar(title = { Text("Endgames") }, colors = clubAppBarColors(), navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Essentials", color = Palette.Endgame, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "The endings every player needs, against perfect defence. Every move is checked by the tablebase.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(essentials, key = { "essential-" + it.id }) { e ->
                // The same card as a puzzle section below: title, then the goal where the bar would be.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
                        .clickable { onEssential(e.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.name, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (e.goal == Goal.Win) "Win it" else "Hold the draw", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    when (e.id) {
                        in mastered -> Text("✓ Mastered", color = Palette.Endgame, fontWeight = FontWeight.Bold)
                        in slipped -> Text("Try again", color = Palette.Gold, fontWeight = FontWeight.Bold)
                        else -> Text("New", color = Palette.TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mastered.isNotEmpty()) {
                item(key = "show-mastered") {
                    TextButton(onClick = { showMastered = !showMastered }, colors = clubTextButtonColors()) {
                        Text(
                            if (showMastered) "Hide mastered" else "Show ${mastered.size} mastered",
                            color = Palette.Endgame,
                        )
                    }
                }
            }
            item {
                Text("Puzzles", color = Palette.Endgame, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Shorter endings from 1800 up, easiest of those first. A clean solve comes back once, after 3 days, then it's done. A section stays hidden until one is due again.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(sections, key = { it.first }) { (type, name, counts) ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
                        .clickable { onOpen(PuzzleSets.endgameKey(type)) }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (counts.due > 0) "${counts.due} due" else "${counts.solved}/${counts.total}",
                            color = if (counts.due > 0) Palette.Gold else Palette.Endgame,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (counts.total == 0) 0f else counts.solved.toFloat() / counts.total },
                        color = Palette.Endgame,
                        trackColor = Palette.SurfaceHigh,
                        // No end dot and no gap: an empty bar is just the track.
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
            if (solvedSections > 0) {
                item(key = "show-solved") {
                    TextButton(onClick = { showSolved = !showSolved }, colors = clubTextButtonColors()) {
                        val noun = if (solvedSections == 1) "section" else "sections"
                        Text(
                            if (showSolved) "Hide solved sections" else "Show $solvedSections solved $noun",
                            color = Palette.Endgame,
                        )
                    }
                }
            }
        }
    }
}
