package app.chessnut.otb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Locale

fun formatClock(millis: Long): String {
    val ms = millis.coerceAtLeast(0)
    if (ms < 10_000) return String.format(Locale.ROOT, "0:%04.1f", ms / 1000.0)
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
fun BoardMark(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val cell = size.minDimension / 4f
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val dark = (row + col) % 2 == 1
                drawRect(
                    if (dark) tint else tint.copy(alpha = 0.35f),
                    Offset(col * cell, row * cell),
                    Size(cell, cell),
                )
            }
        }
    }
}

@Composable
fun PawnMark(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(tint, radius = w * 0.16f, center = Offset(w / 2f, h * 0.32f))
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.62f, h * 0.58f)
            lineTo(w * 0.38f, h * 0.58f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun BookMark(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round)
        val left = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.18f)
            lineTo(size.width * 0.46f, size.height * 0.18f)
            lineTo(size.width * 0.50f, size.height * 0.32f)
            lineTo(size.width * 0.50f, size.height * 0.84f)
            lineTo(size.width * 0.30f, size.height * 0.76f)
            lineTo(size.width * 0.18f, size.height * 0.76f)
            close()
        }
        val right = Path().apply {
            moveTo(size.width * 0.82f, size.height * 0.18f)
            lineTo(size.width * 0.54f, size.height * 0.18f)
            lineTo(size.width * 0.50f, size.height * 0.32f)
            lineTo(size.width * 0.50f, size.height * 0.84f)
            lineTo(size.width * 0.70f, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height * 0.76f)
            close()
        }
        drawPath(left, tint, style = stroke)
        drawPath(right, tint, style = stroke)
    }
}

@Composable
fun ClockMark(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.38f, style = stroke)
        drawLine(tint, Offset(size.width / 2f, size.height * 0.32f), Offset(size.width / 2f, size.height * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width / 2f, size.height * 0.52f), Offset(size.width * 0.66f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun HalfMark(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f)
        drawCircle(tint, radius = size.minDimension * 0.36f, style = stroke)
        val path = Path().apply {
            moveTo(size.width / 2f, size.height * 0.16f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    size.width * 0.14f,
                    size.height * 0.14f,
                    size.width * 0.86f,
                    size.height * 0.86f,
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun BackMark(tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        val y = size.height * 0.5f
        drawLine(tint, Offset(size.width * 0.75f, y), Offset(size.width * 0.28f, y), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, y), Offset(size.width * 0.46f, size.height * 0.30f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, y), Offset(size.width * 0.46f, size.height * 0.70f), stroke, StrokeCap.Round)
    }
}

@Composable
fun FlipMark(tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawLine(tint, Offset(size.width * 0.32f, size.height * 0.72f), Offset(size.width * 0.32f, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.32f, size.height * 0.28f), Offset(size.width * 0.18f, size.height * 0.44f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.32f, size.height * 0.28f), Offset(size.width * 0.46f, size.height * 0.44f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.68f, size.height * 0.28f), Offset(size.width * 0.68f, size.height * 0.72f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.68f, size.height * 0.72f), Offset(size.width * 0.54f, size.height * 0.56f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.68f, size.height * 0.72f), Offset(size.width * 0.82f, size.height * 0.56f), stroke, StrokeCap.Round)
    }
}

@Composable
fun MoreMark(tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.08f
        val y = size.height * 0.5f
        listOf(0.28f, 0.5f, 0.72f).forEach { x ->
            drawCircle(tint, radius, Offset(size.width * x, y))
        }
    }
}

/** A pawn, used to mark a gambit move. */
@Composable
fun GambitMark(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        drawCircle(tint, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.5f, size.height * 0.28f))
        val body = Path().apply {
            moveTo(size.width * 0.38f, size.height * 0.46f)
            lineTo(size.width * 0.62f, size.height * 0.46f)
            lineTo(size.width * 0.74f, size.height * 0.78f)
            lineTo(size.width * 0.26f, size.height * 0.78f)
            close()
        }
        drawPath(body, tint)
    }
}

@Composable
fun FlagMark(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, size.height * 0.12f), Offset(size.width * 0.28f, size.height * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        val flag = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.16f)
            lineTo(size.width * 0.82f, size.height * 0.16f)
            lineTo(size.width * 0.68f, size.height * 0.36f)
            lineTo(size.width * 0.82f, size.height * 0.56f)
            lineTo(size.width * 0.32f, size.height * 0.56f)
            close()
        }
        drawPath(flag, tint)
    }
}

@Composable
fun ShareMark(tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val r = size.minDimension * 0.11f
        val a = Offset(size.width * 0.72f, size.height * 0.24f)
        val b = Offset(size.width * 0.28f, size.height * 0.50f)
        val c = Offset(size.width * 0.72f, size.height * 0.76f)
        val stroke = size.minDimension * 0.07f
        drawLine(tint, a, b, stroke)
        drawLine(tint, b, c, stroke)
        drawCircle(tint, r, a)
        drawCircle(tint, r, b)
        drawCircle(tint, r, c)
    }
}
