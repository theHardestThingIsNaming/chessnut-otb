package app.chessnut.otb.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    /** Charcoal surfaces like Chess.com, so the green board sits naturally. */
    val Background = Color(0xFF1F1D1B)
    val Surface = Color(0xFF2A2724)
    val SurfaceHigh = Color(0xFF36322E)
    /** Primary actions: the board's green. */
    val Accent = Color(0xFF81B64C)
    val AccentDim = Color(0x5581B64C)
    val OnAccent = Color(0xFFFFFFFF)
    /** Highlights only: scores, due counts, move ratings. */
    val Gold = Color(0xFFE2B656)
    val GoldDim = Color(0x66E2B656)
    val Text = Color(0xFFF2F1F0)
    val TextMuted = Color(0xFFBAB8B5)
    val Outline = Color(0xFF4A4643)
    val Error = Color(0xFFE5603D)
    val LightSquare = Color(0xFFEBECD0)
    val DarkSquare = Color(0xFF739552)
    val LastMove = Color(0x99FFFF33)
    val Selected = Color(0x99FFFF33)
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
    val Paper = Color(0xFFF2F1F0)
    val Ink = Color(0xFF312E2B)
}

private val ClubTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.4.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
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
            primary = Palette.Accent,
            onPrimary = Palette.OnAccent,
            primaryContainer = Palette.SurfaceHigh,
            onPrimaryContainer = Palette.Text,
            secondary = Palette.Accent,
            onSecondary = Palette.OnAccent,
            secondaryContainer = Palette.SurfaceHigh,
            onSecondaryContainer = Palette.Text,
            tertiary = Palette.Accent,
            onTertiary = Palette.OnAccent,
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
            inversePrimary = Palette.Accent,
            outline = Palette.Outline,
            outlineVariant = Palette.Outline,
            error = Palette.Error,
            onError = Palette.Text,
            errorContainer = Color(0xFF4A2A22),
            onErrorContainer = Palette.Error,
        ),
        typography = ClubTypography,
        shapes = ClubShapes,
        content = content,
    )
}
