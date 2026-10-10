package app.chessnut.otb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chessnut.otb.analysis.Material
import app.chessnut.otb.ui.ClockFace

/**
 * One side of the table, laid out like Chess.com: a king badge in the player's
 * colour, their name with the pieces they have taken and any material lead,
 * and their clock on the right — light for White, dark for Black, dimmed while
 * the other side is thinking.
 */
@Composable
fun PlayerBar(
    name: String,
    white: Boolean,
    fen: String?,
    millis: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
    flagged: Boolean = false,
) {
    val (byWhite, byBlack) = remember(fen) { fen?.let(Material::captured) ?: ("" to "") }
    val taken = if (white) byWhite else byBlack
    val lead = remember(fen) { fen?.let { Material.balance(it).points } ?: 0 }.let { if (white) it else -it }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (white) Palette.LightSquare else Palette.DarkSquare),
            contentAlignment = Alignment.Center,
        ) {
            PieceIcon(if (white) 'K' else 'k', Modifier.size(30.dp), background = null)
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = Palette.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (taken.isNotEmpty()) {
                    CapturedPieces(taken, Modifier.height(18.dp).width((18 * capturedWidth(taken)).dp))
                }
                if (lead > 0) {
                    Text(
                        "+$lead",
                        color = Palette.TextMuted.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        ClockFace(millis, white, running, Modifier.width(116.dp), flagged = flagged)
    }
}
