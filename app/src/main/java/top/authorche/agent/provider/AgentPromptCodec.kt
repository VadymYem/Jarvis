package top.authorche.agent.provider

import org.json.JSONObject
import top.authorche.agent.agent.AgentDecision
import top.authorche.agent.agent.ToolDefinition

object AgentPromptCodec {
    fun systemPrompt(tools: List<ToolDefinition>): String = buildString {
        appendLine("Ти — виконавчий модуль автономного Android-агента Jarvis | AuthorAi.")
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

    fun userPrompt(task: String, observations: List<String>): String = buildString {
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

    fun parseDecision(content: String): AgentDecision {
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
}
