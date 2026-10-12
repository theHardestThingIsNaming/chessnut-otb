package app.chessnut.otb.puzzles

import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite

/** What you're playing for in an essential ending. */
enum class Goal { Win, Draw }

/**
 * A textbook ending, played against perfect defence from the Lichess
 * tablebase. You have the side to move in [fen].
 */
data class Essential(
    val id: String,
    val name: String,
    val goal: Goal,
    val fen: String,
    /** The technique, in a few sentences, shown before you start. */
    val lesson: String,
) {
    val white: Boolean get() = fen.split(' ')[1] == "w"
}

/** A position's result for the side to move, best first: win, draw, loss. */
enum class Outcome { Win, Draw, Loss }

/** One tablebase answer: the result here, and every legal move with the result it leads to for the mover. */
data class TablebaseView(val outcome: Outcome, val moves: List<Pair<String, Outcome>>) {
    /** The best move for the side to move, as the tablebase sorts them. */
    val best: String? get() = moves.firstOrNull()?.first

    companion object {
        /**
         * Reads tablebase.lichess.ovh's answer. Its move categories are from
         * the opponent's side (a winning move is a "loss" for them), so they
         * are turned round here.
         */
        fun parse(json: String): TablebaseView? {
            val root = runCatching { JsonLite.parse(json) as? Json.Obj }.getOrNull() ?: return null
            val here = outcomeOf(root.str("category") ?: return null) ?: return null
            val moves = (root.fields["moves"] as? Json.Arr)?.items.orEmpty().mapNotNull { item ->
                val move = item as? Json.Obj ?: return@mapNotNull null
                val uci = move.str("uci") ?: return@mapNotNull null
                val theirs = outcomeOf(move.str("category") ?: return@mapNotNull null) ?: return@mapNotNull null
                uci to flip(theirs)
            }
            return TablebaseView(here, moves)
        }

        private fun outcomeOf(category: String): Outcome? = when (category) {
            "win", "maybe-win" -> Outcome.Win
            "loss", "maybe-loss" -> Outcome.Loss
            "draw", "cursed-win", "blessed-loss" -> Outcome.Draw
            else -> null
        }

        private fun flip(outcome: Outcome) = when (outcome) {
            Outcome.Win -> Outcome.Loss
            Outcome.Loss -> Outcome.Win
            Outcome.Draw -> Outcome.Draw
        }
    }

    /** What [uci] does to your result: null when it keeps the best, else what you're left with and the move that kept it. */
    fun judge(uci: String): Pair<Outcome, String>? {
        val best = moves.firstOrNull() ?: return null
        val mine = moves.firstOrNull { it.first.equals(uci, ignoreCase = true) }?.second ?: return null
        return if (mine.ordinal > best.second.ordinal) mine to best.first else null
    }
}

object Essentials {
    val ALL = listOf(
        Essential(
            "ladder", "The ladder mate", Goal.Win, "8/8/4k3/8/8/8/8/R3K2R w - - 0 1",
            "Two rooks mate on their own. Use them like a ladder: one rook cuts the king off along a rank, the other checks on the next rank, and they take turns pushing the king to the edge. Keep the rooks far from the king, so it can't attack them.",
        ),
        Essential(
            "queen", "Mate with the queen", Goal.Win, "8/8/8/4k3/8/8/8/3QK3 w - - 0 1",
            "Use the queen to shrink the king's box: keep a knight's move away and follow it as it moves. Once it's on the edge, bring your king up and mate. Never leave it without a move: that's stalemate.",
        ),
        Essential(
            "rook", "Mate with the rook", Goal.Win, "8/8/8/4k3/8/8/8/R3K3 w - - 0 1",
            "The rook cuts the king off, and your king walks up to face it. When the kings stand opposite, check with the rook to push it back a rank; otherwise make a waiting move. Repeat until it's on the edge.",
        ),
        Essential(
            "square", "The square of the pawn", Goal.Draw, "7k/8/8/p7/8/8/8/5K2 w - - 0 1",
            "Draw a square from the pawn to its queening square. If your king can step into it, it catches the pawn. Here only one direction is quick enough: head straight for the pawn's path.",
        ),
        Essential(
            "opposition", "Take the opposition", Goal.Win, "4k3/8/4K3/4P3/8/8/8/8 w - - 0 1",
            "With your king in front of your pawn, step to the side so the defending king has to give way. Facing the king with one square between, on the move after yours, is the opposition. Win it and the pawn queens.",
        ),
        Essential(
            "hold-opposition", "Hold the opposition", Goal.Draw, "4k3/8/8/4PK2/8/8/8/8 b - - 0 1",
            "Defending against king and pawn, keep your king in front of the pawn and face the attacking king whenever it comes up. Step straight back, never to the side, and the pawn can't get through.",
        ),
        Essential(
            "lucena", "The Lucena: build a bridge", Goal.Win, "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1",
            "Your king is stuck in front of the pawn. Check the defending king away, put the rook on the fourth rank, then walk your king out. When the checks come, block them with the rook: that's the bridge.",
        ),
        Essential(
            "philidor", "The Philidor: the third-rank defence", Goal.Draw, "4k3/8/r7/4PK2/8/8/8/7R b - - 0 1",
            "Keep your rook on your third rank, stopping the king from coming forward. The moment the pawn advances to it, go to the back of the board and check from behind: the king has nowhere to hide.",
        ),
    )
}
