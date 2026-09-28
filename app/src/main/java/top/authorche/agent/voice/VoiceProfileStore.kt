package top.authorche.agent.voice

import android.content.Context
import android.net.Uri

data class VoiceProfile(
    val referenceAudioUri: Uri?,
    val findPhoneTrackUri: Uri?,
    val speakResponses: Boolean,
)

class VoiceProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "jarvis_voice_profile",
        Context.MODE_PRIVATE,
    )

    fun load(): VoiceProfile = VoiceProfile(
        referenceAudioUri = preferences.getString(KEY_REFERENCE_AUDIO, null)?.let(Uri::parse),
        findPhoneTrackUri = preferences.getString(KEY_FIND_PHONE_TRACK, null)?.let(Uri::parse),
        speakResponses = preferences.getBoolean(KEY_SPEAK_RESPONSES, true),
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

    companion object {
        private const val KEY_REFERENCE_AUDIO = "reference_audio_uri"
        private const val KEY_FIND_PHONE_TRACK = "find_phone_track_uri"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
    }
}
