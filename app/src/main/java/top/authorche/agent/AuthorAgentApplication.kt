package top.authorche.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import top.authorche.agent.shizuku.ShizukuBridge

class AuthorAgentApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        graph = AppGraph(this)
        ShizukuBridge.initialize(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS_CHANNEL,
                "Нагадування агента",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Нагадування, які створив Jarvis | AuthorAi"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PHONE_FINDER_CHANNEL,
                "Пошук телефона",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Гучний сигнал Jarvis | AuthorAi для пошуку телефона"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        const val REMINDERS_CHANNEL = "agent_reminders"
        const val PHONE_FINDER_CHANNEL = "jarvis_phone_finder"
    }
}
