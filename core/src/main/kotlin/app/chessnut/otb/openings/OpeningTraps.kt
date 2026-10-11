package app.chessnut.otb.openings

/**
 * A common trap: after [before], the opponent's natural-looking first move in
 * [moves] loses, and the rest of [moves] is how you punish it. Each move has a
 * note in [notes]; [summary] says what it wins. A [mistake] is a smaller
 * error than a trap (a pawn or a broken position, not a piece). Every one was
 * checked with Stockfish at depth 20, and kept because Lichess players rated
 * 1400–2000 actually play it, or it's a popular Lichess puzzle from a game
 * that followed your line. [before] can run a few moves past the line's end.
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

    /** Most moves a trap may start after the game leaves your lines. */
    const val PAST_LINE = 6

    /** Fewest moves the game must share with your lines first, so it's really this opening. */
    const val MIN_SHARED = 4

    /**
     * The game [before] comes up from your [lines] (you are White when [white]):
     * it follows them for at least [MIN_SHARED] moves, leaves them only by the
     * opponent's choice, and reaches the trap within [PAST_LINE] moves of that.
     */
    fun fits(before: List<String>, lines: List<List<String>>, white: Boolean): Boolean {
        val shared = lines.maxOfOrNull { line -> line.zip(before).takeWhile { (a, b) -> a == b }.size } ?: return false
        if (shared < MIN_SHARED || before.size - shared > PAST_LINE) return false
        if (shared == before.size) return true
        // Where the game left the lines: if your lines go on from there, it was your move that left them.
        val yourTurn = (shared % 2 == 0) == white
        val linesGoOn = lines.any { it.size > shared && it.take(shared) == before.take(shared) }
        return !(yourTurn && linesGoOn)
    }

    /** The trap comes up from your [lines] of an opening. */
    fun fits(trap: Trap, lines: List<List<String>>): Boolean = fits(trap.before, lines, trap.before.size % 2 == 1)

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
        // From the Lichess puzzle database (CC0): popular puzzles whose games followed your lines.
        Trap(
            "The queen check on d5",
            san("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Nf6 e5 Ng4 Bxf7+ Kxf7 Ng5+ Kg8 Qf3"),
            san("Ncxe5 Qd5+ Nf7 Qxf7#"),
            listOf(
                "Black's knight hits the queen, but leaves the d5 square open for it.",
                "Check from d5, along the diagonal to the king.",
                "The only block.",
                "The queen takes on f7, guarded by the g5 knight: mate.",
            ),
            "Checkmate.",
        ),
        Trap(
            "Mate on f7",
            san("e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4 exd4 Qxd4 Ne7 Bc4 Ng6 Qd5"),
            san("Be7 Qxf7#"),
            listOf(
                "Black develops, but forgets the queen and bishop are both aiming at f7.",
                "The queen takes f7, guarded by the bishop: mate.",
            ),
            "Checkmate.",
        ),
        Trap(
            "The queen swap that isn't",
            san("e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4 exd4 Qxd4 b6 Bc4 Qf6 e5"),
            san("Bc5 Qxc5 bxc5 exf6"),
            listOf(
                "Black hits the queen, forgetting that e5 has attacked Black's own queen.",
                "White's queen takes the bishop.",
                "Black takes White's queen.",
                "And the pawn takes Black's queen.",
            ),
            "White wins a whole bishop.",
        ),
        Trap(
            "The pin on b5",
            san("e4 e5 Nf3 Nc6 d4 exd4 c3 d5 exd5 Qxd5 Nxd4 Nf6 Nxc6 Qxc6 Qb3"),
            san("Bc5 Bb5 Bd7 Bxc6"),
            listOf(
                "Black develops the bishop, leaving the queen on the same diagonal as the king.",
                "The bishop pins the queen to the king.",
                "Black can only add a defender.",
                "White takes the queen.",
            ),
            "White wins the queen for a bishop.",
        ),
        Trap(
            "The Greek gift",
            san("e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bb4+ c3 dxc3 bxc3 Ba5 e5 Nge7 Bd3"),
            san("O-O Bxh7+ Kxh7 Ng5+ Kg6 h4"),
            listOf(
                "Black castles, with no knight on f6 to guard h7.",
                "The classic bishop sacrifice on h7.",
                "The king takes.",
                "The knight checks and the king can't go back safely.",
                "On g6 the king is out in the open.",
                "The h-pawn joins in with h5+ next, and Black's king is caught.",
            ),
            "White's attack wins: Black's king can't escape.",
        ),
        Trap(
            "The pinned queen",
            PIRC + san("Bg5 Bg7 Qd2 h6 Bh4 g5 Bg3 Nh5 O-O-O Nxg3 hxg3 h5"),
            san("Qxg5 Bh6"),
            listOf(
                "White grabs the g5 pawn, putting the queen on the bishop's diagonal to c1, where the king is.",
                "The bishop pins the queen to the king. It's lost for the bishop.",
            ),
            "Black wins the queen for a bishop.",
        ),

        // Italian Game (from the Lichess puzzle database, like those below).
        Trap(
            "Légal's mate",
            san("e4 e5 Nf3 Nc6 Bc4 d6 O-O Bg4 Nc3 h5 Nxe5"),
            san("Bxd1 Bxf7+ Ke7 Nd5#"),
            listOf(
                "Black grabs the queen White left on d1.",
                "But the bishop checks from f7.",
                "The king's only square.",
                "The knight on d5 mates: e5 and c3's knights and the f7 bishop cover every square.",
            ),
            "Checkmate, a queen down: Légal's mate.",
        ),
        Trap(
            "The Fried Liver king hunt",
            san("e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7 Kxf7 Qf3+ Ke6 Nc3 Nb4 Qg4+ Ke7 Qg5+"),
            san("Nf6 Qxe5+ Kd7 Qe6#"),
            listOf(
                "Black blocks with the knight, but leaves e5 unguarded.",
                "The queen takes e5 with check.",
                "The king runs to d7.",
                "Mate on e6, guarded by the c4 bishop.",
            ),
            "Checkmate.",
        ),
        Trap(
            "Mate after the f7 sacrifice",
            san("e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Nxe4 Bxf7+ Ke7 Nxe4 Kxf7 O-O Qh4 Qf3+"),
            san("Kg8 Qb3+ d5 Qxd5+ Be6 Qxe6#"),
            listOf(
                "The king tucks into the corner, onto the queen's diagonal.",
                "Check from b3.",
                "Black blocks with the pawn.",
                "The queen takes it, with check again.",
                "Another block.",
                "And mate on e6.",
            ),
            "Checkmate.",
        ),

        // Ruy Lopez.
        Trap(
            "Légal's mate in the Ruy",
            san("e4 e5 Nf3 Nc6 Bb5 d6 Nc3 Bg4 Bc4 h6 Nxe5"),
            san("Bxd1 Bxf7+ Ke7 Nd5#"),
            listOf(
                "Black takes the queen White offered.",
                "Check from f7.",
                "The king's only square.",
                "The knight on d5 mates.",
            ),
            "Checkmate, a queen down.",
        ),
        Trap(
            "The queen fork on h5",
            san("e4 e5 Nf3 Nc6 Bb5 Bc5 Bxc6 bxc6 Nxe5 Ba6 Nxf7"),
            san("Kxf7 Qh5+ g6 Qxc5"),
            listOf(
                "Black takes the knight on f7.",
                "Check, and the queen also eyes the bishop on c5.",
                "Black blocks.",
                "The queen takes the loose bishop.",
            ),
            "White wins a piece back and comes out a pawn up, with Black's king exposed.",
        ),
        Trap(
            "The rook in the corner",
            san("e4 e5 Nf3 Nc6 Bb5 a6 Ba4 b5 Bb3 Na5 Bxf7+ Kxf7 Nxe5+ Ke6 Qh5 Qf6 Nf3"),
            san("g6 Qd5+ Ke7 Qxa8"),
            listOf(
                "Black hits the queen, forgetting the long diagonal to a8.",
                "Check from d5, lining up on the rook.",
                "The king steps back.",
                "The queen takes the rook.",
            ),
            "White wins a whole rook.",
        ),

        // Vienna Game.
        Trap(
            "The Vienna's f-pawn",
            san("e4 e5 Nc3 Nf6 f4"),
            san("Bc5 fxe5 Nxe4 Nxe4 Qh4+ Ng3"),
            listOf(
                "Black develops, ignoring that f4 attacks e5 and the knight behind it.",
                "The pawn takes and hits the knight.",
                "Black counter-attacks e4.",
                "White takes back.",
                "The queen checks, hoping to win the knight back.",
                "The knight blocks, and Black has nothing.",
            ),
            "White is a piece up.",
        ),
        Trap(
            "The loose knight on d4",
            san("e4 e5 Nc3 Nf6 f4 d5 fxe5 Nxe4 Qf3 Nc6 Nxe4 Nd4 Qc3 dxe4 Ne2"),
            san("Bb4 Qxd4 Qxd4 Nxd4"),
            listOf(
                "Black hits the queen, but the knight on d4 is unguarded.",
                "The queen takes it.",
                "The queens come off.",
                "And White is a knight up.",
            ),
            "White wins a knight.",
        ),
        Trap(
            "The knight fork on c7",
            san("e4 e5 Nc3 Nf6 f4 exf4 e5 Qe7 Nb5 Qxe5+ Be2"),
            san("c6 Nf3 Qh5 Nc7+ Kd8 Nxa8"),
            listOf(
                "Black kicks the knight, but c7 is now a fork square.",
                "First the queen is chased.",
                "The queen moves.",
                "The knight forks king and rook.",
                "The king moves.",
                "The knight takes the rook.",
            ),
            "White wins a rook.",
        ),

        // English Opening.
        Trap(
            "The b4 bishop",
            san("c4 e5 Nc3 Nc6 g3 Nf6 Bg2 Bb4 Qb3"),
            san("d6 Bxc6+ bxc6 Qxb4"),
            listOf(
                "Black ignores the attack on b4, thinking the bishop is safe.",
                "White takes its only defender, the c6 knight, with check.",
                "Black takes back.",
                "The queen takes the bishop.",
            ),
            "White wins a bishop for nothing.",
        ),

        // London System.
        Trap(
            "The fork on c7",
            san("d4 Nf6 Nf3 d5 Bf4 c5 e3 Nc6 c4 cxd4 Nxd4 Qb6 Nb5"),
            san("dxc4 Nc7+ Qxc7 Bxc7"),
            listOf(
                "Black grabs a pawn, but c7 is guarded only by the queen.",
                "The knight forks king and rook, backed by the f4 bishop.",
                "The queen has to take.",
                "The bishop takes the queen.",
            ),
            "White wins the queen for a knight.",
        ),
        Trap(
            "The pin on d7",
            san("d4 Nf6 Nf3 d5 Bf4 c5 e3 Bf5 c3 e6 Bxb8 Rxb8 Qa4+"),
            san("Qd7 Bb5 a6 Bxd7+"),
            listOf(
                "Black blocks the check with the queen.",
                "The bishop pins it to the king.",
                "Black hits the bishop, too late.",
                "The bishop takes the queen.",
            ),
            "White wins the queen for a bishop.",
        ),
        Trap(
            "The pinned queen on c6",
            san("d4 Nf6 Nf3 d5 Bf4 e6 e3 c5 c3 Qb6 Qb3"),
            san("Qc6 Bb5 c4 Bxc6+"),
            listOf(
                "Black's queen dodges onto the diagonal of the king.",
                "The bishop pins it.",
                "Black hits White's queen.",
                "The bishop takes the queen first.",
            ),
            "White wins the queen for a bishop.",
        ),

        // Sicilian Defense: White's mistakes, which you punish as Black.
        Trap(
            "The b2 pawn",
            san("e4 c5 d4 cxd4 Qxd4 Nc6 Qa4 Qb6 Nc3 e6"),
            san("Be3 Qxb2 Kd2 Qxa1"),
            listOf(
                "White develops, attacking the queen but leaving b2.",
                "The queen takes b2 and hits the rook and the knight.",
                "White guards the knight.",
                "The queen takes the rook.",
            ),
            "Black wins a rook.",
        ),
        Trap(
            "The bishop on b5",
            san("e4 c5 Nc3 Nc6 Nf3 g6 Bb5 Bg7 d3 d6"),
            san("Ne2 Qa5+ Nc3 Bxc3+ bxc3 Qxb5"),
            listOf(
                "The knight leaves c3, and the b5 bishop loses its guard.",
                "Check, also hitting the bishop.",
                "White blocks.",
                "Black removes the blocker.",
                "White takes back.",
                "And the queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The trapped knight on d4",
            san("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Qf3 Bg4"),
            san("Qf4 e5 Qg3 exd4"),
            listOf(
                "The queen dodges, but the d4 knight is left without support.",
                "The pawn hits queen and knight together.",
                "The queen moves again.",
                "The pawn takes the knight.",
            ),
            "Black wins a knight.",
        ),

        // French Defense.
        Trap(
            "The check on a5",
            san("e4 e6 d4 d5 Nd2 c5 exd5 exd5 Bb5+ Nc6"),
            san("Ndf3 Qa5+ c3 Qxb5"),
            listOf(
                "The knight leaves d2, where it blocked the check.",
                "Check, also hitting the bishop on b5.",
                "White blocks.",
                "The queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The loose bishop on b5",
            san("e4 e6 d4 d5 e5 c5 c3 Nc6 b3 cxd4 cxd4 Nge7"),
            san("Bb5 Qa5+ Bd2 Qxb5"),
            listOf(
                "The bishop pins the knight, but nothing guards it.",
                "Check, hitting the bishop too.",
                "White blocks.",
                "The queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The pin on c3",
            san("e4 e6 d4 d5 Nc3 Bb4 e5 c5 Nf3 Nc6 Bf4 Qb6 a3 Ba5"),
            san("dxc5 Qxb2 Rb1 Bxc3+"),
            listOf(
                "White grabs a pawn, but the c3 knight is pinned and b2 is loose.",
                "The queen takes b2 and hits the pinned knight.",
                "White chases the queen.",
                "The bishop takes the knight with check, and the rook on b1 is next.",
            ),
            "Black wins a knight and more.",
        ),

        // Caro-Kann Defense.
        Trap(
            "The undefended b5 bishop",
            san("e4 c6 d4 d5 e5 c5 dxc5 Nc6 Bb5 d4"),
            san("Nf3 Qa5+ c3 Qxb5"),
            listOf(
                "White develops, leaving the b5 bishop unguarded.",
                "Check, hitting the bishop too.",
                "White blocks.",
                "The queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The pinned knight on c3",
            san("e4 c6 d4 d5 e5 c5 dxc5 Nc6 c4 Qa5+"),
            san("Nc3 d4 Bd2 dxc3"),
            listOf(
                "The knight blocks the check, but it's pinned to the king.",
                "The pawn attacks it.",
                "White adds a defender.",
                "The pawn takes the knight anyway.",
            ),
            "Black wins a knight for a pawn.",
        ),
        Trap(
            "The loose knight on e5",
            san("e4 c6 d4 d5 exd5 cxd5 Nf3 Nc6 Bb5 Bd7 O-O e6"),
            san("Ne5 Nxe5 Bxd7+ Nxd7"),
            listOf(
                "The knight jumps in, forgetting that it's loose on e5.",
                "Black takes it.",
                "White takes the bishop with check.",
                "Black takes back, a knight up.",
            ),
            "Black wins a knight.",
        ),

        // Slav Defense.
        Trap(
            "The loose bishop on g5",
            san("d4 d5 c4 c6 cxd5 cxd5 Nc3 Nf6 Bg5 Nc6 a3 Ne4 Nxe4 dxe4"),
            san("e3 Qa5+ b4 Qxg5"),
            listOf(
                "White plays a quiet move, leaving the g5 bishop unguarded.",
                "Check, hitting the bishop as well.",
                "White blocks.",
                "The queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The a3 bishop",
            san("d4 d5 c4 c6 b3 Bf5"),
            san("Ba3 Bxb1 Rxb1 Qa5+ b4 Qxa3"),
            listOf(
                "The bishop goes to a3, where nothing guards it.",
                "Black first removes the knight.",
                "White takes back.",
                "Check, hitting the a3 bishop.",
                "White blocks.",
                "The queen takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "The b2 pawn and the c3 knight",
            san("d4 d5 c4 c6 cxd5 cxd5 Nc3 Nf6 Bf4 Qb6 Qc2 Nc6 e3 Bf5"),
            san("Qxf5 Qxb2 Qb1 Qxc3+"),
            listOf(
                "White grabs the bishop, leaving b2 and the rook behind it.",
                "The queen takes b2, hitting the rook.",
                "White guards the rook.",
                "The queen takes the knight with check.",
            ),
            "Black wins back the piece and more.",
        ),

        // Nimzo-Indian Defense.
        Trap(
            "The pinned knight and the g5 bishop",
            san("d4 Nf6 c4 e6 Nc3 Bb4 Bg5 h6"),
            san("a3 Bxc3+ bxc3 hxg5"),
            listOf(
                "White asks the bishop, forgetting that h6 attacks the g5 bishop.",
                "Black takes the knight with check.",
                "White takes back.",
                "And the pawn takes the bishop.",
            ),
            "Black wins a bishop.",
        ),
        Trap(
            "Mate on h4",
            san("d4 Nf6 c4 e6 Nc3 Bb4 f3 O-O g4 d5 cxd5 Nxd5"),
            san("Bd2 Qh4#"),
            listOf(
                "White develops, with f3 and g4 having opened the king's diagonal.",
                "Mate: nothing can block on h4, g3 or f2.",
            ),
            "Checkmate.",
        ),
        Trap(
            "The knight fork on c2",
            san("d4 Nf6 c4 e6 Nc3 Bb4 Qb3 Nc6 Bg5 Nxd4"),
            san("Qxb4 Nc2+ Kd2 Nxb4"),
            listOf(
                "The queen takes the bishop, walking into a fork.",
                "The knight checks the king and hits the queen.",
                "The king moves.",
                "The knight takes the queen.",
            ),
            "Black wins the queen for a bishop.",
        ),

        // King's Indian Defense.
        Trap(
            "The g3 fork",
            san("d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4 e5 Nf3 exd4 Nxd4 Nh5"),
            san("Be2 Qh4+ g3 Nxg3"),
            listOf(
                "White develops, forgetting the h4 check that f4 allowed.",
                "Check.",
                "White blocks.",
                "The knight takes g3, hitting the rook; hxg3 drops the queen's guard of h1.",
            ),
            "Black wins material against an open king.",
        ),
        Trap(
            "The long diagonal",
            san("d4 Nf6 c4 g6 Nc3 Bg7 Bf4 c5 d5 Nh5 Bxb8 Qa5"),
            san("Bg3 Bxc3+ bxc3 Qxc3+ Qd2 Qxa1+"),
            listOf(
                "The bishop retreats, but the c3 knight is pinned and the long diagonal is open.",
                "The bishop takes the knight with check.",
                "White takes back.",
                "The queen takes c3, with check too.",
                "White blocks.",
                "The queen takes the rook in the corner.",
            ),
            "Black wins a rook.",
        ),
    )
}
