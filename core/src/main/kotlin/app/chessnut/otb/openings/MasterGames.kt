package app.chessnut.otb.openings

/**
 * A master game that reached the end of one of your lines, for guessing the master's moves from there.
 * [start] is the ply where the game reached the line's last position; the guessing runs to [end].
 */
data class MasterGame(
    val id: String,
    val opening: String,
    val userWhite: Boolean,
    /** The drilled line it follows, in SAN. */
    val line: List<String>,
    val white: String,
    val black: String,
    val year: String,
    val event: String,
    val result: String,
    /** Ratings at the time, when known (not for 1840s games, say). */
    val whiteElo: Int? = null,
    val blackElo: Int? = null,
    val moves: List<String>,
    val start: Int,
    val end: Int,
    /** A sentence or two on the game, shown before the first move. */
    val intro: String,
    /** Why each move in the guessing stretch was played, by ply. */
    val notes: Map<Int, String>,
    /** On your plies: other moves the engine rates about as highly as the master's, in UCI. */
    val good: Map<Int, Set<String>>,
    /** On the opponent's plies: the threat the move makes, in UCI. */
    val threats: Map<Int, String>,
) {
    /** "Carlsen–Aronian, Wijk aan Zee 2011": surnames, then where and when. */
    val title: String get() = "${surname(white)}–${surname(black)}, $year"

    /** The player whose moves you guess. */
    val master: String get() = surname(if (userWhite) white else black)

    /** Plies in the guessing stretch where it's your side to move. */
    val yourPlies: List<Int> get() = (start until end).filter { (it % 2 == 0) == userWhite }

    /** "Carlsen" from "Magnus Carlsen" (or from "Carlsen, Magnus"). */
    private fun surname(name: String) = if (',' in name) name.substringBefore(',').trim() else name.trim().substringAfterLast(' ')

    /** "Magnus Carlsen · 2814", or just the name when the rating isn't known. */
    fun player(white: Boolean): String {
        val name = if (white) this.white else black
        val elo = if (white) whiteElo else blackElo
        return elo?.let { "$name · $it" } ?: name
    }
}

/** How a guess compares with the master's move. */
enum class Guess { Match, Good, Miss }

object MasterGames {
    private val ALL: List<MasterGame> by lazy { load() }

    val all: List<MasterGame> get() = ALL

    /** The master game that follows [line], if there is one. */
    fun forLine(opening: String, white: Boolean, line: List<String>): MasterGame? =
        ALL.firstOrNull { it.opening == opening && it.userWhite == white && it.line == line }

    fun byId(id: String): MasterGame? = ALL.firstOrNull { it.id == id }

    /** Your move [uci] at [ply], against the master's [masterUci]. */
    fun judge(game: MasterGame, ply: Int, uci: String, masterUci: String): Guess = when {
        uci.equals(masterUci, ignoreCase = true) -> Guess.Match
        uci.lowercase() in game.good[ply].orEmpty() -> Guess.Good
        else -> Guess.Miss
    }

    /**
     * Reads `master-games.tsv`. A GAME row carries the game; NOTE rows carry a ply's note and,
     * for your plies, the equally good moves (`good:e2e4,d2d4`) or, for the opponent's, the threat (`threat:d8h4`).
     */
    private fun load(): List<MasterGame> {
        val rows = MasterGames::class.java.getResourceAsStream("/master-games.tsv")?.bufferedReader()?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
        val notes = rows.filter { it[0] == "NOTE" }.groupBy { it[1] }
        return rows.filter { it[0] == "GAME" }.map { g ->
            val id = g[1]
            val mine = notes[id].orEmpty()
            MasterGame(
                id = id,
                opening = g[2],
                userWhite = g[3] == "w",
                line = g[4].split(' '),
                white = g[5],
                black = g[6],
                year = g[7],
                event = g[8],
                result = g[9],
                start = g[10].toInt(),
                end = g[11].toInt(),
                moves = g[12].split(' '),
                intro = g[13],
                whiteElo = g.getOrNull(14)?.toIntOrNull(),
                blackElo = g.getOrNull(15)?.toIntOrNull(),
                notes = mine.associate { it[2].toInt() to it[3] },
                good = mine.mapNotNull { n -> n.getOrNull(4)?.takeIf { it.startsWith("good:") }?.let { n[2].toInt() to it.removePrefix("good:").split(',').filter(String::isNotBlank).toSet() } }.toMap(),
                threats = mine.mapNotNull { n -> n.getOrNull(4)?.takeIf { it.startsWith("threat:") }?.let { n[2].toInt() to it.removePrefix("threat:") } }.toMap(),
            )
        }
    }
}
