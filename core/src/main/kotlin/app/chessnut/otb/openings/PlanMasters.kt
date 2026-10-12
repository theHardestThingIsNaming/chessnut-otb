package app.chessnut.otb.openings

/**
 * How often masters played each move of a middlegame plan: of the classical master games that
 * reached the position before the move, how many played it. From `plan-masters.tsv`, made by
 * MasterStatsTest from PGN Mentor's games, matched by position.
 */
object PlanMasters {
    private data class Key(val opening: String, val white: Boolean, val plan: String, val index: Int)

    private val COUNTS: Map<Key, Pair<Int, Int>> by lazy {
        PlanMasters::class.java.getResourceAsStream("/plan-masters.tsv")?.bufferedReader()?.readLines().orEmpty()
            .map { it.split('\t') }
            .filter { it.size == 6 }
            .associate { Key(it[0], it[1] == "w", it[2], it[3].toInt()) to (it[4].toInt() to it[5].toInt()) }
    }

    /** Master games that played [plan]'s move at [index], and that reached the position before it. */
    fun of(plan: MiddlegamePlan, index: Int): Pair<Int, Int>? = COUNTS[Key(plan.opening, plan.white, plan.name, index)]

    /** "Masters: 4,994 of 6,274 games" when enough master games reached the position to mean something. */
    fun line(plan: MiddlegamePlan, index: Int): String? {
        val (played, reached) = of(plan, index) ?: return null
        if (reached < MIN_GAMES) return null
        return if (played == 0) "Masters: not played in ${"%,d".format(reached)} games here" else "Masters: ${"%,d".format(played)} of ${"%,d".format(reached)} games"
    }

    private const val MIN_GAMES = 5
}
