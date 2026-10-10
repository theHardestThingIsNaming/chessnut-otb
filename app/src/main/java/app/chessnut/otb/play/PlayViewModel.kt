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

enum class PlayKind { Bot, Trainer }

/** [winRate] is how often the opening's side wins on Lichess, as a percentage, once fetched. */
data class OpeningRow(
    val name: String,
    val accuracy: Int?,
    val winRate: Int? = null,
    val due: Int? = null,
    /** Lines learned and lines in all, once you have drilled this opening from the book. */
    val learned: Int? = null,
    val lines: Int? = null,
)

data class OpeningSection(val title: String, val rows: List<OpeningRow>)

enum class LineStatus { New, Learned, Due, Missed }

/** One line of the chosen opening, for the line list: its variation name, its moves past the opening, and how you are doing with it. */
data class LineView(val name: String, val moves: String, val status: LineStatus)

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
    /** How the drilled line went, kept on screen while play goes on. */
    val lineNote: String? = null,
    /** Every line of the chosen opening, for picking one to drill. */
    val lines: List<LineView> = emptyList(),
    /** A book move you played instead of the line's, offered as your move from now on, such as "4. c3". */
    val choiceOffer: String? = null,
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
    /** A line picked from the list, drilled next instead of the scheduled one. */
    private var pinnedLine: List<String>? = null
    /** The moves before, and the book move you chose there, when it differs from the line. */
    private var choiceOffer: Pair<List<String>, String>? = null
    private var linesJob: Job? = null
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
        clock.setControl(TimeControl.fromLabel(label), System.currentTimeMillis())
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
                LineView(variation ?: "Main line", numberedFrom(line, shared), status)
            }
            state.update { if (it.openingName == name && it.userWhite == white) it.copy(lines = views) else it }
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
        fen = Board().fen
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
        lineLabelShown = lineLabel()
        lineNote = null
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

    fun leave() {
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
        if (!playing || awaitingBoard || busy || finished != null || prompt != null) {
            note("move $uci ignored playing=$playing awaiting=$awaitingBoard busy=$busy prompt=$prompt")
            return
        }
        if (!userToMove()) return
        launchPlay {
            val verdict = if (kind == PlayKind.Trainer) judge(uci) else null
            if (drillLine != null && verdict != null && verdict != TrainerResult.Book) drillMissed = true
            if (verdict != null && explain && !verdict.accepted) {
                reject(uci, verdict)
                return@launchPlay
            }
            if (!apply(uci)) return@launchPlay
            retry = false
            rejectUci = null
            if (verdict != null && finished == null) {
                status = verdict.label
                publish()
            }
            light(null)
            if (finished != null) {
                celebrate()
                scoreTrainer()
                return@launchPlay
            }
            if (!userToMove()) reply()
        }
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
            delay(200)
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
            clock.begin(System.currentTimeMillis())
            watchClock()
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

    /** Speaks [story] once per game, when history is on. */
    private fun tell(story: String?) {
        if (story == null || !state.value.history || !app.phoneSounds) return
        if (told.add(story)) speech.narrate(story)
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
        // Where you left the line, and what it plays there, to learn from the miss.
        val split = sanMoves.indices.firstOrNull { it < line.size && sanMoves[it] != line[it] }
        val expected = split?.let { "${moveNumber(it)}${line[it]}" }
        val bookToo = split != null && !drillMissed
        val name = openingName
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
                "This line plays $expected. You'll see it again."
            }
            drillBook && name != null -> {
                val review = app.lineProgress.grade(name, userWhite, line, clean, now)
                val keys = drillLines.map(Drill::key).toSet()
                val learned = app.lineProgress.learned(name, userWhite).count { it in keys }
                val days = Drill.days(review, now)
                when {
                    clean -> "Line learned: $learned of ${drillLines.size}. Next review in $days ${if (days == 1) "day" else "days"}."
                    bookToo -> "That's book too, but this line plays $expected. It comes back tomorrow."
                    expected != null -> "This line plays $expected. It comes back tomorrow."
                    else -> "Not quite clean, so this line comes back tomorrow."
                }
            }
            else -> {
                val review = app.repertoire.grade(line, clean, now)
                val days = Drill.days(review, now)
                when {
                    clean -> "Line complete. Next review in $days ${if (days == 1) "day" else "days"}."
                    expected != null -> "Your repertoire plays $expected, so this line comes back tomorrow."
                    else -> "You strayed from your repertoire, so this line comes back tomorrow."
                }
            }
        }
        lineNote = words
        publish()
        if (app.phoneSounds) speech.narrate(words)
        refreshOpenings(state.value.openingQuery)
        refreshLines()
    }

    /** "5. " for White's move at [ply], "5… " for Black's. */
    private fun moveNumber(ply: Int): String = "${ply / 2 + 1}${if (ply % 2 == 0) ". " else "… "}"

    private fun bookLines(name: String, white: Boolean): List<List<String>> =
        synchronized(treeCache) {
            treeCache.getOrPut(name to white) { OpeningTree.lines(name, white, app.moveChoices.of(name, white)) }
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
        val before = eng.analyseMillis(fen, 280, 1).firstOrNull()
        val beforeScore = before?.score ?: Score.Even
        val after = PlayPosition.after(fen, uci) ?: return TrainerGate.judge(false, false, 100.0)
        val whiteMoved = whiteToMove(fen)
        val afterScore = when (ChessLines.terminalOf(after.second)) {
            Terminal.Checkmate -> if (whiteMoved) Score.BlackMated else Score.WhiteMated
            Terminal.Stalemate -> Score.Even
            null -> eng.analyseMillis(after.second, 280, 1).firstOrNull()?.score ?: Score.Even
        }
        fun moverWin(score: Score) = if (whiteMoved) score.whiteWinPercent else 100.0 - score.whiteWinPercent
        return TrainerGate.judge(false, before?.uci?.firstOrNull() == uci, moverWin(beforeScore) - moverWin(afterScore))
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
        if (kind == PlayKind.Trainer && state.value.ideas && app.phoneSounds) {
            MoveIdeas.explain(sanMoves, before, uci)?.let(speech::narrate)
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
                        OpeningRow(name, scores[name], rates[name], due, progress?.first, progress?.second)
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
            state.update { it.copy(openingQuery = query, sections = shown) }
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
        var position = Board().fen
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

    private fun publish() {
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
                controlLabel = clock.control.label,
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
                lineNote = if (kind == PlayKind.Trainer) lineNote else null,
                choiceOffer = choiceOffer?.let { (before, move) -> "${moveNumber(before.size)}$move" },
            )
        }
    }
}
