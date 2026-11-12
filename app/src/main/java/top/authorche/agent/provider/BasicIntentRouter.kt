package top.authorche.agent.provider

import org.json.JSONObject
import top.authorche.agent.agent.AgentDecision

/** Explicit offline commands. The model handles everything outside this small audited set. */
object BasicIntentRouter {
    fun decide(task: String, observations: List<String>): AgentDecision? {
        val input = task.trim()
        val command = when {
            Regex("^(знайди (мій )?телефон|де (мій )?телефон|find my phone)", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> "find_phone" to JSONObject()
            Regex("^(який заряд|скільки заряду|стан телефону|battery status|akkustand)", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> "device_status" to JSONObject()
            Regex("^(відкрий|open|öffne) (налаштування|settings|einstellungen) (wi-?fi|bluetooth|блютуз|батареї|battery)", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val page = when {
                    input.contains("bluetooth", true) || input.contains("блютуз", true) -> "bluetooth"
                    input.contains("батареї", true) || input.contains("battery", true) -> "battery"
                    else -> "wifi"
                }
                "open_settings" to JSONObject().put("page", page)
            }
            Regex("^(знайди|пошукай|find|suche) (на телефоні|на пристрої|on (my )?phone|auf dem handy)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|find|suche) (на телефоні|на пристрої|on (my )?phone|auf dem handy)\\s+", RegexOption.IGNORE_CASE), "")
                "search_device" to JSONObject().put("query", query)
            }
            Regex("^(знайди|пошукай|search|suche) (у вікіпедії|в вікіпедії|wikipedia|in wikipedia)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|search|suche) (у вікіпедії|в вікіпедії|wikipedia|in wikipedia)\\s+", RegexOption.IGNORE_CASE), "")
                val lang = if (input.startsWith("suche", true)) "de" else if (input.startsWith("search", true)) "en" else "uk"
                "search_web" to JSONObject().put("query", query).put("language", lang).put("source", "wikipedia")
            }
            Regex("^(знайди|пошукай|search|suche) (в інтернеті|в мережі|the web|im internet)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|search|suche) (в інтернеті|в мережі|the web|im internet)\\s+", RegexOption.IGNORE_CASE), "")
                val lang = if (input.startsWith("suche", true)) "de" else if (input.startsWith("search", true)) "en" else "uk"
                "search_web" to JSONObject().put("query", query).put("language", lang)
            }
            Regex("^(відкрий|запусти|open|öffne)\\s+", RegexOption.IGNORE_CASE).containsMatchIn(input) -> {
                val app = input.replace(Regex("^(відкрий|запусти|open|öffne)\\s+", RegexOption.IGNORE_CASE), "")
                "launch_app" to JSONObject().put("app", app)
            }
            Regex("^(нагадай|наміти нагадування|remind me)\\s+.+\\s+(через|in)\\s+\\d+\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val match = Regex("(?:через|in)\\s+(\\d+)\\s+(хвилин[уиа]?|хв|minutes?|min|годин[уиа]?|hours?|stunden?)",
                    RegexOption.IGNORE_CASE).find(input) ?: return null
                val count = match.groupValues[1].toLongOrNull() ?: return null
                val unit = match.groupValues[2].lowercase()
                val minutes = if (unit.startsWith("год") || unit.startsWith("hour") || unit.startsWith("stund")) count * 60 else count
                if (minutes !in 1..525_600) return null
                val message = input.substringBefore(match.value).replace(
                    Regex("^(нагадай|наміти нагадування|remind me)\\s*", RegexOption.IGNORE_CASE), "",
                ).trim().ifBlank { "Нагадування" }
                "create_reminder" to JSONObject().put("title", "Jarvis")
                    .put("message", message).put("delay_minutes", minutes)
            }
            else -> null
        } ?: return null
        if (observations.isNotEmpty()) return AgentDecision("Результат виконаної команди", null,
            JSONObject(), formattedResult(observations.last()))
        return AgentDecision("Виконую просту команду без мережевої моделі", command.first, command.second, null)
    }

    private fun formattedResult(observation: String): String {
        val marker = "; дані="
        if (marker !in observation) return observation
        val summary = observation.substringBefore(marker)
        val data = runCatching { JSONObject(observation.substringAfter(marker)) }.getOrNull()
            ?: return observation.take(7000)
        val results = data.optJSONArray("results") ?: data.optJSONArray("apps")
            ?: return summary
        val files = data.optJSONArray("files") ?: org.json.JSONArray()
        return buildString {
            appendLine(summary)
            for (index in 0 until minOf(results.length(), 8)) {
                val item = results.optJSONObject(index) ?: continue
                append("\n${index + 1}. ")
                append(item.optString("title", item.optString("name")))
                item.optString("snippet").takeIf { it.isNotBlank() }?.let { append(" — ${it.take(200)}") }
                item.optString("url", item.optString("package"))
                    .takeIf { it.isNotBlank() }?.let { append("\n$it") }
            }
            for (index in 0 until minOf(files.length(), 8)) {
                val item = files.optJSONObject(index) ?: continue
                append("\nФайл: ${item.optString("name")}")
            }
        }.take(7000)
    }
}
