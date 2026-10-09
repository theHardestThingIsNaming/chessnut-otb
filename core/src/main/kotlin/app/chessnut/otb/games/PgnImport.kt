package app.chessnut.otb.games

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

/** Reads the first game of a PGN (for example one shared from the Chessnut app). Variations and comments are dropped. */
object PgnImport {
    private val tagLine = Regex("""\[([A-Za-z0-9_]+)\s+"(.*)"\]""")
    private val comment = Regex("""\{[^\}]*\}|;[^\n]*""")
    private val nag = Regex("""\$\d+""")
    private val moveNumber = Regex("""\d+\.(\.\.)?""")
    private val result = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")

    fun parse(text: String, now: Long): SavedGame {
        val tags = mutableMapOf<String, String>()
        val movetext = StringBuilder()
        for (line in text.removePrefix("\uFEFF").lines()) {
            val trimmed = line.trim()
            val match = tagLine.matchEntire(trimmed)
            when {
                match != null -> {
                    if (movetext.isNotBlank()) break
                    tags[match.groupValues[1]] = match.groupValues[2].replace("\\\"", "\"")
                }
                else -> movetext.append(trimmed).append(' ')
            }
        }
        val tokens = stripVariations(movetext.toString().replace(comment, " "))
            .replace(nag, " ")
            .replace(moveNumber, " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && !result.matches(it) }
        require(tokens.isNotEmpty()) { "No moves found in that PGN." }

        val board = Board()
        val sans = mutableListOf<String>()
        for (token in tokens) {
            val list = MoveList(board.fen)
            val clean = token.trimEnd('!', '?', '+', '#')
            val move = runCatching { list.apply { loadFromSan(clean) }.last() }.getOrNull()
                ?: throw IllegalArgumentException("Move ${sans.size / 2 + 1} ($token) is not legal here.")
            sans += MoveList(board.fen).apply { add(move) }.toSan().trim()
            board.doMove(move)
        }
        return SavedGame(
            id = SavedGame.idFor(sans, emptyList()),
            createdAt = now,
            source = GameSource.Pgn,
            sanMoves = sans,
            isComplete = true,
            stopReason = null,
            white = tags["White"].orEmpty().takeUnless { it == "?" }.orEmpty(),
            black = tags["Black"].orEmpty().takeUnless { it == "?" }.orEmpty(),
            event = tags["Event"].orEmpty().takeUnless { it == "?" }.orEmpty(),
            date = tags["Date"].orEmpty(),
            result = tags["Result"] ?: "*",
        )
    }

    private fun stripVariations(text: String): String {
        val out = StringBuilder()
        var depth = 0
        for (c in text) {
            when {
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
                depth == 0 -> out.append(c)
            }
        }
        return out.toString()
    }
}
