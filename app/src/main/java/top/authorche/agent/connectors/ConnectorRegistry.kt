package top.authorche.agent.connectors

import android.content.Context
import android.content.Intent
import android.net.Uri
import top.authorche.agent.termux.TermuxBridge

enum class ConnectorState {
    READY,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
}

data class ConnectorStatus(
    val id: String,
    val title: String,
    val state: ConnectorState,
    val detail: String,
)

object ConnectorRegistry {
    fun email(context: Context): ConnectorStatus {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val available = intent.resolveActivity(context.packageManager) != null
        return ConnectorStatus(
            id = "email_app",
            title = "Пошта",
            state = if (available) ConnectorState.READY else ConnectorState.UNAVAILABLE,
            detail = if (available) {
                "Чернетки та робота з листами через поштовий застосунок"
            } else {
                "Сумісний поштовий застосунок не знайдено"
            },
        )
    }

    fun termux(context: Context): ConnectorStatus = when {
        !TermuxBridge.isInstalled(context) -> ConnectorStatus(
            id = "termux",
            title = "Termux / ADB",
            state = ConnectorState.UNAVAILABLE,
            detail = "Termux не встановлено",
        )
        !TermuxBridge.hasPermission(context) -> ConnectorStatus(
            id = "termux",
            title = "Termux / ADB",
            state = ConnectorState.PERMISSION_REQUIRED,
            detail = "Потрібен дозвіл RUN_COMMAND",
        )
        else -> ConnectorStatus(
            id = "termux",
            title = "Termux / ADB",
            state = ConnectorState.READY,
            detail = "Дозволені ADB-операції та сценарії Jarvis готові",
        )
    }
}
