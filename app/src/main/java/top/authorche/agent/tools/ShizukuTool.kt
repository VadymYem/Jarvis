package top.authorche.agent.tools

import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult
import top.authorche.agent.shizuku.CommandPolicy
import top.authorche.agent.shizuku.ShizukuBridge

class ShizukuTool : AgentTool {
    override val definition = ToolDefinition(
        name = "shizuku_action",
        description = "Виконати дозволену ADB/shell-дію через Shizuku. Довільні shell-команди заборонені",
        arguments = "{\"operation\":\"force_stop|open_settings|keyevent|grant_permission|revoke_permission|settings_put|current_activity\",\"package\":\"package.name\",\"permission\":\"android.permission.NAME\",\"action\":\"android.settings.SETTINGS\",\"key\":\"BACK\",\"namespace\":\"system|secure|global\",\"name\":\"setting_name\",\"value\":\"value\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel =
        runCatching { CommandPolicy.prepare(arguments).risk }.getOrDefault(RiskLevel.CRITICAL)

    override fun summarize(arguments: JSONObject): String =
        runCatching { CommandPolicy.prepare(arguments).summary }
            .getOrElse { "Некоректна Shizuku-операція" }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        val prepared = CommandPolicy.prepare(arguments)
        val output = ShizukuBridge.execute(prepared.command)
        ToolResult(true, output.take(8_000))
    }.getOrElse {
        ToolResult(false, it.message ?: "Shizuku-операцію не виконано")
    }
}
