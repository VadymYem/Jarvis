package top.authorche.agent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class ClipboardTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)

    override val definition = ToolDefinition(
        name = "clipboard",
        description = "Прочитати, записати або очистити системний буфер обміну. Читання може містити приватні дані й завжди потребує підтвердження",
        arguments = "{\"operation\":\"read|write|clear\",\"text\":\"текст\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "write", "clear" -> RiskLevel.MEDIUM
        "read" -> RiskLevel.HIGH
        else -> RiskLevel.CRITICAL
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "read" -> "Прочитати системний буфер обміну"
        "write" -> "Записати текст у системний буфер обміну"
        "clear" -> "Очистити системний буфер обміну"
        else -> "Некоректна дія буфера обміну"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        when (arguments.optString("operation")) {
            "read" -> {
                val text = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
                    .orEmpty()
                    .take(4_000)
                ToolResult(true, if (text.isBlank()) "Буфер обміну порожній" else "Вміст буфера: $text")
            }
            "write" -> {
                val text = arguments.optString("text").take(20_000)
                require(text.isNotBlank()) { "Текст порожній" }
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis | AuthorAi", text))
                ToolResult(true, "Текст скопійовано")
            }
            "clear" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                ToolResult(true, "Буфер обміну очищено")
            }
            else -> error("Непідтримувана дія буфера обміну")
        }
    }.getOrElse { ToolResult(false, it.message ?: "Дію буфера обміну не виконано") }
}
