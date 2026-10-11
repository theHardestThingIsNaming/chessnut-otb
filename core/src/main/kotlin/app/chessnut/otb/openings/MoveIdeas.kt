package app.chessnut.otb.openings

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square

/**
 * The idea behind an opening move, to be spoken after the move itself.
 * Main lines have written notes; any other move gets a plain description
 * worked out from the move.
 */
object MoveIdeas {
    /**
     * [played] is the game so far, ending with the move to explain, and
     * [fenBefore] the position before that move.
     */
    fun explain(played: List<String>, fenBefore: String, uci: String): String? {
        if (played.isEmpty()) return null
        NOTES[played.joinToString(" ")]?.let { return it }
        val idea = describe(played, fenBefore, uci) ?: return null
        val offer = offered(fenBefore, uci) ?: return idea
        val inBook = played.last() in OpeningBook.replies(played.dropLast(1))
        return "$idea ${offerPhrase(offer, inBook)}"
    }

    private fun offerPhrase(offer: Offer, inBook: Boolean): String {
        val name = offer.type.name.lowercase()
        val at = offer.square.name.lowercase()
        return when {
            offer.type == PieceType.PAWN -> "It offers the pawn on $at."
            offer.ignored -> "It ignores the attack on the $name on $at."
            inBook -> "It leaves the $name on $at to be taken. That is a deliberate sacrifice, for time and open lines."
            else -> "But it leaves the $name on $at to be taken."
        }
    }

    /** A piece left en prise. [ignored] when it was already attacked before the move. */
    data class Offer(val type: PieceType, val square: Square, val ignored: Boolean)

    /**
     * The most valuable piece this move leaves en prise: undefended, or
     * attacked by something cheaper. A pawn counts only when this move newly
     * offers it, so a declined gambit pawn is not announced every move. A
     * capture that can simply be taken back is a trade, not an offer. A piece
     * newly put en prise is preferred over one whose attack was ignored.
     */
    fun offered(fenBefore: String, uci: String): Offer? {
        if (uci.length < 4) return null
        val board = Board().apply { loadFromFen(fenBefore) }
        val mover = board.sideToMove
        // Out of check the move is forced, so whatever it leaves is not an offer.
        if (board.isKingAttacked) return null
        val already = run {
            board.doNullMove()
            val hanging = hanging(board, mover).keys
            board.undoMove()
            hanging
        }
        val move = board.legalMoves().firstOrNull { it.toString().lowercase() == uci.lowercase() } ?: return null
        val moved = VALUE[board.getPiece(move.from).pieceType] ?: 0
        val taken = VALUE[board.getPiece(move.to).pieceType] ?: 0
        board.doMove(move)
        return hanging(board, mover)
            .filter { (square, type) -> type != PieceType.PAWN || square !in already }
            .filter { (square, _) -> !(square == move.to && taken > 0 && moved <= taken) }
            .map { (square, type) -> Offer(type, square, ignored = square in already && square != move.to) }
            .maxWithOrNull(compareBy<Offer> { !it.ignored }.thenBy { VALUE[it.type] ?: 0 })
    }

    /** [owner]'s pieces the side to move can win: square to piece type. */
    private fun hanging(board: Board, owner: Side): Map<Square, PieceType> {
        val found = mutableMapOf<Square, PieceType>()
        for (capture in board.legalMoves()) {
            val victim = board.getPiece(capture.to)
            if (victim.pieceSide != owner || victim.pieceType == null || victim.pieceType == PieceType.KING) continue
            val attacker = board.getPiece(capture.from).pieceType ?: continue
            val gain = (VALUE[victim.pieceType] ?: 0) - (VALUE[attacker] ?: 0)
            val worth = VALUE[victim.pieceType] ?: 0
            board.doMove(capture)
            val defended = board.legalMoves().any { it.to == capture.to }
            // Taking it walks into losing something at least as valuable, as when the piece attacks the queen.
            val repaid = board.legalMoves().any { (VALUE[board.getPiece(it.to).pieceType] ?: 0) >= worth && it.to != capture.to }
            board.undoMove()
            if (repaid) continue
            if (!defended || gain > 0) found[capture.to] = victim.pieceType
        }
        return found
    }

    private val VALUE = mapOf(
        PieceType.PAWN to 1,
        PieceType.KNIGHT to 3,
        PieceType.BISHOP to 3,
        PieceType.ROOK to 5,
        PieceType.QUEEN to 9,
    )

    /** Move sequences with written notes. */
    val noted: Set<String> get() = NOTES.keys

    /**
     * A move without a written note: what kind of move it is, then what it
     * does in this position, such as a threat, a pin or a defence.
     */
    private fun describe(played: List<String>, fenBefore: String, uci: String): String? {
        if (uci.length < 4) return null
        val board = Board().apply { loadFromFen(fenBefore) }
        val from = Square.fromValue(uci.substring(0, 2).uppercase())
        val to = Square.fromValue(uci.substring(2, 4).uppercase())
        val piece = board.getPiece(from)
        val type = piece.pieceType ?: return null
        val white = board.sideToMove == Side.WHITE
        val victim = board.getPiece(to).pieceType
        val san = played.last()
        val target = uci.substring(2, 4)
        val escaped = "Moves the ${type.name.lowercase()} out of attack."
        val features = MoveFeatures.of(fenBefore, uci, limit = 3).let { found ->
            // Getting out of check or out of attack is the move's main point, so it leads instead of following.
            if (board.isKingAttacked || escaped in found) found else found.take(2)
        }
        val canCastle = board.getCastleRight(board.sideToMove) != com.github.bhlangonijr.chesslib.CastleRight.NONE
        val opening = when {
            board.isKingAttacked && victim == null -> null
            escaped in features && victim == null -> "Moves the ${type.name.lowercase()} out of attack, to $target."
            san.startsWith("O-O-O") -> "Castles long, bringing the rook to the centre. The kings may now attack on opposite wings."
            san.startsWith("O-O") -> "Castles, tucking the king away and bringing the rook towards the centre."
            victim != null && recaptures(played, target) -> "Recaptures on $target."
            victim != null && board.squareAttackedBy(to, board.sideToMove.flip()) == 0L ->
                "Wins the ${victim.name.lowercase()} on $target."
            victim == type && type != PieceType.PAWN -> "Trades ${type.name.lowercase()}s on $target."
            victim != null -> "Takes the ${victim.name.lowercase()} on $target."
            type == PieceType.PAWN -> pawnIdea(target, white, single = kotlin.math.abs(to.rank.ordinal - from.rank.ordinal) == 1)
            type == PieceType.KNIGHT && home(from, white) -> "Develops the knight towards the centre."
            type == PieceType.BISHOP && target in LONG_DIAGONAL -> "Fianchettoes the bishop onto the long diagonal."
            type == PieceType.BISHOP && home(from, white) -> "Develops the bishop onto an active diagonal."
            type == PieceType.ROOK -> "Brings the rook to the ${target[0]} file."
            type == PieceType.KING && canCastle -> "Moves the king, giving up the right to castle."
            type == PieceType.KING -> "Moves the king to $target."
            else -> "Brings the ${type.name.lowercase()} to $target."
        }
        // A plain check is worth saying; a discovered or double check already says so.
        val check = if (san.endsWith("+") && features.none { "check" in it.lowercase() }) listOf("Check.") else emptyList()
        val rest = features.filter { it != escaped || opening == null }.take(2)
        return (listOfNotNull(opening) + check + rest).joinToString(" ")
    }

    /** The move before this one was a capture on the same square. */
    private fun recaptures(played: List<String>, target: String): Boolean {
        val previous = played.getOrNull(played.size - 2) ?: return false
        return previous.contains("x$target")
    }

    private fun pawnIdea(target: String, white: Boolean, single: Boolean = false): String {
        val file = target[0]
        val rank = target[1].digitToInt()
        val advanced = if (white) rank - 2 else 7 - rank
        return when {
            file in "de" && single && advanced == 1 -> "Supports the centre and opens a line for the bishop."
            file in "de" -> "Claims a share of the centre."
            file in "cf" -> "Fights for the centre from the side."
            target in setOf("g3", "b3", "g6", "b6") -> "Makes room to fianchetto the bishop."
            file in "ah" && advanced == 1 -> "A small wing move that takes a square away from the opponent's pieces."
            else -> "Gains space on the wing."
        }
    }

    private fun home(square: Square, white: Boolean): Boolean =
        square.name.endsWith(if (white) "1" else "8")

    private val LONG_DIAGONAL = setOf("g2", "b2", "g7", "b7")

    private val NOTES = mapOf(
        // First moves
        "e4" to "Grabs the centre and opens lines for the queen and the light-squared bishop.",
        "d4" to "Claims the centre with a pawn the queen already protects.",
        "c4" to "The English. It controls d5 from the side and keeps White flexible.",
        "Nf3" to "Develops towards the centre and keeps every option open.",

        // Open games
        "e4 e5" to "Black matches White in the centre.",
        "e4 e5 Nf3" to "Develops with a threat, attacking the e5 pawn.",
        "e4 e5 Nf3 Nc6" to "Defends e5 while developing a piece.",
        "e4 e5 Nf3 Nf6" to "Petrov's Defense. Black counterattacks e4 instead of defending e5.",
        "e4 e5 Nf3 Nf6 Nxe5 Nc6" to "The Stafford Gambit. Black gives a pawn for fast development and plenty of traps.",
        "e4 e5 Nf3 d5" to "The Elephant Gambit. Black strikes back in the centre straight away.",
        "e4 e5 Nf3 f5" to "The Latvian Gambit, a risky counterattack on e4 that weakens Black's own king.",

        // Italian
        "e4 e5 Nf3 Nc6 Bc4" to "The Italian. The bishop aims at f7, the weakest square in Black's camp.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5" to "Black copies White, aiming the bishop at f2.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3" to "Prepares d4, to build a big pawn centre.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 b4" to "The Evans Gambit. White gives a pawn to chase the bishop and gain time for c3 and d4.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6" to "The Two Knights. Black counterattacks e4 rather than copying White.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5" to "The knight joins the bishop in attacking f7.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5" to "Blocks the bishop's line to f7.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5" to "White takes, opening the position.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5" to "A natural recapture, but it invites the knight sacrifice on f7.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7" to "The Fried Liver. White gives up a knight to drag the black king into the open.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5" to "The main line. The knight hits the bishop, and Black gives a pawn for quick development.",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5" to "The Traxler. Black ignores the threat on f7 and counterattacks f2.",

        // Ruy Lopez
        "e4 e5 Nf3 Nc6 Bb5" to "The Ruy Lopez. The bishop pressures the knight that defends e5. There is no threat yet, but the pressure lasts.",
        "e4 e5 Nf3 Nc6 Bb5 a6" to "The Morphy Defense. Black asks the bishop what it intends straight away.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4" to "The bishop keeps its pressure on the c6 knight.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6" to "Develops and attacks e4.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O" to "White castles. The e4 pawn is defended indirectly, because taking it opens the e-file towards Black's king.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7" to "The Closed Ruy Lopez. Black prepares to castle.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1" to "Defends e4, so White can now threaten to win the e5 pawn.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5" to "Drives the bishop away, ending the pressure on c6.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3" to "The bishop retreats, still aiming at f7.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O" to "Black castles, and now the Marshall Attack is possible.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3" to "Prepares d4, and gives the bishop a retreat on c2.",
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3 d5" to "The Marshall Attack. Black gives a pawn for a fierce attack on White's king.",
        "e4 e5 Nf3 Nc6 Bb5 Nf6" to "The Berlin. Black counterattacks e4 instead of defending e5.",

        // Scotch, Danish
        "e4 e5 Nf3 Nc6 d4" to "The Scotch. White strikes in the centre at once.",
        "e4 e5 Nf3 Nc6 d4 exd4" to "Black takes, and White will recapture.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4" to "Recaptures, leaving White with a free hand in the centre.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4" to "The Scotch Gambit. White leaves the pawn and develops with an eye on f7.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3" to "The Göring Gambit. White offers a pawn for fast development.",

        // Danish
        "e4 e5 d4" to "The Centre Game idea. White opens the centre at once, inviting Black to take.",
        "e4 e5 d4 exd4" to "Black takes the pawn. Now White can recapture or gambit with c3.",
        "e4 e5 d4 exd4 c3 dxc3" to "Black accepts. White now offers a second pawn for a huge lead in development.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2" to "Black grabs a second pawn. White's two bishops now rake the board towards Black's king.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Qe7" to "The Chigorin Defense. The queen covers e5 and the e-file, slowing White's attack.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Qe7 Nc3" to "Develops and adds weight to e4 and d5, ready for Nge2 and castling.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Nf6" to "The Classical Defense. Black develops and attacks e4, aiming to return a pawn for safety.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Nf6 e5" to "Drives the knight away and gains space before Black can castle.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Bb4+" to "The Copenhagen Defense. Black checks to swap pieces and blunt White's attack.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2 Bb4+ Nc3" to "Blocks with a developing move and keeps both bishops aiming at Black's king.",
        "e4 e5 d4 exd4 c3 Ne7" to "The Svenonius. Black declines to take, preparing ...d5 with the knight ready to recapture.",
        "e4 e5 d4 exd4 c3 Ne7 Nf3" to "Develops and leaves the d4 pawn to be taken back later.",
        "e4 e5 d4 exd4 c3 d5" to "The Sörensen. Black hits back in the centre instead of taking a pawn.",
        "e4 e5 d4 exd4 c3 d5 exd5" to "Takes, opening the position; White recaptures on d4 next with an easy game.",

        // Pirc
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O e5 Nfd7 h4" to "The Bronstein. White throws the h-pawn at Black's castled king.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O e5 Nfd7 h4 c5" to "Counterattacks in the centre: the best answer to a wing attack is a strike in the middle.",
        "e4 d6" to "The Pirc. Black lets White build a big centre, planning to undermine it later with pieces and pawn breaks.",
        "e4 d6 d4" to "White takes the full centre with two pawns, accepting the invitation.",
        "e4 d6 d4 Nf6" to "Develops with a threat to e4, so White has to spend a move defending it.",
        "e4 d6 d4 Nf6 Nc3" to "Defends e4 with a developing move. This is the main line.",
        "e4 d6 d4 Nf6 Nc3 g6" to "Prepares Bg7, a bishop that will press on White's centre from the long diagonal.",
        "e4 d6 d4 Nf6 Nc3 g6 f4" to "The Austrian Attack. White grabs even more of the centre and eyes a kingside pawn storm with e5.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7" to "Completes the fianchetto. The bishop aims at d4 and waits for the centre to open.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3" to "Develops and adds support to d4 and e5, keeping the e5 push ready.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O" to "Black castles into safety before choosing how to strike at the centre.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Be3" to "The Kurajica. White shores up d4 and keeps queenside castling in mind.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Be3 b6" to "Prepares Bb7 to pile up on e4, and keeps ...c5 in reserve.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Bd3" to "The Weiss. The bishop guards e4 and points at Black's king.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Bd3 Na6" to "Heads for b4 or c7 to support ...c5, the key break against White's centre.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O e5" to "The Unzicker. White gains space at once and kicks the f6 knight.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O e5 Nfd7" to "Retreats, ready to hit the e5 pawn with ...c5 or ...f6.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Bc4" to "The Ljubojevic. The bishop eyes f7 before White commits the knight.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Bc4 O-O" to "Castles first; the bishop on c4 can be challenged later.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Bc4 O-O Nf3" to "Develops and supports a later e5.",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Bc4 O-O Nf3 c5" to "Strikes at d4 straight away, the typical Pirc counter to a big centre.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3" to "The Classical. Calm development: White keeps the centre and gets castled.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7" to "Fianchettoes the bishop onto the long diagonal against d4.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2" to "The Quiet System. Solid development before castling.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O" to "Black castles; both sides finish development before the fight.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O" to "White castles too. The battle is about the centre now.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O Nc6" to "The Chigorin line. The knight hits d4 and prepares ...e5.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 h3" to "The Schlechter. Stops ...Bg4 pinning the f3 knight.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 h3 O-O" to "Black castles while White spends a tempo on h3.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 h3 O-O Be3" to "Develops and protects d4.",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 h3 O-O Be3 a6" to "Prepares ...b5 to gain queenside space.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3" to "The 150 Attack. White plans Qd2, Bh6 to swap off Black's best bishop, then a kingside attack.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6" to "Black prepares ...b5 to counterattack on the queenside before castling.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 Qd2" to "Lines up Bh6 to trade dark-squared bishops.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 Qd2 b5" to "Queenside expansion: ...b4 will chase the c3 knight from defending e4.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 Qd2 b5 f3" to "Supports e4 solidly and prepares g4 and h4.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 Qd2 b5 f3 Nbd7" to "Develops and keeps ...e5 or ...c5 in hand.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 h3" to "The Sveshnikov-Jansa. Stops ...Ng4 hitting the bishop, preparing f4.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 h3 Bg7" to "Completes the fianchetto.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 h3 Bg7 f4" to "Builds the big pawn centre with e4, d4 and f4.",
        "e4 d6 d4 Nf6 Nc3 g6 Be3 c6 h3 Bg7 f4 O-O" to "Castles, ready to hit the centre with ...b5 or ...c5.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2" to "Flexible development, keeping options of h4 or g4 for an attack.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2 Bg7" to "Fianchettoes as usual.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2 Bg7 h4" to "The Bayonet. White pushes the h-pawn to open the h-file against Black's king.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2 Bg7 h4 h5" to "Stops h5 in its tracks before castling.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2 Bg7 g4" to "The Chinese. A pawn storm to drive away the f6 knight.",
        "e4 d6 d4 Nf6 Nc3 g6 Be2 Bg7 g4 h6" to "Keeps g5 covered so the knight isn't kicked.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5" to "The Byrne. The bishop pins the f6 knight and eyes a trade for it.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7" to "Develops, ignoring the pin.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2" to "Prepares Bh6 to trade dark-squared bishops.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6" to "Asks the bishop to decide straight away.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6 Bh4" to "Keeps the pin on the f6 knight.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6 Bh4 g5" to "Breaks the pin by force, gaining kingside space.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6 Bh4 g5 Bg3" to "The bishop retreats, now aiming at d6.",
        "e4 d6 d4 Nf6 Nc3 g6 Bg5 Bg7 Qd2 h6 Bh4 g5 Bg3 Nh5" to "Hits the bishop so Black can win the bishop pair with ...Nxg3.",
        "e4 d6 d4 Nf6 Nc3 g6 Bc4" to "The Kholmov. The bishop aims at f7 for an early attack.",
        "e4 d6 d4 Nf6 Nc3 g6 Bc4 Bg7" to "Develops, the f6 knight already guarding f7's neighbourhood.",
        "e4 d6 d4 Nf6 Nc3 g6 Bc4 Bg7 Qe2" to "Prepares e5, backed by the queen.",
        "e4 d6 d4 Nf6 Nc3 g6 Bc4 Bg7 Qe2 Nc6" to "Hits d4, so e5 isn't easy to play.",
        "e4 d6 d4 Nf6 Nc3 g6 g3" to "The Sveshnikov System. White fianchettoes too, for a slower positional game.",
        "e4 d6 d4 Nf6 Nc3 g6 g3 Bg7" to "Black's bishop meets White's on the long diagonals.",
        "e4 d6 d4 Nf6 Nc3 g6 g3 Bg7 Bg2" to "White's bishop supports e4 from g2.",
        "e4 d6 d4 Nf6 Nc3 g6 g3 Bg7 Bg2 O-O" to "Castles in safety.",
        "e4 d6 d4 Nf6 Nc3 g6 g3 Bg7 Bg2 O-O Nge2" to "The knight goes to e2, keeping f2 free for f4.",
        "e4 d6 d4 Nf6 Nc3 g6 g3 Bg7 Bg2 O-O Nge2 e5" to "Strikes at the centre once White's set-up is slow.",
        "e4 d6 d4 Nf6 Nf3" to "The Roscher Gambit. White leaves e4 hanging for quick development.",
        "e4 d6 d4 Nf6 Nf3 Nxe4" to "Black takes the pawn.",
        "e4 d6 d4 Nf6 Nf3 Nxe4 Bd3" to "Hits the knight and develops, aiming at Black's king.",
        "e4 d6 d4 Nf6 Nf3 Nxe4 Bd3 Nf6" to "Retreats with the extra pawn; Black just has to finish developing safely.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5" to "The Classical Variation. The bishop hits the d4 knight straight away.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3" to "Defends the knight with a developing move. If Black takes on d4, the bishop recaptures.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6" to "Black piles a third attacker on d4, and the queen also eyes f2.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3" to "A pawn now supports the knight, so d4 holds against all three attackers.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7" to "Black develops behind the queen, ready to castle or strike with ...d5.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2" to "The queen guards e3, so the knight can leave d4 without the bishop hanging to ...Bxe3. It also prepares queenside castling.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5" to "Black strikes at e4 at once, opening the centre while White's king is still in the middle.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5 Nb5" to "The knight leaves d4 with a threat: Nxc7+ forks the king and the rook on a8.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Qd2 d5 Nb5 Bxe3" to "Black trades off White's good bishop before dealing with the threat on c7.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bb5" to "Puts pressure on c6: the knight is now attacked by both the bishop and the d4 knight.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bb5 Nd8" to "The knight steps back to keep Black's pawns intact, at the cost of time.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nb3" to "Steps away from the attack while hitting the bishop, and keeps the knight out of tactics on d4.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nb3 Bb4+" to "The bishop retreats with check, so White has to spend a move answering it.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nxc6" to "White trades knights first, before Black can build up on d4.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Nxc6 Qf6" to "A clever in-between move: it threatens mate on f2 and attacks the knight on c6.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4" to "The Steinitz Variation. The queen goes straight for the e4 pawn, but she can be harassed.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5" to "Heads for c7, threatening a fork of the king and rook. Black must deal with it.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+" to "Black hits back with check rather than defending c7 straight away.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2" to "Blocks the check with a developing move.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+" to "Black grabs the pawn with check.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+ Be2" to "Blocks again. White is a pawn down but well ahead in development.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+ Be2 Kd8" to "The king defends c7 itself, but Black can no longer castle.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Qh4 Nb5 Bb4+ Bd2 Qxe4+ Be2 Kd8 O-O" to "White castles and gets ready to bring a rook to e1 against Black's king and queen.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6" to "Black develops and counterattacks e4.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6" to "The Mieses. White trades knights so Black has to recapture towards the centre.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6" to "Black takes back towards the centre, accepting split queenside pawns.",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5" to "Gains space and drives the f6 knight away from its best square.",
        "e4 e5 Nf3 Nc6 d4 Nxd4" to "The Lolli Variation. Black swaps knights, but it costs time.",
        "e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4" to "Recaptures with a knight in the centre.",
        "e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4 exd4" to "Black takes back, but White's queen will recapture.",
        "e4 e5 Nf3 Nc6 d4 Nxd4 Nxd4 exd4 Qxd4" to "The queen dominates the centre, and Black has no quick way to chase her.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5" to "Black keeps the extra pawn and develops, guarding d4.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5" to "The knight joins the bishop against f7.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5 Nh6" to "Guards f7 with the knight.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5 Nh6 Nxf7" to "A sacrifice on f7 to drag Black's king into the open.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 Ng5 Nh6 Nxf7 Nxf7 Bxf7+ Kxf7 Qh5+" to "Checks and forks the king and the bishop on c5, winning the piece back.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 O-O" to "White castles, leaving the d4 pawn for faster development.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 O-O d6" to "Black holds on to the extra pawn with a solid pawn move.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 O-O d6 c3" to "White offers a second pawn to open lines towards Black's king.",
        "e4 e5 Nf3 Nc6 d4 exd4 Bc4 Bc5 O-O d6 c3 Bg4" to "Pins the f3 knight to the queen and adds pressure on d4.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3" to "Black accepts the gambit pawn.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Bc4" to "White offers a second pawn, putting development first.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Bc4 Nf6" to "Black develops and attacks e4 instead of taking on b2.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Bc4 Nf6 Nxc3" to "Takes back with a developing move and defends e4.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Nxc3" to "White recaptures and has quick development for the pawn.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Nxc3 Bb4" to "Pins the c3 knight, adding pressure on e4.",
        "e4 e5 Nf3 Nc6 d4 exd4 c3 dxc3 Bc4 Nf6 Nxc3 Bb4" to "Pins the c3 knight, adding pressure on e4.",
        "e4 e5 d4 exd4 c3" to "The Danish Gambit. White offers a pawn for a lead in development.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4" to "Offers a second pawn. Both white bishops will aim at Black's king.",
        "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2" to "Two bishops now sweep across open diagonals towards the black king.",

        // Four Knights, Vienna, King's Gambit
        "e4 e5 Nf3 Nc6 Nc3" to "Develops and defends e4.",
        "e4 e5 Nf3 Nc6 Nc3 Nf6" to "The Four Knights. Both sides develop calmly.",
        "e4 e5 Nf3 Nc6 Nc3 Nf6 Nxe5" to "The Halloween Gambit. White gives a whole knight for a big pawn centre.",
        "e4 e5 Nc3" to "The Vienna. The knight supports e4 and leaves the f-pawn free to advance.",
        "e4 e5 Nc3 Nf6" to "Develops and puts pressure on e4.",
        "e4 e5 Nc3 Nf6 f4" to "The Vienna Gambit. White hits e5 to open the f-file.",
        "e4 e5 f4" to "The King's Gambit. White offers a pawn to deflect e5 and open the f-file.",
        "e4 e5 f4 exf4" to "Black accepts, winning a pawn but letting White build a centre.",
        "e4 e5 f4 exf4 Nf3" to "Develops and stops the queen check on h4.",
        "e4 e5 f4 exf4 Nf3 g5" to "Holds on to the extra pawn, at the cost of loosening the kingside.",
        "e4 e5 f4 exf4 Nf3 d6" to "Fischer's Defense. It covers e5, so the white knight cannot jump there once Black plays g5.",
        "e4 e5 f4 exf4 Nf3 Be7" to "Keeps it simple, and threatens a check on h4.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4" to "Aims the bishop at f7. White carries on developing, even though g4 is coming to hit the knight.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4" to "Attacks the knight on f3. White now has to decide whether to give it up.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 O-O" to "The Muzio Gambit. White castles and simply leaves the knight to be taken. In return the f-file opens for the rook, the queen recaptures on f3, and White's pieces pour towards f7 while Black's are still at home. A whole piece for a big lead in development. Engines say Black can survive with very careful defence, but over the board it is extremely dangerous.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 O-O gxf3" to "Black takes the knight. Black is now a piece up, but behind in development, with the king stuck in the centre.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 O-O gxf3 Qxf3" to "The queen recaptures. Now the queen, the rook on f1 and the bishop on c4 all aim at f7, and the pawn on f4 is about to fall.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 O-O gxf3 Qxf3 Qf6" to "The main defence. Black's queen guards f7 and blocks the f-file.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 d4" to "The Ghulam-Kassim Gambit. White also gives up the knight, but builds a pawn centre first.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 Nc3" to "The McDonnell Gambit. Another way to give up the knight, this time developing the queen's knight.",
        "e4 e5 f4 exf4 Nf3 g5 Bc4 g4 Bxf7+" to "The Lolli Gambit. White gives the bishop on f7 first, to drag the black king out before anything else.",
        "e4 e5 f4 exf4 Nf3 g5 h4" to "Hits the g5 pawn at once, to break up Black's kingside pawns.",
        "e4 e5 f4 exf4 Nf3 g5 h4 g4" to "Pushes on and attacks the knight.",
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5" to "The Kieseritzky. Rather than be taken, the knight jumps to a strong central square.",
        "e4 e5 f4 exf4 Bc4" to "The Bishop's Gambit. White allows the queen check in exchange for speed against f7.",
        "e4 e5 f4 d5" to "The Falkbeer Countergambit. Black strikes in the centre instead of taking.",

        // Sicilian
        "e4 c5" to "The Sicilian. Black fights for d4 from the side and makes the game unbalanced.",
        "e4 c5 Nf3" to "Prepares d4.",
        "e4 c5 Nf3 d6" to "Controls e5 and opens a path for the light-squared bishop.",
        "e4 c5 Nf3 d6 d4" to "Opens the centre.",
        "e4 c5 Nf3 d6 d4 cxd4" to "Black swaps a wing pawn for a centre pawn.",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4" to "White gets space and quick development. Black gets the half-open c-file.",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6" to "Attacks e4, so that White has to block the c-pawn with Nc3.",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3" to "Defends e4.",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6" to "The Najdorf. A quiet move that takes b5 from White's pieces and keeps every plan open.",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6" to "The Dragon. The bishop goes to g7 to rake the long diagonal.",
        "e4 c5 d4" to "Offers to open the centre at once.",
        "e4 c5 d4 cxd4 c3" to "The Smith-Morra Gambit. White gives a pawn for fast development and open lines.",
        "e4 c5 d4 cxd4 c3 dxc3 Nxc3" to "White recaptures with a lead in development, and the c and d files open for the rooks.",
        "e4 c5 b4" to "The Wing Gambit. White tries to deflect the c-pawn and take the centre.",

        // French
        "e4 e6" to "The French. Black prepares d5, building a solid pawn chain.",
        "e4 e6 d4" to "Takes the whole centre.",
        "e4 e6 d4 d5" to "Challenges e4.",
        "e4 e6 d4 d5 Nc3" to "Defends e4 with a piece.",
        "e4 e6 d4 d5 Nc3 Bb4" to "The Winawer. The bishop pins the knight that defends e4.",
        "e4 e6 d4 d5 Nc3 Nf6" to "Attacks e4 a second time.",
        "e4 e6 d4 d5 Nd2" to "The Tarrasch. The knight defends e4 while avoiding a pin from the bishop.",
        "e4 e6 d4 d5 e5" to "The Advance. White gains space and locks the centre.",
        "e4 e6 d4 d5 e5 c5" to "Strikes at the base of White's pawn chain, the usual French plan.",
        "e4 e6 d4 d5 exd5" to "The Exchange. White releases the tension and the position becomes symmetrical.",

        // Caro-Kann
        "e4 c6" to "The Caro-Kann. Black prepares d5 and keeps the light-squared bishop's path open.",
        "e4 c6 d4" to "Takes the whole centre.",
        "e4 c6 d4 d5" to "Challenges e4, with the c-pawn in support.",
        "e4 c6 d4 d5 Nc3" to "Defends e4.",
        "e4 c6 d4 d5 Nc3 dxe4" to "Black takes on e4.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4" to "White recaptures with a well placed knight.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5" to "The Classical. The bishop comes out before e6 can shut it in, and hits the knight.",
        "e4 c6 d4 d5 e5" to "The Advance. White gains space.",
        "e4 c6 d4 d5 e5 Bf5" to "Gets the bishop out before e6 locks it in.",
        "e4 c6 d4 d5 exd5" to "The Exchange. White releases the tension.",
        "e4 c6 d4 d5 exd5 cxd5" to "Recaptures towards the centre.",

        // Other e4 defences
        "e4 d5" to "The Scandinavian. Black challenges e4 straight away.",

        // Queen's pawn
        "d4 d5" to "Black matches White in the centre.",
        "d4 d5 c4" to "The Queen's Gambit. White challenges Black's centre pawn.",
        "d4 d5 c4 dxc4" to "The Queen's Gambit Accepted. Black takes, but can rarely keep the pawn, and uses the time to develop.",
        "d4 d5 c4 dxc4 Nf3" to "Develops and stops e5.",
        "d4 d5 c4 dxc4 e3" to "Opens the bishop's diagonal, to win back the pawn on c4.",
        "d4 d5 c4 dxc4 e4" to "Takes the whole centre.",
        "d4 d5 c4 e6" to "The Queen's Gambit Declined. A solid defence of d5, though it shuts in the light-squared bishop.",
        "d4 d5 c4 e6 Nc3" to "Adds pressure on d5.",
        "d4 d5 c4 e6 Nc3 Nf6" to "Develops and defends d5.",
        "d4 d5 c4 e6 Nc3 Nf6 Bg5" to "Pins the knight that defends d5.",
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7" to "Breaks the pin and prepares to castle.",
        "d4 d5 c4 e5" to "The Albin Countergambit. Black gives a pawn to drive a wedge into White's position.",
        "d4 d5 c4 e5 dxe5 d4" to "Gains space and cramps White's development.",
        "d4 d5 c4 c6" to "The Slav. It supports d5 and leaves the light-squared bishop free.",
        "d4 d5 c4 c6 Nf3" to "Develops and controls e5.",
        "d4 d5 c4 c6 Nf3 Nf6" to "Develops and defends d5.",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3" to "Adds pressure on d5.",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4" to "Black takes on c4, hoping to hold it with b5.",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4" to "Stops b5, so Black cannot hold on to the pawn.",
        "d4 d5 e4" to "The Blackmar-Diemer. White offers a pawn to open lines.",
        "d4 d5 e4 dxe4 Nc3" to "Develops and attacks the pawn on e4.",
        "d4 d5 e4 dxe4 Nc3 Nf6 f3" to "Offers the pawn to open the f-file for an attack.",
        "d4 e5" to "The Englund Gambit. A dubious pawn offer, full of traps.",

        // Indian defences
        "d4 Nf6" to "Controls e4 with a piece, keeping the pawns flexible.",
        "d4 Nf6 c4" to "Takes more space and fights for d5.",
        "d4 Nf6 c4 e6" to "Opens the dark-squared bishop's diagonal.",
        "d4 Nf6 c4 e6 Nc3" to "Prepares e4.",
        "d4 Nf6 c4 e6 Nc3 Bb4" to "The Nimzo-Indian. The pin stops e4, and Black may double White's pawns by taking on c3.",
        "d4 Nf6 c4 g6" to "Prepares to put the bishop on g7.",
        "d4 Nf6 c4 g6 Nc3" to "Supports e4.",
        "d4 Nf6 c4 g6 Nc3 Bg7" to "The bishop will pressure the centre from a distance.",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4" to "Builds a big pawn centre.",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6" to "The King's Indian. Black prepares e5 to fight White's centre.",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3" to "Develops and controls e5.",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O" to "Black castles. A kingside pawn storm with f5 often follows.",
        "d4 Nf6 c4 c5" to "Hits White's centre from the side.",
        "d4 Nf6 c4 c5 d5" to "Gains space.",
        "d4 Nf6 c4 c5 d5 b5" to "The Benko Gambit. Black gives a pawn to open the a and b files for the rooks.",
        "d4 Nf6 c4 e5" to "The Budapest Gambit. Black offers a pawn to unbalance the game.",
        "d4 Nf6 c4 e5 dxe5 Ng4" to "Attacks e5, to win the pawn back.",

        // Meme openings
        "e4 e5 Ke2" to "The Bongcloud. The king walks out for no reason at all, blocking the bishop and giving up castling. A pure joke.",
        "e4 e5 Nf3 Nc6 Nxe5" to "The Irish Gambit. White gives a whole knight for one pawn.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5" to "Black takes the knight, and is a piece up for a pawn.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 d4" to "White chases the knight and builds a big centre, the only real idea behind the gambit.",
        "e4 e5 Qh5" to "The Wayward Queen. The queen attacks e5 and eyes f7, hoping for a quick Scholar's Mate.",
        "e4 e5 Qh5 Nc6" to "Defends e5 with a developing move.",
        "e4 e5 Qh5 Nc6 Bc4" to "The bishop joins the queen on f7. This threatens mate on f7.",
        "e4 e5 Qh5 Nc6 Bc4 g6" to "Blocks the mate threat and chases the queen.",
        "e4 e5 Qh5 Nc6 Bc4 g6 Qf3" to "Renews the mate threat on f7.",
        "e4 e5 Qh5 Nc6 Bc4 g6 Qf3 Nf6" to "Blocks the queen's line to f7. Black is now well developed and White's queen will be chased again.",
        "e4 e5 Qh5 Nf6" to "The Kiddie Countergambit. Black ignores the threat to e5 and develops, daring the queen to take.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+" to "The Jerome Gambit. White gives a bishop to drag the king out.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7" to "Black takes the bishop. The king is exposed, but Black is a piece up.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+" to "A second piece goes, to win a pawn with check and pull the king further out.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+ Nxe5" to "Black takes again, now two pieces up.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+ Nxe5 Qh5+" to "The queen checks, and will try to win back material on e5 or c5 with forks.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5" to "The knight heads for f7 and e6, and avoids swapping itself off.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6" to "Black kicks the knight, expecting it to retreat.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7" to "The Alien Gambit. Instead of retreating, the knight crashes into f7 to drag the king out.",
        "g4" to "The Grob. It grabs space on the kingside and prepares to fianchetto the bishop, but loosens White's own king.",
        "g4 d5" to "Black takes the centre and attacks the g4 pawn with the bishop.",
        "g4 d5 Bg2" to "The Grob Gambit. The bishop pressures d5 and b7, and White lets the g4 pawn go.",
        "e3" to "A modest move that opens a diagonal for the bishop. It is also the first step of the Cow.",
        "e3 d5 d3" to "Both centre pawns now sit one step forward. The Cow is a fixed setup, so White plays the same moves whatever Black does.",
        "e3 d5 d3 e5 Ne2" to "The knight steps to e2, on its way to g3.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3" to "The knight reaches g3. Out, back to the second rank, and out again: that is the Cow.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2" to "The other knight goes to d2, heading for b3.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3" to "The Cow is complete, with knights on b3 and g3, and here it even hits the bishop. Next White castles and looks for a break with e4 or d4.",
        "d4 Nf6 g4" to "The Kilkenny Gambit. White offers the g-pawn to pull Black's knight away from the centre.",
        "d4 Nf6 g4 Nxg4" to "Black takes. The knight has left its best square, and White gains time to build a centre.",
        "d4 Nf6 g4 Nxg4 e4" to "White takes the centre with two pawns, and will chase the knight with Be2 or f3.",
        "d4 Nf6 g4 Nxg4 f3" to "The Maltese Falcon. White kicks the knight straight away, then plays e4.",
        "d4 Nf6 g4 d5" to "Black declines and takes the centre instead.",
        "d4 Nf6 g4 d5 g5" to "The pawn kicks the knight anyway, so Black loses a little time.",
        "f3" to "The Barnes Opening. It takes away the knight's best square and weakens the king. Not recommended.",
        "f3 e5 Kf2" to "The Hammerschlag. The king walks out on move two, a joke on top of a joke.",
        "Na3" to "The Sodium Attack. The knight goes to the edge, where it controls few squares, though it can come to c4 later.",
        "e4 g6 Bc4" to "Aims the bishop at f7.",
        "e4 g6 Bc4 Bg7" to "Black fianchettoes, ignoring White's early aggression.",
        "e4 g6 Bc4 Bg7 Qf3" to "The Monkey's Bum. The queen joins the bishop against f7.",
        "e4 c5 f4" to "The Grand Prix setup. White plans a kingside attack with f4.",
        "e4 c5 f4 d5" to "Black strikes back in the centre at once.",
        "e4 c5 f4 d5 Nc3" to "The Toilet Variation. White leaves the e4 pawn to be taken in return for quick development.",
        "a4" to "The Ware Opening. A rook pawn move that does almost nothing for the centre.",
        "a4 e5 h4" to "The Crab. The other rook pawn moves too, like a pair of claws. Purely a joke.",

        // Meme openings: book branches
        "e3 d5" to "Black takes the centre with a full two-square step.",
        "e3 d5 d3 e5" to "Black grabs even more of the centre. The Cow does not mind, and just carries on.",
        "e3 d5 d3 e5 Ne2 Nf6" to "Black develops naturally, aiming at the centre and preparing to castle.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6" to "Another natural developing move. Black is already well ahead in the centre.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5" to "The bishop comes out on an active diagonal aiming at f2, and Black is ready to castle.",
        "d4 Nf6 g4 Nxg4 e4 d6" to "Takes e5 away from the white pieces and opens a line for the bishop.",
        "d4 Nf6 g4 Nxg4 e4 d6 Be2" to "Develops with a hit on the knight, which has to move again.",
        "d4 Nf6 g4 Nxg4 e4 d6 Be2 Nf6" to "The knight retreats to its natural square, but it has used three moves to get there.",
        "d4 Nf6 g4 Nxg4 e4 d6 Be2 Nf6 Nc3" to "Defends e4 and develops. For one pawn, White has a big centre and the open g-file for a rook.",
        "d4 Nf6 g4 Nxg4 f3 Nf6" to "The knight goes home, and White is ready for e4.",
        "d4 Nf6 g4 Nxg4 f3 Nf6 e4" to "White has the classic two-pawn centre, with f3 supporting it and the g-file open for a later attack.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6" to "Covers f7 with the knight. It looks odd on the edge, but it stops the mate and can jump to g4 or f5 later.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3" to "Supports e4, and opens the dark-squared bishop, which now eyes the knight on h6.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3 g6" to "Chases the queen away from h5.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3 g6 Qf3" to "The queen keeps aiming at f7 from a new square.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3 g6 Qf3 f6" to "Shields f7 for good and supports e5. It loosens the king a little, but the queen's attack is over.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3 g6 Qf3 f6 Ne2" to "The knight develops to e2, so it does not block the queen on f3.",
        "e4 e5 Qh5 Nc6 Bc4 Nh6 d3 g6 Qf3 f6 Ne2 d5" to "The Mellon Gambit. Black strikes in the centre and offers a pawn to open lines while White's queen is misplaced.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6" to "Challenges the strong knight on e4 and offers to trade it off.",
        "g4 d5 Bg2 c6" to "Declines the gambit. The c6 pawn shores up d5 and blunts the bishop on g2.",
        "g4 d5 Bg2 c6 c4" to "Hits d5 again, trying to open the long diagonal for the bishop.",
        "g4 d5 Bg2 c6 c4 dxc4" to "Black takes. The diagonal opens, but Black is a pawn up.",
        "g4 d5 Bg2 c6 c4 dxc4 b3" to "The Richter-Grob Gambit. Another pawn offer, to open lines on the queenside.",
        "g4 d5 Bg2 c6 g5" to "The Spike Attack. The pawn marches on and takes the f6 square away from Black's knight.",
        "g4 d5 Bg2 Bxg4" to "Black accepts and takes the g-pawn. But the bishop has left b7 without a defender.",
        "g4 d5 Bg2 Bxg4 c4" to "The Fritz Gambit. White hits d5, so that the bishop on g2 can take on b7.",
        "g4 d5 Bg2 Bxg4 c4 d4" to "The Romford Countergambit. Black lets b7 go and grabs space in the centre instead.",
        "g4 d5 Bg2 Bxg4 c4 d4 Bxb7" to "The bishop wins the b7 pawn and attacks the rook on a8.",
        "g4 d5 Bg2 Bxg4 c4 d4 Bxb7 Nd7" to "Black develops and gives up the rook in the corner, betting on the white king's weak light squares.",
        "g4 d5 Bg2 Bxg4 c4 d4 Bxb7 Nd7 Bxa8" to "White wins the rook.",
        "g4 d5 Bg2 Bxg4 c4 d4 Bxb7 Nd7 Bxa8 Qxa8" to "The queen takes back and now rules the long diagonal, attacking the rook on h1 straight away.",
        "g4 d5 Bg2 h5" to "Hits the g4 pawn at once, to open the h-file for Black's rook.",
        "g4 d5 Bg2 h5 gxh5" to "White grabs the pawn, but the kingside is broken and the h-file is open for Black.",
        "g4 d5 e4" to "White offers the e-pawn too, for fast development.",
        "g4 d5 e4 dxe4" to "Black takes a second pawn.",
        "g4 d5 e4 dxe4 Nc3" to "The Zilbermints Gambit. White develops and attacks the pawn on e4, to win it back.",
        "g4 d5 e4 dxe4 Nc3 h5" to "Hits the g4 pawn, to open lines against White's loose kingside.",
        "g4 d5 e4 dxe4 Nc3 e5" to "Black builds a centre and frees the bishop.",
        "g4 d5 e4 dxe4 Nc3 e5 d3" to "White offers yet another pawn to open lines for the pieces.",
        "g4 d5 h3" to "A calmer approach. White protects g4, keeping the Grob setup intact.",
        "g4 d5 h3 e5" to "Black takes the full centre.",
        "g4 d5 h3 e5 Bg2" to "The bishop takes its post on the long diagonal, aiming at d5 and b7.",
        "g4 d5 h3 e5 Bg2 c6" to "The Keene Defense. Black shores up d5 and blunts the bishop.",
        "g4 d5 h3 e5 Bg2 c6 d4" to "Strikes at e5 straight away.",
        "g4 d5 h3 e5 Bg2 c6 d4 e4" to "Black pushes past, gaining space and shutting out the bishop on g2.",
        "g4 d5 h3 e5 Bg2 c6 d4 e4 c4" to "Attacks Black's centre from the other side.",
        "g4 d5 h3 e5 Bg2 c6 d4 e4 c4 Bd6" to "Develops the bishop to a strong square, aiming at White's kingside.",
        "g4 d5 h3 e5 Bg2 c6 d4 e4 c4 Bd6 Nc3" to "Adds pressure on d5.",
        "g4 d5 h3 e5 Bg2 c6 d4 e4 c4 Bd6 Nc3 Ne7" to "The knight goes to e7, where it supports d5 and cannot be kicked by g5.",
        "g4 g5" to "The Double Grob. Black copies White's strange move.",
        "g4 g5 f4" to "The Coca-Cola Gambit. White offers a pawn to rip open Black's kingside.",
        "g4 e5" to "Black takes the centre and opens lines for the queen and bishop.",
        "g4 e5 h3" to "Protects the g4 pawn.",
        "g4 e5 h3 Nc6" to "The London Defense. Black develops and prepares d5.",
        "g4 e5 Bg2" to "The bishop goes to the long diagonal, and g4 is left to fend for itself.",
        "g4 e5 Bg2 d5" to "Black takes the whole centre.",
        "g4 e5 Bg2 d5 c4" to "The Hurst Attack. White hits d5 to open the bishop's diagonal.",
        "f3 e5" to "Black takes the centre. White's king is already exposed on the e1 to h4 diagonal.",
        "Na3 e5" to "Black takes the centre.",
        "Na3 e5 d3" to "Supports a later e4 and opens the dark-squared bishop.",
        "Na3 e5 d3 Bxa3" to "Black gives up the bishop for the knight, wrecking White's queenside pawns.",
        "Na3 e5 d3 Bxa3 bxa3" to "White's pawns are doubled on the a-file, but the b-file opens for the rook.",
        "Na3 e5 d3 Bxa3 bxa3 d5" to "Black builds a broad centre.",
        "Na3 e5 d3 Bxa3 bxa3 d5 e3" to "Solid, but passive.",
        "Na3 e5 d3 Bxa3 bxa3 d5 e3 c5" to "Black takes even more space in the centre.",
        "Na3 e5 d3 Bxa3 bxa3 d5 e3 c5 Rb1" to "The Celadon Variation. The rook uses the open b-file to aim at b7. That is the point of White's broken pawns.",
        "Na3 e5 Nc4" to "The knight heads to c4, where it attacks e5.",
        "Na3 e5 Nc4 Nc6" to "Defends e5 with a developing move.",
        "Na3 e5 Nc4 Nc6 e4" to "White takes a share of the centre.",
        "Na3 e5 Nc4 Nc6 e4 f5" to "Black hits e4 with the f-pawn, like a King's Gambit in reverse.",
        "Na3 g6" to "A calm reply. Black prepares to put the bishop on g7.",
        "Na3 g6 g4" to "The Chenoboskion Variation. White adds a kingside pawn push to the edge knight.",
        "e4 g6" to "The Modern Defense. Black lets White take the centre, and will attack it from a distance with the bishop on g7.",
        "e4 g6 Bc4 Bg7 Qf3 e6" to "Blocks the bishop's line to f7 and covers d5 and f5.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4" to "Offers the d-pawn. If Black takes, the bishop leaves the dark squares around Black's king.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4" to "Black takes the pawn, but the dark squares around the black king are now weak, and White will aim at them.",
        "a3" to "A quiet waiting move that controls b4.",
        "a3 e5" to "Black takes the centre.",
        "a3 e5 b3" to "Prepares to put the bishop on b2.",
        "a3 e5 b3 d5" to "Black takes the whole centre.",
        "a3 e5 b3 d5 c3" to "Another one-step pawn move. White keeps everything on the third rank, like a hippo hiding in the water.",
        "a3 e5 b3 d5 c3 Nf6" to "Black develops, well ahead in the race.",
        "a3 e5 b3 d5 c3 Nf6 d3" to "The d-pawn joins the wall.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6" to "Black develops again, and is far ahead.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3" to "Another pawn steps forward one square.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6" to "Black's bishop aims at the kingside.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3" to "The f-pawn joins the wall too, loosening White's king.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O" to "Black castles with a lovely position.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3" to "Every white pawn but one has now stepped forward one square. That is the Hippopotamus, a wall with nothing behind it yet.",
        "a4 e5" to "Black takes the centre while White moves a rook pawn.",

        // Meme openings: the engine's three strongest branches
        "e4 e5 Ke2 Nf6" to "The strongest reply. Black develops with a hit on e4, which the king cannot comfortably defend. Engines already rate Black almost two pawns better.",
        "e4 e5 Ke2 Nf6 Nc3" to "Defends e4 with the knight. There is little else, since the king now blocks the bishop on f1.",
        "e4 e5 Ke2 Nf6 Nc3 d5" to "Black strikes in the centre at once. Opening the position is exactly what you want against a king on e2.",
        "e4 e5 Ke2 Nf6 Nc3 d5 exd5" to "White takes, to keep the centre from opening even further.",
        "e4 e5 Ke2 Nf6 Nc3 d5 exd5 Bc5" to "Black does not hurry to take back. The bishop develops towards f2, right next to White's wandering king.",
        "e4 e5 Ke2 Nf6 Nc3 d5 exd5 Bc5 Ke1" to "The king walks home again. White admits the Bongcloud cost two moves, and the right to castle, for nothing.",
        "e4 e5 Ke2 Nf6 Nc3 d5 exd5 Bc5 Ke1 O-O" to "Black castles, fully developed and safe, while White's king can never castle. A big lead for Black.",
        "e4 e5 Ke2 d5" to "Black opens the centre straight away, to expose the king on e2.",
        "e4 e5 Ke2 d5 exd5" to "White takes. Leaving the pawn would let Black open the e-file towards the king.",
        "e4 e5 Ke2 d5 exd5 Nf6" to "Black develops instead of taking back. The d5 pawn can be picked up later, and every developing move counts double against a misplaced king.",
        "e4 e5 Ke2 d5 exd5 Nf6 Nc3" to "Defends the extra pawn on d5.",
        "e4 e5 Ke2 d5 exd5 Nf6 Nc3 Bc5" to "The bishop aims at f2, next to the king.",
        "e4 e5 Ke2 d5 exd5 Nf6 Nc3 Bc5 Ke1" to "The king goes back home, with no castling rights left.",
        "e4 e5 Ke2 d5 exd5 Nf6 Nc3 Bc5 Ke1 O-O" to "Black castles with a big lead in development, easily worth the pawn.",
        "e4 e5 Ke2 Bc5" to "Develops the bishop straight at f2, a target right next to White's king.",
        "e4 e5 Ke2 Bc5 d3" to "Opens the dark-squared bishop and gives the king a little more cover.",
        "e4 e5 Ke2 Bc5 d3 Nf6" to "Develops, preparing to castle and to open the centre with d5.",
        "e4 e5 Ke2 Bc5 d3 Nf6 Nc3" to "Defends e4 a second time.",
        "e4 e5 Ke2 Bc5 d3 Nf6 Nc3 d5" to "Black opens the centre, which hurts the side whose king is stuck in the middle.",
        "e4 e5 Ke2 Bc5 d3 Nf6 Nc3 d5 Bg5" to "Pins the f6 knight against the queen, White's only active idea.",
        "e4 e5 Ke2 Bc5 d3 Nf6 Nc3 d5 Bg5 h6" to "Asks the bishop what it wants. Black is far better developed and the white king is still on e2.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6" to "The bishop steps back to b6. It stays on the diagonal towards f2, and the knight can no longer hit it.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2" to "White develops the last minor piece and gets ready to castle. The Cow setup is complete.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2 a5" to "Black threatens a4, to kick the b3 knight back and gain space on the queenside.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2 a5 c3" to "Controls d4 and b4, and prepares a central break later on.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2 a5 c3 h5" to "Black goes after the knight on g3. The plan is h4, a common way to punish the Cow's knights.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2 a5 c3 h5 h4" to "White stops the h-pawn, so the knight can stay on g3.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bb6 Be2 a5 c3 h5 h4 Qe7" to "Black connects the pieces with a comfortable space advantage. Engines rate Black about a pawn and a half better.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7" to "A modest retreat. The bishop is safe from knight jumps and protects the kingside.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2" to "White develops the bishop and prepares to castle.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2 a5" to "Black threatens a4, to drive the b3 knight away.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2 a5 a4" to "White blocks the a-pawn, so the knight on b3 keeps its square.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2 a5 a4 g6" to "Takes the f5 and h5 squares away from the knight on g3.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2 a5 a4 g6 O-O" to "White castles. The Cow is now a complete setup: cramped, but solid and playable.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Be7 Be2 a5 a4 g6 O-O h5" to "Black threatens h4, to kick the knight from g3 after all.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6" to "The bishop retreats to d6, where it guards e5 and points at White's kingside.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4" to "The Cow's central break. With development finished, White finally hits d5.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4 d4" to "Black pushes past, locking the centre and gaining space.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4 d4 c3" to "White chips at the d4 pawn from the side.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4 d4 c3 a5" to "Black threatens a4, kicking the knight.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4 d4 c3 a5 Be2" to "White develops and prepares to castle.",
        "e3 d5 d3 e5 Ne2 Nf6 Ng3 Nc6 Nd2 Bc5 Nb3 Bd6 e4 d4 c3 a5 Be2 a4" to "The pawn kicks the b3 knight back. Black keeps the extra space.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3" to "White develops instead of chasing the knight. A piece down for a pawn, White needs every tempo.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3 Ng6" to "The knight drops back to g6, safe from d4 and f4 pawn kicks.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3 Ng6 g3" to "Prepares to put the bishop on g2, and takes h4 away from Black's queen.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3 Ng6 g3 Nf6" to "Black develops and attacks e4.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3 Ng6 g3 Nf6 d4" to "White takes the centre, the only compensation for the missing piece.",
        "e4 e5 Nf3 Nc6 Nxe5 Nxe5 Nc3 Ng6 g3 Nf6 d4 d5" to "Black strikes back and opens the position. With an extra piece, open lines favour Black. Engines rate Black more than four pawns better.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7" to "A clever reply. The queen and the knight both attack e5, and the queen also eyes e4 behind it, so White cannot hold everything. Engines call it equal.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3" to "The knight retreats. Now the queen is aiming straight at the e4 pawn.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3 d5" to "Rather than grabbing e4 at once, Black opens the centre first.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3 d5 d3" to "Defends e4.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3 d5 d3 dxe4" to "Black trades in the centre.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3 d5 d3 dxe4 dxe4" to "White takes back, and the e-file opens in front of both kings.",
        "e4 e5 Nf3 Nc6 Nxe5 Qe7 Nf3 d5 d3 dxe4 dxe4 Qxe4+" to "Black wins back the pawn with check. Material is level and the game is equal.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4" to "Black attacks e4 and eyes f2. Playable, but it lets White offer a queen trade with an extra pawn.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4" to "White offers to trade queens, and from g4 the queen also guards e4.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4 Qxg4" to "Black takes the trade.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4 Qxg4 Nxg4" to "The knight recaptures and escapes from e5. White is simply a pawn up.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4 Qxg4 Nxg4 f5" to "Black hits e4 to free the position, offering another pawn for activity.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4 Qxg4 Nxg4 f5 exf5" to "White takes. Two pawns up now, but behind in development.",
        "e4 e5 Nf3 Nc6 Nxe5 Qh4 Qg4 Qxg4 Nxg4 f5 exf5 d5" to "Black opens lines for the bishops and plans to win back f5 with Bxf5.",
        "d4 Nf6 g4 Nxg4 e4 d6 Ne2" to "Develops the knight to e2, where it does not block the f-pawn. White wants f3 to kick the knight on g4.",
        "d4 Nf6 g4 Nxg4 e4 d6 Ne2 Nd7" to "The other knight comes to d7, ready to support the g4 knight's retreat to f6.",
        "d4 Nf6 g4 Nxg4 e4 d6 Ne2 Nd7 f3" to "Kicks the knight away from g4, gaining time.",
        "d4 Nf6 g4 Nxg4 e4 d6 Ne2 Nd7 f3 Ngf6" to "The knight retreats. Black is a pawn up, White has a big centre and the g-file. Engines slightly prefer Black.",
        "d4 Nf6 g4 d5 g5 Ne4" to "The knight jumps to e4, a strong central square supported by d5.",
        "d4 Nf6 g4 d5 g5 Ne4 c4" to "Hits d5, to undermine the knight's support.",
        "d4 Nf6 g4 d5 g5 Ne4 c4 h6" to "Black strikes at the g5 pawn, to open the kingside.",
        "d4 Nf6 g4 d5 g5 Ne4 c4 h6 gxh6" to "White takes, opening the g-file.",
        "d4 Nf6 g4 d5 g5 Ne4 c4 h6 gxh6 e5" to "Black opens the centre while White's kingside is full of holes.",
        "d4 Nf6 g4 e5" to "The Oshima Defense. Black ignores the g-pawn and hits d4 instead, a counterattack.",
        "d4 Nf6 g4 e5 Bg2" to "White develops instead of taking on e5.",
        "d4 Nf6 g4 e5 Bg2 Nxg4" to "Now Black takes the g-pawn.",
        "d4 Nf6 g4 e5 Bg2 Nxg4 h3" to "Kicks the knight away.",
        "d4 Nf6 g4 e5 Bg2 Nxg4 h3 Nxf2" to "Black sacrifices the knight on f2 to expose White's king.",
        "d4 Nf6 g4 e5 Bg2 Nxg4 h3 Nxf2 Kxf2" to "White takes the knight.",
        "d4 Nf6 g4 e5 Bg2 Nxg4 h3 Nxf2 Kxf2 Qh4+" to "The queen checks. Black has two pawns for the knight and a lasting attack. Engines rate it dead level.",
        "e4 e5 Qh5 Nc6 Bc4 g6 Qf3 Nf6 Ne2" to "Develops the knight to e2, so it does not block the queen on f3.",
        "e4 e5 Qh5 Nc6 Bc4 g6 Qf3 Nf6 Ne2 Bg7" to "Black completes the fianchetto. Black is comfortable, and White's queen has used three moves for nothing.",
        "e4 e5 Qh5 Nf6 Qxe5+" to "The queen takes the pawn with check.",
        "e4 e5 Qh5 Nf6 Qxe5+ Be7" to "Blocks the check and develops.",
        "e4 e5 Qh5 Nf6 Qxe5+ Be7 Qf4" to "The queen retreats, a pawn up but having moved three times.",
        "e4 e5 Qh5 Nf6 Qxe5+ Be7 Qf4 Nc6" to "Black develops, well ahead in the race.",
        "e4 e5 Qh5 Nf6 Qxe5+ Be7 Qf4 Nc6 e5" to "Kicks the knight from f6.",
        "e4 e5 Qh5 Nf6 Qxe5+ Be7 Qf4 Nc6 e5 Nh5" to "The knight attacks the queen on f4, gaining yet more time. Black's lead in development makes up for the pawn.",
        "e4 e5 Qh5 d6" to "Defends e5 calmly, and opens a line for the c8 bishop.",
        "e4 e5 Qh5 d6 Bc4" to "Aims the bishop at f7, threatening mate.",
        "e4 e5 Qh5 d6 Bc4 g6" to "Blocks the mate and chases the queen.",
        "e4 e5 Qh5 d6 Bc4 g6 Qd1" to "The queen goes all the way home, admitting the attack is over.",
        "e4 e5 Qh5 d6 Bc4 g6 Qd1 Nf6" to "Black develops comfortably.",
        "e4 e5 Qh5 d6 Bc4 g6 Qd1 Nf6 d3" to "Supports e4.",
        "e4 e5 Qh5 d6 Bc4 g6 Qd1 Nf6 d3 Bg7" to "Black completes the fianchetto with a pleasant position.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+ Nxe5 Qh5+ Kf8" to "The king steps to f8, out of the checks.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+ Nxe5 Qh5+ Kf8 Qxe5" to "The queen fork pays off. White wins back a knight, but Black is still a whole bishop up.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kxf7 Nxe5+ Nxe5 Qh5+ Kf8 Qxe5 d5" to "Black opens the c8 bishop's diagonal and gets ready to develop. Engines rate Black more than four pawns better.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8" to "Black declines the bishop. The king steps aside, the bishop escapes, and White is simply a pawn up.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3" to "The bishop retreats safely. White has won f7, and Black's king can no longer castle.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3 d6" to "Defends e5.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3 d6 d3" to "White develops calmly.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3 d6 d3 Qf6" to "Black aims at f2 and down the half-open f-file.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3 d6 d3 Qf6 Nc3" to "Develops, and keeps an eye on d5.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Kf8 Bb3 d6 d3 Qf6 Nc3 Nge7" to "Black develops, but White is a clear pawn up with the safer king. Engines rate White more than two pawns better.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7" to "A mistake. The king stays in the centre and blocks Black's own pieces.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8" to "The bishop takes the g8 knight, keeping the extra pawn and the lead in development.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8 Qxg8" to "Black takes back.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8 Qxg8 Nc3" to "White develops.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8 Qxg8 Nc3 h6" to "A slow move. White can now grab e5.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8 Qxg8 Nc3 h6 Nxe5" to "Wins the e5 pawn.",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 Bxf7+ Ke7 Bxg8 Qxg8 Nc3 h6 Nxe5 Nxe5" to "Black takes, but now d4 forks the knight on e5 and the bishop on c5. Engines rate White about four pawns better.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7" to "Black takes the knight. Black is a piece up, but the king has been dragged into the open.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3" to "White develops the other knight and gets ready for a bishop check on c4.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3 c5" to "Black hits d4 straight away, opening lines before White's attack gets going.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3 c5 Bc4+" to "Check, with the bishop aiming at the exposed king.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3 c5 Bc4+ e6" to "Blocks the check, and the e6 pawn shields the king.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3 c5 Bc4+ e6 Qe2" to "The queen joins in, pressing on e6.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Kxf7 Nf3 c5 Bc4+ e6 Qe2 cxd4" to "Black grabs a pawn and opens the c-file. Black is a piece up and should survive with care. Engines give Black about three pawns' advantage.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4" to "A mistake. The bishop hits White's queen, but leaves the f7 knight free to keep forking the queen and rook.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4" to "The queen simply takes the bishop.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4 Qa5+" to "A check to gain time.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4 Qa5+ Bd2" to "Blocks the check, and the bishop now attacks the queen on a5.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4 Qa5+ Bd2 Nxg4" to "Black takes White's queen with the knight.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4 Qa5+ Bd2 Nxg4 Bxa5" to "And White takes Black's queen.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Bg4 Qxg4 Qa5+ Bd2 Nxg4 Bxa5 Kxf7" to "Black finally takes the knight on f7. After all the trades, White has come out well ahead. Engines rate White about five pawns better.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5" to "A blunder. The queen escapes the fork, but the rook on h8 is still attacked.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8" to "The knight takes the rook. White is now well ahead in material.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8 Bf5" to "Black develops, hoping to trap the knight in the corner later.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8 Bf5 Nf3" to "Develops the other knight towards the centre.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8 Bf5 Nf3 Qe4+" to "The queen checks and eyes the c2 pawn.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8 Bf5 Nf3 Qe4+ Be3" to "Blocks the check with a developing move.",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Ng5 h6 Nxf7 Qd5 Nxh8 Bf5 Nf3 Qe4+ Be3 Qxc2" to "Black grabs a pawn, but is a whole rook down. Engines rate White about eight pawns better.",
        "g4 d5 c4" to "White attacks d5 straight away, hoping to break Black's centre.",
        "g4 d5 c4 Bxg4" to "Black takes the loose g4 pawn.",
        "g4 d5 c4 Bxg4 Qb3" to "The queen attacks b7 and d5 together.",
        "g4 d5 c4 Bxg4 Qb3 dxc4" to "Black gives up b7 and takes on c4, hitting the queen.",
        "g4 d5 c4 Bxg4 Qb3 dxc4 Qxb7" to "White grabs b7, and the queen now eyes the rook on a8.",
        "g4 d5 c4 Bxg4 Qb3 dxc4 Qxb7 Nd7" to "Black develops. The rook on a8 is protected by the queen, and Rb8 will soon chase White's queen. Engines rate Black about a pawn and a half better.",
        "g4 e5 Bg2 h5" to "Black hits the g4 pawn at once.",
        "g4 e5 Bg2 h5 gxh5" to "White takes, leaving doubled h-pawns.",
        "g4 e5 Bg2 h5 gxh5 d5" to "Black takes the centre, and can win back h5 later with the rook or queen.",
        "g4 e5 Bg2 h5 gxh5 d5 d4" to "White challenges the centre.",
        "g4 e5 Bg2 h5 gxh5 d5 d4 exd4" to "Black takes, opening the e-file.",
        "g4 Nc6" to "Develops, and prepares e5 or d5.",
        "g4 Nc6 d4" to "White takes a share of the centre.",
        "g4 Nc6 d4 d5" to "Black stops the d-pawn and takes space.",
        "g4 Nc6 d4 d5 h3" to "Protects g4.",
        "g4 Nc6 d4 d5 h3 h5" to "Black attacks g4 anyway.",
        "g4 Nc6 d4 d5 h3 h5 g5" to "White pushes past, gaining space and keeping the h-file closed.",
        "g4 Nc6 d4 d5 h3 h5 g5 e5" to "Black opens the centre, where White's pieces are not developed.",
        "f3 e5 Kf2 d5" to "Black takes the whole centre. With the king on f2, opening lines is good for Black.",
        "f3 e5 Kf2 d5 e3" to "Gives the king a little air and opens the bishop.",
        "f3 e5 Kf2 d5 e3 Nc6" to "Black develops.",
        "f3 e5 Kf2 d5 e3 Nc6 Ne2" to "The knight comes to e2, to shelter the king.",
        "f3 e5 Kf2 d5 e3 Nc6 Ne2 d4" to "Black gains more space and cramps White.",
        "f3 e5 Kf2 d5 e3 Nc6 Ne2 d4 d3" to "Blocks the d-pawn and frees the c1 bishop.",
        "f3 e5 Kf2 d5 e3 Nc6 Ne2 d4 d3 Be6" to "Black develops with a big lead. Engines rate Black about two pawns better.",
        "f3 e5 Kf2 Nc6" to "Black develops calmly.",
        "f3 e5 Kf2 Nc6 e3" to "Gives the king some space.",
        "f3 e5 Kf2 Nc6 e3 d5" to "Black takes the centre.",
        "f3 e5 Kf2 Nc6 e3 d5 d4" to "White fights for the centre.",
        "f3 e5 Kf2 Nc6 e3 d5 d4 exd4" to "Black takes, opening lines while White's king is exposed.",
        "f3 e5 Kf2 Nc6 e3 d5 d4 exd4 exd4" to "White takes back.",
        "f3 e5 Kf2 Nc6 e3 d5 d4 exd4 exd4 Qh4+" to "The queen checks the king on f2 straight away.",
        "f3 e5 Kf2 Bc5+" to "A check at once, with the bishop aiming at the king.",
        "f3 e5 Kf2 Bc5+ e3" to "Blocks the check.",
        "f3 e5 Kf2 Bc5+ e3 d5" to "Black takes the centre.",
        "f3 e5 Kf2 Bc5+ e3 d5 c3" to "Prepares d4, to kick the bishop.",
        "f3 e5 Kf2 Bc5+ e3 d5 c3 Bd6" to "The bishop steps back before it is kicked.",
        "f3 e5 Kf2 Bc5+ e3 d5 c3 Bd6 d4" to "White takes some centre.",
        "f3 e5 Kf2 Bc5+ e3 d5 c3 Bd6 d4 Nc6" to "Black develops. Engines rate Black about two pawns better.",
        "Na3 e5 Nc4 e4" to "Black gains space and takes d3 and f3 away from White's pieces.",
        "Na3 e5 Nc4 e4 Ne3" to "The knight moves again, to e3, eyeing d5 and f5.",
        "Na3 e5 Nc4 e4 Ne3 d5" to "Black takes the centre and shuts out the knight.",
        "Na3 e5 Nc4 e4 Ne3 d5 d3" to "White hits the e4 pawn.",
        "Na3 e5 Nc4 e4 Ne3 d5 d3 d4" to "The pawn kicks the knight, which has now been chased three times.",
        "Na3 d5" to "Black takes the centre.",
        "Na3 d5 Nf3" to "White develops normally.",
        "Na3 d5 Nf3 Nf6" to "Black develops.",
        "Na3 d5 Nf3 Nf6 e3" to "Solid.",
        "Na3 d5 Nf3 Nf6 e3 c5" to "Black takes more space.",
        "Na3 d5 Nf3 Nf6 e3 c5 d4" to "White takes a share of the centre.",
        "Na3 d5 Nf3 Nf6 e3 c5 d4 Nc6" to "Black develops, with a little more space.",
        "Na3 Nf6" to "Black develops.",
        "Na3 Nf6 Nf3" to "White develops.",
        "Na3 Nf6 Nf3 c5" to "Takes space, and the c5 pawn controls b4 and d4, two squares the a3 knight would like.",
        "Na3 Nf6 Nf3 c5 e3" to "Solid.",
        "Na3 Nf6 Nf3 c5 e3 d5" to "Black takes the centre.",
        "Na3 Nf6 Nf3 c5 e3 d5 d4" to "White fights back in the centre.",
        "Na3 Nf6 Nf3 c5 e3 d5 d4 Nc6" to "Black develops. A normal position where White's a3 knight is a little offside.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3" to "Develops, and offers the b2 pawn as well, for speed.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6" to "Black develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6 Qg3" to "The queen eyes the weakened dark squares on the kingside.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6 Qg3 Nf6" to "Black develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6 Qg3 Nf6 Bh6" to "The bishop stops Black from castling short, because f8 is now covered.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6 Qg3 Nf6 Bh6 Nh5" to "The knight hits the queen on g3.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Nc3 Nc6 Qg3 Nf6 Bh6 Nh5 Qh3" to "The queen keeps eyeing the kingside. White has play for the pawn, but engines still prefer Black.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2" to "Develops with a hit on the d4 bishop.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6" to "The bishop steps back to guard the dark squares.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6 Nbc3" to "White develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6 Nbc3 Nc6" to "Black develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6 Nbc3 Nc6 Qg3" to "The queen eyes the dark squares and g7.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6 Nbc3 Nc6 Qg3 d6" to "Opens the c8 bishop and covers e5.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 Ne2 Bf6 Nbc3 Nc6 Qg3 d6 h4" to "White pushes the h-pawn, to break open Black's kingside.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4" to "White goes straight for Black's kingside with the h-pawn.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6" to "Black offers a queen trade, which would kill White's attack.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6 Nc3" to "White avoids the trade and develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6 Nc3 Nc6" to "Black develops.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6 Nc3 Nc6 a4" to "Gains space on the queenside.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6 Nc3 Nc6 a4 h6" to "Stops White's pieces using g5.",
        "e4 g6 Bc4 Bg7 Qf3 e6 d4 Bxd4 h4 Qf6 Nc3 Nc6 a4 h6 Qg3" to "The queen sidesteps the trade.",
        "e4 c5 f4 d5 Nc3 d4" to "Black ignores e4 and gains space, hitting the knight.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+" to "A check to develop the bishop before moving the knight.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+ Bd7" to "Blocks the check and offers to trade bishops.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+ Bd7 Bxd7+" to "White trades.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+ Bd7 Bxd7+ Qxd7" to "Black recaptures.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+ Bd7 Bxd7+ Qxd7 Nce2" to "The knight retreats to e2, from where it can go to g3.",
        "e4 c5 f4 d5 Nc3 d4 Bb5+ Bd7 Bxd7+ Qxd7 Nce2 d3" to "Black pushes on to d3, hitting the knight on e2 and breaking up White's pawns.",
        "e4 c5 f4 d5 Nc3 dxe4" to "Black takes the pawn White left behind.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4" to "White wins it back with a well placed knight. This quick development is the point of the Toilet Variation.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4 Qc7" to "Black eyes the f4 pawn and prepares to develop.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4 Qc7 Bc4" to "White develops, aiming at f7.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4 Qc7 Bc4 Nf6" to "Develops and challenges the e4 knight.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4 Qc7 Bc4 Nf6 d3" to "Supports the knight.",
        "e4 c5 f4 d5 Nc3 dxe4 Nxe4 Qc7 Bc4 Nf6 d3 g6" to "Black prepares to put the bishop on g7.",
        "e4 c5 f4 d5 Nc3 e6" to "Black keeps a solid centre, ready to take back on d5 with the e-pawn.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+" to "Check, developing the bishop.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+ Bd7" to "Blocks the check and offers a trade.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+ Bd7 Bxd7+" to "White trades.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+ Bd7 Bxd7+ Qxd7" to "Black recaptures.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+ Bd7 Bxd7+ Qxd7 d3" to "Supports e4.",
        "e4 c5 f4 d5 Nc3 e6 Bb5+ Bd7 Bxd7+ Qxd7 d3 Nc6" to "Black develops. The game is level.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4" to "Black strikes in the centre, ready to open lines against White's loose kingside.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4" to "White takes.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4 dxe4" to "Black recaptures, and the pawn now attacks f3.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4 dxe4 f4" to "White pushes past to keep the lines closed.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4 dxe4 f4 Ne7" to "The knight reroutes towards the kingside.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4 dxe4 f4 Ne7 Ne2" to "White finally develops a knight.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 e4 dxe4 dxe4 f4 Ne7 Ne2 Bg4" to "Develops with a hit on the e2 knight. Engines rate Black more than three pawns better.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7" to "Black connects the rooks and prepares e4.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2" to "The rook steps to a2, ready to defend along the second rank.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2 Be6" to "Develops the last minor piece.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2 Be6 Nh3" to "White develops the knight to the edge.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2 Be6 Nh3 a5" to "Takes space on the queenside.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2 Be6 Nh3 a5 Bg2" to "White finally fianchettoes the bishop.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 Qe7 Ra2 Be6 Nh3 a5 Bg2 e4" to "Black opens the centre, with a winning lead in development.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5" to "Takes space and stops b4.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2" to "The bishop goes to g2.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2 e4" to "Black strikes in the centre.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2 e4 d4" to "White keeps the centre closed.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2 e4 d4 Re8" to "The rook supports the e4 pawn.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2 e4 d4 Re8 fxe4" to "White takes.",
        "a3 e5 b3 d5 c3 Nf6 d3 Nc6 e3 Bd6 f3 O-O g3 a5 Bg2 e4 d4 Re8 fxe4 Nxe4" to "Black recaptures with a dominant knight. Engines rate Black about three pawns better.",
        "a4 e5 h4 d5" to "Black takes the whole centre.",
        "a4 e5 h4 d5 c3" to "Prepares d4.",
        "a4 e5 h4 d5 c3 Nc6" to "Black develops.",
        "a4 e5 h4 d5 c3 Nc6 d4" to "White takes a share of the centre.",
        "a4 e5 h4 d5 c3 Nc6 d4 exd4" to "Black takes.",
        "a4 e5 h4 d5 c3 Nc6 d4 exd4 cxd4" to "White recaptures.",
        "a4 e5 h4 d5 c3 Nc6 d4 exd4 cxd4 Bf5" to "Black develops actively. Engines rate Black more than a pawn better.",
        "a4 e5 h4 Nf6" to "Black develops.",
        "a4 e5 h4 Nf6 c3" to "Prepares d4.",
        "a4 e5 h4 Nf6 c3 d5" to "Black takes the centre.",
        "a4 e5 h4 Nf6 c3 d5 d4" to "White challenges e5.",
        "a4 e5 h4 Nf6 c3 d5 d4 Nc6" to "Black develops and keeps the tension.",
        "a4 e5 h4 Nf6 c3 d5 d4 Nc6 dxe5" to "White takes.",
        "a4 e5 h4 Nf6 c3 d5 d4 Nc6 dxe5 Nxe5" to "Black recaptures with a centralised knight.",
        "a4 e5 h4 Nc6" to "Develops and supports e5.",
        "a4 e5 h4 Nc6 c3" to "Prepares d4.",
        "a4 e5 h4 Nc6 c3 d5" to "Black takes the centre.",
        "a4 e5 h4 Nc6 c3 d5 d4" to "White challenges e5.",
        "a4 e5 h4 Nc6 c3 d5 d4 Nf6" to "Black develops.",
        "a4 e5 h4 Nc6 c3 d5 d4 Nf6 dxe5" to "White takes.",
        "a4 e5 h4 Nc6 c3 d5 d4 Nf6 dxe5 Nxe5" to "Black recaptures with a fine knight in the centre.",

        // London, English
        "d4 Nf6 Nf3" to "Develops and keeps the centre flexible.",
        "d4 Nf6 Nf3 g6 Bf4" to "The London. The bishop develops outside the pawn chain before e3.",
        "d4 Nf6 Nf3 g6 Bf4 Bg7 e3" to "Builds the solid London pawn triangle.",
        "c4 e5" to "Black takes the centre. This is a Sicilian with colours reversed.",
        "c4 Nf6" to "Controls the centre with a piece.",
    )
}
