package app.chessnut.otb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The small pictures beside menu options, so each can be told apart at a glance and not only by reading it. */
enum class MenuIcon { Plan, MasterGame, PlayOut, Analyse, Back, Forward, Pause, Restart, Hint, Book, Clock, Target, Bolt, Shield, Openings, First, Last, Cross, Check }

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
            MenuIcon.Back -> {
                drawLine(color, Offset(w * 0.88f, w * 0.5f), Offset(w * 0.15f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, w * 0.22f), Offset(w * 0.13f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, w * 0.78f), Offset(w * 0.13f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            MenuIcon.Forward -> {
                drawLine(color, Offset(w * 0.12f, w * 0.5f), Offset(w * 0.85f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.6f, w * 0.22f), Offset(w * 0.87f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.6f, w * 0.78f), Offset(w * 0.87f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            MenuIcon.Pause -> {
                drawRoundRect(color, Offset(w * 0.2f, w * 0.15f), Size(w * 0.2f, w * 0.7f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
                drawRoundRect(color, Offset(w * 0.6f, w * 0.15f), Size(w * 0.2f, w * 0.7f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            }
            // A circling arrow: go again.
            MenuIcon.Restart -> {
                drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.17f, w * 0.17f), size = Size(w * 0.66f, w * 0.66f), style = line)
                val tip = Path().apply {
                    moveTo(w * 0.64f, w * 0.08f)
                    lineTo(w * 0.9f, w * 0.2f)
                    lineTo(w * 0.62f, w * 0.34f)
                    close()
                }
                drawPath(tip, color)
            }
            // A bulb: a hint.
            MenuIcon.Hint -> {
                drawCircle(color, radius = w * 0.27f, center = Offset(w * 0.5f, w * 0.4f))
                drawLine(color, Offset(w * 0.38f, w * 0.78f), Offset(w * 0.62f, w * 0.78f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, w * 0.92f), Offset(w * 0.58f, w * 0.92f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                drawRect(color, Offset(w * 0.4f, w * 0.6f), Size(w * 0.2f, w * 0.15f))
            }
            // An open book: learn it.
            MenuIcon.Book -> {
                val left = Path().apply {
                    moveTo(w * 0.5f, w * 0.25f); lineTo(w * 0.1f, w * 0.18f); lineTo(w * 0.1f, w * 0.78f); lineTo(w * 0.5f, w * 0.85f); close()
                }
                val right = Path().apply {
                    moveTo(w * 0.5f, w * 0.25f); lineTo(w * 0.9f, w * 0.18f); lineTo(w * 0.9f, w * 0.78f); lineTo(w * 0.5f, w * 0.85f); close()
                }
                drawPath(left, color)
                drawPath(right, color.copy(alpha = 0.75f))
            }
            // A clock: due for review.
            MenuIcon.Clock -> {
                drawCircle(color, radius = w * 0.4f, center = Offset(w / 2, w / 2), style = line)
                drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.5f, w * 0.27f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.67f, w * 0.6f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            // A target: practise it.
            MenuIcon.Target -> {
                drawCircle(color, radius = w * 0.42f, center = Offset(w / 2, w / 2), style = line)
                drawCircle(color, radius = w * 0.22f, center = Offset(w / 2, w / 2), style = line)
                drawCircle(color, radius = w * 0.07f, center = Offset(w / 2, w / 2))
            }
            // A bolt: punish.
            MenuIcon.Bolt -> {
                val bolt = Path().apply {
                    moveTo(w * 0.58f, w * 0.05f); lineTo(w * 0.2f, w * 0.56f); lineTo(w * 0.46f, w * 0.56f)
                    lineTo(w * 0.38f, w * 0.95f); lineTo(w * 0.8f, w * 0.4f); lineTo(w * 0.54f, w * 0.4f); close()
                }
                drawPath(bolt, color)
            }
            // A shield: survive.
            MenuIcon.Shield -> {
                val shield = Path().apply {
                    moveTo(w * 0.5f, w * 0.06f); lineTo(w * 0.86f, w * 0.2f); lineTo(w * 0.84f, w * 0.52f)
                    quadraticBezierTo(w * 0.8f, w * 0.78f, w * 0.5f, w * 0.95f)
                    quadraticBezierTo(w * 0.2f, w * 0.78f, w * 0.16f, w * 0.52f); lineTo(w * 0.14f, w * 0.2f); close()
                }
                drawPath(shield, color)
            }
            // A bar and an arrow: the start of the game, and the latest position.
            MenuIcon.First -> {
                drawLine(color, Offset(w * 0.18f, w * 0.2f), Offset(w * 0.18f, w * 0.8f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, w * 0.2f), Offset(w * 0.36f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, w * 0.8f), Offset(w * 0.36f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            MenuIcon.Last -> {
                drawLine(color, Offset(w * 0.82f, w * 0.2f), Offset(w * 0.82f, w * 0.8f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, w * 0.2f), Offset(w * 0.64f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, w * 0.8f), Offset(w * 0.64f, w * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            MenuIcon.Cross -> {
                drawLine(color, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.8f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.8f, w * 0.2f), Offset(w * 0.2f, w * 0.8f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            }
            MenuIcon.Check -> {
                drawLine(color, Offset(w * 0.15f, w * 0.55f), Offset(w * 0.4f, w * 0.78f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, w * 0.78f), Offset(w * 0.87f, w * 0.25f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            }
            // A board: back to the openings.
            MenuIcon.Openings -> for (r in 0..1) for (c in 0..1) {
                drawRoundRect(
                    color.copy(alpha = if ((r + c) % 2 == 0) 1f else 0.45f),
                    Offset(w * (0.1f + 0.42f * c), w * (0.1f + 0.42f * r)), Size(w * 0.38f, w * 0.38f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
                )
            }
        }
    }
}

/** A [MenuIcon] in the form [GameAction] takes: drawn in the button's own text colour. */
fun menuIcon(icon: MenuIcon): @Composable (Color) -> Unit = { color -> MenuIcon(icon, color, Modifier.size(18.dp)) }
