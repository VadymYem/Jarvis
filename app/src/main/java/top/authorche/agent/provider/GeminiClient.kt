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

class GeminiClient(private val settingsStore: AgentSettingsStore) : AgentProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun decide(
        task: String,
        tools: List<ToolDefinition>,
        observations: List<String>,
    ): AgentDecision = withContext(Dispatchers.IO) {
        val settings = settingsStore.load()
        check(settings.apiKey.isNotBlank()) { "Вкажіть Gemini API-ключ" }
        val payload = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", AgentPromptCodec.systemPrompt(tools))),
                ),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", AgentPromptCodec.userPrompt(task, observations))),
                        ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.1)
                    .put("responseMimeType", "application/json"),
            )

        val url = "${settings.endpoint}/models/${settings.model}:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", settings.apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gemini повернув HTTP ${response.code}: ${body.take(500)}")
            }
            val content = JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            AgentPromptCodec.parseDecision(content)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
