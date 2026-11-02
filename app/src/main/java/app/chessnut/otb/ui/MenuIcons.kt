package app.chessnut.otb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The small pictures beside menu options, so each can be told apart at a glance and not only by reading it. */
enum class MenuIcon { Plan, MasterGame, PlayOut, Analyse }

@Composable
fun MenuIcon(icon: MenuIcon, color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val w = size.minDimension
        val line = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (icon) {
            // A checklist: the plan, step by step.
            MenuIcon.Plan -> for (i in 0..2) {
                val y = w * (0.22f + 0.28f * i)
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.15f, y))
                drawLine(color, Offset(w * 0.35f, y), Offset(w * 0.92f, y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            // A star: a game to learn from.
            MenuIcon.MasterGame -> {
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) w * 0.48f else w * 0.2f
                    val a = -PI / 2 + i * PI / 5
                    val p = Offset(w / 2 + (r * cos(a)).toFloat(), w / 2 + (r * sin(a)).toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color)
            }
            // A play triangle: carry on and play.
            MenuIcon.PlayOut -> {
                val path = Path().apply {
                    moveTo(w * 0.25f, w * 0.12f)
                    lineTo(w * 0.88f, w * 0.5f)
                    lineTo(w * 0.25f, w * 0.88f)
                    close()
                }
                drawPath(path, color)
            }
            // A magnifying glass: look the game over.
            MenuIcon.Analyse -> {
                drawCircle(color, radius = w * 0.29f, center = Offset(w * 0.42f, w * 0.42f), style = line)
                drawLine(color, Offset(w * 0.64f, w * 0.64f), Offset(w * 0.9f, w * 0.9f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            }
        }
    }
}
