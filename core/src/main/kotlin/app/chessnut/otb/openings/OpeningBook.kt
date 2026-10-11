package app.chessnut.otb.openings

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

data class Opening(val eco: String, val name: String, val san: List<String>) {
    val isGambit: Boolean get() = name.contains("gambit", ignoreCase = true)
}

enum class OpeningSide { White, Black }

/**
 * Lichess opening book (CC0). [name] is the longest named line that is a prefix of [san].
 * [gambits] are named gambit lines that continue the current position in one move.
 */
data class OpeningView(val name: String?, val eco: String?, val gambits: List<Opening>)

object OpeningBook {
    private val base: List<Opening> by lazy { load() }

    /** Lines added while the app runs, such as an imported repertoire. Book names still win. */
    @Volatile private var extra: List<Opening> = emptyList()
    @Volatile private var sides: Map<String, OpeningSide> = emptyMap()
    @Volatile private var merged: Pair<List<Opening>, Map<List<String>, Opening>>? = null

    private val lines: List<Opening> get() = index().first
    private val byPrefix: Map<List<String>, Opening> get() = index().second

    private fun index(): Pair<List<Opening>, Map<List<String>, Opening>> =
        merged ?: (extra + base).let { all -> (all to all.associateBy { it.san }).also { merged = it } }

    /** Replaces the added lines. [side] says who plays each added opening, by name. */
    fun setExtra(openings: List<Opening>, side: Map<String, OpeningSide>) {
        extra = openings
        sides = side
        merged = null
    }

    fun view(san: List<String>): OpeningView {
        var best: Opening? = null
        for (n in san.size downTo 1) {
            val hit = byPrefix[san.take(n)]
            if (hit != null) {
                best = hit
                break
            }
        }
        val gambits = if (san.size >= 24) emptyList() else lines.filter { line ->
            line.isGambit && line.san.size == san.size + 1 && line.san.dropLast(1) == san
        }.distinctBy { it.name }
        return OpeningView(best?.name, best?.eco, gambits)
    }

    /** Shortest book line for each well-known opening, in a stable order. */
    fun featured(): List<Opening> = FEATURED.mapNotNull { name -> shortest(name) }

    /** Distinct opening names matching [query]. An empty query returns [featured]. */
    fun search(query: String): List<Opening> {
        val q = query.trim()
        if (q.isEmpty()) return featured()
        return lines.asSequence()
            .filter { it.name.contains(q, ignoreCase = true) || it.eco.equals(q, ignoreCase = true) }
            .groupBy { it.name }
            .map { (_, group) -> group.minBy { it.san.size } }
            .sortedBy { it.name }
            .take(40)
            .toList()
    }

    /**
     * Next book moves from [played]. When [family] is set, only lines of that
     * opening (and its named variations) are included.
     */
    fun replies(played: List<String>, family: String? = null): List<String> =
        rankedReplies(played, family)

    /**
     * Next book moves from [played], main line first. A move that more named
     * lines continue through ranks above a sideline.
     */
    fun rankedReplies(played: List<String>, family: String? = null): List<String> {
        val counts = linkedMapOf<String, Int>()
        for (line in lines) {
            if (line.san.size <= played.size) continue
            if (line.san.take(played.size) != played) continue
            if (family != null && !inFamily(line.name, family)) continue
            val move = line.san[played.size]
            counts[move] = (counts[move] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** Shortest book line whose name is exactly [name]. */
    fun line(name: String): Opening? = shortest(name)

    /**
     * Who the opening belongs to. Accepted and Declined are still the gambit
     * that was offered: Black taking on f4 does not make the King's Gambit a
     * Black opening. A countergambit belongs to the side that plays it.
     */
    fun side(name: String): OpeningSide? {
        sides[name]?.let { return it }
        // A Defense is Black's, however many moves its named line runs to.
        val family = name.substringBefore(':')
        if (family.contains("Defense", ignoreCase = true) || family.contains("Defence", ignoreCase = true)) return OpeningSide.Black
        val san = line(basis(name))?.san?.takeIf { it.isNotEmpty() } ?: return null
        return if (san.lastIndex % 2 == 0) OpeningSide.White else OpeningSide.Black
    }

    private fun basis(name: String): String {
        if (name.contains("Countergambit", ignoreCase = true) || name.contains("Counterattack", ignoreCase = true)) return name
        val cut = listOf(" Accepted", " Declined").map { name.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return name
        val parent = name.substring(0, cut).trim()
        return if (parent.isNotEmpty() && line(parent) != null) parent else name
    }

    private fun shortest(name: String): Opening? =
        lines.filter { it.name == name }.minByOrNull { it.san.size }

    /** "King's Gambit Accepted: …" belongs to the King's Gambit, as "Sicilian Defense: Najdorf" does to the Sicilian. */
    private fun inFamily(name: String, family: String): Boolean =
        name == family || name.startsWith("$family:") || name.startsWith("$family,") || name.startsWith("$family ")

    /**
     * Our own lines (meme openings the Lichess book lacks or names differently)
     * come first, so where a position has both names the book's official one wins.
     */
    private fun load(): List<Opening> = read("/openings-custom.tsv") + read("/openings.tsv")

    private fun read(resource: String): List<Opening> {
        val text = OpeningBook::class.java.getResource(resource)?.readText() ?: return emptyList()
        return text.lineSequence().drop(1).mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val san = sanOf(parts[2]) ?: return@mapNotNull null
            Opening(parts[0], parts[1], san)
        }.toList()
    }

    private fun sanOf(pgn: String): List<String>? {
        val list = MoveList()
        if (runCatching { list.loadFromSan(pgn) }.isFailure) return null
        val board = Board()
        val moves = MoveList(board.fen)
        for (move in list) {
            if (move !in board.legalMoves()) return null
            moves.add(move)
            board.doMove(move)
        }
        return moves.toSan().trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }

    private val FEATURED = listOf(
        "Italian Game",
        "Ruy Lopez",
        "Scotch Game",
        "Vienna Game",
        "King's Gambit",
        "Sicilian Defense",
        "French Defense",
        "Caro-Kann Defense",
        "Queen's Gambit",
        "Slav Defense",
        "Nimzo-Indian Defense",
        "King's Indian Defense",
        "English Opening",
        "London System",
    )
}
