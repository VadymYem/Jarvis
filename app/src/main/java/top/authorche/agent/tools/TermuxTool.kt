package top.authorche.agent.tools

import android.content.Context
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult
import top.authorche.agent.termux.TermuxBridge
import top.authorche.agent.termux.TermuxCommandPolicy

class TermuxTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val definition = ToolDefinition(
        name = "termux_action",
        description = "Виконати дозволену операцію через Termux RUN_COMMAND: перегляд/підключення ADB, обмежені ADB-дії або користувацький executable-сценарій з ~/.jarvis/actions. Довільний shell-текст заборонений",
        arguments = "{\"operation\":\"adb_devices|adb_connect|adb_disconnect|adb_force_stop|adb_keyevent|run_script\",\"target\":\"host:port\",\"serial\":\"adb-serial\",\"package\":\"package.name\",\"key\":\"BACK|HOME|ENTER|TAB|POWER|VOLUME_UP|VOLUME_DOWN|MEDIA_PLAY_PAUSE\",\"script\":\"script-name\",\"arguments\":[\"arg\"]}",
    )

    override fun risk(arguments: JSONObject): RiskLevel =
        runCatching { TermuxCommandPolicy.prepare(arguments).risk }.getOrDefault(RiskLevel.CRITICAL)

    override fun summarize(arguments: JSONObject): String =
        runCatching { TermuxCommandPolicy.prepare(arguments).summary }
            .getOrElse { "Некоректна Termux-операція" }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        val command = TermuxCommandPolicy.prepare(arguments)
        val result = TermuxBridge.execute(appContext, command)
        val output = buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trim())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(result.stderr.trim())
            }
            if (result.error.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(result.error)
            }
            if (isEmpty()) append("Termux завершив команду з кодом ${result.exitCode}")
        }
        ToolResult(result.successful, output.take(8_000))
    }.getOrElse {
        ToolResult(
            false,
            if (it is TimeoutCancellationException) {
                "Termux не повернув результат за 30 секунд"
            } else {
                it.message ?: "Termux-операцію не виконано"
            },
        )
    }
}
