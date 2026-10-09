package app.chessnut.otb.board

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Short table-side beeps for live play. */
class BoardSounds {
    private val handler = Handler(Looper.getMainLooper())
    private var tones: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    }.getOrNull()

    fun check() {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
    }

    fun illegal() {
        tones?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
    }

    fun checkmate() {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
        handler.postDelayed({ tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 220) }, 210)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tones?.release()
        tones = null
    }
}
