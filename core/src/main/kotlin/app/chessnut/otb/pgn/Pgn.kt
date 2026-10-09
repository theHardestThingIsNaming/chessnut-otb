package app.chessnut.otb.pgn

data class GameHeaders(
    val event: String = "",
    val site: String = "",
    val date: String = "",
    val round: String = "",
    val white: String = "",
    val black: String = "",
    val result: String = "",
)

private val tagLine = Regex("""\[([A-Za-z0-9_]+)\s+"(.*)"\]""")
private val trailingResult = Regex("""(?:1-0|0-1|1/2-1/2|\*)\s*$""")

/**
 * Rewrites the Seven Tag Roster fields that [headers] actually sets, and
 * leaves every other tag and the movetext alone.
 */
fun applyHeaders(pgn: String, headers: GameHeaders): String {
    val text = pgn.trim().removePrefix("\uFEFF")
    val lines = text.lines()
    val tags = linkedMapOf<String, String>()
    var index = 0
    while (index < lines.size && lines[index].trimStart().startsWith("[")) {
        val match = tagLine.matchEntire(lines[index].trim())
        if (match != null) {
            tags[match.groupValues[1]] = unescape(match.groupValues[2])
        }
        index++
    }
    while (index < lines.size && lines[index].isBlank()) index++
    var movetext = lines.drop(index).joinToString("\n").trim()

    fun put(key: String, value: String) {
        if (value.isNotBlank()) tags[key] = value
    }
    put("Event", headers.event)
    put("Site", headers.site)
    put("Date", headers.date)
    put("Round", headers.round)
    put("White", headers.white)
    put("Black", headers.black)
    put("Result", headers.result)
    if (headers.result.isNotBlank() && headers.result != "*") {
        movetext = trailingResult.replace(movetext, "").trim()
        movetext = if (movetext.isEmpty()) headers.result else "$movetext ${headers.result}"
    }

    val order = listOf("Event", "Site", "Date", "Round", "White", "Black", "Result")
    return buildString {
        val emitted = mutableSetOf<String>()
        for (key in order) {
            val value = tags[key] ?: continue
            append('[').append(key).append(" \"").append(escape(value)).append("\"]\n")
            emitted += key
        }
        for ((key, value) in tags) {
            if (key in emitted) continue
            append('[').append(key).append(" \"").append(escape(value)).append("\"]\n")
        }
        if (isNotEmpty()) append('\n')
        append(movetext)
        if (movetext.isNotEmpty()) append('\n')
    }
}

fun movetextOf(sanMoves: List<String>): String = buildString {
    sanMoves.forEachIndexed { ply, san ->
        if (ply % 2 == 0) {
            if (isNotEmpty()) append(' ')
            append(ply / 2 + 1).append(". ").append(san)
        } else {
            append(' ').append(san)
        }
    }
}

private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")
