package top.authorche.agent.provider

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import top.authorche.agent.agent.AgentDecision
import top.authorche.agent.agent.AgentProvider
import top.authorche.agent.agent.ToolDefinition

class LocalGgufClient(
    context: Context,
    private val settings: AgentSettingsStore,
) : AgentProvider {
    private val engine = AiChat.getInferenceEngine(context.applicationContext)
    private var loadedPath: String? = null
    private var activeTask: String? = null

    override suspend fun decide(
        task: String,
        tools: List<ToolDefinition>,
        observations: List<String>,
    ): AgentDecision {
        val config = settings.load()
        val path = config.localModelPath
        check(path.isNotBlank() && File(path).isFile) {
            "Імпортуйте GGUF-модель у налаштуваннях"
        }
        ensureLoaded(path, task, AgentPromptCodec.systemPrompt(tools))
        val output = StringBuilder()
        engine.sendUserPrompt(
            AgentPromptCodec.userPrompt(task, observations),
            predictLength = 768,
        ).collect { token -> output.append(token) }
        return AgentPromptCodec.parseDecision(output.toString())
    }

    private suspend fun ensureLoaded(path: String, task: String, systemPrompt: String) {
        val initial = engine.state.filter { state ->
            state is InferenceEngine.State.Initialized ||
                state is InferenceEngine.State.ModelReady ||
                state is InferenceEngine.State.Error
        }.first()
        if (initial is InferenceEngine.State.Error) engine.cleanUp()

        val mustReload = loadedPath != path || activeTask != task
        if (engine.state.value.isModelLoaded && mustReload) engine.cleanUp()
        if (!engine.state.value.isModelLoaded) {
            engine.loadModel(path)
            engine.setSystemPrompt(systemPrompt)
            loadedPath = path
            activeTask = task
        }
    }
}
