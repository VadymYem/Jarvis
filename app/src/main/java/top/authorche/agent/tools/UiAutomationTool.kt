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
        description = "Прочитати нумеровану структуру екрана, натиснути елемент за ID/текстом, виконати довге натискання, ввести текст, прокрутити, виконати Назад/Додому або координатний tap/swipe як останній fallback",
        arguments = "{\"operation\":\"snapshot|click_id|click_text|long_click_text|set_text|scroll_forward|scroll_backward|tap|swipe|back|home\",\"id\":12,\"query\":\"видимий текст\",\"text\":\"текст\",\"target\":\"підпис поля\",\"x\":500,\"y\":800,\"start_x\":500,\"start_y\":1400,\"end_x\":500,\"end_y\":400,\"duration_ms\":350}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "snapshot", "scroll_forward", "scroll_backward", "back", "home" -> RiskLevel.LOW
        "click_id", "click_text", "long_click_text", "set_text", "tap", "swipe" -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "snapshot" -> "Прочитати структуру поточного екрана"
        "click_id" -> "Натиснути елемент #${arguments.optInt("id", -1)}"
        "click_text" -> "Натиснути «${arguments.optString("query") }»"
        "long_click_text" -> "Довго натиснути «${arguments.optString("query") }»"
        "set_text" -> "Ввести текст у поле ${arguments.optString("target", "введення") }"
        "scroll_forward" -> "Прокрутити екран"
        "scroll_backward" -> "Прокрутити екран назад"
        "tap" -> "Натиснути координати ${arguments.optDouble("x")}, ${arguments.optDouble("y")}"
        "swipe" -> "Виконати координатний жест"
        "back" -> "Повернутися назад"
        "home" -> "Перейти на головний екран"
        else -> "Виконати дію з інтерфейсом"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult {
        val result = when (arguments.optString("operation")) {
            "snapshot" -> AgentAccessibilityService.snapshot()
            "click_id" -> AgentAccessibilityService.clickId(arguments.optInt("id", -1))
            "click_text" -> AgentAccessibilityService.clickText(arguments.optString("query"))
            "long_click_text" -> AgentAccessibilityService.longClickText(arguments.optString("query"))
            "set_text" -> AgentAccessibilityService.setText(
                arguments.optString("text"),
                arguments.optString("target").takeIf { it.isNotBlank() },
            )
            "scroll_forward" -> AgentAccessibilityService.scrollForward()
            "scroll_backward" -> AgentAccessibilityService.scrollBackward()
            "tap" -> AgentAccessibilityService.tap(
                arguments.optDouble("x", -1.0).toFloat(),
                arguments.optDouble("y", -1.0).toFloat(),
            )
            "swipe" -> AgentAccessibilityService.swipe(
                arguments.optDouble("start_x", -1.0).toFloat(),
                arguments.optDouble("start_y", -1.0).toFloat(),
                arguments.optDouble("end_x", -1.0).toFloat(),
                arguments.optDouble("end_y", -1.0).toFloat(),
                arguments.optLong("duration_ms", 350),
            )
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
