package top.authorche.agent.tools

import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult
import top.authorche.agent.voice.PhoneFinderController

class FindPhoneTool(
    private val controller: PhoneFinderController,
) : AgentTool {
    override val definition = ToolDefinition(
        name = "find_phone",
        description = "Запускає або зупиняє гучний локальний сигнал пошуку цього телефона.",
        arguments = """{"action":"start|stop"}""",
    )

    override fun risk(arguments: JSONObject): RiskLevel = RiskLevel.LOW

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("action")) {
        "stop" -> "Зупинити сигнал пошуку телефона"
        else -> "Увімкнути сигнал пошуку телефона"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult = when (arguments.optString("action")) {
        "stop" -> {
            controller.stop()
            ToolResult(true, "Сигнал пошуку телефона зупинено")
        }
        "start", "" -> {
            controller.start()
            ToolResult(true, "Сигнал пошуку телефона ввімкнено")
        }
        else -> ToolResult(false, "Невідома дія. Дозволено start або stop")
    }
}
