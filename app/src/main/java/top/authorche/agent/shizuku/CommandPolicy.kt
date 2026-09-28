package top.authorche.agent.shizuku

import org.json.JSONObject
import top.authorche.agent.agent.RiskLevel

data class PreparedCommand(
    val command: List<String>,
    val summary: String,
    val risk: RiskLevel,
)

object CommandPolicy {
    private val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private val permissionPattern = Regex("^android\\.permission\\.[A-Z0-9_]+$")
    private val settingPattern = Regex("^[a-zA-Z0-9_.-]{1,100}$")

    fun prepare(arguments: JSONObject): PreparedCommand {
        return when (val operation = arguments.requireText("operation")) {
            "force_stop" -> {
                val packageName = arguments.requirePackage()
                PreparedCommand(
                    listOf("am", "force-stop", packageName),
                    "Примусово зупинити $packageName",
                    RiskLevel.HIGH,
                )
            }
            "open_settings" -> {
                val action = arguments.optString("action", "android.settings.SETTINGS")
                require(action in allowedSettingsActions) { "Ця сторінка налаштувань не дозволена" }
                PreparedCommand(
                    listOf("am", "start", "-a", action),
                    "Відкрити системні налаштування",
                    RiskLevel.MEDIUM,
                )
            }
            "keyevent" -> {
                val key = arguments.requireText("key").uppercase()
                val code = allowedKeys[key] ?: error("Клавішу $key не дозволено")
                PreparedCommand(
                    listOf("input", "keyevent", code),
                    "Надіслати системну клавішу $key",
                    RiskLevel.LOW,
                )
            }
            "grant_permission", "revoke_permission" -> {
                val packageName = arguments.requirePackage()
                val permission = arguments.requireText("permission")
                require(permissionPattern.matches(permission)) { "Некоректний Android permission" }
                val verb = if (operation == "grant_permission") "grant" else "revoke"
                PreparedCommand(
                    listOf("pm", verb, packageName, permission),
                    "${if (verb == "grant") "Надати" else "Відкликати"} $permission для $packageName",
                    RiskLevel.HIGH,
                )
            }
            "settings_put" -> {
                val namespace = arguments.requireText("namespace")
                val name = arguments.requireText("name")
                val value = arguments.requireText("value")
                require(namespace in setOf("system", "secure", "global")) { "Некоректний простір settings" }
                require(settingPattern.matches(name)) { "Некоректна назва settings" }
                require(value.length <= 200 && '\n' !in value && '\u0000' !in value) { "Некоректне значення settings" }
                PreparedCommand(
                    listOf("settings", "put", namespace, name, value),
                    "Змінити системний параметр $namespace/$name",
                    RiskLevel.HIGH,
                )
            }
            "current_activity" -> PreparedCommand(
                listOf("dumpsys", "activity", "activities"),
                "Прочитати стан активного застосунку",
                RiskLevel.LOW,
            )
            else -> error("Операція Shizuku «$operation» не входить до білого списку")
        }
    }

    private fun JSONObject.requirePackage(): String = requireText("package").also {
        require(packagePattern.matches(it)) { "Некоректна назва пакета" }
    }

    private fun JSONObject.requireText(name: String): String = optString(name).trim().also {
        require(it.isNotEmpty()) { "Відсутній аргумент $name" }
    }

    private val allowedKeys = mapOf(
        "BACK" to "4",
        "HOME" to "3",
        "ENTER" to "66",
        "TAB" to "61",
        "MEDIA_PLAY_PAUSE" to "85",
        "VOLUME_UP" to "24",
        "VOLUME_DOWN" to "25",
    )

    private val allowedSettingsActions = setOf(
        "android.settings.SETTINGS",
        "android.settings.ACCESSIBILITY_SETTINGS",
        "android.settings.APPLICATION_SETTINGS",
        "android.settings.WIFI_SETTINGS",
        "android.settings.BLUETOOTH_SETTINGS",
        "android.settings.NOTIFICATION_SETTINGS",
    )
}
