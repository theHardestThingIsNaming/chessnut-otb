package app.chessnut.otb.board

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import app.chessnut.otb.play.SpokenMove
import java.util.Locale

/** Says moves aloud. The phone-sounds switch decides whether anything calls this. */
class MoveSpeaker(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val waiting = ArrayDeque<String>()
    private var ready = false
    private lateinit var speech: TextToSpeech

    init {
        speech = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            // Keep the voice chosen in the phone's speech settings. Only when it doesn't speak
            // English (the moves and notes are English) does the app pick a voice itself.
            val chosen = runCatching { speech.defaultVoice ?: speech.voice }.getOrNull()
            val english = chosen != null && runCatching { chosen.locale.isO3Language == "eng" }.getOrDefault(chosen.locale.language == "en")
            if (!english) {
                speech.language = Locale.US
                bestVoice()?.let { speech.voice = it }
            }
            ready = true
            while (waiting.isNotEmpty()) speakNow(waiting.removeFirst())
        }
    }

    fun say(san: String, mate: Boolean = false) = enqueue(SpokenMove.phrase(san, mate))

    fun sayCheckmate() = enqueue("checkmate")

    /** A longer passage, such as an opening's history. Queued after any move being said. */
    fun narrate(text: String) = enqueue(text)

    fun stop() {
        handler.post {
            waiting.clear()
            if (ready) speech.stop()
        }
    }

    fun release() {
        handler.post {
            waiting.clear()
            ready = false
            speech.stop()
            speech.shutdown()
        }
    }

    private fun enqueue(phrase: String) {
        handler.post {
            if (ready) speakNow(phrase) else waiting.addLast(phrase)
        }
    }

    /**
     * The most natural English voice installed. Offline voices win ties, so
     * speech still works at a club without signal.
     */
    private fun bestVoice(): Voice? = runCatching {
        speech.voices.orEmpty()
            .filter { it.locale.language == "en" && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .maxWithOrNull(
                compareBy<Voice> { it.quality }
                    .thenBy { !it.isNetworkConnectionRequired }
                    .thenBy { it.locale.country == "US" || it.locale.country == "GB" },
            )
    }.getOrNull()

    private fun speakNow(phrase: String) {
        speech.speak(phrase, TextToSpeech.QUEUE_ADD, null, "move")
    }
}
