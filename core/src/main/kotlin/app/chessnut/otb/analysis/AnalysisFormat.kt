package app.chessnut.otb.analysis

/**
 * Line-based text form of [GameAnalysis]:
 * `engine`, `nodes`, then per position `P <fen>`, optional `T <terminal>`,
 * and `L <depth> cp|mate <value> <uci moves...>` per engine line.
 */
object AnalysisFormat {
    fun write(analysis: GameAnalysis): String = buildString {
        append("engine ").append(analysis.engine).append('\n')
        append("nodes ").append(analysis.nodes).append('\n')
        for (position in analysis.positions) {
            append("P ").append(position.fen).append('\n')
            position.terminal?.let { append("T ").append(it.name).append('\n') }
            for (line in position.lines) {
                append("L ").append(line.depth).append(' ')
                if (line.score.mate != null) append("mate ").append(line.score.mate) else append("cp ").append(line.score.cp)
                if (line.uci.isNotEmpty()) append(' ').append(line.uci.joinToString(" "))
                append('\n')
            }
        }
    }

    fun read(text: String): GameAnalysis {
        var engine = ""
        var nodes = 0L
        val positions = mutableListOf<PositionEval>()
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("engine ") -> engine = line.removePrefix("engine ")
                line.startsWith("nodes ") -> nodes = line.removePrefix("nodes ").toLong()
                line.startsWith("P ") -> positions += PositionEval(line.removePrefix("P "), emptyList())
                line.startsWith("T ") -> positions[positions.lastIndex] =
                    positions.last().copy(terminal = Terminal.valueOf(line.removePrefix("T ")))
                line.startsWith("L ") -> {
                    val parts = line.split(' ')
                    val value = parts[3].toInt()
                    val score = if (parts[2] == "mate") Score(mate = value) else Score(cp = value)
                    val engineLine = EngineLine(score, parts.drop(4), parts[1].toInt())
                    positions[positions.lastIndex] = positions.last().let { it.copy(lines = it.lines + engineLine) }
                }
            }
        }
        return GameAnalysis(engine, nodes, positions)
    }
}
