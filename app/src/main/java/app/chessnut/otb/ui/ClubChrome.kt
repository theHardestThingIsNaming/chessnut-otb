package app.chessnut.otb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClubAtmosphere(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Color(0x18D7B56A),
                radius = size.minDimension * 0.85f,
                center = Offset(size.width * 0.5f, size.height * 0.08f),
            )
        }
        content()
    }
}

@Composable
fun clubChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Palette.SurfaceHigh,
    labelColor = Palette.Text,
    iconColor = Palette.Text,
    selectedContainerColor = Palette.Gold,
    selectedLabelColor = Palette.OnGold,
    selectedLeadingIconColor = Palette.OnGold,
)

@Composable
fun clubTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = Palette.Gold,
    disabledContentColor = Palette.Text.copy(alpha = 0.35f),
)

@Composable
fun clubOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = Palette.Gold,
    disabledContentColor = Palette.Text.copy(alpha = 0.35f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun clubAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    titleContentColor = Palette.Text,
    actionIconContentColor = Palette.Gold,
    navigationIconContentColor = Palette.Gold,
    scrolledContainerColor = Color.Transparent,
)

fun Modifier.clubPanel(): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return clip(shape)
        .background(Palette.Surface)
        .border(1.dp, Palette.GoldDim, shape)
}

@Composable
fun ResultChip(result: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val (bg, fg) = when (result) {
        "1-0" -> Palette.Paper to Palette.Ink
        "0-1" -> Color(0xFF1A1A1A) to Palette.Text
        "1/2-1/2" -> Palette.SurfaceHigh to Palette.Gold
        else -> Palette.SurfaceHigh to Palette.Gold
    }
    val label = if (result == "*") "*" else result
    Text(
        label,
        color = fg,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, Palette.GoldDim, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
