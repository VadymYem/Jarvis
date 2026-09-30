package top.authorche.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class EmailAppTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val definition = ToolDefinition(
        name = "email_app",
        description = "Відкрити вхідні поштового застосунку або підготувати видиму користувачу чернетку. Для пошуку й читання листів після відкриття використовуй UI tools. Самостійно не надсилає лист",
        arguments = "{\"operation\":\"open_inbox|draft\",\"to\":\"user@example.com\",\"subject\":\"тема\",\"body\":\"текст\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "open_inbox" -> RiskLevel.LOW
        "draft" -> RiskLevel.MEDIUM
        else -> RiskLevel.CRITICAL
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "open_inbox" -> "Відкрити поштову скриньку"
        "draft" -> "Підготувати чернетку листа для ${arguments.optString("to", "одержувача")}"
        else -> "Невідома поштова операція"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        runCatching {
            when (arguments.optString("operation")) {
                "open_inbox" -> openInbox()
                "draft" -> openDraft(arguments)
                else -> error("Непідтримувана поштова операція")
            }
        }.getOrElse { ToolResult(false, it.message ?: "Не вдалося відкрити поштовий застосунок") }
    }

    private fun openInbox(): ToolResult {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(intent.resolveActivity(appContext.packageManager) != null) {
            "Поштовий застосунок не знайдено"
        }
        appContext.startActivity(intent)
        return ToolResult(true, "Поштову скриньку відкрито; можна продовжити через Accessibility")
    }

    private fun openDraft(arguments: JSONObject): ToolResult {
        val recipients = arguments.optString("to")
            .split(',', ';')
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(recipients.isNotEmpty()) { "Не вказано одержувача" }
        require(recipients.size <= 10 && recipients.all(::isEmail)) { "Некоректна адреса одержувача" }
        val subject = arguments.optString("subject").trim().take(300)
        val body = arguments.optString("body").take(20_000)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${recipients.joinToString(",")}")
            putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        require(intent.resolveActivity(appContext.packageManager) != null) {
            "Поштовий застосунок не знайдено"
        }
        appContext.startActivity(intent)
        return ToolResult(true, "Чернетку відкрито в поштовому застосунку. Надсилання залишено користувачу")
    }

    private fun isEmail(value: String): Boolean =
        value.length <= 254 && EMAIL.matches(value)

    private companion object {
        val EMAIL = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
    }
}
