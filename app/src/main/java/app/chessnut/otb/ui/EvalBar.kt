package app.chessnut.otb.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import java.util.Locale

/** How the position stands, as a white and black bar with the evaluation beside it. */
@Composable
fun EvalBar(score: Score?, terminal: Terminal?, fen: String) {
    val whiteShare = when {
        terminal == Terminal.Checkmate -> if (fen.split(' ')[1] == "w") 0f else 1f
        terminal == Terminal.Stalemate -> 0.5f
        score == null -> 0.5f
        else -> (score.whiteWinPercent / 100).toFloat().coerceIn(0f, 1f)
    }
    val animated by animateFloatAsState(whiteShare, label = "eval")
    val dark = Color(0xFF2B2B2B)
    val label = when (terminal) {
        Terminal.Checkmate -> "#"
        Terminal.Stalemate -> "½"
        null -> score?.let { compactLabel(it) }.orEmpty()
    }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Evaluation $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxSize()) {
                if (animated > 0f) Box(Modifier.fillMaxHeight().weight(animated).background(Color.White))
                if (1f - animated > 0f) Box(Modifier.fillMaxHeight().weight(1f - animated).background(dark))
            }
        }
        if (label.isNotEmpty()) {
            Text(label, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun compactLabel(score: Score): String {
    val mate = score.mate
    return if (mate != null) "M${kotlin.math.abs(mate)}" else String.format(Locale.ROOT, "%.1f", kotlin.math.abs(score.cappedCp) / 100.0)
}
