package app.chessnut.otb.openings

/**
 * A common trap: after [before], the opponent's natural-looking first move in
 * [moves] loses, and the rest of [moves] is how you punish it. Each move has a
 * note in [notes]; [summary] says what it wins. Every trap here was checked
 * with Stockfish at depth 20.
 */
data class Trap(
    val name: String,
    val before: List<String>,
    val moves: List<String>,
    val notes: List<String>,
    val summary: String,
) {
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
            "The c7 fork",
            CLASSICAL,
            san("dxe4 Nxc7+ Kf8 Bxc5"),
            listOf(
                "Black grabs the e4 pawn, forgetting the knight on b5.",
                "Check, and the knight forks the king and the rook on a8.",
                "The king has to move.",
                "White also takes the bishop on c5. The rook on a8 falls next.",
            ),
            "White wins a bishop now and the rook on a8 next.",
        ),
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
        Trap(
            "The f7 fork",
            san("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5"),
            san("d6 Nxf7 Qe7 O-O Na5 Nxh8"),
            listOf(
                "A natural developing move, but it leaves f7 defended only by the king.",
                "The knight takes on f7, forking the queen and the rook on h8.",
                "The queen steps out of the attack.",
                "White castles first, so ...Qxe4 doesn't come with check.",
                "Black chases the bishop on c4.",
                "White takes the rook on h8.",
            ),
            "White wins the rook on h8 for the knight.",
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
            "The trapped bishop",
            PIRC + san("Bg5 Bg7 Qd2 h6 Bh4 g5"),
            san("O-O-O gxh4"),
            listOf(
                "White castles long, but ...g5 attacked the bishop and its only safe square was g3.",
                "The pawn takes the bishop.",
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
            "The b5 pawn",
            PIRC + san("Be3 c6 Qd2 b5"),
            san("Bc4 bxc4"),
            listOf(
                "White develops the bishop to c4, the square Black's b5 pawn covers.",
                "The pawn takes the bishop.",
            ),
            "Black wins a whole bishop.",
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
        Trap(
            "The loose e4 pawn",
            PIRC + san("f4 Bg7 Nf3 O-O"),
            san("Nb5 Nxe4 Bd3 Nf6"),
            listOf(
                "The knight leaves c3, where it was defending e4.",
                "Black takes the pawn.",
                "White hits the knight, hoping to win it back.",
                "The knight drops back. Black keeps the extra pawn.",
            ),
            "Black wins the e4 pawn and keeps it.",
        ),
    )
}
