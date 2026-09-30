package top.authorche.agent.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

data class VoiceRecognitionState(
    val listening: Boolean = false,
    val partialText: String = "",
    val error: String? = null,
)

class VoiceRecognitionController(
    context: Context,
    private val profileProvider: () -> VoiceProfile,
    private val onState: (VoiceRecognitionState) -> Unit,
    private val onResult: (String) -> Unit,
) : RecognitionListener, AutoCloseable {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onState(VoiceRecognitionState(error = "Надайте доступ до мікрофона"))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onState(VoiceRecognitionState(error = "На пристрої немає служби розпізнавання мовлення"))
            return
        }

        closeRecognizer()
        val profile = profileProvider()
        recognizer = if (
            profile.preferOfflineRecognition &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }.also { it.setRecognitionListener(this) }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, profile.inputLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, profile.inputLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, profile.preferOfflineRecognition)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Говоріть: ${VoiceLanguage.fromTag(profile.inputLanguageTag).title}",
            )
        }
        onState(VoiceRecognitionState(listening = true))
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onState(VoiceRecognitionState())
    }

    override fun close() {
        closeRecognizer()
        onState(VoiceRecognitionState())
    }

    private fun closeRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onState(VoiceRecognitionState(listening = true, partialText = "Слухаю…"))
    }

    override fun onBeginningOfSpeech() {
        onState(VoiceRecognitionState(listening = true, partialText = "Говоріть"))
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Помилка мікрофона"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Немає доступу до мікрофона"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Мовна служба недоступна без мережі"
            SpeechRecognizer.ERROR_NO_MATCH -> "Не вдалося розпізнати команду"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Розпізнавач уже зайнятий"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не почув команди"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Обрана мова не підтримується мовною службою"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Мовний пакет тимчасово недоступний"
            else -> "Помилка розпізнавання ($error)"
        }
        onState(VoiceRecognitionState(error = message))
        closeRecognizer()
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        closeRecognizer()
        if (text.isBlank()) {
            onState(VoiceRecognitionState(error = "Не почув команди"))
        } else {
            onState(VoiceRecognitionState(partialText = text))
            onResult(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        onState(VoiceRecognitionState(listening = true, partialText = text))
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
