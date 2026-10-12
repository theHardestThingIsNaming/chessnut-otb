package app.chessnut.otb.openings

/**
 * A middlegame plan for one opening and side: from the end of one of your
 * lines ([before]), a model continuation ([moves], both sides) with a note per
 * move and a [summary] of the idea. Every move was checked with Stockfish:
 * none of the planning side's gives away more than about half a pawn.
 */
data class MiddlegamePlan(
    val name: String,
    val opening: String,
    val white: Boolean,
    val before: List<String>,
    val moves: List<String>,
    val notes: List<String>,
    val summary: String,
) {
    init {
        require(moves.size == notes.size) { "$name: one note per move" }
    }
}

object MiddlegamePlans {
    fun of(opening: String, white: Boolean): List<MiddlegamePlan> = ALL.filter { it.opening == opening && it.white == white }

    val all: List<MiddlegamePlan> get() = ALL

    private fun san(line: String) = line.split(' ').filter { it.isNotBlank() }

    private const val SCOTCH = "Scotch Game"
    private const val PIRC = "Pirc Defense"

    private val ALL = listOf(
        MiddlegamePlan(
            "Space on the queenside",
            SCOTCH, true,
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5"),
            san("Qe7 Qe2 Nd5 c4 Ba6 b3 g6 g3 Bg7 Bb2 O-O Bg2 Rfe8 O-O"),
            listOf(
                "Black hits the e5 pawn with the queen.",
                "White defends it and keeps the e-file closed.",
                "The knight jumps to its best square.",
                "White kicks it at once: the start of the plan, space on the queenside.",
                "Black pins the c4 pawn to the queen.",
                "b3 holds c4 and makes room for the bishop on b2.",
                "Black prepares the bishop on g7, aiming at e5.",
                "White fianchettoes too.",
                "The bishop hits e5.",
                "And White's covers it: e5 is the key square of the position.",
                "Black castles.",
                "The second bishop takes the long diagonal.",
                "Black piles up on the e5 pawn.",
                "White castles. More space, and Black's split pawns on a7, c6 and c7 stay weak for good.",
            ),
            "Keep the e5 pawn, gain queenside space with c4 and b3, and put both bishops on the long diagonals. Black's broken queenside pawns are a target for the rest of the game.",
        ),
        MiddlegamePlan(
            "The exchange on a8",
            SCOTCH, true,
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5 Nb5"),
            san("Bxe3 fxe3 O-O Nxc7 dxe4 Nxa8 Rd8 Qc2 Nd5"),
            listOf(
                "Black trades the bishop off.",
                "The f-pawn takes back: it opens the f-file towards f7 and covers d4.",
                "Black castles, leaving c7 to its fate.",
                "The knight forks the rook on a8.",
                "Black grabs a pawn in return.",
                "White takes the rook.",
                "Black hits the queen to win time.",
                "The queen steps away, still watching e4.",
                "Black's pieces are lively, but White is the exchange up.",
            ),
            "Take on c7 and win the exchange for a pawn. The a8 knight comes back through b6 or c7, and the extra material tells once the pieces are untangled.",
        ),
        MiddlegamePlan(
            "Hunt the king in the middle",
            SCOTCH, true,
            san("e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+ Be2 Kd8 O-O"),
            san("Bxd2 Nxd2 Qf5 Nb3 Nf6 Qd2 Re8 Rae1 d6 Bd3"),
            listOf(
                "Black trades bishops.",
                "The knight takes back and hits the queen.",
                "The queen steps aside.",
                "The knight heads for d4, the centre.",
                "Black develops.",
                "White connects the rooks.",
                "Black covers the e-file, castling by hand.",
                "White lines up on the e-file against the king.",
                "Black shores up e5 and d6.",
                "The bishop hits the queen, gaining time again.",
            ),
            "White is a pawn down, but every piece is out while Black's king is stuck on d8 and the queenside hasn't moved. Open lines, keep gaining time on the queen, and go after the king.",
        ),
        MiddlegamePlan(
            "Faster development without queens",
            SCOTCH, true,
            san("e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4 exd4 Qxd4"),
            san("Qf6 e5 Qb6 Qxb6 axb6 Nc3 f6 exf6 Nxf6 Bd3"),
            listOf(
                "Black offers a queen trade.",
                "White gains space with tempo.",
                "The queen goes to b6, still offering the trade.",
                "White takes it.",
                "Black's b-pawns are now doubled.",
                "White develops.",
                "Black strikes at e5 to free the pieces.",
                "White takes.",
                "Black develops with the capture.",
                "White is ahead in development, with a small and lasting edge.",
            ),
            "With queens off early, White develops faster and Black's b-pawns are doubled. Castle quickly, put a rook on the e-file, and aim at the weak pawns.",
        ),
        MiddlegamePlan(
            "Strike with ...c5, then ...b5",
            PIRC, false,
            san("e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Bd3 Na6"),
            san("O-O c5 d5 Nc7 a4 b6 Qe1 a6 Qh4 b5"),
            listOf(
                "White castles.",
                "Black strikes at d4: the key break against the Austrian.",
                "White gains space.",
                "The a6 knight comes round to c7, eyeing b5.",
                "White holds back ...b5 for now.",
                "Black prepares ...a6 and ...b5 anyway.",
                "White's queen heads for h4: the usual attack.",
                "Black insists on ...b5.",
                "White lines up against the king.",
                "Black breaks open the queenside: counterplay before the attack lands.",
            ),
            "Against f4, strike with ...c5, bring the a6 knight to c7 and push ...b5. Speed matters: the queenside play has to land before White's Qh4 and f5.",
        ),
        MiddlegamePlan(
            "Undermine d5 with ...c6",
            PIRC, false,
            san("e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O Nc6"),
            san("d5 Nb8 Re1 c6 a4 Nbd7 Bf4 Qb6"),
            listOf(
                "White kicks the knight and takes space.",
                "It goes back, to come again through d7.",
                "White prepares e5.",
                "Black hits the d5 pawn and opens the c-file.",
                "White stops ...b5.",
                "The knight reaches d7, eyeing c5 and e5.",
                "White develops.",
                "The queen comes to b6, pressing b2 and the queenside.",
            ),
            "When White gains space with d5, undermine it with ...c6, bring the knight back into play through d7, and use the queen and the c-file on the queenside.",
        ),
        MiddlegamePlan(
            "Block the storm, then e5",
            PIRC, false,
            san("e4 d6 d4 Nf6 Nc3 g6 Be3 c6 Qd2 b5 f3 Nbd7"),
            san("g4 Nb6 h4 h5 g5 Nfd7 d5 Ne5"),
            listOf(
                "White storms the kingside: the 150 Attack's plan.",
                "Black frees d7 and eyes c4.",
                "More pawns come.",
                "...h5 blocks: the h-file stays shut.",
                "White gains space.",
                "The knight drops back, heading for e5.",
                "White closes the centre.",
                "The knight lands on e5, a superb square nothing can kick.",
            ),
            "Against g4 and h4, block with ...h5 and route a knight to e5. With the kingside shut, play on the queenside with ...b4 and ...a5.",
        ),
        MiddlegamePlan(
            "Queenside pawns at the king",
            PIRC, false,
            san("e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6 Bh4 g5 Bg3 Nh5"),
            san("O-O-O Nd7 Nge2 c6 f3 b5 Bf2 Nb6"),
            listOf(
                "White castles long.",
                "Black develops, ready for ...c6 and ...b5.",
                "White develops.",
                "Black prepares ...b5.",
                "White supports e4.",
                "The queenside pawns roll towards White's king.",
                "The bishop steps away from ...Nxg3.",
                "The knight heads for c4.",
            ),
            "When White castles long, throw the queenside pawns at the king with ...c6 and ...b5, and bring a knight to c4. The g3 bishop is always a target for ...Nxg3.",
        ),
    )
}
