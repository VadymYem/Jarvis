package top.authorche.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import top.authorche.agent.agent.AgentUiState

/** Foreground, user-initiated turn-taking. No background microphone or wake word. */
class VoiceSession(private val context: Context, private val onCommand: (String) -> Unit) {
    var active by mutableStateOf(false)
        private set
    var status by mutableStateOf("Голос вимкнено")
        private set
    var transcript by mutableStateOf("")
        private set
    var language by mutableStateOf("uk-UA")
        private set
    val onDevice: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    private var speakingTask: String? = null
    private var ttsReady = false
    private val tts = TextToSpeech(context) { result ->
        ttsReady = result == TextToSpeech.SUCCESS
        if (!ttsReady && active) status = "Синтез мовлення недоступний; слухайте наступну команду вручну"
    }

    fun setLanguage(value: String) {
        language = value
        if (active) { recognizer?.cancel(); listen() }
    }

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !onDevice) {
            status = "У системі немає служби розпізнавання мовлення"
            return
        }
        active = true
        speakingTask = null
        transcript = ""
        listen()
    }

    fun stop() {
        active = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts.stop()
        status = "Голос вимкнено"
    }

    fun onAgentState(state: AgentUiState) {
        if (!active || state.running || state.task.isBlank() || state.task == speakingTask) return
        val reply = state.finalAnswer ?: state.error ?: return
        speakingTask = state.task
        say(reply)
    }

    private fun listen() {
        if (!active) return
        recognizer?.destroy()
        recognizer = try {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (error: Exception) {
            status = "Не вдалося запустити мікрофон: ${error.message}"
            active = false
            return
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "Слухаю · ${language}…" }
            override fun onBeginningOfSpeech() { status = "Говоріть…" }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { status = "Розпізнаю…" }
            override fun onError(error: Int) {
                if (!active) return
                status = "Розпізнавання: код $error · натисніть «Слухати» ще раз"
                recognizer?.destroy()
                recognizer = null
            }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                recognizer?.destroy()
                recognizer = null
                if (phrase.isBlank()) { status = "Не почув команду"; return }
                transcript = phrase
                speakingTask = null
                status = "Виконую команду…"
                onCommand(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (!onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        status = "Запускаю мікрофон…"
        recognizer?.startListening(request)
    }

    private fun say(text: String) {
        if (!ttsReady) { status = "Відповідь на екрані · натисніть «Слухати»"; return }
        val languageResult = tts.setLanguage(Locale.forLanguageTag(language))
        if (languageResult < 0) { status = "Голос для ${language} не встановлено · натисніть «Слухати»"; return }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { android.os.Handler(context.mainLooper).post { listen() } }
            @Deprecated("Android TTS callback")
            override fun onError(utteranceId: String?) { android.os.Handler(context.mainLooper).post {
                status = "Озвучення недоступне · натисніть «Слухати»"
            } }
        })
        status = "Відповідаю…"
        tts.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
    }

    fun listenAgain() {
        if (active) { tts.stop(); recognizer?.cancel(); listen() }
    }

    fun close() { stop(); tts.shutdown() }
}
