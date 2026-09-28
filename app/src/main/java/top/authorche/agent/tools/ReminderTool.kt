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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
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
        description = "Створити локальне нагадування через задану кількість хвилин",
        arguments = "{\"title\":\"заголовок\",\"message\":\"текст\",\"delay_minutes\":30}",
    )

    override fun risk(arguments: JSONObject) = RiskLevel.LOW

    override fun summarize(arguments: JSONObject): String =
        "Створити нагадування «${arguments.optString("title", "Нагадування") }»"

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        val title = arguments.optString("title", "Нагадування Jarvis | AuthorAi").take(120)
        val message = arguments.optString("message").take(500)
        val delay = arguments.optLong("delay_minutes", -1)
        require(delay in 1..525_600) { "Затримка має бути від 1 хвилини до 1 року" }
        require(message.isNotBlank()) { "Текст нагадування порожній" }

        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_MESSAGE, message)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setInputData(data)
            .addTag("author_ai_reminder")
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
        ToolResult(true, "Нагадування заплановано через $delay хв")
    }.getOrElse { ToolResult(false, it.message ?: "Нагадування не створено") }
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
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
    }
}
