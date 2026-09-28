package top.authorche.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.authorche.agent.agent.AgentUiState
import top.authorche.agent.provider.ProviderSettings

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as AuthorAgentApplication).graph
    val state: StateFlow<AgentUiState> = graph.orchestrator.state

    fun run(task: String) {
        viewModelScope.launch { graph.orchestrator.run(task) }
    }

    fun confirm(approved: Boolean) = graph.orchestrator.respondToConfirmation(approved)

    fun reset() = graph.orchestrator.reset()

    fun loadSettings(): ProviderSettings = graph.settings.load()

    fun saveSettings(endpoint: String, model: String, apiKey: String) =
        graph.settings.save(endpoint, model, apiKey)
}
