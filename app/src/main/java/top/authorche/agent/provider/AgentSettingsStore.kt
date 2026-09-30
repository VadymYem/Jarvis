package top.authorche.agent.provider

import android.content.Context
import top.authorche.agent.security.SecretStore

data class ProviderSettings(
    val provider: ProviderKind,
    val endpoint: String,
    val model: String,
    val apiKey: String,
    val localModelPath: String,
)

enum class SearchProvider(val title: String) {
    WIKIPEDIA("Вікіпедія · без ключа"),
    GITHUB("GitHub · без ключа"),
    SEARXNG("SearXNG · власний сервер"),
    BRAVE("Brave Search API"),
}

data class SearchSettings(val provider: SearchProvider, val searxngUrl: String, val braveKey: String)

enum class ProviderKind(
    val title: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val needsApiKey: Boolean,
) {
    LOCAL_GGUF("Local GGUF", "local://llama.cpp", "GGUF на пристрої", false),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", true),
    OPENAI("OpenAI / GPT", "https://api.openai.com/v1", "gpt-5-mini", true),
    GROQ("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", true),
    COMPATIBLE("Сумісний API", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b", true),
}

class AgentSettingsStore(
    context: Context,
    private val secrets: SecretStore,
) {
    private val preferences = context.getSharedPreferences("author_agent_settings", Context.MODE_PRIVATE)

    fun search(): SearchSettings = SearchSettings(
        provider = runCatching { SearchProvider.valueOf(
            preferences.getString("search_provider", SearchProvider.WIKIPEDIA.name).orEmpty(),
        ) }.getOrDefault(SearchProvider.WIKIPEDIA),
        searxngUrl = preferences.getString("searxng_url", "").orEmpty(),
        braveKey = secrets.get("brave_search_key"),
    )

    fun saveSearch(provider: SearchProvider, searxngUrl: String, braveKey: String) {
        val url = searxngUrl.trim().trimEnd('/')
        if (provider == SearchProvider.SEARXNG) {
            require(url.startsWith("https://")) { "SearXNG потребує HTTPS-адресу" }
        }
        if (provider == SearchProvider.BRAVE) require(braveKey.isNotBlank()) { "Вкажіть Brave API-ключ" }
        preferences.edit().putString("search_provider", provider.name)
            .putString("searxng_url", url).apply()
        secrets.put("brave_search_key", braveKey.trim())
    }

    fun load(): ProviderSettings = ProviderSettings(
        provider = currentProvider(),
        endpoint = preferences.getString(keyFor("endpoint"), currentProvider().defaultEndpoint)
            ?: currentProvider().defaultEndpoint,
        model = preferences.getString(keyFor("model"), currentProvider().defaultModel)
            ?: currentProvider().defaultModel,
        apiKey = secrets.get(keyFor("api_key")),
        localModelPath = preferences.getString(KEY_LOCAL_MODEL_PATH, "").orEmpty(),
    )

    fun save(provider: ProviderKind, endpoint: String, model: String, apiKey: String) {
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        if (provider != ProviderKind.LOCAL_GGUF) {
            require(normalizedEndpoint.startsWith("https://")) {
                "Endpoint має використовувати HTTPS"
            }
            require(model.isNotBlank()) { "Вкажіть модель" }
            require(apiKey.isNotBlank()) { "Вкажіть API-ключ" }
        }

        preferences.edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString("${provider.name.lowercase()}_endpoint", normalizedEndpoint)
            .putString("${provider.name.lowercase()}_model", model.trim())
            .apply()
        secrets.put("${provider.name.lowercase()}_api_key", apiKey.trim())
    }

    fun selectProvider(provider: ProviderKind): ProviderSettings {
        preferences.edit().putString(KEY_PROVIDER, provider.name).apply()
        return load()
    }

    fun saveLocalModel(path: String) {
        preferences.edit().putString(KEY_LOCAL_MODEL_PATH, path).apply()
    }

    private fun currentProvider(): ProviderKind = runCatching {
        ProviderKind.valueOf(preferences.getString(KEY_PROVIDER, ProviderKind.OPENAI.name).orEmpty())
    }.getOrDefault(ProviderKind.OPENAI)

    private fun keyFor(suffix: String): String = "${currentProvider().name.lowercase()}_$suffix"

    fun defaults(provider: ProviderKind): ProviderSettings = ProviderSettings(
        provider = provider,
        endpoint = preferences.getString("${provider.name.lowercase()}_endpoint", provider.defaultEndpoint)
            ?: provider.defaultEndpoint,
        model = preferences.getString("${provider.name.lowercase()}_model", provider.defaultModel)
            ?: provider.defaultModel,
        apiKey = secrets.get("${provider.name.lowercase()}_api_key"),
        localModelPath = preferences.getString(KEY_LOCAL_MODEL_PATH, "").orEmpty(),
    )

    companion object {
        private const val KEY_PROVIDER = "provider_kind"
        private const val KEY_LOCAL_MODEL_PATH = "local_model_path"
    }
}
