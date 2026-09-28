package top.authorche.agent.tools

import android.content.Context
import top.authorche.agent.agent.AgentTool

object AndroidTools {
    fun create(context: Context): List<AgentTool> = listOf(
        AppLaunchTool(context),
        UiAutomationTool(),
        ReminderTool(context),
        ShizukuTool(),
    )
}
