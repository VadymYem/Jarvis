package top.authorche.agent.provider

import org.json.JSONObject
import top.authorche.agent.agent.AgentDecision

/** Explicit offline commands. The model handles everything outside this small audited set. */
object BasicIntentRouter {
    fun decide(task: String, observations: List<String>): AgentDecision? {
        val input = task.trim()
        val command = when {
            Regex("^(знайди|пошукай|find|suche) (на телефоні|на пристрої|on (my )?phone|auf dem handy)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|find|suche) (на телефоні|на пристрої|on (my )?phone|auf dem handy)\\s+", RegexOption.IGNORE_CASE), "")
                "search_device" to JSONObject().put("query", query)
            }
            Regex("^(знайди|пошукай|search|suche) (у вікіпедії|в вікіпедії|wikipedia|in wikipedia)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|search|suche) (у вікіпедії|в вікіпедії|wikipedia|in wikipedia)\\s+", RegexOption.IGNORE_CASE), "")
                "search_web" to JSONObject().put("query", query).put("language", "uk")
            }
            Regex("^(знайди|пошукай|search|suche) (в інтернеті|в мережі|the web|im internet)\\s+", RegexOption.IGNORE_CASE)
                .containsMatchIn(input) -> {
                val query = input.replace(Regex("^(знайди|пошукай|search|suche) (в інтернеті|в мережі|the web|im internet)\\s+", RegexOption.IGNORE_CASE), "")
                "search_web" to JSONObject().put("query", query).put("language", "uk")
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
            JSONObject(), observations.last().take(7000))
        return AgentDecision("Виконую просту команду без мережевої моделі", command.first, command.second, null)
    }
}
