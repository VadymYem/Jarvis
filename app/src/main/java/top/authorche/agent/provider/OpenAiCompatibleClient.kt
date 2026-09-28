package top.authorche.agent.provider

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import top.authorche.agent.agent.AgentDecision
import top.authorche.agent.agent.AgentProvider
import top.authorche.agent.agent.ToolDefinition

class OpenAiCompatibleClient(
    private val settingsStore: AgentSettingsStore,
) : AgentProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun decide(
        task: String,
        tools: List<ToolDefinition>,
        observations: List<String>,
    ): AgentDecision = withContext(Dispatchers.IO) {
        val settings = settingsStore.load()
        check(settings.apiKey.isNotBlank()) {
            "Відкрийте налаштування й збережіть API-ключ"
        }

        val payload = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", AgentPromptCodec.systemPrompt(tools)))
                    .put(JSONObject().put("role", "user").put("content", AgentPromptCodec.userPrompt(task, observations))),
            )

        val request = Request.Builder()
            .url("${settings.endpoint}/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Провайдер повернув HTTP ${response.code}: ${body.take(500)}")
            }
            val root = JSONObject(body)
            val content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            AgentPromptCodec.parseDecision(content)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
