package app.chessnut.otb.live

/** Fischer clock: [initialMillis] on each side, [incrementMillis] added after a move. */
data class TimeControl(val initialMillis: Long, val incrementMillis: Long) {
    val label: String
        get() {
            val minutes = initialMillis / 60_000
            val increment = incrementMillis / 1_000
            return if (increment == 0L) "$minutes min" else "$minutes+$increment"
        }

    companion object {
        fun minutes(minutes: Int, incrementSeconds: Int = 0) =
            TimeControl(minutes * 60_000L, incrementSeconds * 1_000L)

        val Presets: List<TimeControl> = listOf(
            minutes(1),
            minutes(1, 1),
            minutes(2, 1),
            minutes(3),
            minutes(3, 2),
            minutes(5),
            minutes(5, 3),
            minutes(5, 5),
            minutes(10),
            minutes(10, 5),
            minutes(15, 10),
            minutes(30),
        )

        val Default: TimeControl = minutes(5, 5)

        fun fromLabel(label: String): TimeControl =
            Presets.firstOrNull { it.label == label } ?: Default
    }
}
