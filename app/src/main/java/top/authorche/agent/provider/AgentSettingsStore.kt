package top.authorche.agent.provider

import android.content.Context
import top.authorche.agent.security.SecretStore

data class ProviderSettings(
    val endpoint: String,
    val model: String,
    val apiKey: String,
)

class AgentSettingsStore(
    context: Context,
    private val secrets: SecretStore,
) {
    private val preferences = context.getSharedPreferences("author_agent_settings", Context.MODE_PRIVATE)

    fun load(): ProviderSettings = ProviderSettings(
        endpoint = preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
        model = preferences.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
        apiKey = secrets.get(KEY_API_KEY),
    )

    fun save(endpoint: String, model: String, apiKey: String) {
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        require(normalizedEndpoint.startsWith("https://")) {
            "Endpoint має використовувати HTTPS"
        }
        require(model.isNotBlank()) { "Вкажіть модель" }

        preferences.edit()
            .putString(KEY_ENDPOINT, normalizedEndpoint)
            .putString(KEY_MODEL, model.trim())
            .apply()
        secrets.put(KEY_API_KEY, apiKey.trim())
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-5-mini"
        private const val KEY_ENDPOINT = "provider_endpoint"
        private const val KEY_MODEL = "provider_model"
        private const val KEY_API_KEY = "provider_api_key"
    }
}
