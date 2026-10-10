package app.chessnut.otb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

internal class PieceStroke(
    val d: String,
    val fill: Long?,
    val stroke: Long?,
    val width: Float,
    val cap: String,
    val join: String,
)

private class ParsedStroke(val path: Path, val fill: Color?, val stroke: Stroke?, val strokeColor: Color?)

private val parsedPieces: Map<Char, List<ParsedStroke>> by lazy {
    cburnettPieces.mapKeys { (name, _) -> if (name[0] == 'w') name[1] else name[1].lowercaseChar() }
        .mapValues { (_, strokes) ->
            strokes.map { s ->
                val path = PathParser().parsePathString(s.d).toPath().apply { fillType = PathFillType.EvenOdd }
                val stroke = s.stroke?.let {
                    Stroke(
                        width = s.width,
                        cap = when (s.cap) {
                            "round" -> StrokeCap.Round
                            "square" -> StrokeCap.Square
                            else -> StrokeCap.Butt
                        },
                        join = when (s.join) {
                            "round" -> StrokeJoin.Round
                            "bevel" -> StrokeJoin.Bevel
                            else -> StrokeJoin.Miter
                        },
                    )
                }
                ParsedStroke(path, s.fill?.let { Color(it) }, stroke, s.stroke?.let { Color(it) })
            }
        }
}

/** Square index 0..63 with a1 = 0, h8 = 63. */
fun squareName(index: Int): String = "${'a' + index % 8}${index / 8 + 1}"

fun squareIndex(name: String): Int = (name[1] - '1') * 8 + (name[0] - 'a')

/** Expands a FEN (or just its placement) into 64 squares, a1 first; '.' is empty. */
fun squaresOf(fen: String): CharArray {
    val squares = CharArray(64) { '.' }
    val ranks = fen.substringBefore(' ').split('/')
    for ((row, rank) in ranks.withIndex()) {
        var file = 0
        for (c in rank) {
            if (c.isDigit()) {
                file += c.digitToInt()
            } else {
                if (file < 8 && row < 8) squares[(7 - row) * 8 + file] = c
                file++
            }
        }
    }
    return squares
}

data class Arrow(val from: Int, val to: Int, val color: Color) {
    companion object {
        fun ofUci(uci: String?, color: Color): Arrow? {
            if (uci == null || uci.length < 4) return null
            return Arrow(squareIndex(uci.substring(0, 2)), squareIndex(uci.substring(2, 4)), color)
        }
    }
}

@Composable
fun ChessBoard(
    fen: String,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    lastMove: Pair<Int, Int>? = null,
    selected: Int? = null,
    targets: Set<Int> = emptySet(),
    arrows: List<Arrow> = emptyList(),
    highlights: Set<Int> = emptySet(),
    coordinates: Boolean = true,
    onSquare: ((Int) -> Unit)? = null,
    onDrag: ((Int, Int) -> Unit)? = null,
) {
    val squares = squaresOf(fen)
    val measurer = rememberTextMeasurer()
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragAt by remember { mutableStateOf<Offset?>(null) }
    val fenNow = rememberUpdatedState(fen)
    val onSquareNow = rememberUpdatedState(onSquare)
    val onDragNow = rememberUpdatedState(onDrag)
    val gesture = when {
        onDrag != null -> Modifier.pointerInput(flipped) {
            val boardWidth = size.width.toFloat()
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val origin = squareAt(down.position, boardWidth, flipped)
                if (squaresOf(fenNow.value).getOrElse(origin) { '.' } == '.') return@awaitEachGesture
                down.consume()
                var past = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val end = squareAt(change.position, boardWidth, flipped)
                            if (past && end != origin) onDragNow.value?.invoke(origin, end) else onSquareNow.value?.invoke(origin)
                            break
                        }
                        if (!past && (change.position - down.position).getDistance() > slop) past = true
                        if (past) {
                            dragFrom = origin
                            dragAt = change.position
                            change.consume()
                        }
                    }
                } finally {
                    dragFrom = null
                    dragAt = null
                }
            }
        }
        onSquare != null -> Modifier.pointerInput(flipped, onSquare) {
            detectTapGestures { offset ->
                onSquare(squareAt(offset, size.width.toFloat(), flipped))
            }
        }
        else -> Modifier
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(gesture)) {
        val cell = size.width / 8f
        val held = dragFrom
        val finger = dragAt
        for (index in 0 until 64) {
            val file = index % 8
            val rank = index / 8
            val column = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            val topLeft = Offset(column * cell, row * cell)
            val light = (file + rank) % 2 == 1
            drawRect(if (light) Palette.LightSquare else Palette.DarkSquare, topLeft, Size(cell, cell))
            if (lastMove != null && (index == lastMove.first || index == lastMove.second)) {
                drawRect(Palette.LastMove, topLeft, Size(cell, cell))
            }
            if (index == selected) drawRect(Palette.Selected, topLeft, Size(cell, cell))
            if (index in highlights) drawRect(Palette.Gambit, topLeft, Size(cell, cell))
            if (coordinates) {
                // Labels sit inside the edge squares, in the other square colour, as on Chess.com.
                val style = TextStyle(
                    color = if (light) Palette.DarkSquare else Palette.LightSquare,
                    fontSize = (cell * 0.22f).toSp(),
                    fontWeight = FontWeight.Bold,
                )
                if (column == 0) {
                    drawText(measurer, "${rank + 1}", topLeft + Offset(cell * 0.05f, cell * 0.02f), style)
                }
                if (row == 7) {
                    val label = measurer.measure("${'a' + file}", style)
                    drawText(
                        label,
                        topLeft = topLeft + Offset(
                            cell * 0.95f - label.size.width,
                            cell * 0.98f - label.size.height,
                        ),
                    )
                }
            }
            val piece = squares[index]
            if (piece != '.' && index != held) drawPiece(piece, topLeft, cell)
            if (index in targets) {
                drawCircle(Color(0x66000000), cell * 0.16f, topLeft + Offset(cell / 2, cell / 2))
            }
        }
        fun centre(index: Int): Offset {
            val column = if (flipped) 7 - index % 8 else index % 8
            val row = if (flipped) index / 8 else 7 - index / 8
            return Offset((column + 0.5f) * cell, (row + 0.5f) * cell)
        }
        for (arrow in arrows) {
            if (arrow.from != arrow.to) drawArrow(centre(arrow.from), centre(arrow.to), arrow.color, cell)
        }
        if (held != null && finger != null && held in squares.indices && squares[held] != '.') {
            drawPiece(squares[held], finger - Offset(cell / 2f, cell / 2f), cell)
        }
    }
}

/** One piece, on a light square unless [background] is null, for material counts and other small marks. */
@Composable
fun PieceIcon(piece: Char, modifier: Modifier = Modifier, background: Color? = Palette.LightSquare) {
    Canvas(modifier.clip(RoundedCornerShape(2.dp))) {
        background?.let { drawRect(it) }
        val inset = size.minDimension * 0.08f
        drawPiece(piece, Offset(inset, inset), size.minDimension - inset * 2)
    }
}

/** A row of taken pieces, with repeats of one kind tucked behind each other. */
@Composable
fun CapturedPieces(pieces: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.height
        var x = 0f
        pieces.forEachIndexed { i, piece ->
            if (i > 0) x += if (pieces[i - 1] == piece) cell * 0.35f else cell * 0.8f
            drawPiece(piece, Offset(x, 0f), cell)
        }
    }
}

/** How wide [CapturedPieces] needs to be for [pieces], in piece heights. */
fun capturedWidth(pieces: String): Float {
    if (pieces.isEmpty()) return 0f
    var width = 1f
    for (i in 1 until pieces.length) width += if (pieces[i - 1] == pieces[i]) 0.35f else 0.8f
    return width
}

private fun squareAt(offset: Offset, width: Float, flipped: Boolean): Int {
    val cell = width / 8f
    val column = (offset.x / cell).toInt().coerceIn(0, 7)
    val row = (offset.y / cell).toInt().coerceIn(0, 7)
    val file = if (flipped) 7 - column else column
    val rank = if (flipped) row else 7 - row
    return rank * 8 + file
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, cell: Float) {
    val delta = to - from
    val length = delta.getDistance()
    val unit = delta / length
    val normal = Offset(-unit.y, unit.x)
    val headLength = cell * 0.45f
    val headHalfWidth = cell * 0.26f
    val shaftHalfWidth = cell * 0.09f
    val start = from + unit * (cell * 0.2f)
    val headBase = to - unit * headLength
    val path = Path().apply {
        moveTo(start.x + normal.x * shaftHalfWidth, start.y + normal.y * shaftHalfWidth)
        lineTo(headBase.x + normal.x * shaftHalfWidth, headBase.y + normal.y * shaftHalfWidth)
        lineTo(headBase.x + normal.x * headHalfWidth, headBase.y + normal.y * headHalfWidth)
        lineTo(to.x, to.y)
        lineTo(headBase.x - normal.x * headHalfWidth, headBase.y - normal.y * headHalfWidth)
        lineTo(headBase.x - normal.x * shaftHalfWidth, headBase.y - normal.y * shaftHalfWidth)
        lineTo(start.x - normal.x * shaftHalfWidth, start.y - normal.y * shaftHalfWidth)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPiece(piece: Char, topLeft: Offset, cell: Float) {
    val strokes = parsedPieces[piece] ?: return
    translate(topLeft.x, topLeft.y) {
        scale(cell / 45f, pivot = Offset.Zero) {
            for (s in strokes) {
                s.fill?.let { drawPath(s.path, it) }
                if (s.stroke != null && s.strokeColor != null) drawPath(s.path, s.strokeColor, style = s.stroke)
            }
        }
    }
}
