package top.authorche.agent.tools

import android.accessibilityservice.AccessibilityService
import org.json.JSONObject
import top.authorche.agent.accessibility.AgentAccessibilityService
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class UiAutomationTool : AgentTool {
    override val definition = ToolDefinition(
        name = "ui_action",
        description = "Прочитати структуру екрана, натиснути елемент, ввести текст, прокрутити або виконати Назад/Додому",
        arguments = "{\"operation\":\"snapshot|click_text|set_text|scroll_forward|back|home\",\"query\":\"видимий текст\",\"text\":\"текст для введення\",\"target\":\"підпис поля\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "snapshot", "scroll_forward", "back", "home" -> RiskLevel.LOW
        "click_text", "set_text" -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "snapshot" -> "Прочитати структуру поточного екрана"
        "click_text" -> "Натиснути «${arguments.optString("query") }»"
        "set_text" -> "Ввести текст у поле ${arguments.optString("target", "введення") }"
        "scroll_forward" -> "Прокрутити екран"
        "back" -> "Повернутися назад"
        "home" -> "Перейти на головний екран"
        else -> "Виконати дію з інтерфейсом"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult {
        val result = when (arguments.optString("operation")) {
            "snapshot" -> AgentAccessibilityService.snapshot()
            "click_text" -> AgentAccessibilityService.clickText(arguments.optString("query"))
            "set_text" -> AgentAccessibilityService.setText(
                arguments.optString("text"),
                arguments.optString("target").takeIf { it.isNotBlank() },
            )
            "scroll_forward" -> AgentAccessibilityService.scrollForward()
            "back" -> AgentAccessibilityService.globalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
                "Назад",
            )
            "home" -> AgentAccessibilityService.globalAction(
                AccessibilityService.GLOBAL_ACTION_HOME,
                "Додому",
            )
            else -> Result.failure(IllegalArgumentException("Невідома UI-операція"))
        }
        return result.fold(
            onSuccess = { ToolResult(true, it) },
            onFailure = { ToolResult(false, it.message ?: "Дію не виконано") },
        )
    }
}
