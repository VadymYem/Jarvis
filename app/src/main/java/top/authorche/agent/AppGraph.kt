package top.authorche.agent

import android.content.Context
import top.authorche.agent.agent.AgentOrchestrator
import top.authorche.agent.agent.PolicyEngine
import top.authorche.agent.agent.ToolRegistry
import top.authorche.agent.provider.AgentSettingsStore
import top.authorche.agent.provider.AgentProviderRouter
import top.authorche.agent.provider.LocalModelStore
import top.authorche.agent.security.SecretStore
import top.authorche.agent.tools.AndroidTools
import top.authorche.agent.voice.PhoneFinderController
import top.authorche.agent.voice.VoiceOutput
import top.authorche.agent.voice.VoiceProfileStore

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val secrets = SecretStore(appContext)
    val settings = AgentSettingsStore(appContext, secrets)
    val localModels = LocalModelStore(appContext, settings)
    val voiceProfile = VoiceProfileStore(appContext)
    val voiceOutput = VoiceOutput(appContext, voiceProfile::load)
    val phoneFinder = PhoneFinderController(appContext)
    private val tools = ToolRegistry(AndroidTools.create(appContext, phoneFinder))
    private val provider = AgentProviderRouter(appContext, settings)
    private val policy = PolicyEngine()

    val orchestrator = AgentOrchestrator(
        provider = provider,
        tools = tools,
        policy = policy,
    )
}
