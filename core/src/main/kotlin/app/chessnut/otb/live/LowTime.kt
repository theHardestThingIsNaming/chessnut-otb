package app.chessnut.otb.live

/**
 * When a clock turns red: the last fifteenth of the starting time, so the
 * last 20 seconds of a 5 minute game and 40 of a 10 minute one, kept
 * between 10 seconds and a minute.
 */
object LowTime {
    fun threshold(initialMillis: Long): Long = (initialMillis / 15).coerceIn(10_000L, 60_000L)

    fun low(millis: Long, initialMillis: Long): Boolean = millis <= threshold(initialMillis)
}
