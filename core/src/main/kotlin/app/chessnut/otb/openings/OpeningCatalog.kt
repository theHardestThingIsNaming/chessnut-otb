package app.chessnut.otb.openings

data class CatalogSection(val title: String, val names: List<String>)

data class OpeningFilter(
    val gambits: Boolean = false,
    val white: Boolean = false,
    val black: Boolean = false,
    val unrated: Boolean = false,
    val memes: Boolean = false,
)

/** The trainer's opening list: everyday openings, a section of real gambits, then the joke openings. */
object OpeningCatalog {
    fun browse(query: String): List<CatalogSection> {
        val q = query.trim()
        if (q.isNotEmpty()) {
            // Openings whose name starts with the search come first, the main ones first of all,
            // so "Sicilian" finds the Sicilian Defense before the Pterodactyl's Sicilian lines.
            val names = OpeningBook.search(q).map { it.name }.sortedWith(
                compareBy<String>({ it !in OPENINGS }, { !it.startsWith(q, ignoreCase = true) }),
            )
            return if (names.isEmpty()) emptyList() else listOf(CatalogSection("", names))
        }
        return listOfNotNull(
            repertoire.takeIf { it.isNotEmpty() }?.let { CatalogSection(REPERTOIRE_SECTION, it) },
            CatalogSection("Openings", present(OPENINGS)),
            // Variations of a listed opening, such as the Göring Gambit, live inside it rather than on their own.
            CatalogSection("Gambits", present(GAMBITS).filter { family(it) == null }),
            CatalogSection("Meme openings", present(MEMES)),
        )
    }

    const val REPERTOIRE_SECTION = "My repertoire"

    /** The listed opening [name] belongs to, such as "Scotch Game" for "Scotch Game: Göring Gambit". */
    fun family(name: String): String? = name.substringBefore(':', "").trim().takeIf { it.isNotEmpty() && it in OPENINGS }

    /**
     * How many of the opponent's book moves a repertoire covers at each turn.
     * Most openings take the three most common; a few branch so widely that
     * their main variations need more.
     */
    fun breadth(family: String): Int = WIDE[family] ?: 3

    private val WIDE = mapOf("Pirc Defense" to 8)

    /** The listed variations grouped under [family], whose lines its page and Survival include. */
    fun variationsOf(family: String): List<String> = present(GAMBITS).filter { family(it) == family }

    /** Chapter names of the imported repertoire, shown first. Set by the app. */
    @Volatile var repertoire: List<String> = emptyList()

    private fun present(names: List<String>): List<String> = names.filter { OpeningBook.line(it) != null }

    /** Keeps [name] when every selected filter matches. No selection keeps the full list. */
    fun matches(name: String, accuracy: Int?, filter: OpeningFilter): Boolean {
        if (filter.gambits && !name.contains("gambit", ignoreCase = true)) return false
        if (filter.memes && name !in MEMES) return false
        if (filter.white != filter.black) {
            val side = OpeningBook.side(name)
            if (filter.white && side != OpeningSide.White) return false
            if (filter.black && side != OpeningSide.Black) return false
        }
        if (filter.unrated && accuracy != null && accuracy != 0) return false
        return true
    }

    private val OPENINGS = listOf(
        "Italian Game",
        "Ruy Lopez",
        "Scotch Game",
        "Vienna Game",
        "Bishop's Opening",
        "Sicilian Defense",
        "French Defense",
        "Caro-Kann Defense",
        "Slav Defense",
        "Nimzo-Indian Defense",
        "King's Indian Defense",
        "Pirc Defense",
        "English Opening",
        "London System",
    )

    private val MEMES = listOf(
        "Bongcloud Attack",
        "The Cow",
        "Irish Gambit",
        "Kilkenny Gambit",
        "King's Pawn Game: Wayward Queen Attack",
        "Italian Game: Jerome Gambit",
        "Caro-Kann Defense: Alien Gambit",
        "Grob Opening",
        "Barnes Opening: Hammerschlag",
        "Sodium Attack",
        "Modern Defense: Bishop Attack, Monkey's Bum",
        "Sicilian Defense: McDonnell Attack, Toilet Variation",
        "Formation: Hippopotamus Attack",
        "Ware Opening: Crab Variation",
    )

    private val GAMBITS = listOf(
        "King's Gambit",
        "King's Gambit Accepted",
        "King's Gambit Declined: Falkbeer Countergambit",
        "Queen's Gambit",
        "Queen's Gambit Accepted",
        "Queen's Gambit Declined",
        "Queen's Gambit Declined: Albin Countergambit",
        "Danish Gambit",
        "Scotch Game: Scotch Gambit",
        "Scotch Game: Göring Gambit",
        "Italian Game: Evans Gambit",
        "Italian Game: Rousseau Gambit",
        "Italian Game: Two Knights Defense, Fried Liver Attack",
        "Italian Game: Two Knights Defense, Traxler Counterattack",
        "Vienna Game: Vienna Gambit",
        "Elephant Gambit",
        "Latvian Gambit",
        "Petrov's Defense: Stafford Gambit",
        "Petrov's Defense: Cochrane Gambit",
        "Four Knights Game: Halloween Gambit",
        "Four Knights Game: Scotch Variation, Belgrade Gambit",
        "Ruy Lopez: Marshall Attack",
        "Sicilian Defense: Smith-Morra Gambit",
        "Sicilian Defense: Wing Gambit",
        "French Defense: Wing Gambit",
        "Bishop's Opening: Calabrese Countergambit",
        "Scandinavian Defense: Portuguese Gambit",
        "Scandinavian Defense: Icelandic-Palme Gambit",
        "Englund Gambit",
        "Benko Gambit",
        "Indian Defense: Budapest Gambit",
        "Blackmar-Diemer Gambit",
        "Tarrasch Defense: Schara Gambit",
        "Blumenfeld Countergambit",
        "Bird Opening: From's Gambit",
        "Zukertort Opening: Tennison Gambit",
    )
}
