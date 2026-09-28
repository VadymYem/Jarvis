package top.authorche.agent.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import java.time.Instant
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class SystemIntentTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val definition = ToolDefinition(
        name = "system_intent",
        description = "Відкрити безпечну системну дію: вебпошук, HTTPS-сторінку, карту, номер у телефоні, SMS-чернетку, календарну подію, будильник, таймер, камеру або системне поширення тексту. Не надсилає SMS і не телефонує без участі користувача",
        arguments = "{\"operation\":\"web_search|open_url|map|dial|sms_draft|calendar_event|set_alarm|set_timer|camera|share_text\",\"query\":\"пошук або місце\",\"url\":\"https://...\",\"phone\":\"+49...\",\"message\":\"текст\",\"title\":\"назва\",\"description\":\"опис\",\"location\":\"місце\",\"start_epoch_ms\":0,\"end_epoch_ms\":0,\"hour\":7,\"minute\":30,\"seconds\":300,\"text\":\"текст\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "web_search", "open_url", "map", "camera" -> RiskLevel.LOW
        "dial", "sms_draft", "calendar_event", "set_alarm", "set_timer", "share_text" -> RiskLevel.MEDIUM
        else -> RiskLevel.CRITICAL
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "web_search" -> "Знайти в інтернеті: ${arguments.optString("query").take(100)}"
        "open_url" -> "Відкрити ${arguments.optString("url").take(120)}"
        "map" -> "Показати на карті: ${arguments.optString("query").take(100)}"
        "dial" -> "Відкрити номер ${arguments.optString("phone") } у телефоні"
        "sms_draft" -> "Підготувати SMS для ${arguments.optString("phone") }"
        "calendar_event" -> "Підготувати подію «${arguments.optString("title") }»"
        "set_alarm" -> "Підготувати будильник ${arguments.optInt("hour")}:${arguments.optInt("minute").toString().padStart(2, '0')}"
        "set_timer" -> "Підготувати таймер на ${arguments.optInt("seconds")} с"
        "camera" -> "Відкрити камеру"
        "share_text" -> "Відкрити системне меню поширення тексту"
        else -> "Некоректна системна дія"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        val operation = arguments.optString("operation")
        val intent = when (operation) {
            "web_search" -> webSearch(arguments.requireText("query"))
            "open_url" -> openUrl(arguments.requireText("url"))
            "map" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(arguments.requireText("query"))}"))
            "dial" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${validatePhone(arguments.requireText("phone"))}"))
            "sms_draft" -> Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("smsto:${validatePhone(arguments.requireText("phone"))}"),
            ).putExtra("sms_body", arguments.optString("message").take(4_000))
            "calendar_event" -> calendarEvent(arguments)
            "set_alarm" -> alarm(arguments)
            "set_timer" -> timer(arguments)
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "share_text" -> Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, arguments.requireText("text").take(20_000))
                    arguments.optString("title").takeIf(String::isNotBlank)?.let {
                        putExtra(Intent.EXTRA_SUBJECT, it.take(300))
                    }
                },
                "Поділитися через…",
            )
            else -> error("Непідтримувана системна дія")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        appContext.startActivity(intent)
        ToolResult(true, "Системну дію «$operation» відкрито")
    }.getOrElse { ToolResult(false, it.message ?: "Системну дію не виконано") }

    private fun webSearch(query: String): Intent {
        val nativeSearch = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        return if (nativeSearch.resolveActivity(appContext.packageManager) != null) nativeSearch else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        }
    }

    private fun openUrl(url: String): Intent {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Дозволені лише коректні HTTPS-посилання" }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    private fun calendarEvent(arguments: JSONObject): Intent {
        val title = arguments.requireText("title").take(300)
        val start = arguments.optLong("start_epoch_ms", -1)
        val end = arguments.optLong("end_epoch_ms", start + 3_600_000)
        require(start >= Instant.now().minusSeconds(86_400).toEpochMilli()) { "Некоректний час початку" }
        require(end > start && end - start <= 31_536_000_000L) { "Некоректний час завершення" }
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.DESCRIPTION, arguments.optString("description").take(4_000))
            putExtra(CalendarContract.Events.EVENT_LOCATION, arguments.optString("location").take(500))
        }
    }

    private fun alarm(arguments: JSONObject): Intent {
        val hour = arguments.optInt("hour", -1)
        val minute = arguments.optInt("minute", -1)
        require(hour in 0..23 && minute in 0..59) { "Некоректний час будильника" }
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, arguments.optString("title", "Jarvis | AuthorAi").take(200))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    private fun timer(arguments: JSONObject): Intent {
        val seconds = arguments.optInt("seconds", -1)
        require(seconds in 1..604_800) { "Таймер має бути від 1 секунди до 7 днів" }
        return Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, arguments.optString("title", "Jarvis | AuthorAi").take(200))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    private fun validatePhone(value: String): String = value.also {
        require(Regex("^\\+?[0-9 ()-]{3,30}$").matches(it)) { "Некоректний номер телефона" }
    }

    private fun JSONObject.requireText(name: String): String = optString(name).trim().also {
        require(it.isNotBlank()) { "Не вказано $name" }
    }
}
