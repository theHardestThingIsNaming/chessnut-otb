package app.chessnut.otb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.chessnut.otb.chess.PositionSetup

/**
 * Picks up from pieces set up on the board: asks whose move it is, then
 * analyses the position or plays the bot from it. [placement] is the board's
 * reading, pieces only.
 */
@Composable
fun PositionDialog(
    placement: String,
    onDismiss: () -> Unit,
    onAnalyse: (fen: String) -> Unit,
    onPlay: (fen: String, userWhite: Boolean) -> Unit,
) {
    var whiteToMove by remember { mutableStateOf(true) }
    var youWhite by remember { mutableStateOf(true) }
    val result = remember(placement, whiteToMove) { PositionSetup.fen(placement, whiteToMove) }
    val fen = (result as? PositionSetup.Result.Ok)?.fen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("From this position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChessBoard(fen = placement, modifier = Modifier.fillMaxWidth(), flipped = !youWhite, coordinates = false)
                Text("Whose move is it?", color = Palette.Text, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = whiteToMove, onClick = { whiteToMove = true; youWhite = true }, label = { Text("White") }, colors = clubChipColors())
                    FilterChip(selected = !whiteToMove, onClick = { whiteToMove = false; youWhite = false }, label = { Text("Black") }, colors = clubChipColors())
                }
                Text("Against the bot, you play", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = youWhite, onClick = { youWhite = true }, label = { Text("White") }, colors = clubChipColors())
                    FilterChip(selected = !youWhite, onClick = { youWhite = false }, label = { Text("Black") }, colors = clubChipColors())
                }
                (result as? PositionSetup.Result.Problem)?.let { Text(it.reason, color = Palette.Error, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameAction(
                        "Analyse",
                        light = false,
                        onClick = { fen?.let(onAnalyse) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                    PrimaryAction("Play the bot", Modifier.weight(1f).height(48.dp), enabled = fen != null) {
                        fen?.let { onPlay(it, youWhite) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.TextMuted) } },
    )
}
