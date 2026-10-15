package app.chessnut.otb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A side's clock: light for White, dark for Black, dimmed while the other side is thinking, red once flagged. */
@Composable
fun ClockFace(
    millis: Long,
    white: Boolean,
    running: Boolean,
    modifier: Modifier = Modifier,
    flagged: Boolean = false,
    fontSize: TextUnit = 20.sp,
    /** Nearly out of time: the clock turns red. */
    low: Boolean = false,
) {
    val (fill, ink) = when {
        flagged -> Palette.Blunder to Color.White
        low && running -> Palette.Blunder to Color.White
        low -> Palette.Blunder.copy(alpha = 0.55f) to Color.White
        white && running -> Color.White to Color(0xFF312E2B)
        white -> Color(0xFF989795) to Color(0xFF4B4847)
        running -> Color(0xFF1C1A18) to Color.White
        else -> Color(0xFF262421) to Color(0xFF8B8987)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            formatClock(millis),
            color = ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            style = TextStyle(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

enum class ActionTone { Plain, Active, Danger }

/**
 * A game button such as Draw or Resign, coloured for the side it belongs to.
 * [icon] is drawn in the label colour.
 */
@Composable
fun GameAction(
    label: String,
    light: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Plain,
    icon: (@Composable (Color) -> Unit)? = null,
) {
    val (fill, text) = when (tone) {
        ActionTone.Active -> Palette.Accent to Palette.OnAccent
        ActionTone.Danger -> (if (light) Color(0xFFF6D7CF) else Color(0xFF4A2A22)) to Palette.Error
        ActionTone.Plain -> if (light) Palette.Paper to Palette.Ink else Palette.SurfaceHigh to Palette.Text
    }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(fill).clickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon?.invoke(text)
            Text(label, color = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** One of the |< < > >| keys for stepping through a game's moves. */
@Composable
fun NavKey(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .semantics { if (description != null) contentDescription = description }
            .clip(shape)
            .background(Palette.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Palette.Text else Palette.Text.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** The screen's main action, in the accent green. */
@Composable
fun PrimaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Accent,
    subtext: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color else Palette.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = if (enabled) Palette.OnAccent else Palette.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
            )
            subtext?.let {
                Text(it, color = if (enabled) Palette.OnAccent.copy(alpha = 0.8f) else Palette.TextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
