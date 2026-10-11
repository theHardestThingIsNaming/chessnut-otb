package app.chessnut.otb.repertoire

/**
 * How well you know an opening from one side, as a medal: bronze once a line
 * is learned, silver with half the lines and a Survival run of
 * [SILVER_RUN], gold with every line and a run of [GOLD_RUN].
 */
enum class OpeningMedal {
    Bronze,
    Silver,
    Gold,
    ;

    companion object {
        const val SILVER_RUN = 2
        const val GOLD_RUN = 5

        fun of(learned: Int, total: Int, survivalBest: Int?): OpeningMedal? {
            if (total <= 0 || learned <= 0) return null
            val run = survivalBest ?: 0
            return when {
                learned >= total && run >= GOLD_RUN -> Gold
                learned * 2 >= total && run >= SILVER_RUN -> Silver
                else -> Bronze
            }
        }
    }
}
