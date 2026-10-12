package app.chessnut.otb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A pass or fail, big enough to see at a glance with the sound off: Punish mode and puzzles. */
@Composable
fun ResultMark(pass: Boolean, words: String) {
    val color = if (pass) Palette.Accent else Palette.Blunder
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).border(1.dp, color, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(if (pass) "✓" else "✗", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Text(words, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
