package top.authorche.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

class VoiceOutput(
    context: Context,
    private val profileProvider: () -> VoiceProfile,
) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return

        applyProfile(profileProvider())
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
        applyProfile(profileProvider())
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

    private fun applyProfile(profile: VoiceProfile) {
        if (!ready) return
        val locale = Locale.forLanguageTag(profile.outputLanguageTag)
        val result = textToSpeech.setLanguage(locale)
        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.voices
                ?.asSequence()
                ?.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
                ?.sortedWith(
                    compareByDescending<android.speech.tts.Voice> {
                        it.locale.toLanguageTag().equals(profile.outputLanguageTag, ignoreCase = true)
                    }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality },
                )
                ?.firstOrNull()
                ?.let { textToSpeech.voice = it }
        }
        textToSpeech.setSpeechRate(profile.speechRate)
        textToSpeech.setPitch(profile.speechPitch)
    }

    companion object {
        private const val MAX_SPEECH_LENGTH = 3_500
    }
}
