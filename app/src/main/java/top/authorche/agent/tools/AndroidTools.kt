package top.authorche.agent.tools

import android.content.Context
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.voice.PhoneFinderController

object AndroidTools {
    fun create(context: Context, phoneFinder: PhoneFinderController): List<AgentTool> = listOf(
        AppLaunchTool(context),
        UiAutomationTool(),
        ReminderTool(context),
        FindPhoneTool(phoneFinder),
        ShizukuTool(),
    )
}
