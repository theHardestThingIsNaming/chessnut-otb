package app.chessnut.otb.games

import app.chessnut.otb.chess.StoredGameReconstructor
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.shortOpening
import app.chessnut.otb.pgn.GameFingerprint
import app.chessnut.otb.pgn.GameHeaders
import app.chessnut.otb.pgn.applyHeaders
import app.chessnut.otb.pgn.movetextOf

enum class GameSource { Board, Pgn, Play }

data class SavedGame(
    val id: String,
    val createdAt: Long,
    val source: GameSource,
    val sanMoves: List<String>,
    val isComplete: Boolean,
    val stopReason: String?,
    val white: String = "",
    val black: String = "",
    val event: String = "",
    val date: String = "",
    val result: String = "*",
    /** Placements exactly as the board sent them; empty for imported PGN. */
    val placements: List<String> = emptyList(),
    /** Empty when the game is in the main list. */
    val folder: String = "",
    /** Shown instead of "White vs Black" when set. */
    val label: String = "",
    /**
     * Whose mistakes to practise when the names don't say which side you
     * were, as on the board: "white", "black", "both", or empty for none yet.
     */
    val practise: String = "",
) {
    /** The label, else the players, else (a board game with no names) the opening it played. */
    val title: String
        get() = label.ifBlank {
            if (white.isBlank() && black.isBlank()) openingTitle(sanMoves) else "${white.ifBlank { "White" }} vs ${black.ifBlank { "Black" }}"
        }

    fun toPgn(): String = applyHeaders(
        movetextOf(sanMoves),
        GameHeaders(event = event, date = date, white = white, black = black, result = result),
    )

    companion object {
        private val openingTitles = java.util.concurrent.ConcurrentHashMap<List<String>, String>()

        /** "Scotch Game" for a nameless game, found once per opening and kept; "Game" when the book has no name for it. */
        private fun openingTitle(sanMoves: List<String>): String =
            openingTitles.getOrPut(sanMoves.take(16)) {
                OpeningBook.view(sanMoves.take(16)).name?.let(::shortOpening) ?: "Game"
            }

        fun idFor(sanMoves: List<String>, placements: List<String>): String =
            if (sanMoves.isNotEmpty()) {
                GameFingerprint.of(movetextOf(sanMoves))
            } else {
                GameFingerprint.of(placements.joinToString(";"))
            }

        fun fromBoard(placements: List<String>, now: Long, date: String): SavedGame {
            val game = StoredGameReconstructor.reconstruct(placements)
            return SavedGame(
                id = idFor(game.sanMoves, placements),
                createdAt = now,
                source = GameSource.Board,
                sanMoves = game.sanMoves,
                isComplete = game.isComplete,
                stopReason = game.stopReason,
                date = date,
                placements = placements,
            )
        }
    }
}
