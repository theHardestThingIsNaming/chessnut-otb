package app.chessnut.otb.openings

/**
 * A common trap: after [before], the opponent's natural-looking first move in
 * [moves] loses, and the rest of [moves] is how you punish it. Each move has a
 * note in [notes]; [summary] says what it wins. A [mistake] is a smaller
 * error than a trap (a pawn or a broken position, not a piece). Every one was
 * checked with Stockfish at depth 20, and kept because Lichess players rated
 * 1400–2000 actually play it.
 */
data class Trap(
    val name: String,
    val before: List<String>,
    val moves: List<String>,
    val notes: List<String>,
    val summary: String,
    val mistake: Boolean = false,
) {
    /** "Trap" or "Common mistake", for buttons and banners. */
    val kind: String get() = if (mistake) "Common mistake" else "Trap"

    init {
        require(moves.size == notes.size) { "$name: one note per move" }
    }
}

object OpeningTraps {
    /** Traps that can happen now, with the opponent to move after [played]. */
    fun at(played: List<String>): List<Trap> = ALL.filter { it.before == played }

    val all: List<Trap> get() = ALL

    private fun san(line: String) = line.split(' ').filter { it.isNotBlank() }

    private val CLASSICAL = san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5 Nb5")

    private val PIRC = san("e4 d6 d4 Nf6 Nc3 g6")

    private val ALL = listOf(
        Trap(
            "The loose bishop",
            CLASSICAL,
            san("O-O Bxc5"),
            listOf(
                "Black castles, but the bishop on c5 is attacked by White's bishop and defended by nothing.",
                "White simply takes it. Nb5 has been threatening c7 too, so Black can't win it back.",
            ),
            "White wins a whole piece.",
        ),
        Trap(
            "The greedy queen",
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+ Be2"),
            san("Qxg2 Nxc7+ Kd8 Bf3 Qh3 Bxb4 Nxb4 Nxa8"),
            listOf(
                "Black takes a second pawn, but the queen leaves the defence of c7.",
                "Check, forking the king and the rook on a8.",
                "The king has to step aside.",
                "Attacks the queen, gaining time.",
                "The queen runs to h3, far from the action.",
                "Removes the bishop that defended the king's side.",
                "Black takes back.",
                "White collects the rook on a8.",
            ),
            "White wins the rook on a8 and Black's queen is stranded on h3.",
        ),
        Trap(
            "The hanging knight",
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5"),
            san("Ng4 Qxg4"),
            listOf(
                "The knight jumps forward to attack e5, but g4 is covered by White's queen.",
                "The queen just takes it.",
            ),
            "White wins a whole knight.",
        ),

        // Pirc: traps White falls into against you.
        Trap(
            "The hanging bishop",
            PIRC + san("Bg5 Bg7 Qd2 h6"),
            san("O-O-O hxg5"),
            listOf(
                "White castles long, forgetting that ...h6 attacked the bishop on g5.",
                "The pawn just takes it.",
            ),
            "Black wins a whole bishop.",
        ),
        Trap(
            "The f7 sacrifice",
            PIRC + san("Bc4 Bg7"),
            san("Bxf7+ Kxf7"),
            listOf(
                "White gives the bishop for the f7 pawn, hoping to catch Black's king in the centre.",
                "The king takes. It's safe on f7, and soon tucks away with ...Rf8 and ...Kg8.",
            ),
            "Black is a bishop for a pawn up, with no real attack against the king.",
        ),
        Trap(
            "The early f5",
            PIRC + san("f4 Bg7 Nf3 O-O e5 Nfd7"),
            san("f5 dxe5 fxg6 hxg6"),
            listOf(
                "White lunges with the f-pawn, but it no longer defends e5.",
                "Black takes on e5, and the d4 pawn is attacked too.",
                "White opens the f-file towards Black's king.",
                "Black takes back. The king is safe and Black is a pawn up with a strong centre.",
            ),
            "Black wins a pawn, and the g7 bishop's diagonal opens.",
        ),
        // Found in club games: moves Lichess players rated 1400–2000 often play here, and lose.
        Trap(
            "The undefended f7 pawn",
            san("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5"),
            san("Ne5 Nxf7 Nxf7 Bxf7+ Kf8"),
            listOf(
                "Black guards f7 and hits the bishop, a popular move (about one game in five).",
                "White takes on f7 anyway.",
                "The knight takes back.",
                "And the bishop takes again, with check.",
                "Kxf7 loses to Qh5+ and Qxc5, so the king must step aside and can no longer castle.",
            ),
            "White wins a pawn and Black's king is stuck in the middle.",
            mistake = true,
        ),
        Trap(
            "The f7 queen check",
            san("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bb4+ c3 dxc3 bxc3"),
            san("Be7 Qd5 Nh6 Bxh6"),
            listOf(
                "The bishop drops back to e7, where it does nothing for f7.",
                "Queen and bishop both aim at f7: Qxf7 is mate.",
                "Only ...Nh6 guards f7.",
                "White takes the knight. If ...gxh6, Qxf7 is mate.",
            ),
            "White wins a whole knight.",
        ),
        Trap(
            "Removing the defender",
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3"),
            san("Nf6 Nxc6 dxc6 Qxd8+ Kxd8 Bxc5"),
            listOf(
                "Black develops, but nothing guards the c5 bishop. Only White's own knight on d4 stands between it and the e3 bishop.",
                "White's knight takes on c6 and gets out of the bishop's way.",
                "Black takes back.",
                "White trades queens with check.",
                "The king takes.",
                "Now the bishop on c5 falls.",
            ),
            "White wins a piece for a pawn.",
        ),
        Trap(
            "Broken pawns",
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3"),
            san("d6 Nxc6 bxc6 Bxc5 dxc5 Qxd8+ Kxd8"),
            listOf(
                "Black guards the bishop with a pawn, but now the d-file opens.",
                "White trades knights first.",
                "Black takes back towards the centre.",
                "White trades bishops.",
                "Black's pawns end up doubled on c5 and c6.",
                "The queens come off with check.",
                "Black's king can no longer castle.",
            ),
            "Black's pawns are broken and the king is stuck: a clear edge for White.",
            mistake = true,
        ),
        Trap(
            "The early e5 push",
            san("e4 d6 d4 Nf6"),
            san("e5 dxe5 dxe5 Qxd1+ Kxd1 Ng4 Ke1 Nxe5"),
            listOf(
                "White pushes before defending e5 with Nc3 or f4.",
                "Black takes.",
                "White takes back.",
                "Trade queens. White's king loses castling.",
                "The king takes.",
                "The knight hits e5 and f2.",
                "White guards f2.",
                "Black collects the e5 pawn.",
            ),
            "Black wins a pawn, with an easy game.",
            mistake = true,
        ),
    )
}
