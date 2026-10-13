package app.chessnut.otb.games

import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.analysis.MoveClass
import app.chessnut.otb.mistakes.Mistake
import app.chessnut.otb.mistakes.MistakeDeck
import app.chessnut.otb.play.ScoredGame
import java.io.File
import java.util.Properties

/**
 * What the app keeps from a game after the game itself is cleared away: its
 * positions to practise and what it scored in its opening.
 */
data class GameDigest(
    val gameId: String,
    val createdAt: Long,
    val score: ScoredGame?,
    val mistakes: List<Mistake>,
) {
    val empty: Boolean get() = score == null && mistakes.isEmpty()

    companion object {
        fun of(game: SavedGame, analysis: GameAnalysis?, myNames: List<String>): GameDigest {
            val score = analysis?.let { ScoredGame.of(game, it) }
            val mistakes = analysis?.let { MistakeDeck.collect(game, it, myNames) }.orEmpty()
            // Opening scores only look at the first moves, so the rest of the game can go.
            val short = score?.copy(sanMoves = score.sanMoves.take(OPENING_PLIES))
            return GameDigest(game.id, game.createdAt, short, mistakes)
        }

        const val OPENING_PLIES = 40
    }
}

/** One properties file per digest, named by game id. */
class DigestStore(private val root: File) {
    init {
        root.mkdirs()
    }

    @Synchronized
    fun save(digest: GameDigest) {
        if (digest.empty) return
        val props = Properties()
        props["id"] = digest.gameId
        props["createdAt"] = digest.createdAt.toString()
        digest.score?.let { score ->
            props["white"] = score.white
            props["black"] = score.black
            props["moves"] = score.sanMoves.joinToString(" ")
            score.whiteAccuracy?.let { props["whiteAccuracy"] = it.toString() }
            score.blackAccuracy?.let { props["blackAccuracy"] = it.toString() }
        }
        props["mistakes"] = digest.mistakes.size.toString()
        digest.mistakes.forEachIndexed { i, m ->
            props["mistake.$i.ply"] = m.ply.toString()
            props["mistake.$i.fen"] = m.fen
            props["mistake.$i.played"] = m.playedSan
            m.bestUci?.let { props["mistake.$i.best"] = it }
            props["mistake.$i.class"] = m.classification.name
            props["mistake.$i.whiteToMove"] = m.whiteToMove.toString()
            props["mistake.$i.title"] = m.gameTitle
            m.lastUci?.let { props["mistake.$i.last"] = it }
            m.winBefore?.let { props["mistake.$i.winBefore"] = it.toString() }
            m.winDrop?.let { props["mistake.$i.winDrop"] = it.toString() }
        }
        val target = File(root, "${digest.gameId}.properties")
        val temp = File(root, "${digest.gameId}.properties.tmp")
        temp.writer().use { props.store(it, null) }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    @Synchronized
    fun all(): List<GameDigest> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".properties") }
            .mapNotNull { file -> runCatching { read(file) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    private fun read(file: File): GameDigest {
        val props = Properties().apply { file.reader().use { load(it) } }
        val id = props.getProperty("id")
        val moves = props.getProperty("moves")
        val score = moves?.let {
            ScoredGame(
                white = props.getProperty("white", ""),
                black = props.getProperty("black", ""),
                sanMoves = it.split(' ').filter { san -> san.isNotBlank() },
                whiteAccuracy = props.getProperty("whiteAccuracy")?.toDoubleOrNull(),
                blackAccuracy = props.getProperty("blackAccuracy")?.toDoubleOrNull(),
            )
        }
        val count = props.getProperty("mistakes", "0").toInt()
        val mistakes = (0 until count).map { i ->
            Mistake(
                gameId = id,
                ply = props.getProperty("mistake.$i.ply").toInt(),
                fen = props.getProperty("mistake.$i.fen"),
                playedSan = props.getProperty("mistake.$i.played"),
                bestUci = props.getProperty("mistake.$i.best"),
                classification = MoveClass.valueOf(props.getProperty("mistake.$i.class")),
                whiteToMove = props.getProperty("mistake.$i.whiteToMove").toBoolean(),
                gameTitle = props.getProperty("mistake.$i.title", ""),
                lastUci = props.getProperty("mistake.$i.last"),
                winBefore = props.getProperty("mistake.$i.winBefore")?.toDoubleOrNull(),
                winDrop = props.getProperty("mistake.$i.winDrop")?.toDoubleOrNull(),
            )
        }
        return GameDigest(id, props.getProperty("createdAt").toLong(), score, mistakes)
    }
}

/**
 * Keeps the main game list short. The newest [keep] games outside folders
 * stay; older ones are deleted once their digest is saved. Games in folders
 * are never touched, and neither is anything in [protect].
 *
 * A game with moves but no analysis yet is kept for up to [ANALYSIS_GRACE_MS]
 * so its mistakes can be collected first.
 */
class GameKeeper(private val store: GameStore, private val digests: DigestStore) {
    fun tidy(now: Long, myNames: List<String>, protect: Set<String> = emptySet(), keep: Int = 5): List<String> {
        val removed = mutableListOf<String>()
        for (game in store.unfiled().sortedByDescending { it.createdAt }.drop(keep)) {
            if (game.id in protect) continue
            val analysis = store.loadAnalysis(game.id)
            if (analysis == null && game.sanMoves.size >= 2 && now - game.createdAt < ANALYSIS_GRACE_MS) continue
            digests.save(GameDigest.of(game, analysis, myNames))
            store.delete(game.id)
            removed += game.id
        }
        return removed
    }

    /**
     * Lets go of games in [folder] older than [maxAgeMs], such as imported Chess.com games past
     * the 30 days a re-import would fetch. Like [tidy], each is digested first, so its mistakes
     * and opening score stay; anything in [protect], or not yet analysed, is left alone.
     */
    fun tidyFolder(folder: String, now: Long, maxAgeMs: Long, myNames: List<String>, protect: Set<String> = emptySet()): List<String> {
        val removed = mutableListOf<String>()
        for (game in store.inFolder(folder)) {
            if (now - game.createdAt <= maxAgeMs || game.id in protect) continue
            val analysis = store.loadAnalysis(game.id)
            if (analysis == null && game.sanMoves.size >= 2 && now - game.createdAt < maxAgeMs + ANALYSIS_GRACE_MS) continue
            digests.save(GameDigest.of(game, analysis, myNames))
            store.delete(game.id)
            removed += game.id
        }
        return removed
    }

    companion object {
        const val ANALYSIS_GRACE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
