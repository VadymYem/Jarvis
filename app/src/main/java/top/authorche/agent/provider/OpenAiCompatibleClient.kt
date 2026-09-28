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
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(tools)))
                    .put(JSONObject().put("role", "user").put("content", userPrompt(task, observations))),
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
            parseDecision(content)
        }
    }

    private fun systemPrompt(tools: List<ToolDefinition>): String = buildString {
        appendLine("Ти — виконавчий модуль Android-агента AuthorAI.")
        appendLine("Плануй лише одну перевірювану дію за крок. Спочатку використовуй семантичний UI, потім Shizuku.")
        appendLine("Не вигадуй успіх: після кожної дії аналізуй спостереження.")
        appendLine("Відповідай ТІЛЬКИ одним JSON-об'єктом без Markdown.")
        appendLine("Для дії: {\"rationale\":\"коротко\",\"tool\":\"назва\",\"arguments\":{...},\"final\":null}")
        appendLine("Для завершення: {\"rationale\":\"коротко\",\"tool\":null,\"arguments\":{},\"final\":\"результат користувачу\"}")
        appendLine("Доступні інструменти:")
        tools.forEach { tool ->
            appendLine("- ${tool.name}: ${tool.description}; аргументи: ${tool.arguments}")
        }
    }

    private fun userPrompt(task: String, observations: List<String>): String = buildString {
        appendLine("ЗАВДАННЯ: $task")
        if (observations.isEmpty()) {
            appendLine("СПОСТЕРЕЖЕННЯ: ще немає")
        } else {
            appendLine("СПОСТЕРЕЖЕННЯ:")
            observations.takeLast(12).forEachIndexed { index, value ->
                appendLine("${index + 1}. $value")
            }
        }
    }

    private fun parseDecision(content: String): AgentDecision {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start) { "Модель повернула не JSON: ${content.take(300)}" }
        val json = JSONObject(content.substring(start, end + 1))
        return AgentDecision(
            rationale = json.optString("rationale"),
            tool = json.optString("tool").takeUnless { it.isBlank() || it == "null" },
            arguments = json.optJSONObject("arguments") ?: JSONObject(),
            finalAnswer = json.optString("final").takeUnless { it.isBlank() || it == "null" },
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
