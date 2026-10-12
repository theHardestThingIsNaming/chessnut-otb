package app.chessnut.otb.lichess

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a rated game could do to your rating: the change for a win, a draw and a loss. */
data class RatingStakes(val win: Int, val draw: Int, val loss: Int) {
    /** "Win +6 · Draw −1 · Loss −8". */
    val label: String get() = "Win ${signed(win)} · Draw ${signed(draw)} · Loss ${signed(loss)}"

    private fun signed(n: Int) = when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"
        else -> "±0"
    }

    companion object {
        private val Q = ln(10.0) / 400

        /**
         * Lichess rates with Glicko-2; for a single game the Glicko update
         * comes within a point or two of it. [rd] is the rating deviation:
         * the less settled a rating, the more one game moves it.
         */
        fun of(rating: Int, rd: Int, opponent: Int, opponentRd: Int): RatingStakes {
            fun change(score: Double): Int {
                val g = 1 / sqrt(1 + 3 * Q * Q * opponentRd * opponentRd / (PI * PI))
                val expected = 1 / (1 + 10.0.pow(-g * (rating - opponent) / 400))
                val dSquared = 1 / (Q * Q * g * g * expected * (1 - expected))
                return (Q / (1.0 / (rd * rd) + 1 / dSquared) * g * (score - expected)).roundToInt()
            }
            return RatingStakes(change(1.0), change(0.5), change(0.0))
        }

        /** The rating deviation for [speed] from a /api/user answer. */
        fun deviation(userJson: String, speed: String): Int? =
            (JsonLite.parse(userJson) as? Json.Obj)?.obj("perfs")?.obj(speed)?.long("rd")?.toInt()
    }
}
