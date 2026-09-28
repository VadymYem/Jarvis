package top.authorche.agent.voice

import android.content.Context
import android.net.Uri

data class VoiceProfile(
    val referenceAudioUri: Uri?,
    val findPhoneTrackUri: Uri?,
    val speakResponses: Boolean,
    val inputLanguageTag: String,
    val outputLanguageTag: String,
    val preferOfflineRecognition: Boolean,
    val speechRate: Float,
    val speechPitch: Float,
)

enum class VoiceLanguage(val languageTag: String, val title: String) {
    UKRAINIAN("uk-UA", "Українська"),
    ENGLISH("en-US", "English"),
    GERMAN("de-DE", "Deutsch"),
    RUSSIAN("ru-RU", "Русский"),
    ;

    companion object {
        fun fromTag(tag: String): VoiceLanguage = entries.firstOrNull {
            it.languageTag.equals(tag, ignoreCase = true)
        } ?: UKRAINIAN
    }
}

class VoiceProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "jarvis_voice_profile",
        Context.MODE_PRIVATE,
    )

    fun load(): VoiceProfile = VoiceProfile(
        referenceAudioUri = preferences.getString(KEY_REFERENCE_AUDIO, null)?.let(Uri::parse),
        findPhoneTrackUri = preferences.getString(KEY_FIND_PHONE_TRACK, null)?.let(Uri::parse),
        speakResponses = preferences.getBoolean(KEY_SPEAK_RESPONSES, true),
        inputLanguageTag = preferences.getString(KEY_INPUT_LANGUAGE, null) ?: VoiceLanguage.UKRAINIAN.languageTag,
        outputLanguageTag = preferences.getString(KEY_OUTPUT_LANGUAGE, null) ?: VoiceLanguage.UKRAINIAN.languageTag,
        preferOfflineRecognition = preferences.getBoolean(KEY_PREFER_OFFLINE, true),
        speechRate = preferences.getFloat(KEY_SPEECH_RATE, 1.0f).coerceIn(0.7f, 1.3f),
        speechPitch = preferences.getFloat(KEY_SPEECH_PITCH, 1.0f).coerceIn(0.7f, 1.3f),
    )

    fun setReferenceAudio(uri: Uri?) {
        preferences.edit().putString(KEY_REFERENCE_AUDIO, uri?.toString()).apply()
    }

    fun setFindPhoneTrack(uri: Uri?) {
        preferences.edit().putString(KEY_FIND_PHONE_TRACK, uri?.toString()).apply()
    }

    fun setSpeakResponses(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SPEAK_RESPONSES, enabled).apply()
    }

    fun setInputLanguage(language: VoiceLanguage) {
        preferences.edit().putString(KEY_INPUT_LANGUAGE, language.languageTag).apply()
    }

    fun setOutputLanguage(language: VoiceLanguage) {
        preferences.edit().putString(KEY_OUTPUT_LANGUAGE, language.languageTag).apply()
    }

    fun setPreferOfflineRecognition(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PREFER_OFFLINE, enabled).apply()
    }

    fun setSpeechRate(rate: Float) {
        preferences.edit().putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.7f, 1.3f)).apply()
    }

    fun setSpeechPitch(pitch: Float) {
        preferences.edit().putFloat(KEY_SPEECH_PITCH, pitch.coerceIn(0.7f, 1.3f)).apply()
    }

    companion object {
        private const val KEY_REFERENCE_AUDIO = "reference_audio_uri"
        private const val KEY_FIND_PHONE_TRACK = "find_phone_track_uri"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
        private const val KEY_INPUT_LANGUAGE = "input_language_tag"
        private const val KEY_OUTPUT_LANGUAGE = "output_language_tag"
        private const val KEY_PREFER_OFFLINE = "prefer_offline_recognition"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
    }
}
