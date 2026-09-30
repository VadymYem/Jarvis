package top.authorche.agent

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.authorche.agent.agent.AgentUiState
import top.authorche.agent.provider.ProviderSettings
import top.authorche.agent.provider.ProviderKind
import top.authorche.agent.provider.SearchProvider
import top.authorche.agent.provider.SearchSettings

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as AuthorAgentApplication).graph
    val state: StateFlow<AgentUiState> = graph.orchestrator.state
    val capabilities = graph.capabilities
    fun searchSettings(): SearchSettings = graph.settings.search()
    fun saveSearch(provider: SearchProvider, url: String, key: String) =
        graph.settings.saveSearch(provider, url, key)
    fun searchFolder(): String = graph.settings.searchFolder()?.lastPathSegment ?: "Теку не вибрано"
    fun saveSearchFolder(uri: Uri): String = runCatching {
        graph.settings.saveSearchFolder(uri)
        "Теку додано до локального пошуку"
    }.getOrElse { it.message ?: "Не вдалося відкрити теку" }

    fun importFindPhoneSong(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching {
                val app = getApplication<Application>()
                val target = File(app.filesDir, "find_phone.mp3")
                val temporary = File(app.filesDir, "find_phone.tmp")
                try {
                    app.contentResolver.openInputStream(uri)?.use { source ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= 20_000_000) { "MP3 має бути не більшим за 20 МБ" }
                                output.write(buffer, 0, read)
                            }
                            require(total > 0) { "MP3 порожній" }
                        }
                    } ?: error("Не вдалося відкрити аудіофайл")
                    require(temporary.renameTo(target)) { "Не вдалося зберегти аудіофайл" }
                } finally { temporary.delete() }
                "Звук пошуку телефону імпортовано"
            }.getOrElse { it.message ?: "Помилка імпорту MP3" }
            mutableFindPhoneStatus.value = result
        }
    }
    private val mutableModelImportStatus = MutableStateFlow<String?>(null)
    val modelImportStatus: StateFlow<String?> = mutableModelImportStatus.asStateFlow()
    private val mutableFindPhoneStatus = MutableStateFlow<String?>(null)
    val findPhoneStatus: StateFlow<String?> = mutableFindPhoneStatus.asStateFlow()
    private var activeTask: Job? = null

    fun run(task: String) {
        if (activeTask?.isActive == true) return
        activeTask = viewModelScope.launch { graph.orchestrator.run(task) }
    }

    fun stop() { activeTask?.cancel(); graph.orchestrator.respondToConfirmation(false) }

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
}
