package top.authorche.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

class VoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return

        val preferred = Locale.getDefault()
        val result = textToSpeech.setLanguage(preferred)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.language = Locale("uk", "UA")
        }
        textToSpeech.setSpeechRate(1.02f)
        pendingText?.let {
            pendingText = null
            speak(it)
        }
    }

    fun speak(text: String) {
        val safeText = text.trim().take(MAX_SPEECH_LENGTH)
        if (safeText.isBlank()) return
        if (!ready) {
            pendingText = safeText
            return
        }
        textToSpeech.speak(
            safeText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis-${UUID.randomUUID()}",
        )
    }

    fun stop() {
        pendingText = null
        textToSpeech.stop()
    }

    fun shutdown() = textToSpeech.shutdown()

    companion object {
        private const val MAX_SPEECH_LENGTH = 3_500
    }
}
