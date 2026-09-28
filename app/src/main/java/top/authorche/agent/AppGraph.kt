package top.authorche.agent

import android.content.Context
import top.authorche.agent.agent.AgentOrchestrator
import top.authorche.agent.agent.PolicyEngine
import top.authorche.agent.agent.ToolRegistry
import top.authorche.agent.provider.AgentSettingsStore
import top.authorche.agent.provider.OpenAiCompatibleClient
import top.authorche.agent.security.SecretStore
import top.authorche.agent.tools.AndroidTools

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val secrets = SecretStore(appContext)
    val settings = AgentSettingsStore(appContext, secrets)
    private val tools = ToolRegistry(AndroidTools.create(appContext))
    private val provider = OpenAiCompatibleClient(settings)
    private val policy = PolicyEngine()

    val orchestrator = AgentOrchestrator(
        provider = provider,
        tools = tools,
        policy = policy,
    )
}
