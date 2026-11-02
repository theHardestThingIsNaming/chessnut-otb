package app.chessnut.otb.play

/** What the Train screen offers once a line is done, so the rules sit in one tested place. */
object FinishedLine {
    /** [openings]: the bottom bar's Openings button. [playOn]: the "Play on" list under the board. [engine]: playing the position out. */
    class Offer(val openings: Boolean, val playOn: Boolean, val engine: Boolean)

    fun offer(lineDone: Boolean, clean: Boolean, learning: Boolean, survival: Boolean, punish: Boolean, trap: Boolean, reviewing: Boolean): Offer {
        val finishing = lineDone && !trap && !punish && !survival && !learning
        return Offer(
            // A finished line has Try again and Next line; the back arrow leaves.
            openings = !learning && !finishing,
            playOn = lineDone && clean && !trap,
            engine = lineDone && clean && !trap && !survival && !punish && !reviewing,
        )
    }
}
