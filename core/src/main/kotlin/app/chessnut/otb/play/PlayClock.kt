package app.chessnut.otb.play

import app.chessnut.otb.analysis.Score
import app.chessnut.otb.live.TimeControl

/** Fischer clock for a bot game. The time control locks after the first move. */
class PlayClock(control: TimeControl = TimeControl.Default) {
    var control: TimeControl = control
        private set
    var whiteMillis: Long = control.initialMillis
        private set
    var blackMillis: Long = control.initialMillis
        private set
    var running: Boolean = false
        private set
    var whiteToMove: Boolean = true
        private set
    var started: Boolean = false
        private set
    var ended: Boolean = false
        private set
    var moveCount: Int = 0
        private set
    var flaggedWhite: Boolean = false
        private set
    var flaggedBlack: Boolean = false
        private set
    private var runningSince: Long? = null

    val locked: Boolean get() = moveCount > 0

    fun setControl(next: TimeControl, now: Long): Boolean {
        if (locked) return false
        control = next
        whiteMillis = next.initialMillis
        blackMillis = next.initialMillis
        if (running) runningSince = now
        return true
    }

    fun resetKeepingControl() {
        val kept = control
        whiteMillis = kept.initialMillis
        blackMillis = kept.initialMillis
        running = false
        whiteToMove = true
        started = false
        ended = false
        moveCount = 0
        flaggedWhite = false
        flaggedBlack = false
        runningSince = null
        control = kept
    }

    fun begin(now: Long, whiteFirst: Boolean = true) {
        whiteMillis = control.initialMillis
        blackMillis = control.initialMillis
        whiteToMove = whiteFirst
        started = true
        ended = false
        running = true
        moveCount = 0
        flaggedWhite = false
        flaggedBlack = false
        runningSince = now
    }

    fun remaining(white: Boolean, now: Long): Long {
        val base = if (white) whiteMillis else blackMillis
        if (!running || ended || white != whiteToMove) return base.coerceAtLeast(0)
        val from = runningSince ?: return base.coerceAtLeast(0)
        return (base - (now - from).coerceAtLeast(0)).coerceAtLeast(0)
    }

    /** "1-0" or "0-1" when the side to move has run out of time. */
    fun poll(now: Long): String? {
        if (!running || ended) return null
        if (remaining(whiteToMove, now) > 0) return null
        return flag(whiteToMove)
    }

    /**
     * Charge the side that just moved and start the opponent's clock.
     * Returns a flag result when that side had already expired.
     */
    fun moved(now: Long): String? {
        if (!started || ended) return null
        poll(now)?.let { return it }
        val spent = runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0
        if (whiteToMove) whiteMillis = (whiteMillis - spent + control.incrementMillis).coerceAtLeast(0)
        else blackMillis = (blackMillis - spent + control.incrementMillis).coerceAtLeast(0)
        whiteToMove = !whiteToMove
        moveCount += 1
        runningSince = now
        running = true
        return null
    }

    fun shot(): Shot = Shot(
        whiteMillis, blackMillis, running, whiteToMove, started, ended,
        moveCount, runningSince, flaggedWhite, flaggedBlack,
    )

    fun restore(shot: Shot) {
        whiteMillis = shot.whiteMillis
        blackMillis = shot.blackMillis
        running = shot.running
        whiteToMove = shot.whiteToMove
        started = shot.started
        ended = shot.ended
        moveCount = shot.moveCount
        runningSince = shot.runningSince
        flaggedWhite = shot.flaggedWhite
        flaggedBlack = shot.flaggedBlack
    }

    fun freeze(now: Long) {
        if (running && !ended) {
            val spent = runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0
            if (whiteToMove) whiteMillis = (whiteMillis - spent).coerceAtLeast(0)
            else blackMillis = (blackMillis - spent).coerceAtLeast(0)
        }
        ended = true
        running = false
        runningSince = null
    }

    data class Shot(
        val whiteMillis: Long,
        val blackMillis: Long,
        val running: Boolean,
        val whiteToMove: Boolean,
        val started: Boolean,
        val ended: Boolean,
        val moveCount: Int,
        val runningSince: Long?,
        val flaggedWhite: Boolean,
        val flaggedBlack: Boolean,
    )

    private fun flag(white: Boolean): String {
        if (white) {
            whiteMillis = 0
            flaggedWhite = true
        } else {
            blackMillis = 0
            flaggedBlack = true
        }
        ended = true
        running = false
        runningSince = null
        return if (white) "0-1" else "1-0"
    }
}

/** The bot takes a draw when neither side is a pawn ahead, and never in a mating line. */
fun botAcceptsDraw(score: Score): Boolean = score.mate == null && kotlin.math.abs(score.cappedCp) <= 80
