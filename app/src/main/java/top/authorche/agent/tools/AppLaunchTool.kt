package top.authorche.agent.tools

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class AppLaunchTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val definition = ToolDefinition(
        name = "launch_app",
        description = "Відкрити встановлений застосунок за назвою або package name",
        arguments = "{\"app\":\"назва застосунку або package.name\"}",
    )

    override fun risk(arguments: JSONObject) = RiskLevel.LOW

    override fun summarize(arguments: JSONObject): String =
        "Відкрити ${arguments.optString("app", "застосунок")}"

    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        runCatching {
            val query = arguments.optString("app").trim()
            require(query.isNotEmpty()) { "Не вказано застосунок" }
            val launcher = appContext.getSystemService(LauncherApps::class.java)
            val activities = launcher.getActivityList(null, Process.myUserHandle())
            val exact = activities.firstOrNull { it.applicationInfo.packageName.equals(query, true) }
            val match = exact ?: activities.firstOrNull {
                it.label.toString().equals(query, true)
            } ?: activities.firstOrNull {
                it.label.toString().contains(query, true)
            } ?: error("Застосунок «$query» не знайдено")

            launcher.startMainActivity(
                ComponentName(match.applicationInfo.packageName, match.name),
                Process.myUserHandle(),
                null,
                null,
            )
            ToolResult(true, "Відкрито ${match.label} (${match.applicationInfo.packageName})")
        }.getOrElse { ToolResult(false, it.message ?: "Не вдалося відкрити застосунок") }
    }
}
