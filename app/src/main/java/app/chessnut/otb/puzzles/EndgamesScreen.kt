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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Endgame puzzles by type, each with how far you've got. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndgamesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentState
    val rows = remember(lifecycle) {
        val progress = PuzzleProgress(context.filesDir)
        val now = System.currentTimeMillis()
        PuzzleSets.ENDGAMES.map { (type, name) -> Triple(type, name, progress.counts(PuzzleSets.endgameKey(type), now)) }
    }
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
                Text(
                    "Real endings from Lichess games, easiest first. Solved ones come back after 1, 3, 7, 14 and 30 days.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(rows, key = { it.first }) { (type, name, counts) ->
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
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}
