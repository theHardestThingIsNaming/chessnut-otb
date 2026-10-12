package app.chessnut.otb.games

import app.chessnut.otb.chess.StoredGameReconstructor
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
    val title: String
        get() = label.ifBlank { "${white.ifBlank { "White" }} vs ${black.ifBlank { "Black" }}" }

    fun toPgn(): String = applyHeaders(
        movetextOf(sanMoves),
        GameHeaders(event = event, date = date, white = white, black = black, result = result),
    )

    companion object {
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
