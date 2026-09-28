package top.authorche.agent

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.view.KeyEvent
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.authorche.agent.agent.AgentUiState
import top.authorche.agent.provider.ProviderSettings
import top.authorche.agent.provider.ProviderKind
import top.authorche.agent.voice.VoiceCommand
import top.authorche.agent.voice.VoiceCommandParser
import top.authorche.agent.voice.VoiceProfile
import top.authorche.agent.voice.VoiceLanguage
import top.authorche.agent.voice.MediaAction
import top.authorche.agent.voice.VolumeAction

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as AuthorAgentApplication).graph
    val state: StateFlow<AgentUiState> = graph.orchestrator.state
    private val mutableModelImportStatus = MutableStateFlow<String?>(null)
    val modelImportStatus: StateFlow<String?> = mutableModelImportStatus.asStateFlow()
    private val mutableVoiceStatus = MutableStateFlow("Голосовий режим готовий")
    val voiceStatus: StateFlow<String> = mutableVoiceStatus.asStateFlow()
    private var voiceAnswerPending = false
    private var lastSpokenAnswer: String? = null

    init {
        viewModelScope.launch {
            state.collect { current ->
                if (!voiceAnswerPending || current.running) return@collect
                val answer = current.finalAnswer ?: current.error?.let { "Помилка: $it" }
                if (!answer.isNullOrBlank() && answer != lastSpokenAnswer) {
                    lastSpokenAnswer = answer
                    voiceAnswerPending = false
                    mutableVoiceStatus.value = answer
                    speakIfEnabled(answer)
                }
            }
        }
    }

    fun run(task: String) {
        voiceAnswerPending = false
        viewModelScope.launch { graph.orchestrator.run(task) }
    }

    fun handleVoiceCommand(spokenText: String) {
        when (val command = VoiceCommandParser.parse(spokenText)) {
            VoiceCommand.FindPhone -> findPhone()
            VoiceCommand.StopFindingPhone -> stopFindingPhone()
            VoiceCommand.TellTime -> respondLocally(
                "Зараз ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}",
            )
            VoiceCommand.TellBattery -> {
                val battery = getApplication<Application>()
                    .getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                respondLocally(if (battery in 0..100) "Заряд батареї $battery відсотків" else "Не вдалося прочитати заряд батареї")
            }
            VoiceCommand.StopSpeaking -> {
                graph.voiceOutput.stop()
                mutableVoiceStatus.value = "Озвучення зупинено"
            }
            is VoiceCommand.Media -> controlMedia(command.action)
            is VoiceCommand.Volume -> controlVolume(command.action)
            is VoiceCommand.AgentTask -> {
                if (command.text.isBlank()) return
                mutableVoiceStatus.value = "Виконую: ${command.text}"
                voiceAnswerPending = true
                lastSpokenAnswer = null
                viewModelScope.launch { graph.orchestrator.run(command.text) }
            }
        }
    }

    fun findPhone() {
        graph.phoneFinder.start()
        respondLocally("Я тут. Вмикаю was-wollen-wir-trinken.mp3 із папки Downloads.")
    }

    fun stopFindingPhone() {
        graph.phoneFinder.stop()
        respondLocally("Сигнал пошуку телефона зупинено")
    }

    fun loadVoiceProfile(): VoiceProfile = graph.voiceProfile.load()

    fun setVoiceReference(uri: Uri) {
        persistReadPermission(uri)
        graph.voiceProfile.setReferenceAudio(uri)
        mutableVoiceStatus.value = "Еталон голосу збережено приватно на пристрої"
    }

    fun setFindPhoneTrack(uri: Uri) {
        persistReadPermission(uri)
        graph.voiceProfile.setFindPhoneTrack(uri)
        mutableVoiceStatus.value = "Музику для пошуку телефона збережено"
    }

    fun setSpeakResponses(enabled: Boolean) {
        graph.voiceProfile.setSpeakResponses(enabled)
        if (!enabled) graph.voiceOutput.stop()
    }

    fun setInputLanguage(language: VoiceLanguage) = graph.voiceProfile.setInputLanguage(language)

    fun setOutputLanguage(language: VoiceLanguage) {
        graph.voiceProfile.setOutputLanguage(language)
        previewVoice()
    }

    fun setPreferOfflineRecognition(enabled: Boolean) =
        graph.voiceProfile.setPreferOfflineRecognition(enabled)

    fun setSpeechRate(rate: Float) = graph.voiceProfile.setSpeechRate(rate)

    fun setSpeechPitch(pitch: Float) = graph.voiceProfile.setSpeechPitch(pitch)

    fun previewVoice() = respondLocally("Jarvis | AuthorAi на зв'язку")

    fun confirm(approved: Boolean) = graph.orchestrator.respondToConfirmation(approved)

    fun reset() = graph.orchestrator.reset()

    fun loadSettings(): ProviderSettings = graph.settings.load()

    fun loadSettings(provider: ProviderKind): ProviderSettings = graph.settings.defaults(provider)

    fun saveSettings(
        provider: ProviderKind,
        endpoint: String,
        model: String,
        apiKey: String,
    ) = graph.settings.save(provider, endpoint, model, apiKey)

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            mutableModelImportStatus.value = "Імпортую GGUF-модель…"
            runCatching { graph.localModels.import(uri) }
                .onSuccess { file ->
                    val gib = file.length().toDouble() / 1024 / 1024 / 1024
                    mutableModelImportStatus.value = "Імпортовано ${file.name} · %.2f GiB".format(gib)
                }
                .onFailure { error ->
                    mutableModelImportStatus.value = error.message ?: "Не вдалося імпортувати модель"
                }
        }
    }

    private fun respondLocally(message: String) {
        mutableVoiceStatus.value = message
        speakIfEnabled(message)
    }

    private fun controlMedia(action: MediaAction) {
        val audio = getApplication<Application>().getSystemService(AudioManager::class.java)
        val code = when (action) {
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        respondLocally(
            when (action) {
                MediaAction.PLAY -> "Відтворення продовжено"
                MediaAction.PAUSE -> "Відтворення призупинено"
                MediaAction.NEXT -> "Перемикаю на наступний трек"
                MediaAction.PREVIOUS -> "Повертаю попередній трек"
            },
        )
    }

    private fun controlVolume(action: VolumeAction) {
        val audio = getApplication<Application>().getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            when (action) {
                VolumeAction.UP -> AudioManager.ADJUST_RAISE
                VolumeAction.DOWN -> AudioManager.ADJUST_LOWER
                VolumeAction.MUTE -> AudioManager.ADJUST_MUTE
            },
            AudioManager.FLAG_SHOW_UI,
        )
        respondLocally(
            when (action) {
                VolumeAction.UP -> "Збільшую гучність"
                VolumeAction.DOWN -> "Зменшую гучність"
                VolumeAction.MUTE -> "Звук вимкнено"
            },
        )
    }

    private fun speakIfEnabled(message: String) {
        if (graph.voiceProfile.load().speakResponses) graph.voiceOutput.speak(message)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
