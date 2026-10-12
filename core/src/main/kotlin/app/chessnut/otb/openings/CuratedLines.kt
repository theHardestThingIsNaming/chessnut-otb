package app.chessnut.otb.openings

/**
 * Lines chosen by hand, ahead of the raw book. The book ranks a move by how
 * many named variations mention it, which fills the Bishop's Opening with old
 * gambits. These follow the replies Lichess players rated 1400–2000 actually
 * play, and White's move in each is the one that keeps the advantage.
 */
object CuratedLines {
    /** Next moves to drill after [line], or null when [family] has no curated set. An empty list means the line ends here. */
    fun replies(family: String, line: List<String>): List<String>? {
        val lines = LINES[family] ?: return null
        if (lines.none { it.size >= line.size && it.take(line.size) == line }) return null
        return lines.filter { it.size > line.size && it.take(line.size) == line }.map { it[line.size] }.distinct()
    }

    private fun san(line: String) = line.split(' ').filter { it.isNotBlank() }

    private val LINES = mapOf(
        "Bishop's Opening" to listOf(
            // 2...Nf6 is the most common reply in the 1400–2000 band. 3.Nc3 is the move that keeps a plus.
            san("e4 e5 Bc4 Nf6 Nc3 Nc6 Nf3 Nxe4 Nxe4 d5 Bd3 dxe4 Bxe4 Bd6 d4"),
            // 2...Nc6 is next. 3.d3 stays in the Bishop's Opening; 3.Nf3 is the Italian, already drilled there.
            san("e4 e5 Bc4 Nc6 d3 Nf6 Nf3 Bc5 c3 a5 O-O d6 Nbd2 O-O h3"),
            // 2...Bc5. 3.Nf3 attacks e5. This is where the punishes live.
            san("e4 e5 Bc4 Bc5 Nf3 Nc6 c3 Nf6 d3 a5 O-O d6 Re1 O-O h3"),
            // 2...d6. 3.Nf3, then break with d4.
            san("e4 e5 Bc4 d6 Nf3 Nf6 Nc3 Be7 d4 exd4 Nxd4 Nc6 O-O O-O a4"),
        ),
    )
}
