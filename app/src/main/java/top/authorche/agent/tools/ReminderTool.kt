package top.authorche.agent.tools

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.time.Instant
import org.json.JSONObject
import top.authorche.agent.AuthorAgentApplication
import top.authorche.agent.R
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class ReminderTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val definition = ToolDefinition(
        name = "create_reminder",
        description = "Створити, перелічити або скасувати локальні нагадування. Підтримує затримку, абсолютний epoch time і повторення від 15 хвилин",
        arguments = "{\"operation\":\"create|list|cancel\",\"id\":\"reminder-id\",\"title\":\"заголовок\",\"message\":\"текст\",\"delay_minutes\":30,\"trigger_epoch_ms\":0,\"repeat_minutes\":0}",
    )

    override fun risk(arguments: JSONObject) = when (arguments.optString("operation", "create")) {
        "list" -> RiskLevel.LOW
        "create" -> if (arguments.optLong("repeat_minutes", 0) > 0) RiskLevel.MEDIUM else RiskLevel.LOW
        "cancel" -> RiskLevel.MEDIUM
        else -> RiskLevel.CRITICAL
    }

    override fun summarize(arguments: JSONObject): String =
        when (arguments.optString("operation", "create")) {
            "create" -> "Створити нагадування «${arguments.optString("title", "Нагадування") }»"
            "list" -> "Переглянути заплановані нагадування"
            "cancel" -> "Скасувати нагадування ${arguments.optString("id") }"
            else -> "Некоректна дія нагадування"
        }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        when (arguments.optString("operation", "create")) {
            "create" -> create(arguments)
            "list" -> list()
            "cancel" -> cancel(arguments)
            else -> error("Непідтримувана дія нагадування")
        }
    }.getOrElse { ToolResult(false, it.message ?: "Дію нагадування не виконано") }

    private fun create(arguments: JSONObject): ToolResult {
        val title = arguments.optString("title", "Нагадування Jarvis | AuthorAi").take(120)
        val message = arguments.optString("message").take(500)
        val triggerEpoch = arguments.optLong("trigger_epoch_ms", 0)
        val delay = if (triggerEpoch > 0) {
            val remaining = (triggerEpoch - System.currentTimeMillis()).coerceAtLeast(1_000)
            ((remaining + 59_999) / 60_000).coerceAtLeast(1)
        } else {
            arguments.optLong("delay_minutes", -1)
        }
        val repeat = arguments.optLong("repeat_minutes", 0)
        require(delay in 1..525_600) { "Затримка має бути від 1 хвилини до 1 року" }
        require(repeat == 0L || repeat in 15..525_600) { "Період повторення має бути від 15 хвилин до 1 року" }
        require(message.isNotBlank()) { "Текст нагадування порожній" }

        val reminderId = UUID.randomUUID().toString()

        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_MESSAGE, message)
            .putString(ReminderWorker.KEY_REMINDER_ID, reminderId)
            .putBoolean(ReminderWorker.KEY_REPEATING, repeat > 0)
            .build()
        val request = if (repeat > 0) {
            PeriodicWorkRequestBuilder<ReminderWorker>(repeat, TimeUnit.MINUTES)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setInputData(data)
                .addTag(TAG_ALL)
                .addTag(tag(reminderId))
                .build()
        } else {
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setInputData(data)
                .addTag(TAG_ALL)
                .addTag(tag(reminderId))
                .build()
        }
        WorkManager.getInstance(appContext).enqueue(request)
        ReminderStore(appContext).put(
            ReminderRecord(
                id = reminderId,
                title = title,
                message = message,
                nextAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delay),
                repeatMinutes = repeat,
            ),
        )
        return ToolResult(
            true,
            "Нагадування $reminderId заплановано через $delay хв" +
                if (repeat > 0) " і повторюватиметься кожні $repeat хв" else "",
        )
    }

    private fun list(): ToolResult {
        val records = ReminderStore(appContext).list()
        if (records.isEmpty()) return ToolResult(true, "Запланованих нагадувань немає")
        return ToolResult(
            true,
            records.joinToString("\n") {
                "id=${it.id} at=${Instant.ofEpochMilli(it.nextAt)} repeat=${it.repeatMinutes} title=${it.title} message=${it.message}"
            }.take(8_000),
        )
    }

    private fun cancel(arguments: JSONObject): ToolResult {
        val id = arguments.optString("id").trim()
        require(Regex("^[0-9a-fA-F-]{36}$").matches(id)) { "Некоректний id нагадування" }
        WorkManager.getInstance(appContext).cancelAllWorkByTag(tag(id))
        val existed = ReminderStore(appContext).remove(id)
        return ToolResult(existed, if (existed) "Нагадування $id скасовано" else "Нагадування $id не знайдено")
    }

    private companion object {
        const val TAG_ALL = "author_ai_reminder"
        fun tag(id: String) = "author_ai_reminder_$id"
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure()
        }
        val title = inputData.getString(KEY_TITLE) ?: "Нагадування Jarvis | AuthorAi"
        val message = inputData.getString(KEY_MESSAGE) ?: return Result.failure()
        val notification = NotificationCompat.Builder(
            applicationContext,
            AuthorAgentApplication.REMINDERS_CHANNEL,
        )
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(id.hashCode(), notification)
        if (!inputData.getBoolean(KEY_REPEATING, false)) {
            inputData.getString(KEY_REMINDER_ID)?.let { ReminderStore(applicationContext).remove(it) }
        }
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_REPEATING = "repeating"
    }
}

private data class ReminderRecord(
    val id: String,
    val title: String,
    val message: String,
    val nextAt: Long,
    val repeatMinutes: Long,
)

private class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE)

    @Synchronized
    fun put(record: ReminderRecord) {
        val all = JSONObject(preferences.getString(KEY_RECORDS, "{}") ?: "{}")
        all.put(
            record.id,
            JSONObject()
                .put("title", record.title)
                .put("message", record.message)
                .put("nextAt", record.nextAt)
                .put("repeatMinutes", record.repeatMinutes),
        )
        preferences.edit().putString(KEY_RECORDS, all.toString()).apply()
    }

    @Synchronized
    fun list(): List<ReminderRecord> {
        val all = runCatching { JSONObject(preferences.getString(KEY_RECORDS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        return all.keys().asSequence().mapNotNull { id ->
            all.optJSONObject(id)?.let {
                ReminderRecord(
                    id,
                    it.optString("title"),
                    it.optString("message"),
                    it.optLong("nextAt"),
                    it.optLong("repeatMinutes"),
                )
            }
        }.sortedBy { it.nextAt }.toList()
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val all = runCatching { JSONObject(preferences.getString(KEY_RECORDS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        if (!all.has(id)) return false
        all.remove(id)
        preferences.edit().putString(KEY_RECORDS, all.toString()).apply()
        return true
    }

    private companion object {
        const val KEY_RECORDS = "records"
    }
}
