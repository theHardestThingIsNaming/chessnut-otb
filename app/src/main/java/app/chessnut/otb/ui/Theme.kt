package app.chessnut.otb.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0C0B09)
    val BackgroundGlow = Color(0xFF1A1610)
    val Surface = Color(0xFF16130F)
    val SurfaceHigh = Color(0xFF1F1B16)
    val Gold = Color(0xFFD7B56A)
    val GoldDim = Color(0x66D7B56A)
    val OnGold = Color(0xFF1C1406)
    val Text = Color(0xFFF4EDE1)
    val TextMuted = Color(0xFFE4DBCE)
    val Outline = Color(0xFF3E372E)
    val Error = Color(0xFFE07A5F)
    val LightSquare = Color(0xFFE6D0A8)
    val DarkSquare = Color(0xFF7C5433)
    val LastMove = Color(0x80C9A227)
    val Selected = Color(0x8020A0FF)
    val Inaccuracy = Color(0xFFE6C84F)
    val Mistake = Color(0xFFE69F3C)
    val Blunder = Color(0xFFE05A47)
    val Brilliant = Color(0xFF3DDEC8)
    val Best = Color(0xFF8FBF73)
    val Excellent = Color(0xFF7EB6FF)
    val ArrowBest = Color(0xCC15781B)
    val ArrowBetter = Color(0xCC2F6FD0)
    val ArrowBook = Color(0xCCD7B56A)
    val ArrowThreat = Color(0xCCD03A2F)
    val Gambit = Color(0x66E07A5F)
    val BezelDark = Color(0xFF2A2118)
    val BezelMid = Color(0xFFB89555)
    val BezelLight = Color(0xFFE8C98A)
    val Paper = Color(0xFFEDE4D4)
    val Ink = Color(0xFF1C1406)
}

private val ClubTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.4.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

private val ClubShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun ClubGamesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.OnGold,
            primaryContainer = Palette.SurfaceHigh,
            onPrimaryContainer = Palette.Text,
            secondary = Palette.Gold,
            onSecondary = Palette.OnGold,
            secondaryContainer = Palette.SurfaceHigh,
            onSecondaryContainer = Palette.Text,
            tertiary = Palette.Gold,
            onTertiary = Palette.OnGold,
            tertiaryContainer = Palette.SurfaceHigh,
            onTertiaryContainer = Palette.Text,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.Text,
            surfaceBright = Palette.SurfaceHigh,
            surfaceDim = Palette.Background,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHigh,
            surfaceContainerHighest = Palette.SurfaceHigh,
            surfaceContainerLow = Palette.Surface,
            surfaceContainerLowest = Palette.Background,
            inverseSurface = Palette.Paper,
            inverseOnSurface = Palette.Ink,
            inversePrimary = Palette.Gold,
            outline = Palette.Outline,
            outlineVariant = Palette.Outline,
            error = Palette.Error,
            onError = Palette.Text,
            errorContainer = Color(0xFF3A221C),
            onErrorContainer = Palette.Error,
        ),
        typography = ClubTypography,
        shapes = ClubShapes,
        content = content,
    )
}
