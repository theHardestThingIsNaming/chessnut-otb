package app.chessnut.otb

import android.app.Application
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.chessnut.otb.analysis.AnalysisWorker
import app.chessnut.otb.games.DigestStore
import app.chessnut.otb.mistakes.MistakeDeck
import app.chessnut.otb.mistakes.ReviewFile
import app.chessnut.otb.games.GameKeeper
import app.chessnut.otb.games.GameStore
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.lichess.HttpImporter
import app.chessnut.otb.lichess.LichessHttp
import app.chessnut.otb.lichess.LichessVault
import app.chessnut.otb.repertoire.DepthRecords
import app.chessnut.otb.repertoire.Drill
import app.chessnut.otb.repertoire.LineProgress
import app.chessnut.otb.repertoire.OpeningTree
import app.chessnut.otb.repertoire.MoveChoices
import app.chessnut.otb.repertoire.RepertoireStore
import app.chessnut.otb.upload.FileGameMemory
import app.chessnut.otb.upload.ImportService
import app.chessnut.otb.upload.SubmitResult
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.chessnut.otb.protocol.ChessnutSession
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OtbApp : Application() {
    val settings: SharedPreferences by lazy { getSharedPreferences("club_games", MODE_PRIVATE) }

    val store: GameStore by lazy { GameStore(File(filesDir, "games")) }

    /** Practice positions and opening scores kept from games the list has let go. */
    val digests: DigestStore by lazy { DigestStore(File(filesDir, "digests")) }

    private val keeper by lazy { GameKeeper(store, digests) }

    /** Names that mean "me" in a game's headers: the one set here, and the Lichess login. */
    val myNames: List<String> get() = listOfNotNull(myName.ifBlank { null }, vault.username(), chessComUser.ifBlank { null })

    /** Your Chess.com username, for importing your games there. */
    var chessComUser: String
        get() = settings.getString(KEY_CHESS_COM, "").orEmpty()
        set(value) = settings.edit().putString(KEY_CHESS_COM, value).apply()

    private val gamesFlow = MutableStateFlow<List<SavedGame>>(emptyList())
    val games: StateFlow<List<SavedGame>> = gamesFlow.asStateFlow()

    private val progressFlow = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Analysis progress per game id, 0..1, while a job is running. */
    val analysisProgress: StateFlow<Map<String, Float>> = progressFlow.asStateFlow()

    private val analysedFlow = MutableStateFlow(0)
    val analysisVersion: StateFlow<Int> = analysedFlow.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vault by lazy { LichessVault(this) }
    private val uploads by lazy {
        ImportService(HttpImporter(LichessHttp { vault.token() }), FileGameMemory(File(filesDir, "uploads")))
    }
    private val uploadLock = Mutex()

    /** Which lines of each book opening you have drilled, and when each is due again. */
    val lineProgress: LineProgress by lazy { LineProgress(File(filesDir, "repertoire/book-lines.tsv")) }

    /** Your best run of engine-approved moves past the book, per opening and side. */
    val depthRecords: DepthRecords by lazy { DepthRecords(File(filesDir, "repertoire/depth.tsv")) }

    /** Moves you picked over the book's main move, which shape each opening's lines. */
    val moveChoices: MoveChoices by lazy { MoveChoices(File(filesDir, "repertoire/choices.tsv")) }

    /** Book opening lines whose review has come due, across every opening you have drilled. */
    fun openingLinesDue(now: Long): Int = lineProgress.started().sumOf { (name, white) ->
        Drill.overdue(OpeningTree.lines(name, white, moveChoices.of(name, white)), lineProgress.reviews(name, white), now)
    }

    /** Mistake positions due for practice: never practised, or past their review date. */
    fun mistakesDue(now: Long): Int {
        val live = store.all().flatMap { game ->
            val analysis = store.loadAnalysis(game.id) ?: return@flatMap emptyList()
            MistakeDeck.collect(game, analysis, myNames)
        }
        val keys = (live + digests.all().flatMap { it.mistakes }).map { it.key }.toSet()
        val reviews = ReviewFile(File(filesDir, "mistakes/reviews.tsv")).all()
        return keys.count { (reviews[it]?.dueAt ?: 0L) <= now }
    }

    /** Imported study chapters for the trainer, with their review dates. */
    val repertoire: RepertoireStore by lazy { RepertoireStore(File(filesDir, "repertoire")) }

    /** Club games (from the board) go to Lichess as soon as they are saved. Imported games are public there. */
    var autoUpload: Boolean
        get() = settings.getBoolean(KEY_AUTO_UPLOAD, true)
        set(value) = settings.edit().putBoolean(KEY_AUTO_UPLOAD, value).apply()

    /** The Lichess page of an uploaded game, if it has one. */
    fun lichessUrl(id: String): String? = settings.getString(KEY_LINK_PREFIX + id, null)

    /** Records where a game lives on Lichess, such as an online game played from here. */
    fun rememberLichessUrl(id: String, url: String) {
        settings.edit().putString(KEY_LINK_PREFIX + id, url).apply()
        uploadsFlow.update { it + 1 }
    }

    /**
     * Uploads a board game to Lichess when auto-upload is on and the phone is
     * logged in. Games already sent are skipped; without a connection the game
     * waits in a queue for [flushUploads].
     */
    fun autoUploadToLichess(game: SavedGame) {
        if (!autoUpload || game.source != GameSource.Board) return
        uploadToLichess(game)
    }

    /** Uploads one game now, whatever the setting. Used by the analysis screen for older games. */
    fun uploadToLichess(game: SavedGame, onDone: (String) -> Unit = {}) {
        if (vault.token() == null) {
            onDone("Log in to Lichess first, from Play online.")
            return
        }
        if (game.sanMoves.size < 2 || lichessUrl(game.id) != null) return
        scope.launch {
            val message = uploadLock.withLock {
                when (val result = uploads.submit(game.toPgn(), vault.token())) {
                    is SubmitResult.Uploaded -> {
                        settings.edit()
                            .putString(KEY_LINK_PREFIX + game.id, result.game.url)
                            .putStringSet(KEY_PENDING, pendingUploads() - game.id)
                            .apply()
                        uploadsFlow.update { it + 1 }
                        "Uploaded to Lichess"
                    }
                    is SubmitResult.Queued -> {
                        settings.edit().putStringSet(KEY_PENDING, pendingUploads() + game.id).apply()
                        result.message
                    }
                    is SubmitResult.Duplicate -> "Already on Lichess"
                    is SubmitResult.Failed -> result.message
                }
            }
            Log.i(TAG, "upload ${game.id.take(8)}: $message")
            onDone(message)
        }
    }

    /**
     * A Lichess link for sharing [game]: the one it already has, or a fresh
     * upload. Works without a Lichess login (the game is then not on your
     * account). Returns the link, or a reason there is none.
     */
    suspend fun shareLink(game: SavedGame): Result<String> {
        lichessUrl(game.id)?.let { return Result.success(it) }
        if (game.sanMoves.size < 2) return Result.failure(IllegalStateException("This game is too short to put on Lichess."))
        return uploadLock.withLock {
            when (val result = uploads.uploadNow(game.toPgn(), vault.token())) {
                is SubmitResult.Uploaded -> {
                    settings.edit()
                        .putString(KEY_LINK_PREFIX + game.id, result.game.url)
                        .putStringSet(KEY_PENDING, pendingUploads() - game.id)
                        .apply()
                    uploadsFlow.update { it + 1 }
                    Result.success(result.game.url)
                }
                is SubmitResult.Failed -> Result.failure(IllegalStateException(result.message))
                else -> Result.failure(IllegalStateException("Couldn't get a Lichess link."))
            }
        }
    }

    private fun pendingUploads(): Set<String> = settings.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

    /** Retries uploads that were queued without a connection. Called at start and after logging in. */
    fun flushUploads() {
        if (vault.token() == null) return
        pendingUploads().mapNotNull(store::load).forEach { uploadToLichess(it) }
    }

    private val uploadsFlow = MutableStateFlow(0)

    /** Bumps whenever a game reaches Lichess, so screens can show its link. */
    val uploadVersion: StateFlow<Int> = uploadsFlow.asStateFlow()

    var myName: String
        get() = settings.getString(KEY_MY_NAME, "").orEmpty()
        set(value) = settings.edit().putString(KEY_MY_NAME, value).apply()

    var phoneSounds: Boolean
        get() = settings.getBoolean(KEY_PHONE_SOUNDS, true)
        set(value) = settings.edit().putBoolean(KEY_PHONE_SOUNDS, value).apply()

    var boardSounds: Boolean
        get() = settings.getBoolean(KEY_BOARD_SOUNDS, true)
        set(value) = settings.edit().putBoolean(KEY_BOARD_SOUNDS, value).apply()

    var moveIdeas: Boolean
        get() = settings.getBoolean(KEY_MOVE_IDEAS, false)
        set(value) = settings.edit().putBoolean(KEY_MOVE_IDEAS, value).apply()

    var openingHistory: Boolean
        get() = settings.getBoolean(KEY_OPENING_HISTORY, false)
        set(value) = settings.edit().putBoolean(KEY_OPENING_HISTORY, value).apply()

    var explainOpening: Boolean
        get() = settings.getBoolean(KEY_EXPLAIN_OPENING, true)
        set(value) = settings.edit().putBoolean(KEY_EXPLAIN_OPENING, value).apply()

    /** True while Play is using the Air, so the live game does not score those placements. */
    @Volatile var playEngaged: Boolean = false

    @Volatile var boardSession: ChessnutSession? = null

    /** Another screen, such as a master game, is reading the board, so Play leaves its moves alone. */
    @Volatile var boardBorrowed: Boolean = false

    @Volatile var latestPlacement: String? = null

    private val placementFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val placements: SharedFlow<String> = placementFlow.asSharedFlow()

    fun attachBoard(session: ChessnutSession?) {
        boardSession = session
    }

    fun notePlacement(placement: String) {
        latestPlacement = placement
        placementFlow.tryEmit(placement)
    }

    override fun onCreate() {
        super.onCreate()
        refreshGames()
        repertoire.names()
        flushUploads()
    }

    /**
     * Reloads the game list after keeping it to the newest five outside
     * folders. Games being analysed or waiting to upload are left alone.
     */
    fun refreshGames() {
        val busy = progressFlow.value.keys + pendingUploads()
        val removed = keeper.tidy(System.currentTimeMillis(), myNames, protect = busy)
        if (removed.isNotEmpty()) Log.i(TAG, "tidied ${removed.size} older game(s)")
        gamesFlow.value = store.all()
    }

    fun createFolder(name: String): String? {
        val created = store.createFolder(name)
        refreshGames()
        return created
    }

    fun renameFolder(from: String, to: String) {
        store.renameFolder(from, to)
        refreshGames()
    }

    fun deleteFolder(name: String) {
        store.deleteFolder(name)
        refreshGames()
    }

    fun moveGame(id: String, folder: String) {
        if (folder.isBlank()) store.moveToGames(id) else store.moveToFolder(id, folder)
        refreshGames()
    }

    fun renameGame(id: String, label: String) {
        val game = store.load(id) ?: return
        store.save(game.copy(label = label.trim()))
        refreshGames()
    }

    fun deleteGame(id: String) {
        store.delete(id)
        refreshGames()
    }

    fun analyse(id: String, replace: Boolean = false) {
        if (replace) store.clearAnalysis(id)
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KEY_GAME_ID to id))
            .build()
        // One queue, run in order: an import of many games would otherwise run several Stockfish side by side.
        // A game already analysed when its turn comes is skipped by the worker.
        WorkManager.getInstance(this).enqueueUniqueWork(ANALYSIS_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        reportProgress(id, 0f)
    }

    fun reportProgress(id: String, fraction: Float?) {
        progressFlow.update { if (fraction == null) it - id else it + (id to fraction) }
        if (fraction == null) {
            analysedFlow.update { it + 1 }
            // An analysed game past the newest five can now be digested and let go.
            refreshGames()
        }
    }

    private companion object {
        const val KEY_MY_NAME = "my_name"
        const val ANALYSIS_QUEUE = "analysis-queue"
        const val KEY_CHESS_COM = "chess_com_user"
        const val KEY_AUTO_UPLOAD = "auto_upload"
        const val KEY_LINK_PREFIX = "lichess_link_"
        const val KEY_PENDING = "lichess_pending_uploads"
        const val TAG = "Upload"
        const val KEY_PHONE_SOUNDS = "phone_sounds"
        const val KEY_BOARD_SOUNDS = "board_sounds"
        const val KEY_EXPLAIN_OPENING = "explain_opening"
        const val KEY_OPENING_HISTORY = "opening_history"
        const val KEY_MOVE_IDEAS = "move_ideas"
    }
}
