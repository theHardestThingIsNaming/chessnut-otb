package app.chessnut.otb

import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.lichess.Json
import app.chessnut.otb.lichess.JsonLite
import app.chessnut.otb.openings.OpeningTraps
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test

/**
 * Not a check: a tool. Builds the puzzle sets the app ships, from the Lichess
 * puzzle database (CC0), in `data-cache/puzzles.csv`:
 *
 * - Middlegame tactics for each opening in EXPORT_OPENINGS ("Scotch Game|w,Pirc Defense|b"):
 *   puzzles tagged with the opening and `middlegame`, where your opponent errs and you
 *   punish, whose game started in your lines. Games are fetched from Lichess in bulk and
 *   cached in `data-cache/games.ndjson`.
 * - Endgame puzzles by type, spread over ratings.
 *
 * Writes `core/src/main/resources/puzzles-middlegame.tsv` and `puzzles-endgame.tsv`.
 * Run: EXPORT_OPENINGS="Scotch Game|w,Pirc Defense|b" ./gradlew :core:test --tests '*PuzzleExportTest*' --rerun-tasks
 * EXPORT_CAP raises how many of the most-played puzzles are checked (default 1500).
 * EXPORT_ENDGAMES=skip leaves the endgame file alone.
 * EXPORT_MIN_RATING (default 900) and EXPORT_MIN_PLIES (default 2: the opponent's move and one of yours)
 * narrow the middlegame tactics to harder, longer ones; EXPORT_MIN_PLIES=6 asks for three moves of yours.
 */
class PuzzleExportTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() || File(it, "settings.gradle").exists() }
    private val cache = File(root, "data-cache")
    private val resources = File(root, "core/src/main/resources")

    private class Row(val fields: List<String>) {
        val id get() = fields[0]
        val fen get() = fields[1]
        val moves get() = fields[2]
        val rating get() = fields[3].toInt()
        val popularity get() = fields[5].toInt()
        val plays get() = fields[6].toInt()
        val themes get() = fields[7]
        val gameUrl get() = fields[8]
        val tags get() = fields[9]
        val whiteToMove get() = fen.split(' ')[1] == "w"
    }

    @Test
    fun export() {
        val spec = System.getenv("EXPORT_OPENINGS") ?: return
        val openings = spec.split(',').map { it.split('|') }.map { (name, side) -> name to (side == "w") }
        val tags = openings.associate { (name, _) -> name to tagOf(name) }
        val middle = openings.associateWith { mutableListOf<Row>() }
        val endgames = ENDGAME_TYPES.associateWith { mutableListOf<Row>() }
        File(cache, "puzzles.csv").useLines { lines ->
            lines.drop(1).forEach { line ->
                val row = Row(line.split(','))
                if (row.fields.size < 10) return@forEach
                val themes = row.themes.split(' ')
                val minRating = System.getenv("EXPORT_MIN_RATING")?.toIntOrNull() ?: 900
                val minPlies = System.getenv("EXPORT_MIN_PLIES")?.toIntOrNull() ?: 2
                val longEnough = row.moves.split(' ').size >= minPlies
                if ("middlegame" in themes && row.popularity >= 80 && row.plays >= 300 && row.rating in minRating..2300 && longEnough) {
                    for ((opening, white) in openings) {
                        // Your opponent moves first in a puzzle: Black's move when you are White.
                        if (row.whiteToMove == white) continue
                        if (row.tags.split(' ').none { it == tags[opening] || it.startsWith(tags[opening] + "_") }) continue
                        middle.getValue(opening to white) += row
                    }
                }
                if ("endgame" in themes && "veryLong" !in themes && row.popularity >= 85 && row.plays >= 1000 && row.rating in 1800..2400) {
                    ENDGAME_TYPES.firstOrNull { it in themes }?.let { endgames.getValue(it) += row }
                }
            }
        }
        val out = StringBuilder()
        for ((key, rows) in middle) {
            val (opening, white) = key
            val lines = OpeningTree.lines(opening, white)
            val cap = System.getenv("EXPORT_CAP")?.toIntOrNull() ?: 1500
            val candidates = rows.sortedByDescending { it.plays }.take(cap)
            val games = games(candidates.mapNotNull { gameId(it.gameUrl) })
            val kept = candidates.mapNotNull { row ->
                val moves = games[gameId(row.gameUrl)] ?: return@mapNotNull null
                val ply = plyOf(row.fen)
                if (moves.size < ply) return@mapNotNull null
                val before = moves.take(ply)
                val replay = runCatching { ChessLines.replay(before) }.getOrNull() ?: return@mapNotNull null
                if (replay.fens.last().split(' ')[0] != row.fen.split(' ')[0]) return@mapNotNull null
                // The Bishop's model lines run on to move 8, and puzzle games leave before that.
                // Matching the first three moves of each side is enough: the game began in the line.
                val fitLines = if (opening == "Bishop's Opening") lines.map { it.take(6) } else lines
                if (!OpeningTraps.fits(before, fitLines, white, reach = Int.MAX_VALUE, minShared = 6)) return@mapNotNull null
                row to ply
            }
            val picked = spread(kept.map { it.first }, PER_OPENING)
            println("$opening: ${rows.size} tagged, ${kept.size} from your lines, ${picked.size} kept")
            for (row in picked) {
                val ply = kept.first { it.first === row }.second
                out.append(listOf(opening, if (white) "w" else "b", row.id, row.rating, row.themes, row.fen, row.moves, ply / 2 + 1).joinToString("\t")).append('\n')
            }
        }
        File(resources, "puzzles-middlegame.tsv").writeText(out.toString())
        val end = StringBuilder()
        for ((type, rows) in endgames) {
            val picked = spread(rows, PER_ENDGAME)
            println("$type: ${rows.size} candidates, ${picked.size} kept")
            for (row in picked) end.append(listOf(type, row.id, row.rating, row.themes, row.fen, row.moves).joinToString("\t")).append('\n')
        }
        if (System.getenv("EXPORT_ENDGAMES") != "skip") File(resources, "puzzles-endgame.tsv").writeText(end.toString())
    }

    /** Up to [count] puzzles spread evenly over the ratings, the most played in each band. */
    private fun spread(rows: List<Row>, count: Int): List<Row> {
        if (rows.isEmpty()) return emptyList()
        val bands = rows.groupBy { it.rating / 150 }.toSortedMap()
        val each = (count + bands.size - 1) / bands.size
        return bands.values.flatMap { band -> band.sortedByDescending { it.plays }.distinctBy { it.fen }.take(each) }
            .sortedBy { it.rating }
            .take(count)
    }

    /** Game moves by id, fetched from Lichess 300 at a time and cached. */
    private fun games(ids: List<String>): Map<String, List<String>> {
        val file = File(cache, "games.ndjson")
        val known = mutableMapOf<String, List<String>>()
        if (file.exists()) file.forEachLine { line -> parseGame(line)?.let { known[it.first] = it.second } }
        val missing = ids.distinct().filter { it !in known }
        for (batch in missing.chunked(300)) {
            var body: String? = null
            for (attempt in 1..3) {
                val connection = URL("https://lichess.org/api/games/export/_ids?moves=true&tags=false&clocks=false&evals=false&opening=false")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/x-ndjson")
                connection.outputStream.use { it.write(batch.joinToString(",").toByteArray()) }
                if (connection.responseCode == 200) {
                    body = connection.inputStream.bufferedReader().readText()
                    break
                }
                Thread.sleep(65_000)
            }
            val text = body ?: continue
            file.appendText(text.trimEnd() + "\n")
            text.lines().forEach { line -> parseGame(line)?.let { known[it.first] = it.second } }
            Thread.sleep(2_000)
        }
        return known
    }

    private fun parseGame(line: String): Pair<String, List<String>>? {
        val game = runCatching { JsonLite.parse(line) as? Json.Obj }.getOrNull() ?: return null
        val id = game.str("id") ?: return null
        return id to game.str("moves").orEmpty().split(' ').filter { it.isNotBlank() }
    }

    private fun gameId(url: String) = Regex("lichess.org/([A-Za-z0-9]{8})").find(url)?.groupValues?.get(1)

    private fun plyOf(fen: String): Int {
        val parts = fen.split(' ')
        return (parts[5].toInt() - 1) * 2 + if (parts[1] == "b") 1 else 0
    }

    /** The Lichess opening tag for a catalog name: "King's Indian Defense" is Kings_Indian_Defense. */
    private fun tagOf(name: String) = name.replace("'", "").replace(' ', '_')

    private companion object {
        val ENDGAME_TYPES = listOf("pawnEndgame", "rookEndgame", "knightEndgame", "bishopEndgame", "queenEndgame", "queenRookEndgame")
        const val PER_OPENING = 60
        const val PER_ENDGAME = 15
    }
}
