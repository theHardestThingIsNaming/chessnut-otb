package app.chessnut.otb.repertoire

import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board

/** One chapter of a study: who you play, and every line from the start to the end of a variation. */
data class StudyChapter(val name: String, val userWhite: Boolean, val lines: List<List<String>>)

/**
 * Reads a Lichess study export: several games, each with variations,
 * comments and annotations. Chapters set up from a custom position are
 * skipped, since the trainer always starts from the initial position.
 */
object StudyPgn {
    fun chapters(pgn: String): List<StudyChapter> = games(pgn).mapNotNull { (headers, movetext) ->
        if (headers["FEN"]?.let { it.substringBefore(' ') != START_PLACEMENT } == true) return@mapNotNull null
        val lines = legal(leaves(movetext)).distinct()
        if (lines.isEmpty()) return@mapNotNull null
        val name = headers["ChapterName"]
            ?: headers["Event"]?.substringAfter(": ", headers["Event"].orEmpty())
            ?: "Chapter"
        StudyChapter(name.trim().ifEmpty { "Chapter" }, headers["Orientation"]?.lowercase() != "black", lines)
    }

    /** Splits an export into (headers, movetext) per game. */
    private fun games(pgn: String): List<Pair<Map<String, String>, String>> {
        val out = mutableListOf<Pair<Map<String, String>, String>>()
        var headers = mutableMapOf<String, String>()
        val moves = StringBuilder()
        fun flush() {
            if (headers.isNotEmpty() || moves.isNotBlank()) out += headers to moves.toString()
            headers = mutableMapOf()
            moves.clear()
        }
        for (raw in pgn.lineSequence()) {
            val line = raw.trim()
            val header = HEADER.matchEntire(line)
            if (header != null) {
                if (moves.isNotBlank()) flush()
                headers[header.groupValues[1]] = header.groupValues[2].replace("\\\"", "\"")
            } else {
                moves.append(line).append(' ')
            }
        }
        flush()
        return out
    }

    /** Every line that ends a variation or the main line, as SAN moves. */
    fun leaves(movetext: String): List<List<String>> {
        val leaves = mutableListOf<List<String>>()
        val stack = ArrayDeque<MutableList<String>>()
        var current = mutableListOf<String>()
        var i = 0
        val text = movetext
        while (i < text.length) {
            val c = text[i]
            when {
                c == '{' -> i = text.indexOf('}', i).let { if (it < 0) text.length else it + 1 }
                c == ';' -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it + 1 }
                c == '(' -> {
                    stack.addLast(current)
                    current = current.dropLast(1).toMutableList()
                    i++
                }
                c == ')' -> {
                    if (current.isNotEmpty()) leaves += current.toList()
                    current = stack.removeLastOrNull() ?: mutableListOf()
                    i++
                }
                c.isWhitespace() -> i++
                else -> {
                    var end = i
                    while (end < text.length && !text[end].isWhitespace() && text[end] !in "(){};") end++
                    val token = text.substring(i, end)
                    i = end
                    val san = token.replace(MOVE_NUMBER, "").trimEnd('!', '?')
                    if (san.isEmpty() || san.startsWith("$") || san in RESULTS) continue
                    current += san
                }
            }
        }
        if (current.isNotEmpty()) leaves += current.toList()
        return leaves
    }

    /**
     * Keeps each line up to its first move that is not legal. Lines share
     * most of their moves, so each position and move is worked out once.
     */
    private fun legal(lines: List<List<String>>): List<List<String>> {
        val steps = HashMap<String, Pair<String, String>?>()
        return lines.mapNotNull { line ->
            var fen = Board().fen
            val kept = mutableListOf<String>()
            for (san in line) {
                val step = steps.getOrPut("$fen|$san") {
                    PlayPosition.uciOfSan(fen, san)?.let { PlayPosition.after(fen, it) }
                } ?: break
                kept += step.first
                fen = step.second
            }
            kept.takeIf { it.isNotEmpty() }
        }
    }

    private const val START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    private val HEADER = Regex("""^\[(\w+)\s+"(.*)"\]$""")
    private val MOVE_NUMBER = Regex("""^\d+\.(\.\.)?""")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
}
