package app.chessnut.otb.lichess

import app.chessnut.otb.analysis.ChessLines
import com.github.bhlangonijr.chesslib.Board

/** Real-time games of at least 10 minutes. The board API rejects anything faster. */
data class OnlineControl(val minutes: Int, val increment: Int) {
    val label: String
        get() = if (increment == 0) "$minutes min" else "$minutes+$increment"

    companion object {
        val Presets: List<OnlineControl> = listOf(
            OnlineControl(10, 0),
            OnlineControl(10, 5),
            OnlineControl(15, 10),
            OnlineControl(30, 0),
            OnlineControl(30, 20),
        )
        val Default: OnlineControl = Presets.first { it.minutes == 10 && it.increment == 5 }
    }
}

data class OnlineTable(
    val id: String,
    val userWhite: Boolean,
    val white: String,
    val black: String,
    val moves: List<String>,
    val fen: String,
    val whiteMillis: Long,
    val blackMillis: Long,
    val incrementMillis: Long,
    val status: String,
    val winner: String?,
    val whiteDraw: Boolean,
    val blackDraw: Boolean,
    val whiteId: String = "",
    val blackId: String = "",
    val whiteRating: Int? = null,
    val blackRating: Int? = null,
    /** A rating Lichess marks with "?": too few games to be settled. */
    val whiteProvisional: Boolean = false,
    val blackProvisional: Boolean = false,
    val rated: Boolean = false,
    /** "rapid" or "classical": which of your ratings the game counts for. */
    val speed: String? = null,
) {
    val opponent: String get() = if (userWhite) black else white
    val opponentId: String get() = if (userWhite) blackId else whiteId
    val userId: String get() = if (userWhite) whiteId else blackId
    val opponentRating: Int? get() = if (userWhite) blackRating else whiteRating
    val userRating: Int? get() = if (userWhite) whiteRating else blackRating
    val opponentProvisional: Boolean get() = if (userWhite) blackProvisional else whiteProvisional
    val userProvisional: Boolean get() = if (userWhite) whiteProvisional else blackProvisional
    val live: Boolean get() = status == "started" || status == "created"
    val userToMove: Boolean get() = userWhite == (moves.size % 2 == 0)

    fun withState(state: GameState): OnlineTable = copy(
        moves = state.moves,
        fen = LichessPlay.position(state.initialFen, state.moves).first,
        whiteMillis = state.whiteMillis,
        blackMillis = state.blackMillis,
        incrementMillis = state.incrementMillis,
        status = state.status,
        winner = state.winner,
        whiteDraw = state.whiteDraw,
        blackDraw = state.blackDraw,
    )
}

data class GameState(
    val initialFen: String,
    val moves: List<String>,
    val whiteMillis: Long,
    val blackMillis: Long,
    val incrementMillis: Long,
    val status: String,
    val winner: String?,
    val whiteDraw: Boolean,
    val blackDraw: Boolean,
)

/** A game in progress on the account, from /api/account/playing. [fen] is the placement Lichess reports. */
data class OngoingGame(
    val gameId: String,
    val opponent: String,
    val userWhite: Boolean,
    val fen: String,
    val isMyTurn: Boolean,
    val secondsLeft: Long?,
    val speed: String,
)

sealed class LichessEvent {
    data class GameStarted(val gameId: String, val color: String) : LichessEvent()
    data class Full(val table: OnlineTable) : LichessEvent()
    data class State(val state: GameState) : LichessEvent()
    data class OpponentGone(val gone: Boolean) : LichessEvent()
}

object LichessPlay {
    fun seekForm(control: OnlineControl, rated: Boolean): String =
        "rated=$rated&time=${control.minutes}&increment=${control.increment}&variant=standard"

    fun parse(line: String, accountId: String): LichessEvent? {
        val root = runCatching { JsonLite.parse(line) as? Json.Obj }.getOrNull() ?: return null
        return when (root.str("type")) {
            "gameStart" -> {
                val game = root.obj("game") ?: return null
                val id = game.str("gameId") ?: return null
                LichessEvent.GameStarted(id, game.str("color") ?: "random")
            }
            "gameFull" -> full(root, accountId)
            "gameState" -> LichessEvent.State(state(root, "startpos"))
            "opponentGone" -> LichessEvent.OpponentGone(root.bool("gone"))
            else -> null
        }
    }

    /** Games the account is playing now. Correspondence games are left out: the board flow is real-time only. */
    fun ongoing(json: String): List<OngoingGame> {
        val root = runCatching { JsonLite.parse(json) as? Json.Obj }.getOrNull() ?: return emptyList()
        val games = root.fields["nowPlaying"] as? Json.Arr ?: return emptyList()
        return games.items.mapNotNull { item ->
            val game = item as? Json.Obj ?: return@mapNotNull null
            val id = game.str("gameId") ?: return@mapNotNull null
            val speed = game.str("speed").orEmpty()
            if (speed == "correspondence") return@mapNotNull null
            OngoingGame(
                gameId = id,
                opponent = game.obj("opponent")?.str("username") ?: "Opponent",
                userWhite = game.str("color") != "black",
                fen = game.str("fen").orEmpty(),
                isMyTurn = game.bool("isMyTurn"),
                secondsLeft = game.long("secondsLeft"),
                speed = speed,
            )
        }
    }

    /** Before both sides have moved, Lichess lets either player abort with no result. */
    fun canAbort(moveCount: Int): Boolean = moveCount < 2

    fun position(initialFen: String, moves: List<String>): Pair<String, List<String>> {
        val start = if (initialFen.isBlank() || initialFen == "startpos") Board().fen else initialFen
        val sans = ChessLines.sanOf(start, moves)
        val board = Board().apply { loadFromFen(start) }
        for (uci in moves.take(sans.size)) {
            val move = board.legalMoves().firstOrNull { it.toString().equals(uci, ignoreCase = true) } ?: break
            board.doMove(move)
        }
        return board.fen to sans
    }

    fun result(status: String, winner: String?): String = when {
        winner == "white" -> "1-0"
        winner == "black" -> "0-1"
        status == "draw" || status == "stalemate" -> "1/2-1/2"
        else -> "*"
    }

    private fun full(root: Json.Obj, accountId: String): LichessEvent.Full? {
        val id = root.str("id") ?: return null
        val white = root.obj("white")
        val black = root.obj("black")
        val whiteId = white?.str("id").orEmpty()
        val blackId = black?.str("id").orEmpty()
        val userWhite = when {
            whiteId.equals(accountId, ignoreCase = true) -> true
            blackId.equals(accountId, ignoreCase = true) -> false
            else -> true
        }
        val initial = root.str("initialFen") ?: "startpos"
        val clock = state(root.obj("state") ?: return null, initial)
        val (fen, _) = position(initial, clock.moves)
        return LichessEvent.Full(
            OnlineTable(
                id = id,
                userWhite = userWhite,
                white = white?.str("name") ?: whiteId.ifBlank { "White" },
                black = black?.str("name") ?: blackId.ifBlank { "Black" },
                moves = clock.moves,
                fen = fen,
                whiteMillis = clock.whiteMillis,
                blackMillis = clock.blackMillis,
                incrementMillis = clock.incrementMillis,
                status = clock.status,
                winner = clock.winner,
                whiteDraw = clock.whiteDraw,
                blackDraw = clock.blackDraw,
                whiteId = whiteId,
                blackId = blackId,
                whiteRating = white?.long("rating")?.toInt(),
                blackRating = black?.long("rating")?.toInt(),
                whiteProvisional = white?.bool("provisional") == true,
                blackProvisional = black?.bool("provisional") == true,
                rated = root.bool("rated"),
                speed = root.str("speed"),
            ),
        )
    }

    private fun state(obj: Json.Obj, initialFen: String): GameState {
        val moves = obj.str("moves").orEmpty().split(' ').filter { it.isNotBlank() }
        val increment = obj.long("winc") ?: obj.long("binc") ?: 0L
        return GameState(
            initialFen = initialFen,
            moves = moves,
            whiteMillis = obj.long("wtime") ?: 0L,
            blackMillis = obj.long("btime") ?: 0L,
            incrementMillis = increment,
            status = obj.str("status") ?: "started",
            winner = obj.str("winner"),
            whiteDraw = obj.bool("wdraw"),
            blackDraw = obj.bool("bdraw"),
        )
    }
}
