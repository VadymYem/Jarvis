package top.authorche.agent.tools

import android.content.Context
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.provider.AgentSettingsStore

object AndroidTools {
    fun create(context: Context, settings: AgentSettingsStore): List<AgentTool> = listOf(
        AppLaunchTool(context),
        DeviceSearchTool(context, settings),
        WebSearchTool(settings),
        DeviceStatusTool(context),
        OpenSettingsTool(context),
        FindPhoneTool(context),
        UiAutomationTool(),
        ReminderTool(context),
        ShizukuTool(),
    )
}
