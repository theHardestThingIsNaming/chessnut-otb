package app.chessnut.otb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.chessnut.otb.OtbApp
import app.chessnut.otb.flags.FlagKey
import app.chessnut.otb.flags.MoveFlag
import app.chessnut.otb.flags.MoveInsights

/**
 * A quiet way to ask for more on a move. Hollow bookmark: tap to flag the move last played (with an optional
 * question); filled: tap to change the question or take it back. Once an explanation has been written for the move,
 * an "i" opens it. [moves] are the UCI moves from the start, ending with the move in question.
 */
@Composable
fun FlagControl(
    moves: List<String>,
    source: String,
    context: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as OtbApp
    val key = remember(moves) { FlagKey.of(moves) } ?: return
    var version by remember { mutableIntStateOf(0) }
    val flag = remember(key, version) { app.flags.get(key) }
    val insight = remember(key) { MoveInsights.of(key) }
    var asking by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        if (insight != null) {
            IconButton(onClick = { reading = true }, modifier = Modifier.semantics { contentDescription = "Explanation of this move" }) {
                InfoMark(Palette.Accent)
            }
        }
        IconButton(
            onClick = { asking = true },
            modifier = Modifier.semantics { contentDescription = if (flag != null) "Flagged for a closer look" else "Flag this move for a closer look" },
        ) {
            BookmarkMark(if (flag != null) Palette.Gold else Palette.TextMuted, filled = flag != null)
        }
    }

    if (asking) {
        var question by remember { mutableStateOf(flag?.question.orEmpty()) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(if (flag != null) "Flagged for a closer look" else "Flag this move") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next time the phone is plugged in, this move gets a fuller explanation.", color = Palette.TextMuted)
                    OutlinedTextField(
                        question, { question = it },
                        label = { Text("Anything you want to know? (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.flags.put(MoveFlag(key, moves, source, context, note, question.trim(), System.currentTimeMillis()))
                    version++
                    asking = false
                }) { Text(if (flag != null) "Save" else "Flag it") }
            },
            dismissButton = {
                if (flag != null) {
                    TextButton(onClick = {
                        app.flags.remove(key)
                        version++
                        asking = false
                    }) { Text("Remove flag") }
                } else {
                    TextButton(onClick = { asking = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (reading && insight != null) {
        AlertDialog(
            onDismissRequest = { reading = false },
            title = { Text("This move") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(insight, color = Palette.Text) } },
            confirmButton = { TextButton(onClick = { reading = false }) { Text("Close") } },
        )
    }
}
