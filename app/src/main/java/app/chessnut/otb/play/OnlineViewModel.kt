package app.chessnut.otb.play

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.board.BoardSounds
import app.chessnut.otb.board.MoveSpeaker
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.lichess.LichessEvent
import app.chessnut.otb.lichess.LichessHttp
import app.chessnut.otb.lichess.LichessHttpException
import app.chessnut.otb.lichess.LichessOAuth
import app.chessnut.otb.lichess.LichessPlay
import app.chessnut.otb.lichess.LichessVault
import app.chessnut.otb.lichess.OnlineControl
import app.chessnut.otb.lichess.OnlineTable
import app.chessnut.otb.lichess.OngoingGame
import app.chessnut.otb.lichess.RatingStakes
import com.github.bhlangonijr.chesslib.Board
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.play.BOARD_SETTLE_MS

data class OnlineUi(
    val username: String? = null,
    val checking: Boolean = true,
    val control: OnlineControl = OnlineControl.Default,
    val rated: Boolean = false,
    val seeking: Boolean = false,
    val playing: Boolean = false,
    val fen: String = Board().fen,
    val status: String = "Log in with Lichess. This phone keeps the login.",
    val opponent: String = "Opponent",
    /** Ratings beside the names, "1688?" when provisional. */
    val opponentRating: String? = null,
    val userRating: String? = null,
    /** A rated game's stakes for you, such as "Rated rapid · Win +6 · Draw −1 · Loss −8". */
    val stakes: String? = null,
    val userWhite: Boolean = true,
    val whiteMillis: Long = OnlineControl.Default.minutes * 60_000L,
    val blackMillis: Long = OnlineControl.Default.minutes * 60_000L,
    val whiteRunning: Boolean = false,
    val blackRunning: Boolean = false,
    val promptUci: String? = null,
    val lastUci: String? = null,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val finished: String? = null,
    val thinking: Boolean = false,
    /** A game is underway, so leaving resigns it. */
    val live: Boolean = false,
    val drawOffered: Boolean = false,
    val savedId: String? = null,
    val physical: Boolean = false,
    /** The Lichess game on screen, for "Open on Lichess". */
    val gameId: String? = null,
    /** No one has moved twice yet, so leaving aborts instead of resigning. */
    val canAbort: Boolean = false,
    /** Real-time games already in progress on the account, offered for resuming. */
    val resumable: List<OngoingGame> = emptyList(),
)

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val vault = LichessVault(application)
    private val http = LichessHttp { vault.token() }
    private val speech = MoveSpeaker(application)
    private val sounds = BoardSounds()
    private val state = MutableStateFlow(OnlineUi())
    val ui: StateFlow<OnlineUi> = state.asStateFlow()

    private var accountId: String? = vault.accountId()
    private var username: String? = vault.username()
    private var control = OnlineControl.Default
    private var rated = false
    @Volatile private var seeking = false
    @Volatile private var playing = false
    private var table: OnlineTable? = null
    /** What this game could do to your rating, once both deviations are in. */
    private var stakes: RatingStakes? = null
    private var stakesFor: String? = null
    private var fen = Board().fen
    private var status = if (username == null) "Log in with Lichess. This phone keeps the login." else "Signed in as $username"
    private var prompt: String? = null
    private var lastUci: String? = null
    private var selected: Int? = null
    private var finished: String? = null
    private var thinking = false
    private var savedId: String? = null
    private var spokenMoves = 0
    private var clockAt = 0L
    private var whiteMillis = control.minutes * 60_000L
    private var blackMillis = control.minutes * 60_000L
    private var opponentGone = false

    private var eventJob: Job? = null
    private var seekJob: Job? = null
    private var gameJob: Job? = null
    private var clockJob: Job? = null
    private var settleJob: Job? = null
    private var eventPipe: LichessHttp.Pipe? = null
    private var seekPipe: LichessHttp.Pipe? = null
    private var gamePipe: LichessHttp.Pipe? = null
    @Volatile private var paired = false
    private var ignorePlacement: String? = null
    private var watchJob: Job? = null
    private var mismatchJob: Job? = null
    @Volatile private var gameId: String? = null
    /** When the game stream last sent anything, blank keep-alive lines included. */
    @Volatile private var lastLineAt = 0L
    /** A move from the board that Lichess refused. The piece has to go back. */
    private var rejected: String? = null
    /** The board did not match the game, and the status says which squares. */
    private var mismatchShown = false
    private val warned = mutableSetOf<Long>()
    private var resumable: List<OngoingGame> = emptyList()

    init {
        viewModelScope.launch {
            app.placements.collect { onPhysical(it) }
        }
        publish()
        refreshAccount()
    }

    fun engage() {
        app.playEngaged = true
        refreshResumable()
    }

    /** Opens a real-time game already in progress, from the list in the lobby. */
    fun resume(id: String) {
        if (playing || seeking) return
        paired = true
        finished = null
        savedId = null
        spokenMoves = 0
        openGame(id)
    }

    private fun refreshResumable() {
        if (vault.token() == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val games = runCatching { LichessPlay.ongoing(http.playing()) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                resumable = games.filter { it.speed == "rapid" || it.speed == "classical" }
                publish()
            }
        }
    }

    fun setControl(next: OnlineControl) {
        if (playing || seeking) return
        control = next
        whiteMillis = next.minutes * 60_000L
        blackMillis = next.minutes * 60_000L
        publish()
    }

    fun setRated(next: Boolean) {
        if (playing || seeking) return
        rated = next
        publish()
    }

    /** Starts a Lichess login. The screen opens it over this app, then [finishLogin] stores it. */
    fun loginUrl(): String {
        val verifier = LichessOAuth.verifier()
        val oauthState = LichessOAuth.verifier()
        vault.savePending(verifier, oauthState)
        return LichessOAuth.authorizeUrl(oauthState, LichessOAuth.challenge(verifier))
    }

    fun finishLogin(url: String) {
        val redirect = LichessOAuth.redirect(url)
        val code = redirect.code
        if (redirect.error != null || code.isNullOrBlank()) {
            status = "Login cancelled"
            publish()
            return
        }
        if (redirect.state != vault.pendingState()) {
            status = "That login did not match this phone. Try again."
            publish()
            return
        }
        val verifier = vault.pendingVerifier()
        if (verifier.isNullOrBlank()) {
            status = "The login expired. Try again."
            publish()
            return
        }
        thinking = true
        status = "Signing in…"
        publish()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = LichessOAuth.accessToken(http.exchange(LichessOAuth.tokenForm(code, verifier)))
                vault.saveToken(token)
                vault.clearPending()
                val account = LichessOAuth.account(http.account())
                vault.saveAccount(account.id, account.username)
                withContext(Dispatchers.Main) {
                    accountId = account.id
                    username = account.username
                    thinking = false
                    status = "Signed in as ${account.username}"
                    publish()
                    refreshResumable()
                    app.flushUploads()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    thinking = false
                    status = failure(e)
                    publish()
                }
            }
        }
    }

    fun logout() {
        stopStreams()
        viewModelScope.launch(Dispatchers.IO) { http.revoke() }
        vault.clear()
        accountId = null
        username = null
        playing = false
        seeking = false
        table = null
        finished = null
        status = "Log in with Lichess. This phone keeps the login."
        publish()
    }

    /** Leaves a finished game and returns to the time controls. */
    fun again() {
        playing = false
        seeking = false
        stopStreams()
        clockJob?.cancel()
        settleJob?.cancel()
        table = null
        paired = false
        finished = null
        prompt = null
        lastUci = null
        selected = null
        savedId = null
        spokenMoves = 0
        opponentGone = false
        thinking = false
        gameId = null
        rejected = null
        mismatchShown = false
        warned.clear()
        light(null)
        status = username?.let { "Signed in as $it" } ?: status
        publish()
        refreshResumable()
    }

    fun seek() {
        if (username == null || seeking || playing) return
        seeking = true
        paired = false
        finished = null
        savedId = null
        status = "Waiting for an opponent…"
        publish()
        val previous = listOf(eventPipe, seekPipe, gamePipe)
        eventPipe = null
        seekPipe = null
        gamePipe = null
        eventJob?.cancel()
        seekJob?.cancel()
        gameJob?.cancel()
        seekJob = viewModelScope.launch(Dispatchers.IO) {
            previous.forEach { runCatching { it?.close() } }
            if (!seeking) return@launch
            val events = http.Pipe()
            eventPipe = events
            eventJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    events.lines("GET", "/api/stream/event", null) { line ->
                        if (isActive) onEvent(line)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "event stream", e)
                    if (isActive && seeking && !paired) report(e)
                }
            }
            delay(400)
            if (!isActive || !seeking) return@launch
            val seek = http.Pipe()
            seekPipe = seek
            try {
                seek.lines("POST", "/api/board/seek", LichessPlay.seekForm(control, rated)) { }
            } catch (e: Exception) {
                Log.w(TAG, "seek", e)
                if (isActive && seeking && !paired) report(e)
                return@launch
            }
            // The seek closes when someone accepts. If the event stream missed the start, find the game directly.
            delay(2_500)
            if (paired || !seeking) return@launch
            val started = runCatching { LichessPlay.ongoing(http.playing()) }.getOrNull()
                ?.firstOrNull { it.speed == "rapid" || it.speed == "classical" }
            withContext(Dispatchers.Main) {
                if (paired || !seeking) return@withContext
                seeking = false
                if (started != null) {
                    Log.i(TAG, "seek closed without gameStart, found ${started.gameId}")
                    paired = true
                    openGame(started.gameId)
                } else {
                    status = "No opponent found"
                    publish()
                }
            }
        }
    }

    fun cancelSeek() {
        if (!seeking) return
        seeking = false
        paired = false
        status = username?.let { "Signed in as $it" } ?: status
        Log.i(TAG, "cancel seek")
        publish()
        stopStreams()
    }

    fun tap(square: Int) {
        val current = table ?: return
        if (!playing || physical() || thinking || finished != null || prompt != null || !current.userToMove) return
        when (val tap = MoveInput.tap(fen, selected, square)) {
            is MoveInput.Tap.Play -> {
                selected = null
                publish()
                send(tap.uci)
            }
            is MoveInput.Tap.Select -> {
                selected = tap.square
                publish()
            }
        }
    }

    /** Offers a draw, or accepts the opponent's offer. Lichess uses the same call for both. */
    fun draw() {
        val current = table ?: return
        if (!playing || finished != null) return
        val accepting = if (current.userWhite) current.blackDraw else current.whiteDraw
        viewModelScope.launch(Dispatchers.IO) {
            try {
                http.draw(current.id, accept = true)
                withContext(Dispatchers.Main) {
                    if (!accepting) {
                        status = "Draw offered"
                        publish()
                    }
                }
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun declineDraw() {
        val id = table?.id ?: return
        if (!playing || finished != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                http.draw(id, accept = false)
                withContext(Dispatchers.Main) {
                    status = "Draw declined"
                    publish()
                }
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun resign() {
        postResign(andLeave = false)
    }

    /** Resigns on Lichess, then the screen can close. */
    fun resignAndLeave() {
        postResign(andLeave = true)
    }

    private fun postResign(andLeave: Boolean) {
        val id = table?.id
        val current = table
        if (id == null || current == null || !current.live) {
            if (andLeave) leave()
            return
        }
        val abort = LichessPlay.canAbort(current.moves.size)
        status = if (abort) "Aborting…" else "Resigning…"
        publish()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { if (abort) http.abort(id) else http.resign(id) }
                .onFailure { Log.w(TAG, if (abort) "abort" else "resign", it) }
            if (andLeave) withContext(Dispatchers.Main) { leave() }
        }
    }

    fun leave() {
        archive()
        stopStreams()
        watchJob?.cancel()
        mismatchJob?.cancel()
        speech.stop()
        settleJob?.cancel()
        clockJob?.cancel()
        app.playEngaged = false
        playing = false
        seeking = false
        prompt = null
        light(null)
        publish()
    }

    fun archiveId(): String? {
        archive()
        return savedId
    }

    private fun refreshAccount() {
        if (vault.token() == null) {
            thinking = false
            publish()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = LichessOAuth.account(http.account())
                vault.saveAccount(account.id, account.username)
                withContext(Dispatchers.Main) {
                    accountId = account.id
                    username = account.username
                    if (!playing && !seeking) status = "Signed in as ${account.username}"
                    thinking = false
                    publish()
                    refreshResumable()
                }
            } catch (e: LichessHttpException) {
                withContext(Dispatchers.Main) {
                    thinking = false
                    if (e.status == 401) {
                        vault.clear()
                        accountId = null
                        username = null
                        status = "The saved login expired. Log in again."
                    }
                    publish()
                }
            } catch (_: IOException) {
                withContext(Dispatchers.Main) {
                    thinking = false
                    publish()
                }
            }
        }
    }

    private fun onEvent(line: String) {
        val event = LichessPlay.parse(line, accountId.orEmpty()) ?: return
        if (event is LichessEvent.GameStarted && seeking) paired = true
        viewModelScope.launch(Dispatchers.Main) {
            when (event) {
                is LichessEvent.GameStarted -> {
                    if (!seeking || playing) return@launch
                    paired = true
                    seeking = false
                    openGame(event.gameId)
                }
                is LichessEvent.Full -> showFull(event.table)
                is LichessEvent.State -> table?.let { showFull(it.withState(event.state)) }
                is LichessEvent.OpponentGone -> {
                    opponentGone = event.gone
                    if (event.gone && finished == null) status = "Opponent has left"
                    publish()
                }
            }
        }
    }

    private fun openGame(id: String) {
        val seek = seekPipe
        seekPipe = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { seek?.close() } }
        gameId = id
        playing = true
        resumable = emptyList()
        warned.clear()
        status = "Game starting…"
        publish()
        watchClock()
        streamGame(id)
        watchStream(id)
    }

    /**
     * Follows the game, reconnecting whenever the stream drops. Each connection
     * starts with the full game, so nothing is lost by reconnecting.
     */
    private fun streamGame(id: String) {
        gameJob?.cancel()
        lastLineAt = System.currentTimeMillis()
        gameJob = viewModelScope.launch(Dispatchers.IO) {
            var backoff = 1_000L
            while (isActive) {
                val pipe = http.Pipe()
                gamePipe = pipe
                try {
                    pipe.lines("GET", "/api/board/game/stream/$id", null) { line ->
                        lastLineAt = System.currentTimeMillis()
                        backoff = 1_000L
                        if (isActive) onEvent(line)
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.w(TAG, "game stream", e)
                }
                // Let the final state reach the main thread before deciding the game is still on.
                delay(500)
                if (!isActive || !playing || finished != null || gameId != id) break
                withContext(Dispatchers.Main) {
                    if (finished == null) {
                        status = RECONNECTING
                        publish()
                    }
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000L)
            }
        }
    }

    /**
     * A stream can go silent without failing, for example when the phone
     * switches networks. When it has been quiet a while, ask Lichess for the
     * position and reconnect if ours is out of date.
     */
    private fun watchStream(id: String) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5_000)
                if (!playing || finished != null || gameId != id) break
                if (System.currentTimeMillis() - lastLineAt < QUIET_MS) continue
                lastLineAt = System.currentTimeMillis()
                val games = runCatching { LichessPlay.ongoing(http.playing()) }
                    .onFailure { Log.w(TAG, "check quiet stream", it) }
                    .getOrNull()
                if (games == null) {
                    withContext(Dispatchers.Main) {
                        if (finished == null) {
                            status = RECONNECTING
                            publish()
                        }
                    }
                    gamePipe?.let { runCatching { it.close() } }
                    continue
                }
                val ours = withContext(Dispatchers.Main) { PlayPosition.placementOf(fen) }
                val live = games.firstOrNull { it.gameId == id }
                if (live == null || live.fen.substringBefore(' ') != ours) {
                    Log.i(TAG, "quiet stream is stale, reconnecting")
                    gamePipe?.let { runCatching { it.close() } }
                }
            }
        }
    }

    private fun showFull(next: OnlineTable) {
        val previous = table?.moves?.size ?: 0
        table = next
        fetchStakes(next)
        fen = next.fen
        whiteMillis = next.whiteMillis
        blackMillis = next.blackMillis
        clockAt = System.currentTimeMillis()
        playing = true
        lastUci = next.moves.lastOrNull()
        val terminal = !next.live
        val theirs = opponentJustMoved(next)
        if (next.moves.size > previous && next.moves.size > spokenMoves && theirs) {
            val uci = next.moves.last()
            val san = LichessPlay.position("startpos", next.moves).second.lastOrNull() ?: uci
            spokenMoves = next.moves.size
            val mate = ChessLines.terminalOf(fen) == Terminal.Checkmate
            if (app.phoneSounds) speech.say(san, mate = mate)
            if (!terminal && physical()) {
                prompt = uci
                status = "Play $san on the board"
                light(uci)
            }
        } else if (next.moves.size > spokenMoves) {
            spokenMoves = next.moves.size
            prompt = null
            light(null)
        }
        if (status == RECONNECTING && prompt != null) {
            status = LichessPlay.position("startpos", next.moves).second.lastOrNull()?.let { "Play $it on the board" } ?: "Play the lit move"
        }
        if (terminal && next.status == "aborted") {
            again()
            status = "Game aborted"
            publish()
            return
        }
        if (terminal && finished == null) {
            finished = outcome(next)
            status = finished ?: "Game over"
            prompt = null
            light(null)
            archive()
        } else if (!terminal && finished == null && prompt == null) {
            val offered = if (next.userWhite) next.blackDraw else next.whiteDraw
            status = when {
                offered -> "Opponent offers a draw"
                opponentGone -> "Opponent has left"
                next.userToMove -> "Your move"
                else -> "Opponent is thinking"
            }
        }
        publish()
    }

    /** The latest move was the opponent's. White moves on the odd plies. */
    private fun opponentJustMoved(next: OnlineTable): Boolean {
        val whiteMoved = next.moves.size % 2 == 1
        return whiteMoved != next.userWhite
    }

    private fun send(uci: String) {
        val id = table?.id ?: return
        if (thinking || finished != null) return
        thinking = true
        publish()
        val fromBoard = physical()
        viewModelScope.launch(Dispatchers.IO) {
            // One retry covers a brief network drop. A refusal from Lichess is not retried.
            val result = runCatching { http.move(id, uci) }.recoverCatching { error ->
                if (error !is IOException) throw error
                delay(1_000)
                http.move(id, uci)
            }
            val error = result.exceptionOrNull()
            withContext(Dispatchers.Main) {
                thinking = false
                if (error != null) {
                    Log.w(TAG, "move $uci", error)
                    if (app.phoneSounds) sounds.illegal()
                    if (fromBoard) {
                        rejected = uci
                        status = "${failure(error as? Exception ?: Exception(error))} Put the piece back on ${uci.substring(0, 2)}."
                        light(uci)
                    } else {
                        status = failure(error as? Exception ?: Exception(error))
                    }
                }
                publish()
            }
        }
    }

    private fun onPhysical(placement: String) {
        if (!playing || !physical()) return
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            delay(BOARD_SETTLE_MS)
            onSettled(placement)
        }
    }

    private fun onSettled(placement: String) {
        if (!playing || !physical() || finished != null) return
        mismatchJob?.cancel()
        if (PlayPosition.matches(fen, placement)) {
            if (prompt != null || rejected != null || mismatchShown) {
                prompt = null
                rejected = null
                mismatchShown = false
                light(null)
                status = if (table?.userToMove == true) "Your move" else "Opponent is thinking"
                publish()
            }
            return
        }
        if (rejected != null) return
        if (prompt != null) {
            if (placement != ignorePlacement && PlayPosition.uciMatching(fen, placement) != null) {
                ignorePlacement = placement
                status = "Play the lit move"
                publish()
                if (app.phoneSounds) sounds.illegal()
            }
            return
        }
        if (thinking) return
        val uci = if (table?.userToMove == true) PlayPosition.uciMatching(fen, placement) else null
        if (uci == null) {
            hintMismatch(placement)
            return
        }
        send(uci)
    }

    /**
     * The board shows neither the game nor a legal move. A piece held in the
     * hand looks the same for a moment, so wait before naming the squares.
     */
    private fun hintMismatch(placement: String) {
        mismatchJob = viewModelScope.launch {
            delay(MISMATCH_MS)
            if (!playing || finished != null || prompt != null) return@launch
            val wrong = PlayPosition.differences(fen, placement)
            if (wrong.isEmpty() || wrong.size > MAX_MISMATCH) return@launch
            mismatchShown = true
            status = "The board doesn't match the game. Check ${wrong.joinToString(", ")}."
            lightSquares(wrong)
            publish()
        }
    }

    private fun watchClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                delay(200)
                if (!playing || finished != null) break
                publish()
                warnLowTime()
            }
        }
    }

    /** Says the time left as your clock crosses a minute, thirty seconds and ten seconds. */
    private fun warnLowTime() {
        val current = table ?: return
        val mine = if (current.userWhite) state.value.whiteMillis else state.value.blackMillis
        val running = if (current.userWhite) state.value.whiteRunning else state.value.blackRunning
        if (!running) return
        for ((at, words) in LOW_TIME) {
            if (mine in (at - 3_000 + 1)..at && warned.add(at)) {
                if (app.phoneSounds) speech.narrate(words)
            }
        }
    }

    private fun shownMillis(stored: Long, running: Boolean): Long {
        if (!running || clockAt == 0L) return stored
        return (stored - (System.currentTimeMillis() - clockAt)).coerceAtLeast(0)
    }

    private fun archive() {
        val current = table ?: return
        if (savedId != null || current.moves.isEmpty()) return
        val sans = LichessPlay.position("startpos", current.moves).second
        if (sans.isEmpty()) return
        val now = System.currentTimeMillis()
        val me = username ?: app.myName.ifBlank { "Me" }
        val saved = SavedGame(
            id = SavedGame.idFor(sans, emptyList()),
            createdAt = now,
            source = GameSource.Play,
            sanMoves = sans,
            isComplete = finished != null,
            stopReason = finished,
            white = if (current.userWhite) me else current.opponent,
            black = if (current.userWhite) current.opponent else me,
            event = "Lichess ${control.label}",
            date = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(now)),
            result = LichessPlay.result(current.status, current.winner),
        )
        app.store.addIfNew(saved)
        app.rememberLichessUrl(saved.id, "https://lichess.org/${current.id}")
        app.refreshGames()
        savedId = saved.id
    }

    private fun outcome(current: OnlineTable): String {
        val mine = if (current.userWhite) "white" else "black"
        return when {
            current.status == "mate" && current.winner == mine -> "Checkmate — you win"
            current.status == "mate" -> "Checkmate"
            current.status == "resign" && current.winner == mine -> "Opponent resigned"
            current.status == "resign" -> "You resigned"
            current.status == "outoftime" && current.winner == mine -> "Opponent flagged"
            current.status == "outoftime" -> "You flagged"
            current.status == "stalemate" || current.status == "draw" || current.status == "timeout" -> "Draw"
            current.status == "aborted" -> "Game aborted"
            else -> "Game over"
        }
    }

    private fun stopStreams() {
        eventJob?.cancel()
        seekJob?.cancel()
        gameJob?.cancel()
        val pipes = listOf(eventPipe, seekPipe, gamePipe)
        eventPipe = null
        seekPipe = null
        gamePipe = null
        viewModelScope.launch(Dispatchers.IO) {
            pipes.forEach { runCatching { it?.close() } }
        }
    }

    private fun physical(): Boolean = app.boardSession != null

    private fun light(uci: String?) =
        lightSquares(if (uci == null || uci.length < 4) emptyList() else listOf(uci.substring(0, 2), uci.substring(2, 4)))

    private fun lightSquares(squares: List<String>) {
        if (app.boardSession == null) return
        viewModelScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(2.seconds) {
                runCatching { app.boardSession?.setLeds(squares) }
            }
        }
    }

    private suspend fun report(error: Exception) {
        Log.w(TAG, "lichess", error)
        withContext(Dispatchers.Main) {
            if (error is kotlinx.coroutines.CancellationException) return@withContext
            if (!seeking && !playing) return@withContext
            thinking = false
            seeking = false
            status = failure(error)
            publish()
        }
    }

    private fun failure(error: Exception): String = when (error) {
        is LichessHttpException -> error.message ?: "Lichess refused the request."
        is IOException -> "Lichess is unreachable"
        else -> error.message ?: "Something went wrong"
    }

    /** For a rated game, fetches both players' rating deviations once and works out the stakes. */
    private fun fetchStakes(game: OnlineTable) {
        if (!game.rated || stakesFor == game.id) return
        val speed = game.speed ?: return
        val mine = game.userRating ?: return
        val theirs = game.opponentRating ?: return
        stakesFor = game.id
        stakes = null
        viewModelScope.launch(Dispatchers.IO) {
            val found = runCatching {
                val myRd = RatingStakes.deviation(http.user(game.userId), speed) ?: return@runCatching null
                val theirRd = RatingStakes.deviation(http.user(game.opponentId), speed) ?: return@runCatching null
                RatingStakes.of(mine, myRd, theirs, theirRd)
            }.onFailure { Log.w(TAG, "stakes", it) }.getOrNull()
            if (found != null && table?.id == game.id) {
                stakes = found
                publish()
            }
        }
    }

    private fun publish() {
        val current = table
        val whiteToMove = current == null || current.moves.size % 2 == 0
        // Lichess starts the clocks only once both players have moved.
        val running = playing && finished == null && current?.live == true && current.moves.size >= 2
        state.value = OnlineUi(
            username = username,
            checking = false,
            control = control,
            rated = rated,
            seeking = seeking,
            playing = playing,
            fen = fen,
            status = status,
            opponent = current?.opponent ?: "Opponent",
            opponentRating = current?.opponentRating?.let { "$it" + if (current.opponentProvisional) "?" else "" },
            userRating = current?.userRating?.let { "$it" + if (current.userProvisional) "?" else "" },
            stakes = current?.takeIf { it.rated && it.id == stakesFor }?.let { game ->
                val kind = "Rated ${game.speed.orEmpty()}".trim()
                stakes?.let { "$kind · ${it.label}" } ?: kind
            },
            userWhite = current?.userWhite ?: true,
            whiteMillis = shownMillis(whiteMillis, running && whiteToMove),
            blackMillis = shownMillis(blackMillis, running && !whiteToMove),
            whiteRunning = running && whiteToMove,
            blackRunning = running && !whiteToMove,
            promptUci = prompt,
            lastUci = lastUci,
            selected = selected,
            targets = if (finished != null || prompt != null) emptySet() else MoveInput.targets(fen, selected),
            finished = finished,
            thinking = thinking,
            live = playing && finished == null && current?.live != false,
            drawOffered = current?.let { if (it.userWhite) it.blackDraw else it.whiteDraw } == true,
            savedId = savedId,
            physical = physical(),
            gameId = gameId,
            canAbort = current != null && LichessPlay.canAbort(current.moves.size),
            resumable = if (playing || seeking) emptyList() else resumable,
        )
    }

    override fun onCleared() {
        leave()
        speech.release()
    }

    private companion object {
        const val TAG = "Lichess"

        /** Silence on the game stream before asking Lichess whether we missed something. */
        const val QUIET_MS = 20_000L

        const val RECONNECTING = "Connection lost. Reconnecting…"

        /** How long a wrong-looking board must stay put before the squares are named. */
        const val MISMATCH_MS = 2_500L

        const val MAX_MISMATCH = 6

        val LOW_TIME = listOf(60_000L to "One minute left", 30_000L to "Thirty seconds", 10_000L to "Ten seconds")
    }
}
