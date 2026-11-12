package top.authorche.agent.provider

import android.content.Context
import top.authorche.agent.agent.AgentDecision
import top.authorche.agent.agent.AgentProvider
import top.authorche.agent.agent.ToolDefinition

class AgentProviderRouter(
    context: Context,
    private val settings: AgentSettingsStore,
) : AgentProvider {
    private val openAiCompatible = OpenAiCompatibleClient(settings)
    private val gemini = GeminiClient(settings)
    private val local = LocalGgufClient(context.applicationContext, settings)

    override suspend fun decide(
        task: String,
        tools: List<ToolDefinition>,
        observations: List<String>,
    ): AgentDecision = BasicIntentRouter.decide(task, observations) ?: when (settings.load().provider) {
        ProviderKind.LOCAL_GGUF -> local.decide(task, tools, observations)
        ProviderKind.GEMINI -> gemini.decide(task, tools, observations)
        ProviderKind.OPENAI,
        ProviderKind.GROQ,
        ProviderKind.COMPATIBLE,
        -> openAiCompatible.decide(task, tools, observations)
    }
}
