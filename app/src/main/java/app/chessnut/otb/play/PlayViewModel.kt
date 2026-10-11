package app.chessnut.otb.play

import java.io.IOException
import app.chessnut.otb.lichess.LichessHttpException
import app.chessnut.otb.repertoire.StudyPgn
import app.chessnut.otb.repertoire.RepertoireStore
import app.chessnut.otb.repertoire.Drill
import java.io.File
import app.chessnut.otb.openings.OpeningStats
import app.chessnut.otb.lichess.LichessVault
import app.chessnut.otb.lichess.LichessHttp
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.chessnut.otb.OtbApp
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.analysis.Score
import app.chessnut.otb.analysis.Terminal
import app.chessnut.otb.board.BoardSounds
import app.chessnut.otb.board.MoveSpeaker
import app.chessnut.otb.engine.StockfishProcess
import app.chessnut.otb.engine.UciEngine
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.live.TimeControl
import app.chessnut.otb.openings.OpeningBook
import app.chessnut.otb.openings.OpeningLore
import app.chessnut.otb.openings.MoveIdeas
import app.chessnut.otb.openings.OpeningCatalog
import app.chessnut.otb.openings.OpeningFilter
import app.chessnut.otb.protocol.LedCodec
import com.github.bhlangonijr.chesslib.Board
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.chessnut.otb.chess.MoveInput
import app.chessnut.otb.repertoire.OpeningTree
import app.chessnut.otb.review.POSITION_PREFIX
import app.chessnut.otb.repertoire.DepthRun
import app.chessnut.otb.play.BOARD_SETTLE_MS
import app.chessnut.otb.openings.OpeningSide

enum class PlayKind { Bot, Trainer }

/** The bot's time menu entry for untimed play. */
const val NO_CLOCK = "No clock"

/** [winRate] is how often the opening's side wins on Lichess, as a percentage, once fetched. */
data class OpeningRow(
    val name: String,
    val accuracy: Int?,
    val winRate: Int? = null,
    val due: Int? = null,
    /** Lines learned and lines in all, once you have drilled this opening from the book. */
    val learned: Int? = null,
    val lines: Int? = null,
    /** Your longest run of engine-approved moves past the book, from this side. */
    val depthBest: Int? = null,
)

data class OpeningSection(val title: String, val rows: List<OpeningRow>)

/** Everything about one opening from one side, for its own page. */
data class OpeningDetail(
    val name: String,
    /** The opening's own moves, numbered, such as "1. e4 e5 2. Nf3 Nc6 3. d4". */
    val moves: String,
    /** The position the opening reaches. */
    val fen: String,
    val learned: Int,
    val total: Int,
    val due: Int,
    val depthBest: Int?,
    val depthLast: Int?,
    val accuracy: Int?,
    val winRate: Int?,
    val story: String?,
    /** Book moves you chose over the main line, such as "4. c3". */
    val choices: List<String>,
    /** Lines come from an imported study rather than the book. */
    val study: Boolean,
)

enum class LineStatus { New, Learned, Due, Missed }

/** One line of the chosen opening, for the line list: its variation name, its moves past the opening, and how you are doing with it. */
data class LineView(
    val name: String,
    val moves: String,
    val status: LineStatus,
    /** When the line is next due for review, such as "Review in 3 days"; null for a line never played from memory. */
    val next: String? = null,
    /** Your best run of good moves after this line, from Survival or training. */
    val best: Int? = null,
    /** How often your colour wins from the line's end position on Lichess, once fetched. */
    val winRate: Int? = null,
)

data class PlayUi(
    val kind: PlayKind = PlayKind.Bot,
    val userWhite: Boolean = true,
    val elo: Int = EloBracket.Default.elo,
    val openingQuery: String = "",
    val openingName: String? = null,
    val gambitsOnly: Boolean = false,
    val whiteOpenings: Boolean = false,
    val blackOpenings: Boolean = false,
    val unratedOnly: Boolean = false,
    val memesOnly: Boolean = false,
    val sections: List<OpeningSection> = emptyList(),
    val playing: Boolean = false,
    val fen: String = Board().fen,
    val san: List<String> = emptyList(),
    val status: String = "Play a bot, or train an opening.",
    val thinking: Boolean = false,
    val hintUci: String? = null,
    val promptUci: String? = null,
    val rejectUci: String? = null,
    val physical: Boolean = false,
    val awaitingBoard: Boolean = false,
    val retry: Boolean = false,
    val finished: String? = null,
    val controlLabel: String = TimeControl.Default.label,
    val whiteMillis: Long = TimeControl.Default.initialMillis,
    val blackMillis: Long = TimeControl.Default.initialMillis,
    val whiteRunning: Boolean = false,
    val blackRunning: Boolean = false,
    val flaggedWhite: Boolean = false,
    val flaggedBlack: Boolean = false,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val lastUci: String? = null,
    /** Ply on screen. Equal to [moveCount] when the live position is showing. */
    val glancePly: Int = 0,
    val moveCount: Int = 0,
    val browsing: Boolean = false,
    /** Trainer only: the position still has a book move in the chosen opening. */
    val inTheory: Boolean = true,
    val explain: Boolean = true,
    /** Trainer: speak the history of the opening as its lines are reached. */
    val history: Boolean = false,
    /** Trainer: speak the idea behind each move as it is played. */
    val ideas: Boolean = false,
    /** Book replies, main line first. Shown as arrows when [explain] is on. */
    val bookUcis: List<String> = emptyList(),
    /** [bookUcis] in SAN, same order, for the key under the board. */
    val bookSans: List<String> = emptyList(),
    /** Past the book, [bookUcis] are the engine's top moves rather than theory. */
    val explainedByEngine: Boolean = false,
    /** Which line of the opening this game drills, such as "Line 3 of 12 · new". */
    val lineLabel: String? = null,
    /** The drilled line is over, so the next one can start. */
    val lineDone: Boolean = false,
    /** The finished line was played cleanly. */
    val lineClean: Boolean = false,
    /** How the drilled line went, kept on screen while play goes on. */
    val lineNote: String? = null,
    /** Every line of the chosen opening, for picking one to drill. */
    val lines: List<LineView> = emptyList(),
    /** The chosen opening's page is open. */
    val showDetail: Boolean = false,
    val detail: OpeningDetail? = null,
    /** A book move you played instead of the line's, offered as your move from now on, such as "4. c3". */
    val choiceOffer: String? = null,
    /** The named variation the game is in now. */
    val variation: String? = null,
    /** The last move, numbered, and the idea behind it, when Ideas is on. */
    val ideaMove: String? = null,
    val idea: String? = null,
    /** The latest story about the opening, when History is on. */
    val story: String? = null,
    /** After a miss, the line's move to take back and play instead, such as "7. O-O". */
    val retryMove: String? = null,
    /** Across every opening you drill: lines learned, openings started, and lines due for review. */
    val learnedTotal: Int = 0,
    val openingsStarted: Int = 0,
    val dueTotal: Int = 0,
    /** Bot games are played with clocks; off for untimed play, such as from a set-up position. */
    val timed: Boolean = true,
    /** Past the book, from memory: good moves in a row so far, null before the book runs out. */
    val depthRun: Int? = null,
    /** The run ended on a move outside the engine's top two. */
    val depthOver: Boolean = false,
    /** Your best run for this opening and side, before this game. */
    val depthBest: Int? = null,
    /** Survival: one of your learned lines, then as long as you can keep finding the engine's top two moves. */
    val survival: Boolean = false,
    /** The survival game has ended: you left the line or played a move outside the top two. */
    val survivalOver: Boolean = false,
    /** The trainer's evaluation of the position on the board, for the eval bar. */
    val evalScore: Score? = null,
    val evalTerminal: Terminal? = null,
    /** The bot game starts from this position instead of the usual start, such as one set up on the board. */
    val startFen: String? = null,
    /** A review session is running through due lines from several openings. */
    val reviewing: Boolean = false,
    /** No review lines are left after this one. */
    val reviewLast: Boolean = false,
)

class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OtbApp
    private val state = MutableStateFlow(PlayUi(explain = app.explainOpening, history = app.openingHistory, ideas = app.moveIdeas))
    val ui: StateFlow<PlayUi> = state.asStateFlow()
    private val sounds = BoardSounds()
    private val speech = MoveSpeaker(application)
    private val random = Random.Default
    private var engine: UciEngine? = null
    private val vault = LichessVault(application)
    private val openingStats = OpeningStatsCache(File(application.filesDir, "opening-stats.tsv"), LichessHttp { vault.token() })
    private var statsJob: Job? = null
    private var job: Job? = null
    private var clockJob: Job? = null
    private var settleJob: Job? = null
    /** Position before the last move played on the board, so a slide can still correct it. */
    private var revision: Revision? = null
    private val clock = PlayClock()
    private var clockNoted: String? = null
    private var catalogJob: Job? = null
    private var busy = false

    private var kind = PlayKind.Bot
    private var userWhite = true
    private var elo = EloBracket.Default.elo
    private var openingName: String? = null
    private val sanMoves = mutableListOf<String>()
    private val uciMoves = mutableListOf<String>()
    private var glance: Int? = null
    private var fen = Board().fen
    private var playing = false
    private var physical = false
    private var awaitingBoard = false
    private var status = "Play a bot, or train an opening."
    private var hint: String? = null
    private var prompt: String? = null
    private var rejectUci: String? = null
    private var retry = false
    private var finished: String? = null
    private var selected: Int? = null
    private var lastUci: String? = null
    private var ignorePlacement: String? = null
    private var pendingPlacement: String? = null
    private var lastPlacement: String? = null
    private var heldPlacement: String? = null
    private var explain = app.explainOpening
    /** Stories told this game, so none is repeated. */
    private val told = mutableSetOf<String>()
    /** The out-of-theory cue has been spoken for this game. */
    private var leftTheory = false
    /** Repertoire drill: the line chosen for this game, and whether a move strayed from it. */
    private var drillLine: List<String>? = null
    private var drillMissed = false
    private var drillGraded = false
    /** Every line of the opening being drilled. */
    private var drillLines: List<List<String>> = emptyList()
    /** True when the lines come from the opening book rather than an imported study. */
    private var drillBook = false
    /** Explain mode walks through the lines in turn without grading them. */
    private var lineCursor = 0
    /** Book lines per opening and side, since building them walks the whole book. */
    private val treeCache = mutableMapOf<Pair<String, Boolean>, List<List<String>>>()
    private var lineLabelShown: String? = null
    private var lineNote: String? = null
    private var lineClean = false
    private var survival = false
    private var survivalOver = false
    /** Explain's setting before survival turned it off, to restore after. */
    private var explainBeforeSurvival: Boolean? = null
    /** The last evaluated position and its score, and whether an evaluation is running. */
    private var evalFen: String? = null
    private var evalScore: Score? = null
    private var evaluating = false
    /** Depth run past the book: good moves so far, whether it has ended, and the record going in. */
    private var depthRun: Int? = null
    private var depthOver = false
    private var depthBest: Int? = null
    /** The drilled line's own best run, before this game. */
    private var lineBest: Int? = null
    /** From the last judged move: the engine's top two and the winning chances it gave up. */
    private var lastTopTwo: List<String> = emptyList()
    private var lastWinDrop = 0.0
    private var lastJudgedFen: String? = null
    /** What the engine would have played when the run ended, such as "8. c4 or 8. Nd2". */
    private var depthMissed: String? = null
    /** Where a bot game starts, when not from the usual position. */
    private var startFen: String? = null
    private var timed = true
    /** Whether games were timed before a set-up position turned the clock off, to restore after. */
    private var timedBeforePosition: Boolean? = null
    private var ideaMove: String? = null
    /** The ply where you left the line, to take back to. */
    private var retryAt: Int? = null

    /** One line in a review session. */
    private data class ReviewItem(val opening: String, val white: Boolean, val line: List<String>)

    /** Due lines still to come in the review session, most overdue first. */
    private val reviewQueue = ArrayDeque<ReviewItem>()
    private var reviewTotal = 0
    private var reviewing = false
    private var explainBeforeReview = false
    private var idea: String? = null
    private var story: String? = null
    /** A line picked from the list, drilled next instead of the scheduled one. */
    private var pinnedLine: List<String>? = null
    /** The moves before, and the book move you chose there, when it differs from the line. */
    private var choiceOffer: Pair<List<String>, String>? = null
    private var linesJob: Job? = null
    private var lineStatsJob: Job? = null
    /** Explain past the book: the engine's top moves as (SAN, UCI), for [ideasFen]. */
    private var ideas: List<Pair<String, String>> = emptyList()
    private var ideasFen: String? = null
    private var ideasJob: Job? = null
    private var ideasTarget: String? = null
    private var scored = false
    /** Squares that did not match the start at the last reading, while waiting for setup. */
    private var setupWrong: List<String> = emptyList()

    init {
        viewModelScope.launch {
            app.analysisVersion.collect { refreshOpenings(state.value.openingQuery) }
        }
        viewModelScope.launch {
            app.placements.collect { onPhysical(it) }
        }
    }

    fun setKind(next: PlayKind) {
        if (playing) return
        state.update { it.copy(kind = next) }
    }

    /** Plays the bot from [fen], with you on the side [userWhite]. Cleared when you leave. */
    fun setStartPosition(fen: String, userWhite: Boolean) {
        if (playing) leave()
        startFen = fen
        // A position you set up is for thinking it through, so it starts untimed.
        if (timedBeforePosition == null) timedBeforePosition = timed
        timed = false
        state.update { it.copy(kind = PlayKind.Bot, startFen = fen, userWhite = userWhite, timed = false, controlLabel = NO_CLOCK) }
    }

    /** Back to games from the usual starting position. */
    fun clearStartPosition() {
        if (playing) return
        startFen = null
        restoreTiming()
        state.update { it.copy(startFen = null) }
        publish()
    }

    private fun restoreTiming() {
        timedBeforePosition?.let { timed = it }
        timedBeforePosition = null
    }

    /** Where this game began. */
    private fun origin(): String = if (kind == PlayKind.Bot) startFen ?: Board().fen else Board().fen

    fun setUserWhite(white: Boolean) {
        if (playing) return
        state.update { it.copy(userWhite = white) }
        refreshOpenings(state.value.openingQuery)
        refreshLines()
    }

    fun setElo(value: Int) {
        if (playing) return
        state.update { it.copy(elo = value) }
    }

    fun setTime(label: String) {
        if (sanMoves.isNotEmpty()) return
        timed = label != NO_CLOCK
        if (timed) clock.setControl(TimeControl.fromLabel(label), System.currentTimeMillis())
        publish()
    }

    fun resign() {
        glance = null
        if (kind != PlayKind.Bot || !playing || finished != null || sanMoves.isEmpty()) return
        finishClock(if (userWhite) "0-1" else "1-0", "Resign")
    }

    fun offerDraw() {
        glance = null
        if (kind != PlayKind.Bot || !playing || finished != null || busy || awaitingBoard || sanMoves.isEmpty() || !userToMove()) return
        launchPlay {
            status = "Offering a draw…"
            publish()
            val eng = engine()
            eng.limitStrength(elo)
            val score = eng.analyseMillis(fen, 400, multiPv = 1).firstOrNull()?.score
            if (finished != null) return@launchPlay
            if (score != null && botAcceptsDraw(score)) finishClock("1/2-1/2", "Draw")
            else {
                status = "Bot declines the draw"
                publish()
            }
        }
    }

    fun setQuery(query: String) {
        if (playing) return
        state.update { it.copy(openingQuery = query) }
        refreshOpenings(query)
    }

    fun setGambitsOnly(on: Boolean) = setListFilter { it.copy(gambitsOnly = on) }

    fun setWhiteOpenings(on: Boolean) = setListFilter { it.copy(whiteOpenings = on) }

    fun setBlackOpenings(on: Boolean) = setListFilter { it.copy(blackOpenings = on) }

    fun setUnratedOnly(on: Boolean) = setListFilter { it.copy(unratedOnly = on) }

    fun setMemesOnly(on: Boolean) = setListFilter { it.copy(memesOnly = on) }

    fun setIdeas(on: Boolean) {
        app.moveIdeas = on
        state.update { it.copy(ideas = on) }
        if (!on) speech.stop()
    }

    fun setHistory(on: Boolean) {
        app.openingHistory = on
        state.update { it.copy(history = on) }
        if (!on) speech.stop()
    }

    fun setExplain(on: Boolean) {
        explain = on
        app.explainOpening = on
        if (playing && kind == PlayKind.Trainer) {
            if (on && retry) {
                retry = false
                rejectUci = null
                if (!busy && finished == null) status = openingName ?: "Opening trainer"
            }
            publish()
        } else {
            state.update { it.copy(explain = on) }
        }
    }

    private fun setListFilter(update: (PlayUi) -> PlayUi) {
        if (playing) return
        state.update(update)
        refreshOpenings(state.value.openingQuery)
    }

    fun selectOpening(name: String) {
        if (playing) return
        val chapter = app.repertoire.chapter(name)
        state.update { it.copy(openingName = name, userWhite = chapter?.userWhite ?: it.userWhite) }
        refreshLines()
    }

    /** Opens [name]'s own page: its scores, lines and history. */
    fun openDetail(name: String) {
        selectOpening(name)
        state.update { it.copy(showDetail = true) }
    }

    fun closeDetail() {
        state.update { it.copy(showDetail = false) }
    }

    /** Goes back to the book's main moves for the chosen opening and side. */
    fun resetChoices() {
        val ui = state.value
        val name = ui.openingName ?: return
        app.moveChoices.clear(name, ui.userWhite)
        synchronized(treeCache) { treeCache.remove(name to ui.userWhite) }
        refreshLines()
        refreshOpenings(ui.openingQuery)
    }

    /** Drills line [index] of the chosen opening now. */
    fun trainLine(index: Int) {
        if (playing) return
        val ui = state.value
        val name = ui.openingName ?: return
        val lines = app.repertoire.chapter(name)?.lines ?: bookLines(name, ui.userWhite)
        pinnedLine = lines.getOrNull(index) ?: return
        start()
    }

    /** Fills [PlayUi.lines] for the chosen opening and side. */
    private fun refreshLines() {
        linesJob?.cancel()
        val ui = state.value
        val name = ui.openingName ?: return
        val white = ui.userWhite
        linesJob = viewModelScope.launch(Dispatchers.Default) {
            val chapter = app.repertoire.chapter(name)
            val lines = chapter?.lines ?: bookLines(name, white)
            val reviews = if (chapter != null) app.repertoire.reviews() else app.lineProgress.reviews(name, white)
            val learned = if (chapter != null) null else app.lineProgress.learned(name, white)
            val now = System.currentTimeMillis()
            // The moves every line shares are the opening itself; show what comes after.
            val shared = lines.firstOrNull()?.indices?.takeWhile { i -> lines.all { it.getOrNull(i) == lines[0][i] } }?.size ?: 0
            fetchLineStats(lines)
            val views = lines.map { line ->
                val key = Drill.key(line)
                val review = reviews[key]
                val status = when {
                    review == null -> LineStatus.New
                    review.dueAt <= now -> LineStatus.Due
                    learned != null && key !in learned -> LineStatus.Missed
                    else -> LineStatus.Learned
                }
                val variation = OpeningBook.view(line).name
                    ?.removePrefix("$name: ")
                    ?.removePrefix(name)
                    ?.trim(' ', ',', ':')
                    ?.ifBlank { null }
                val next = review?.let {
                    val days = Drill.days(it, now)
                    when {
                        it.dueAt <= now -> "Review now"
                        days <= 1 -> "Review tomorrow"
                        else -> "Review in $days days"
                    }
                }
                LineView(
                    variation ?: "Main line",
                    numberedFrom(line, shared),
                    status,
                    next,
                    app.depthRecords.ofLine(name, white, line)?.best,
                    lineStatKey(line)?.let { openingStats.record(it) }?.winPercent(if (white) OpeningSide.White else OpeningSide.Black),
                )
            }
            state.update { if (it.openingName == name && it.userWhite == white) it.copy(
                    lines = views,
                    detail = detailOf(
                        name, white, chapter != null, views,
                        learnedCount = learned?.count { key -> lines.any { Drill.key(it) == key } }
                            ?: views.count { it.status == LineStatus.Learned },
                    ),
                ) else it }
        }
    }

    private fun detailOf(name: String, white: Boolean, study: Boolean, lines: List<LineView>, learnedCount: Int): OpeningDetail {
        val start = OpeningBook.line(name)?.san.orEmpty()
        val fen = runCatching { ChessLines.replay(start).fens.last() }.getOrDefault(Board().fen)
        val row = state.value.sections.flatMap { it.rows }.firstOrNull { it.name == name }
        val depth = app.depthRecords.of(name, white)
        val choices = app.moveChoices.of(name, white).entries
            .sortedBy { it.key.size }
            .map { (before, move) -> "${moveNumber(before.size)}$move" }
        return OpeningDetail(
            name = name,
            moves = numberedFrom(start, 0),
            fen = fen,
            learned = learnedCount,
            total = lines.size,
            due = lines.count { it.status == LineStatus.Due },
            depthBest = depth?.best,
            depthLast = depth?.last,
            accuracy = row?.accuracy,
            winRate = row?.winRate,
            story = OpeningLore.of(name),
            choices = choices,
            study = study,
        )
    }

    /** The explorer cache key and UCI moves for [line]'s end position. */
    private fun linePlay(line: List<String>): String? {
        var position = Board().fen
        val ucis = mutableListOf<String>()
        for (san in line) {
            val uci = PlayPosition.uciOfSan(position, san) ?: return null
            ucis += uci
            position = PlayPosition.after(position, uci)?.second ?: return null
        }
        return ucis.joinToString(",")
    }

    private fun lineStatKey(line: List<String>): String? = linePlay(line)?.let { "line:$it" }

    /** Fetches Lichess results for the lines on the open page that lack them, then shows them. Needs a Lichess login. */
    private fun fetchLineStats(lines: List<List<String>>) {
        if (vault.token() == null || lineStatsJob?.isActive == true) return
        val entries = lines.mapNotNull { line -> linePlay(line)?.let { "line:$it" to it } }.filter { openingStats.missing(it.first) }
        if (entries.isEmpty()) return
        lineStatsJob = viewModelScope.launch(Dispatchers.IO) {
            openingStats.fillPlays(entries) { refreshLines() }
        }
    }

    /** "6… exd4 7. O-O dxc3", the moves of [line] from ply [from]. */
    private fun numberedFrom(line: List<String>, from: Int): String = buildString {
        for (ply in from until line.size) {
            if (ply % 2 == 0) append("${ply / 2 + 1}. ") else if (ply == from) append("${ply / 2 + 1}… ")
            append(line[ply]).append(' ')
        }
    }.trim()

    /** Imports a Lichess study's chapters into the repertoire. [onDone] gets a message to show. */
    fun importStudy(link: String, onDone: (String) -> Unit) {
        val id = RepertoireStore.studyId(link)
        if (id == null) {
            onDone("That doesn't look like a Lichess study link.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                val chapters = StudyPgn.chapters(LichessHttp { vault.token() }.study(id))
                if (chapters.isEmpty()) {
                    "No chapters from the starting position in that study."
                } else {
                    val (count, lines) = app.repertoire.add(chapters)
                    "Added $count chapters, $lines lines."
                }
            } catch (e: LichessHttpException) {
                if (e.status == 403 || e.status == 404) {
                    "Couldn't read that study. If it's private, make it unlisted, or log out of Lichess and back in."
                } else {
                    e.message ?: "Lichess refused the request."
                }
            } catch (e: IOException) {
                "No connection to Lichess."
            }
            withContext(Dispatchers.Main) {
                refreshOpenings(state.value.openingQuery)
                onDone(message)
            }
        }
    }

    fun clearRepertoire() {
        app.repertoire.clear()
        if (app.repertoire.isRepertoire(state.value.openingName)) state.update { it.copy(openingName = null) }
        refreshOpenings(state.value.openingQuery)
    }

    fun start() {
        // Save the last game's run under its own opening before this one replaces it.
        endDepthRun()
        val previous = job
        job = null
        busy = false
        previous?.cancel()
        clockJob?.cancel()
        settleJob?.cancel()
        revision = null
        scored = false
        clock.resetKeepingControl()
        clockNoted = null
        speech.stop()
        told.clear()
        leftTheory = false
        drillMissed = false
        drillGraded = false
        val ui = state.value
        val trained = ui.openingName?.takeIf { ui.kind == PlayKind.Trainer }
        val chapter = trained?.let { app.repertoire.chapter(it) }
        drillBook = trained != null && chapter == null
        drillLines = when {
            chapter != null -> chapter.lines
            trained != null -> bookLines(trained, ui.userWhite)
            else -> emptyList()
        }
        val pinned = pinnedLine?.takeIf { it in drillLines }
        pinnedLine = null
        drillLine = when {
            drillLines.isEmpty() -> null
            pinned != null -> pinned
            !drillBook -> Drill.next(drillLines, app.repertoire.reviews(), System.currentTimeMillis())
            ui.explain -> {
                // Explaining shows each line in turn, those not yet learned first.
                val learned = app.lineProgress.learned(trained!!, ui.userWhite)
                val fresh = drillLines.filter { Drill.key(it) !in learned }.ifEmpty { drillLines }
                fresh[lineCursor++ % fresh.size]
            }
            else -> Drill.next(drillLines, app.lineProgress.reviews(trained!!, ui.userWhite), System.currentTimeMillis())
        }
        if (ui.kind == PlayKind.Trainer && ui.openingName == null) return
        kind = ui.kind
        userWhite = ui.userWhite
        elo = ui.elo
        openingName = ui.openingName
        explain = ui.explain
        sanMoves.clear()
        uciMoves.clear()
        glance = null
        fen = origin()
        playing = true
        physical = app.boardSession != null
        hint = null
        prompt = null
        rejectUci = null
        retry = false
        finished = null
        selected = null
        lastUci = null
        ignorePlacement = null
        pendingPlacement = null
        lastPlacement = null
        heldPlacement = null
        app.playEngaged = true
        note("start ${kind.name} physical=$physical session=${app.boardSession != null}")
        val ready = !physical || app.latestPlacement?.let { PlayPosition.matches(fen, it) } == true
        awaitingBoard = !ready
        setupWrong = if (awaitingBoard) app.latestPlacement?.let { PlayPosition.differences(fen, it) }.orEmpty() else emptyList()
        if (awaitingBoard) lightSquares(if (setupWrong.size <= MAX_SETUP_HINTS) setupWrong else emptyList())
        depthRun = null
        depthOver = false
        depthBest = ui.openingName?.let { app.depthRecords.of(it, ui.userWhite)?.best }
        lineBest = ui.openingName?.let { name -> drillLine?.let { app.depthRecords.ofLine(name, ui.userWhite, it)?.best } }
        lineLabelShown = lineLabel()
        lineNote = null
        survivalOver = false
        evalFen = null
        evalScore = null
        retryAt = null
        ideaMove = null
        idea = null
        story = null
        choiceOffer = null
        status = when {
            awaitingBoard -> setupStatus(setupWrong)
            kind == PlayKind.Trainer -> openingName ?: "Opening trainer"
            userWhite -> "You have White · $elo"
            else -> "You have Black · $elo"
        }
        publish()
        if (ready) kickoff()
    }

    fun hint() {
        glance = null
        if (!playing || awaitingBoard || finished != null) return
        val shown = prompt
        if (shown != null) {
            status = "The lit squares are the move"
            publish()
            light(shown)
            return
        }
        if (busy || !userToMove()) {
            note("hint ignored busy=$busy user=${userToMove()}")
            return
        }
        launchPlay {
            val uci = bookMove() ?: engineBest()
            hint = uci
            status = if (physical) "Hint lit. Play it on the board." else "Hint lit"
            note("hint $uci")
            publish()
            light(uci)
        }
    }

    /** Show an earlier ply. The live game, clocks, and board lights stay where they are. */
    fun glanceTo(ply: Int) {
        if (!playing) return
        val count = uciMoves.size
        glance = when {
            ply >= count -> null
            ply <= 0 -> 0
            else -> ply
        }
        publish()
    }

    fun glanceBy(delta: Int) {
        glanceTo((glance ?: uciMoves.size) + delta)
    }

    fun tap(square: Int) {
        if (glance != null) return
        if (!playing || physical || awaitingBoard || busy || finished != null || prompt != null) {
            note("tap $square ignored playing=$playing physical=$physical awaiting=$awaitingBoard busy=$busy prompt=$prompt")
            return
        }
        if (!userToMove()) return
        when (val tap = MoveInput.tap(fen, selected, square)) {
            is MoveInput.Tap.Play -> {
                selected = null
                publish()
                playUci(tap.uci)
            }
            is MoveInput.Tap.Select -> {
                selected = tap.square
                publish()
            }
        }
    }

    /** Saves a trainer game once, so the opening list can show accuracy. */
    private fun scoreTrainer() {
        if (scored || kind != PlayKind.Trainer || explain || sanMoves.isEmpty()) return
        scored = true
        viewModelScope.launch { analyse() }
    }

    /** Saves the game played so far and queues the same review analysis as a club game. */
    fun analyse(): String? {
        if (sanMoves.isEmpty()) return null
        // A game from a set-up position can't be saved as a normal game, so analyse where it stands now.
        if (kind == PlayKind.Bot && startFen != null) return POSITION_PREFIX + fen
        clockJob?.cancel()
        if (kind == PlayKind.Bot) clock.freeze(System.currentTimeMillis())
        job?.cancel()
        busy = false
        val closing = engine
        engine = null
        job = null
        closing?.close()
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(now))
        val me = app.myName.ifBlank { "Me" }
        val opponent = if (kind == PlayKind.Bot) "Bot $elo" else "Trainer"
        val result = when (finished) {
            "White wins" -> "1-0"
            "Black wins" -> "0-1"
            "Draw" -> "1/2-1/2"
            else -> "*"
        }
        val moves = sanMoves.toList()
        val saved = SavedGame(
            id = SavedGame.idFor(moves, emptyList()),
            createdAt = now,
            source = GameSource.Play,
            sanMoves = moves,
            isComplete = finished != null,
            stopReason = finished,
            white = if (userWhite) me else opponent,
            black = if (userWhite) opponent else me,
            event = if (kind == PlayKind.Trainer) openingName ?: "Opening trainer" else "Bot $elo",
            date = date,
            result = result,
            label = if (kind == PlayKind.Trainer) openingName.orEmpty() else "",
        )
        app.store.addIfNew(saved)
        app.refreshGames()
        app.analyse(saved.id)
        return saved.id
    }

    /** Starts a session through every due line, from memory, across all the openings you drill. */
    fun startReview() {
        if (playing) return
        val now = System.currentTimeMillis()
        val items = app.lineProgress.started().flatMap { (name, white) ->
            val reviews = app.lineProgress.reviews(name, white)
            bookLines(name, white).mapNotNull { line ->
                val due = reviews[Drill.key(line)]?.dueAt ?: return@mapNotNull null
                if (due <= now) due to ReviewItem(name, white, line) else null
            }
        }.sortedBy { it.first }.map { it.second }
        if (items.isEmpty()) return
        reviewQueue.clear()
        reviewQueue.addAll(items)
        reviewTotal = items.size
        explainBeforeReview = explain
        reviewing = true
        nextReview()
    }

    /**
     * Survival: plays one of your learned lines of the chosen opening, picked
     * at random, from memory, then keeps going with a varied opponent until a
     * move falls outside the engine's top two. Reviews are not affected.
     */
    fun startSurvival() {
        val ui = state.value
        val name = ui.openingName ?: return
        val chapter = app.repertoire.chapter(name)
        val lines = chapter?.lines ?: bookLines(name, ui.userWhite)
        if (lines.isEmpty()) return
        val known = if (chapter != null) {
            app.repertoire.reviews().filterValues { it.box > 0 }.keys
        } else {
            app.lineProgress.learned(name, ui.userWhite)
        }
        val pool = lines.filter { Drill.key(it) in known }.ifEmpty { lines }
        val line = pool[random.nextInt(pool.size)]
        if (explainBeforeSurvival == null) explainBeforeSurvival = explain
        survival = true
        // Survival is played from memory.
        explain = false
        state.update { it.copy(explain = false) }
        pinnedLine = line
        start()
        // The variation shows above the board, so the label stays short and the board doesn't move.
        lineLabelShown = "Survival"
        publish()
    }

    private fun endSurvival() {
        if (!survival) return
        survival = false
        survivalOver = false
        explainBeforeSurvival?.let { before ->
            explain = before
            state.update { it.copy(explain = before) }
        }
        explainBeforeSurvival = null
    }

    /** Plays the same line again from the first move, as when you missed it and want another go. */
    fun againLine() {
        val line = drillLine
        val label = lineLabelShown
        if (line != null) pinnedLine = line
        start()
        // A review keeps its "Review 2 of 5" place.
        if (reviewing && label != null) {
            lineLabelShown = label
            publish()
        }
    }

    /** The next line: the next due review in a session, otherwise the next line of this opening. */
    fun nextLine() {
        if (reviewing) nextReview() else start()
    }

    private fun nextReview() {
        val item = reviewQueue.removeFirstOrNull()
        if (item == null) {
            endReview()
            leave()
            return
        }
        // Reviews are from memory, so the arrows stay off.
        explain = false
        state.update { it.copy(kind = PlayKind.Trainer, openingName = item.opening, userWhite = item.white, explain = false) }
        pinnedLine = item.line
        start()
        lineLabelShown = "Review ${reviewTotal - reviewQueue.size} of $reviewTotal"
        publish()
    }

    private fun endReview() {
        if (!reviewing) return
        reviewing = false
        reviewQueue.clear()
        explain = explainBeforeReview
        state.update { it.copy(explain = explainBeforeReview) }
    }

    fun leave() {
        endDepthRun()
        endSurvival()
        endReview()
        startFen = null
        restoreTiming()
        state.update { it.copy(startFen = null) }
        job?.cancel()
        job = null
        clockJob?.cancel()
        settleJob?.cancel()
        revision = null
        clock.resetKeepingControl()
        clockNoted = null
        busy = false
        speech.stop()
        app.playEngaged = false
        val closing = engine
        engine = null
        closing?.close()
        playing = false
        awaitingBoard = false
        prompt = null
        hint = null
        rejectUci = null
        finished = null
        retry = false
        selected = null
        fen = Board().fen
        sanMoves.clear()
        uciMoves.clear()
        glance = null
        lastUci = null
        status = "Play a bot, or train an opening."
        viewModelScope.launch(NonCancellable) {
            runCatching { app.boardSession?.setLeds(emptyList()) }
        }
        publish()
    }

    override fun onCleared() {
        job?.cancel()
        app.playEngaged = false
        engine?.close()
        engine = null
        sounds.release()
        speech.release()
        val session = app.boardSession
        if (session != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { session.setLeds(emptyList()) }
            }
        }
    }

    private fun playUci(uci: String) {
        if (!playing || awaitingBoard || busy || finished != null || prompt != null || (survival && survivalOver)) {
            note("move $uci ignored playing=$playing awaiting=$awaitingBoard busy=$busy prompt=$prompt")
            return
        }
        if (!userToMove()) return
        launchPlay {
            // The run starts once the drilled line is played cleanly, or wherever the book runs out.
            val lineFinished = drillGraded && lineClean
            // Only Survival scores moves past the line; training just carries on with the book.
            val pastBook = survival && openingName != null && (lineFinished || !legalBookMove())
            val verdict = if (kind == PlayKind.Trainer) judge(uci) else null
            if (drillLine != null && verdict != null && verdict != TrainerResult.Book) drillMissed = true
            if (verdict != null && explain && !verdict.accepted) {
                reject(uci, verdict)
                return@launchPlay
            }
            if (!apply(uci)) return@launchPlay
            if (pastBook) countDepth(uci, book = verdict == TrainerResult.Book)
            if (survival && survivalOver) {
                // The game is over, so the opponent doesn't reply.
                light(null)
                publish()
                return@launchPlay
            }
            retry = false
            rejectUci = null
            if (verdict != null && finished == null) {
                status = verdict.label
                publish()
            }
            light(null)
            if (finished != null) {
                endDepthRun()
                celebrate()
                scoreTrainer()
                return@launchPlay
            }
            if (!userToMove()) reply()
        }
    }

    /** One more move past the book: extends the run, or ends it if the engine had two better. */
    private fun countDepth(uci: String, book: Boolean) {
        if (depthOver) return
        // A book move is theory, so it always counts.
        if (book || DepthRun.good(uci, lastTopTwo, lastWinDrop)) {
            depthRun = (depthRun ?: 0) + 1
        } else {
            depthRun = depthRun ?: 0
            val before = lastJudgedFen
            val ply = sanMoves.size - 1
            depthMissed = before?.let { position ->
                lastTopTwo.mapNotNull { ChessLines.sanOf(position, listOf(it)).firstOrNull() }
                    .distinct()
                    .joinToString(" or ") { "${moveNumber(ply)}$it" }
                    .ifEmpty { null }
            }
            endDepthRun()
        }
        publish()
    }

    /** Saves the run, once, when it ends or the game does. */
    private fun endDepthRun() {
        val run = depthRun ?: return
        val name = openingName ?: return
        if (depthOver) return
        depthOver = true
        app.depthRecords.finish(name, userWhite, run)
        // A run that followed a line you played cleanly also counts for that line.
        val line = drillLine?.takeIf { lineClean }
        if (line != null) app.depthRecords.finishLine(name, userWhite, line, run)
        // Survival compares against the line you were dealt; training against the whole opening.
        val record = if (survival) lineBest else depthBest
        if (survival) survivalOver = true
        val moves = "$run ${if (run == 1) "move" else "moves"}"
        val result = when {
            survival && run > (record ?: 0) && run > 0 -> "You survived $moves past the line. A new best!"
            survival -> "You survived $moves past the line." + (record?.let { " Your best on this line is $it." } ?: "")
            run > (record ?: 0) && run > 0 -> "New best: $run good ${if (run == 1) "move" else "moves"} past the line."
            record != null -> "Run over at $run. Your best is $record."
            else -> "Run over at $run."
        }
        val words = listOfNotNull(result, depthMissed?.let { "The engine wanted $it." }).joinToString(" ")
        depthMissed = null
        // In survival the result is the whole story; elsewhere it follows the line's own note.
        lineNote = if (survival) words else listOfNotNull(lineNote, words).joinToString(" ")
        if (app.phoneSounds && playing) speech.narrate(words)
        refreshOpenings(state.value.openingQuery)
        refreshLines()
    }

    private fun onPhysical(placement: String) {
        if (placement != lastPlacement) {
            lastPlacement = placement
            note("board ${placement.take(48)} playing=$playing physical=$physical awaiting=$awaitingBoard busy=$busy prompt=$prompt")
        }
        if (!playing || !physical) return
        // A slide reports every square the piece crosses. Wait until it stops.
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            delay(BOARD_SETTLE_MS)
            onSettled(placement)
        }
    }

    private fun onSettled(placement: String) {
        if (!playing || !physical) return
        if (awaitingBoard) {
            if (PlayPosition.matches(fen, placement)) {
                note("board matches the start")
                setupWrong = emptyList()
                lightSquares(emptyList())
                kickoff()
                return
            }
            val wrong = PlayPosition.differences(fen, placement)
            if (wrong != setupWrong) {
                setupWrong = wrong
                status = setupStatus(wrong)
                publish()
                lightSquares(if (wrong.size <= MAX_SETUP_HINTS) wrong else emptyList())
            }
            return
        }
        if (prompt != null) {
            val expected = PlayPosition.after(fen, prompt!!)?.second
            if (expected != null && PlayPosition.matches(expected, placement)) {
                note("board played the lit move $prompt")
                apply(prompt!!)
                light(null)
                if (finished != null) {
                    celebrateAsync()
                    scoreTrainer()
                } else if (!userToMove()) launchPlay { reply() }
                return
            }
            // The trainer lets you pick the other side's book move yourself, to explore that line.
            val chosen = bookReplyOnBoard(placement)
            if (chosen != null) {
                note("board played book move $chosen instead of the lit move $prompt")
                prompt = null
                apply(chosen)
                light(null)
                if (finished != null) {
                    celebrateAsync()
                    scoreTrainer()
                } else if (!userToMove()) launchPlay { reply() }
                return
            }
        }
        if (reviseSlide(placement)) return
        val waiting = prompt
        if (waiting != null) {
            if (PlayPosition.matches(fen, placement) || placement == ignorePlacement || busy) return
            if (PlayPosition.uciMatching(fen, placement) != null) {
                ignorePlacement = placement
                note("board moved, but not the lit move $waiting")
                status = "Play the lit move"
                publish()
                if (app.phoneSounds) sounds.illegal()
                viewModelScope.launch {
                    if (app.boardSounds) runCatching { app.boardSession?.beep(240, 220) }
                    light(waiting)
                }
            }
            return
        }
        if (busy || finished != null || !userToMove()) {
            if (busy && userToMove() && finished == null && placement != ignorePlacement) heldPlacement = placement
            return
        }
        if (placement == ignorePlacement) return
        if (PlayPosition.matches(fen, placement)) {
            if (retry || rejectUci != null) {
                retry = false
                rejectUci = null
                status = if (kind == PlayKind.Trainer) "Try a solid move" else "Your move"
                publish()
                light(null)
            }
            return
        }
        val uci = PlayPosition.uciMatching(fen, placement)
        if (uci == null) {
            note("board frame is not a legal move")
            return
        }
        note("board played $uci")
        pendingPlacement = placement
        playUci(uci)
    }

    /**
     * A slide can rest on a legal square and then continue. Until the reply is
     * played on the board, a later reading from the position before that move
     * replaces it.
     */
    private fun reviseSlide(placement: String): Boolean {
        val saved = revision ?: return false
        if (PlayPosition.matches(fen, placement)) return false
        if (PlayPosition.matches(saved.fen, placement)) {
            note("slide returned the piece")
            revert(saved)
            status = "Your move"
            publish()
            light(null)
            return true
        }
        val uci = PlayPosition.uciMatching(saved.fen, placement) ?: return false
        if (uci == uciMoves.lastOrNull()) return false
        note("slide continued, now $uci")
        revert(saved)
        pendingPlacement = placement
        playUci(uci)
        return true
    }

    private fun revert(saved: Revision) {
        val running = job
        job = null
        busy = false
        running?.cancel()
        prompt = null
        hint = null
        clockNoted = null
        finished = null
        selected = null
        glance = null
        clock.restore(saved.clock)
        fen = saved.fen
        while (sanMoves.size > saved.sans) sanMoves.removeAt(sanMoves.lastIndex)
        while (uciMoves.size > saved.ucis) uciMoves.removeAt(uciMoves.lastIndex)
        lastUci = saved.lastUci
        revision = null
    }

    private fun kickoff() {
        awaitingBoard = false
        if (kind == PlayKind.Bot) {
            if (timed) {
                clock.begin(System.currentTimeMillis(), whiteFirst = whiteToMove(fen))
                watchClock()
            }
            status = if (userWhite) "You have White · $elo" else "You have Black · $elo"
        } else if (kind == PlayKind.Trainer) {
            status = openingName ?: "Opening trainer"
            openingName?.let { tell(OpeningLore.of(it)) }
        }
        publish()
        if (finished == null && !userToMove()) launchPlay { reply() }
    }

    private suspend fun reply() {
        if (finished != null || userToMove()) return
        status = "Thinking…"
        publish()
        val uci = chooseReply() ?: run {
            status = "No move"
            publish()
            return
        }
        if (finished != null) return
        if (physical) {
            if (!chargeClock(uci)) return
            clockNoted = uci
            prompt = uci
            val san = PlayPosition.after(fen, uci)?.first ?: uci
            status = if (kind == PlayKind.Trainer && legalBookMove()) "Play $san, or any book move" else "Play $san on the board"
            glance = null
            publish()
            light(uci)
        } else {
            apply(uci)
            light(null)
            if (finished != null) celebrate()
        }
    }

    /**
     * The engine's top moves for the current position, once known. The first
     * call for a position starts the analysis and publishes again when done.
     */
    private fun engineIdeas(): List<Pair<String, String>> {
        if (ideasFen == fen) return ideas
        if (ChessLines.terminalOf(fen) != null) return emptyList()
        val target = fen
        if (ideasJob?.isActive == true && ideasTarget == target) return emptyList()
        ideasJob?.cancel()
        ideasTarget = target
        ideasJob = viewModelScope.launch {
            val found = runCatching { engine().analyseMillis(target, 400, MAX_BOOK_ARROWS) }
                .onFailure { note("ideas failed: ${it.message}") }
                .getOrDefault(emptyList())
                .mapNotNull { line -> line.uci.firstOrNull()?.let { uci -> PlayPosition.after(target, uci)?.first?.let { it to uci } } }
                .distinctBy { it.second }
            ideas = found
            ideasFen = target
            if (fen == target) publish()
        }
        return emptyList()
    }

    /** Says once when the game leaves the book. Coming back into the book (a slide undone) allows it again. */
    private fun announceTheory() {
        if (openingName == null || finished != null) return
        if (legalBookMove()) {
            leftTheory = false
            return
        }
        if (leftTheory) return
        leftTheory = true
        if (!app.phoneSounds) return
        speech.narrate(
            if (explain) "Out of theory. The arrows now show the engine's best moves." else "Out of theory. You're on your own now.",
        )
    }

    /** Shows [story] once per game when history is on, and speaks it when sound is on. */
    private fun tell(story: String?) {
        if (story == null || !state.value.history) return
        if (!told.add(story)) return
        this.story = story
        publish()
        if (app.phoneSounds) speech.narrate(story)
    }

    /** The next move of the line being drilled, while the game still follows it. */
    private fun drillMove(): String? {
        val line = drillLine ?: return null
        if (sanMoves.size >= line.size || line.take(sanMoves.size) != sanMoves) return null
        return PlayPosition.uciOfSan(fen, line[sanMoves.size])
    }

    /** Grades the drilled line once it is finished or left: clean only if every move was yours from the line. */
    private fun gradeDrill() {
        val line = drillLine ?: return
        if (drillGraded) return
        val onLine = line.take(sanMoves.size) == sanMoves.take(line.size)
        if (sanMoves.size < line.size && onLine) return
        drillGraded = true
        val now = System.currentTimeMillis()
        val clean = !drillMissed && onLine
        lineClean = clean
        // Where you left the line, and what it plays there, to learn from the miss.
        val split = sanMoves.indices.firstOrNull { it < line.size && sanMoves[it] != line[it] }
        val expected = split?.let { "${moveNumber(it)}${line[it]}" }
        val bookToo = split != null && !drillMissed
        val name = openingName
        if (survival) {
            // Survival doesn't touch the review schedule: the line just has to be known.
            if (clean) {
                lineNote = "Line played from memory. Now keep it going: book moves or the engine's top two."
            } else {
                survivalOver = true
                lineNote = "Off the line: it continues $expected. That ends this game."
            }
            publish()
            if (app.phoneSounds) speech.narrate(lineNote.orEmpty())
            return
        }
        // On the phone you can take the move back and play the line's; a physical board would need the pieces moved back.
        retryAt = split?.takeIf { !physical && (it % 2 == 0) == userWhite }
        // A different book move on your turn can become your move here.
        choiceOffer = if (drillBook && name != null && split != null && (split % 2 == 0) == userWhite &&
            sanMoves[split] in OpeningBook.replies(sanMoves.take(split), name)
        ) {
            sanMoves.take(split) to sanMoves[split]
        } else {
            null
        }
        val words = when {
            drillBook && explain -> if (onLine) {
                "End of this line. Turn off Explain to test yourself on it."
            } else {
                "You've left the main line, which continues $expected."
            }
            drillBook && name != null -> {
                val review = app.lineProgress.grade(name, userWhite, line, clean, now)
                val keys = drillLines.map(Drill::key).toSet()
                val learned = app.lineProgress.learned(name, userWhite).count { it in keys }
                val days = Drill.days(review, now)
                when {
                    clean -> "Nice one! Line learned: $learned of ${drillLines.size}. ${seeYou(days)}"
                    bookToo -> "That's a book move too, but this line continues $expected. Queuing this line for review."
                    expected != null -> "Good effort! This line continues $expected. Queuing this line for review."
                    else -> "Nearly there! It wasn't quite clean. Queuing this line for review."
                }
            }
            else -> {
                val review = app.repertoire.grade(line, clean, now)
                val days = Drill.days(review, now)
                when {
                    clean -> "Nice one! Line complete. ${seeYou(days)}"
                    expected != null -> "Good effort! Your repertoire continues $expected. Queuing this line for review."
                    else -> "Good effort! This one strayed from your repertoire. Queuing this line for review."
                }
            }
        }
        lineNote = words
        publish()
        if (app.phoneSounds) speech.narrate(words)
        refreshOpenings(state.value.openingQuery)
        refreshLines()
    }

    /** When a cleanly played line comes round again, said warmly. */
    private fun seeYou(days: Int): String = if (days <= 1) "We'll see it again tomorrow." else "We'll see it again in $days days."

    /** "5. " for White's move at [ply], "5… " for Black's. */
    private fun moveNumber(ply: Int): String = "${ply / 2 + 1}${if (ply % 2 == 0) ". " else "… "}"

    private fun bookLines(name: String, white: Boolean): List<List<String>> =
        synchronized(treeCache) {
            treeCache.getOrPut(name to white) { OpeningTree.lines(name, white, app.moveChoices.of(name, white)) }
        }

    /**
     * Takes the game back to where you left the line and lights the line's
     * move, so you play it straight away. The miss still counts.
     */
    fun retryLine() {
        val at = retryAt ?: return
        val line = drillLine ?: return
        val expected = line.getOrNull(at) ?: return
        job?.cancel()
        job = null
        busy = false
        var position = Board().fen
        val kept = uciMoves.take(at)
        for (uci in kept) position = PlayPosition.after(position, uci)?.second ?: return
        while (sanMoves.size > at) sanMoves.removeAt(sanMoves.lastIndex)
        while (uciMoves.size > at) uciMoves.removeAt(uciMoves.lastIndex)
        fen = position
        lastUci = kept.lastOrNull()
        finished = null
        prompt = null
        selected = null
        rejectUci = null
        retry = false
        glance = null
        retryAt = null
        choiceOffer = null
        ideaMove = null
        idea = null
        hint = PlayPosition.uciOfSan(fen, expected)
        status = "Play ${moveNumber(at)}$expected"
        publish()
    }

    /** Makes the offered book move your move in this opening, and rebuilds its lines around it. */
    fun adoptChoice() {
        val (before, move) = choiceOffer ?: return
        val name = openingName ?: return
        app.moveChoices.set(name, userWhite, before, move)
        synchronized(treeCache) { treeCache.remove(name to userWhite) }
        choiceOffer = null
        lineNote = "Your $name now plays ${moveNumber(before.size)}$move. Its lines are rebuilt around it."
        publish()
        refreshOpenings(state.value.openingQuery)
        refreshLines()
    }

    /** "Line 3 of 12 · new", for the drilled line. */
    private fun lineLabel(): String? {
        val line = drillLine ?: return null
        val name = openingName ?: return null
        val index = drillLines.indexOf(line)
        if (index < 0) return null
        val key = Drill.key(line)
        val reviews = if (drillBook) app.lineProgress.reviews(name, userWhite) else app.repertoire.reviews()
        val state = when {
            reviews[key] == null -> "new"
            drillBook && key in app.lineProgress.learned(name, userWhite) -> "review"
            else -> "practice"
        }
        return "Line ${index + 1} of ${drillLines.size} · $state"
    }

    /** Trainer, opponent to move: the book move (or, past the book, the explained idea) the board now shows. */
    private fun bookReplyOnBoard(placement: String): String? {
        if (kind != PlayKind.Trainer || userToMove()) return null
        val choices = legalBookChoices().ifEmpty { if (explain && ideasFen == fen) ideas.map { it.second } else emptyList() }
        return choices.firstOrNull { uci ->
            PlayPosition.after(fen, uci)?.second?.let { PlayPosition.matches(it, placement) } == true
        }
    }

    private suspend fun chooseReply(): String? {
        drillMove()?.let { return it }
        // Explaining lights the main line, matching the green arrow; any other book move is accepted too.
        if (kind == PlayKind.Trainer && explain) {
            legalBookChoices().firstOrNull()?.let { return it }
            if (legalBookChoices().isEmpty()) {
                // Wait for the arrows' analysis, so the lit move is the one marked Best.
                engineIdeas()
                if (ideasTarget == fen) ideasJob?.join()
                if (ideasFen == fen) ideas.firstOrNull()?.let { return it.second }
            }
        }
        bookMove()?.let { return it }
        val eng = engine()
        if (kind == PlayKind.Bot) eng.limitStrength(elo) else eng.limitStrength(null)
        val strength = if (kind == PlayKind.Bot) elo else 2200
        val millis = if (kind == PlayKind.Bot) 700L else 450L
        val lines = eng.analyseMillis(fen, millis, 4)
        return BotChooser.pick(lines, strength, whiteToMove(fen), random)
    }

    private suspend fun judge(uci: String): TrainerResult {
        val san = PlayPosition.after(fen, uci)?.first
        val book = san != null && openingName != null && san in OpeningBook.replies(sanMoves.toList(), openingName)
        if (book) return TrainerResult.Book
        val eng = engine()
        eng.limitStrength(null)
        lastJudgedFen = fen
        val top = eng.analyseMillis(fen, 400, 2)
        val before = top.firstOrNull()
        // The engine can report the same first move on both lines, so keep each move once.
        lastTopTwo = top.mapNotNull { it.uci.firstOrNull()?.lowercase() }.distinct()
        val beforeScore = before?.score ?: Score.Even
        val after = PlayPosition.after(fen, uci) ?: return TrainerGate.judge(false, false, 100.0)
        val whiteMoved = whiteToMove(fen)
        val afterScore = when (ChessLines.terminalOf(after.second)) {
            Terminal.Checkmate -> if (whiteMoved) Score.BlackMated else Score.WhiteMated
            Terminal.Stalemate -> Score.Even
            null -> eng.analyseMillis(after.second, 280, 1).firstOrNull()?.score ?: Score.Even
        }
        fun moverWin(score: Score) = if (whiteMoved) score.whiteWinPercent else 100.0 - score.whiteWinPercent
        lastWinDrop = moverWin(beforeScore) - moverWin(afterScore)
        return TrainerGate.judge(false, before?.uci?.firstOrNull() == uci, lastWinDrop)
    }

    private suspend fun engineBest(): String? {
        val eng = engine()
        eng.limitStrength(null)
        return eng.analyseMillis(fen, 400, 1).firstOrNull()?.uci?.firstOrNull()
    }

    /** A book move when one is legal here. A hint or reply must not leave the book while one exists. */
    private fun bookMove(): String? {
        val choices = legalBookChoices()
        if (choices.isEmpty()) return null
        return choices[random.nextInt(choices.size)]
    }

    private fun legalBookMove(): Boolean = legalBookChoices().isNotEmpty()

    private fun legalBookChoices(): List<String> = legalBookSteps().map { it.second }

    /** Legal book replies as (SAN, UCI), main line first. */
    private fun legalBookSteps(): List<Pair<String, String>> {
        if (kind != PlayKind.Trainer || openingName == null) return emptyList()
        return OpeningBook.replies(sanMoves.toList(), openingName)
            .mapNotNull { san -> PlayPosition.uciOfSan(fen, san)?.let { san to it } }
            .distinctBy { it.second }
    }

    private fun apply(uci: String): Boolean {
        val fromBoard = physical && userToMove()
        val shot = if (fromBoard) Revision(fen, sanMoves.size, uciMoves.size, lastUci, clock.shot()) else null
        if (!chargeClock(uci)) return false
        val before = fen
        val step = PlayPosition.after(fen, uci) ?: return false
        sanMoves += step.first
        uciMoves += uci
        fen = step.second
        lastUci = uci
        glance = null
        revision = if (fromBoard) shot else null
        hint = null
        prompt = null
        selected = null
        when (ChessLines.terminalOf(fen)) {
            Terminal.Checkmate -> {
                val whiteWon = !whiteToMove(fen)
                finished = if (whiteWon) "White wins" else "Black wins"
                status = if (whiteWon == userWhite) "Checkmate — you win" else "Checkmate"
            }
            Terminal.Stalemate -> {
                finished = "Draw"
                status = "Stalemate"
            }
            null -> status = step.first
        }
        if (kind == PlayKind.Bot && finished != null) clock.freeze(System.currentTimeMillis())
        publish()
        if (app.phoneSounds) {
            val mate = ChessLines.terminalOf(fen) == Terminal.Checkmate
            speech.say(step.first, mate = mate)
        }
        if (kind == PlayKind.Trainer && state.value.ideas) {
            val explained = MoveIdeas.explain(sanMoves, before, uci)
            ideaMove = "${moveNumber(sanMoves.lastIndex)}${step.first}"
            idea = explained
            publish()
            if (explained != null && app.phoneSounds) speech.narrate(explained)
        }
        if (kind == PlayKind.Trainer) {
            OpeningBook.view(sanMoves).name?.let { tell(OpeningLore.exact(it)) }
            gradeDrill()
            if (drillLine == null || !drillGraded) announceTheory()
        }
        return true
    }

    private suspend fun reject(uci: String, verdict: TrainerResult) {
        retry = true
        rejectUci = uci
        status = "${verdict.label} — try a different move"
        publish()
        if (app.phoneSounds) sounds.illegal()
        ignorePlacement = pendingPlacement
        if (app.boardSounds) runCatching { app.boardSession?.beep(240, 220) }
        light(uci)
    }

    private suspend fun celebrate() {
        val session = app.boardSession ?: return
        if (ChessLines.terminalOf(fen) != Terminal.Checkmate) return
        val whiteWon = !whiteToMove(fen)
        val half = LedCodec.winnerHalf(whiteWon)
        if (app.phoneSounds) sounds.checkmate()
        if (app.boardSounds) {
            runCatching { session.beep(1200, 140) }
            delay(80)
            runCatching { session.beep(1200, 220) }
        }
        repeat(5) {
            runCatching { session.setLeds(half) }
            delay(50)
            runCatching { session.setLeds(emptyList()) }
            delay(50)
        }
    }

    private fun celebrateAsync() {
        viewModelScope.launch { runCatching { celebrate() } }
    }

    /** Lights the board without holding the turn. A stalled radio used to leave the spinner up. */
    private fun light(uci: String?) =
        lightSquares(if (uci == null || uci.length < 4) emptyList() else listOf(uci.substring(0, 2), uci.substring(2, 4)))

    /** Names the squares still wrong for the start, when there are few enough to be useful. */
    private fun setupStatus(wrong: List<String>): String = when {
        wrong.isEmpty() || wrong.size > MAX_SETUP_HINTS -> "Set the pieces to the starting position."
        else -> "Almost set up. Check ${wrong.joinToString(", ")}. They are lit on the board."
    }

    private fun lightSquares(squares: List<String>) {
        if (app.boardSession == null) return
        viewModelScope.launch(Dispatchers.IO) {
            note("leds ${squares.joinToString().ifEmpty { "off" }}")
            val wrote = withTimeoutOrNull(2.seconds) {
                runCatching { app.boardSession?.setLeds(squares) }
                    .onFailure { note("leds failed: ${it.message}") }
                    .isSuccess
            }
            if (wrote == null) note("leds timed out")
        }
    }

    private fun note(message: String) {
        Log.i(TAG, message)
    }

    private fun watchClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                delay(200)
                if (!playing || kind != PlayKind.Bot || finished != null) break
                val flagged = clock.poll(System.currentTimeMillis())
                if (flagged != null) {
                    finishClock(flagged, "Flag")
                    break
                }
                publish()
            }
        }
    }

    private fun chargeClock(uci: String): Boolean {
        if (kind != PlayKind.Bot || !clock.started) return true
        if (clockNoted == uci) {
            clockNoted = null
            return true
        }
        val flagged = clock.moved(System.currentTimeMillis())
        if (flagged != null) {
            finishClock(flagged, "Flag")
            return false
        }
        return true
    }

    private fun finishClock(result: String, how: String) {
        clock.freeze(System.currentTimeMillis())
        finished = when (result) {
            "1-0" -> "White wins"
            "0-1" -> "Black wins"
            else -> "Draw"
        }
        val userWon = (result == "1-0" && userWhite) || (result == "0-1" && !userWhite)
        status = when (how) {
            "Flag" -> if (userWon) "Flag — you win" else "Flag"
            "Resign" -> "You resign"
            else -> "Draw agreed"
        }
        clockJob?.cancel()
        prompt = null
        hint = null
        publish()
    }

    private suspend fun engine(): UciEngine {
        engine?.let { return it }
        val created = withContext(Dispatchers.IO) {
            UciEngine(StockfishProcess.start(getApplication()), threads = 2)
        }
        created.start()
        engine = created
        return created
    }

    private fun launchPlay(block: suspend () -> Unit) {
        val previous = job
        busy = true
        // Start only after `job` is assigned. Main is immediate, so the body
        // otherwise finishes before the assignment and the turn stays busy.
        val launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                state.update { it.copy(thinking = true) }
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note("play failed: ${e.message}")
                status = e.message ?: "The engine stopped"
                publish()
            } finally {
                if (job === self) {
                    busy = false
                    job = null
                    state.update { it.copy(thinking = false) }
                    val held = heldPlacement
                    heldPlacement = null
                    if (held != null) onPhysical(held)
                }
            }
        }
        job = launched
        previous?.cancel()
        launched.start()
    }

    private fun refreshOpenings(query: String) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch(Dispatchers.Default) {
            val catalog = OpeningCatalog.browse(query)
            val names = catalog.flatMap { it.names }
            val played = app.games.value.mapNotNull { game ->
                val analysis = app.store.loadAnalysis(game.id) ?: return@mapNotNull null
                ScoredGame.of(game, analysis)
            }
            // Scores from trainer games the list has since let go.
            val kept = app.digests.all().mapNotNull { it.score }
            val scores = OpeningScores.bestOf(played + kept, names, app.myName)
            val filter = state.value.let {
                OpeningFilter(it.gambitsOnly, it.whiteOpenings, it.blackOpenings, it.unratedOnly, it.memesOnly)
            }
            val rates = names.associateWith { OpeningStats.winPercent(it, openingStats.record(it)) }
            val started = app.lineProgress.started()
            val side = state.value.userWhite
            val sections = catalog.mapNotNull { section ->
                val rows = OpeningStats.sorted(section.names, rates)
                    .filter { OpeningCatalog.matches(it, scores[it], filter) }
                    .map { name ->
                        val due = if (app.repertoire.isRepertoire(name)) app.repertoire.due(name, System.currentTimeMillis()) else null
                        // Book openings you have drilled show how many of their lines you know.
                        val progress = if (due == null && (name to side) in started) {
                            val keys = bookLines(name, side).map(Drill::key).toSet()
                            app.lineProgress.learned(name, side).count { it in keys } to keys.size
                        } else {
                            null
                        }
                        OpeningRow(
                            name, scores[name], rates[name], due, progress?.first, progress?.second,
                            depthBest = app.depthRecords.of(name, side)?.best,
                        )
                    }
                if (rows.isEmpty()) null else OpeningSection(section.title, rows)
            }
            // Book openings with reviews that have come due, first, so learned lines don't slip.
            val now = System.currentTimeMillis()
            val dueRows = if (query.isNotBlank()) emptyList() else started.filter { it.second == side }.mapNotNull { (name, white) ->
                val overdue = Drill.overdue(bookLines(name, white), app.lineProgress.reviews(name, white), now)
                if (overdue == 0) null else OpeningRow(name, scores[name], rates[name], due = overdue)
            }.sortedByDescending { it.due }
            val shown = if (dueRows.isEmpty()) sections else listOf(OpeningSection(DUE_SECTION, dueRows)) + sections
            // The summary counts every opening and side, not only the one shown.
            var learnedTotal = 0
            var dueTotal = 0
            for ((name, white) in started) {
                val lines = bookLines(name, white)
                val keys = lines.map(Drill::key).toSet()
                learnedTotal += app.lineProgress.learned(name, white).count { it in keys }
                dueTotal += Drill.overdue(lines, app.lineProgress.reviews(name, white), now)
            }
            state.update {
                it.copy(
                    openingQuery = query,
                    sections = shown,
                    learnedTotal = learnedTotal,
                    openingsStarted = started.size,
                    dueTotal = dueTotal,
                )
            }
            fetchOpeningStats()
        }
    }

    /** Fills in explorer win rates for the trainer's list in the background, when logged in to Lichess. */
    private fun fetchOpeningStats() {
        if (vault.token() == null || statsJob?.isActive == true) return
        val names = OpeningCatalog.browse("").flatMap { it.names }
        if (names.all { openingStats.record(it) != null }) return
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            openingStats.fill(names) { refreshOpenings(state.value.openingQuery) }
        }
    }

    private fun userToMove(): Boolean = whiteToMove(fen) == userWhite

    private fun whiteToMove(position: String): Boolean = position.split(' ').getOrNull(1) != "b"

    private data class Revision(
        val fen: String,
        val sans: Int,
        val ucis: Int,
        val lastUci: String?,
        val clock: PlayClock.Shot,
    )

    private companion object {
        const val TAG = "Play"

        /** Explain shows only the strongest few book moves, best first. */
        const val MAX_BOOK_ARROWS = 3

        const val DUE_SECTION = "Due for review"

        /** More wrong squares than this means the board is still being set up, so no hint. */
        const val MAX_SETUP_HINTS = 6
    }

    private fun shownPosition(): Pair<String, String?> {
        val at = glance ?: return fen to lastUci
        var position = origin()
        var last: String? = null
        for (uci in uciMoves.take(at)) {
            val step = PlayPosition.after(position, uci) ?: break
            position = step.second
            last = uci
        }
        return position to last
    }

    private fun glanceStatus(ply: Int): String {
        if (ply <= 0) return "Start"
        val san = sanMoves.getOrNull(ply - 1) ?: return "Start"
        val number = (ply + 1) / 2
        return if (ply % 2 == 1) "$number. $san" else "$number… $san"
    }

    /**
     * Evaluates the position for the eval bar while it is your move, when the
     * engine is otherwise idle. A search is never cut short, as that would
     * leave the engine mid-reply; a result for an old position is dropped.
     */
    private fun refreshEval() {
        if (kind != PlayKind.Trainer || !playing || evaluating || evalFen == fen || !userToMove()) return
        val target = fen
        if (ChessLines.terminalOf(target) != null) {
            evalFen = target
            evalScore = null
            return
        }
        evaluating = true
        viewModelScope.launch {
            val score = runCatching { engine().analyseMillis(target, 250, 1).firstOrNull()?.score }.getOrNull()
            evaluating = false
            if (fen == target) {
                evalFen = target
                evalScore = score
                publish()
            } else {
                refreshEval()
            }
        }
    }

    private fun publish() {
        refreshEval()
        val at = glance
        val (shownFen, shownLast) = shownPosition()
        val explained = if (at == null && kind == PlayKind.Trainer && explain && finished == null && !retry) {
            legalBookSteps().take(MAX_BOOK_ARROWS).ifEmpty { engineIdeas() }
        } else {
            emptyList()
        }
        state.update {
            it.copy(
                playing = playing,
                fen = shownFen,
                san = sanMoves.toList(),
                status = if (at == null) status else glanceStatus(at),
                hintUci = if (at == null) hint else null,
                // The book arrows already show the lit move when explaining.
                promptUci = if (at == null && explained.isEmpty()) prompt else null,
                rejectUci = if (at == null) rejectUci else null,
                physical = physical,
                awaitingBoard = awaitingBoard,
                retry = retry,
                finished = finished,
                controlLabel = if (timed) clock.control.label else NO_CLOCK,
                timed = timed,
                whiteMillis = clock.remaining(white = true, now = System.currentTimeMillis()),
                blackMillis = clock.remaining(white = false, now = System.currentTimeMillis()),
                whiteRunning = clock.running && clock.whiteToMove,
                blackRunning = clock.running && !clock.whiteToMove,
                flaggedWhite = clock.flaggedWhite,
                flaggedBlack = clock.flaggedBlack,
                selected = if (at == null) selected else null,
                targets = if (at == null) MoveInput.targets(fen, selected) else emptySet(),
                lastUci = shownLast,
                glancePly = at ?: sanMoves.size,
                moveCount = sanMoves.size,
                browsing = at != null,
                inTheory = kind != PlayKind.Trainer || openingName == null || legalBookMove(),
                explain = explain,
                bookUcis = explained.map { it.second },
                bookSans = explained.map { it.first },
                explainedByEngine = explained.isNotEmpty() && legalBookSteps().isEmpty(),
                lineLabel = if (kind == PlayKind.Trainer) lineLabelShown else null,
                lineDone = kind == PlayKind.Trainer && drillGraded,
                lineClean = lineClean,
                survival = survival,
                survivalOver = survivalOver,
                // The bar shows the live game; when stepping back through it there is no evaluation to show.
                evalScore = if (kind == PlayKind.Trainer && at == null) evalScore else null,
                evalTerminal = if (kind == PlayKind.Trainer && at == null) ChessLines.terminalOf(fen) else null,
                lineNote = if (kind == PlayKind.Trainer) lineNote else null,
                reviewing = reviewing,
                depthRun = if (kind == PlayKind.Trainer) depthRun else null,
                depthOver = depthOver,
                depthBest = if (survival) lineBest else depthBest,
                reviewLast = reviewing && reviewQueue.isEmpty(),
                choiceOffer = choiceOffer?.let { (before, move) -> "${moveNumber(before.size)}$move" },
                retryMove = retryAt?.let { at -> drillLine?.getOrNull(at)?.let { "${moveNumber(at)}$it" } },
                variation = if (kind == PlayKind.Trainer) OpeningBook.view(sanMoves).name else null,
                ideaMove = if (kind == PlayKind.Trainer && at == null) ideaMove else null,
                idea = if (kind == PlayKind.Trainer && at == null) idea else null,
                story = if (kind == PlayKind.Trainer) story else null,
            )
        }
    }
}
