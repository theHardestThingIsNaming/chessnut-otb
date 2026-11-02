package app.chessnut.otb.live

import app.chessnut.otb.play.BOARD_SETTLE_MS
import app.chessnut.otb.protocol.PositionCodec

/**
 * Runs a board log from the phone (`files/logs/board-*.txt`) through the recorder the way the app does:
 * a reading counts once it has stood for [BOARD_SETTLE_MS], and a board that stays wrong for
 * [MISMATCH_MS] is called illegal. Returns one line per thing the recorder did, for finding out
 * why a real session went wrong without sitting at the board.
 */
object LogReplay {
    private const val MISMATCH_MS = 2_000L
    private val frame = Regex("""^(\d\d):(\d\d):(\d\d)\.(\d\d\d) <- \S+ ((?:[0-9a-f]{2} ?)+)""")

    fun replay(log: String, control: TimeControl = TimeControl.Default): List<String> {
        val tracker = LiveGameTracker(control)
        val out = mutableListOf<String>()
        var pending: Pair<Long, String>? = null
        var lastPlacement: String? = null
        var illegalAt: Long? = null

        fun settle(at: Long, placement: String) {
            val event = tracker.onPlacement(placement, at)
            when (event) {
                is LiveEvent.Move -> { illegalAt = null; out += "${stamp(at)} ${(event.earlier + event.san).joinToString(" ")}${if (event.earlier.isEmpty()) "" else " (caught up)"}" }
                is LiveEvent.Finished -> { illegalAt = null; out += "${stamp(at)} finished ${event.result} after ${event.sanMoves.size} moves" }
                is LiveEvent.Illegal -> if (illegalAt == null) illegalAt = at
                LiveEvent.Ignored -> illegalAt = null
            }
        }

        fun flush(upTo: Long) {
            pending?.let { (at, placement) ->
                if (upTo - at >= BOARD_SETTLE_MS) { settle(at + BOARD_SETTLE_MS, placement); pending = null }
            }
            illegalAt?.let { since ->
                if (upTo - since >= MISMATCH_MS) { out += "${stamp(since + MISMATCH_MS)} illegal (board stayed wrong)"; illegalAt = null }
            }
        }

        for (line in log.lineSequence()) {
            val m = frame.find(line) ?: continue
            val at = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[4].toLong()
            val bytes = m.groupValues[5].trim().split(' ').map { it.toInt(16).toByte() }.toByteArray()
            val placement = PositionCodec.decodePlacement(bytes) ?: continue
            flush(at)
            if (placement != lastPlacement) {
                lastPlacement = placement
                pending = at to placement
            }
        }
        flush(Long.MAX_VALUE / 2)
        return out
    }

    private fun stamp(ms: Long): String =
        "%02d:%02d:%02d.%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
}
