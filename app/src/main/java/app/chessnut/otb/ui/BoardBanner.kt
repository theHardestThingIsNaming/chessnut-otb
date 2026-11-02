package app.chessnut.otb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The large notice every screen that plays on the board shows when the pieces stop matching the game:
 * what is wrong, in a red panel that can't be missed across the table, with room for an action.
 */
@Composable
fun BoardBanner(title: String, text: String, modifier: Modifier = Modifier, action: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Palette.Error.copy(alpha = 0.18f))
            .border(2.dp, Palette.Error, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.Error)
        Text(text, color = Palette.Text)
        action?.invoke(this)
    }
}
