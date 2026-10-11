package app.chessnut.otb.openings

/**
 * Book moves the trainer never teaches as yours: famous traps where your side
 * is the one falling in, and moves Stockfish rates far below the best (each
 * gives away 1.5 pawns or more at depth 20). Where one is skipped, the line stops before it, or
 * carries on with the engine's move from the custom book.
 */
object BookBlunders {
    private fun key(before: List<String>, move: String) = before.joinToString(" ") + " | " + move

    private val AVOID: Set<String> = listOf(
        // Noah's Ark: ...c5 and ...c4 trap the b3 bishop.
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 b5 Bb3 d6 d4 Nxd4 Nxd4 exd4" to "Qxd4",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 d4 Nxd4 Nxd4 exd4" to "Qxd4",
        // Evans Gambit declined: Bxf7+ is far stronger than Qf3 or d4 here.
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bb6 b5 Na5 Nxe5 Qg5" to "Qf3",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bb6 b5 Na5 Nxe5 Nh6" to "d4",
    ).map { (before, move) -> key(before.split(' '), move) }.toSet()

    /** [move] after [before] is a known blunder for the side playing it. */
    fun avoid(before: List<String>, move: String): Boolean = key(before, move) in AVOID
}
