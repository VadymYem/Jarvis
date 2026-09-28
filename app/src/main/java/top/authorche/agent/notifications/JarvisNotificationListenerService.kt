package top.authorche.agent.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import top.authorche.agent.BuildConfig

data class JarvisNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val canReply: Boolean,
)

object NotificationBridge {
    private var service = WeakReference<JarvisNotificationListenerService>(null)
    private val recent = LinkedHashMap<String, JarvisNotification>()

    @Synchronized
    fun attach(value: JarvisNotificationListenerService) {
        service = WeakReference(value)
        value.activeNotifications?.forEach(::record)
    }

    @Synchronized
    fun detach(value: JarvisNotificationListenerService) {
        if (service.get() === value) service.clear()
    }

    fun isConnected(): Boolean = service.get() != null

    @Synchronized
    fun record(sbn: StatusBarNotification) {
        if (sbn.packageName == BuildConfig.APPLICATION_ID) return
        val notification = sbn.notification
        val extras = notification.extras
        val secret = notification.visibility == Notification.VISIBILITY_SECRET
        val title = if (secret) "Приховане сповіщення" else extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
            .sanitize()
        val text = if (secret) "Вміст приховано системою" else listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        )
            .distinct()
            .joinToString(" · ")
            .sanitize()
        recent[sbn.key] = JarvisNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title.take(300),
            text = text.take(1_500),
            postedAt = sbn.postTime,
            canReply = findReplyAction(notification) != null,
        )
        while (recent.size > MAX_NOTIFICATIONS) recent.remove(recent.keys.first())
    }

    @Synchronized
    fun remove(key: String) {
        recent.remove(key)
    }

    @Synchronized
    fun list(packageFilter: String?, limit: Int): List<JarvisNotification> = recent.values
        .asSequence()
        .filter { packageFilter.isNullOrBlank() || it.packageName.equals(packageFilter, true) }
        .sortedByDescending { it.postedAt }
        .take(limit.coerceIn(1, 30))
        .toList()

    fun dismiss(key: String): Result<Unit> = runCatching {
        val listener = service.get() ?: error("Надайте Jarvis доступ до сповіщень")
        require(listener.activeNotifications.orEmpty().any { it.key == key }) { "Сповіщення вже недоступне" }
        listener.cancelNotification(key)
        remove(key)
    }

    fun reply(key: String, message: String): Result<Unit> = runCatching {
        val listener = service.get() ?: error("Надайте Jarvis доступ до сповіщень")
        val sbn = listener.activeNotifications.orEmpty().firstOrNull { it.key == key }
            ?: error("Сповіщення вже недоступне")
        val action = findReplyAction(sbn.notification) ?: error("Сповіщення не підтримує швидку відповідь")
        val remoteInputs = action.remoteInputs ?: error("Поле швидкої відповіді недоступне")
        val fillIn = Intent()
        val results = Bundle().apply {
            remoteInputs.forEach { putCharSequence(it.resultKey, message) }
        }
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
        action.actionIntent.send(listener, 0, fillIn)
    }

    private fun findReplyAction(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action ->
            !action.remoteInputs.isNullOrEmpty() &&
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY) ||
                    action.title?.toString()?.contains("reply", true) == true ||
                    action.title?.toString()?.contains("відпов", true) == true ||
                    action.title?.toString()?.contains("antwort", true) == true)
        } ?: notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

    private fun String.sanitize(): String = replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MAX_NOTIFICATIONS = 40
}

class JarvisNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.attach(this)
    }

    override fun onListenerDisconnected() {
        NotificationBridge.detach(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let(NotificationBridge::record)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(NotificationBridge::remove)
    }

    override fun onDestroy() {
        NotificationBridge.detach(this)
        super.onDestroy()
    }
}
