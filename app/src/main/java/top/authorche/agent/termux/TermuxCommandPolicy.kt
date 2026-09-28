package top.authorche.agent.termux

import org.json.JSONArray
import org.json.JSONObject
import top.authorche.agent.agent.RiskLevel

data class PreparedTermuxCommand(
    val executable: String,
    val arguments: List<String>,
    val summary: String,
    val risk: RiskLevel,
)

object TermuxCommandPolicy {
    private const val BIN = "/data/data/com.termux/files/usr/bin"
    private const val ACTIONS = "/data/data/com.termux/files/home/.jarvis/actions"
    private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")
    private val serial = Regex("^[A-Za-z0-9._:-]{1,255}$")
    private val scriptName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private val keyEvents = mapOf(
        "BACK" to "4",
        "HOME" to "3",
        "ENTER" to "66",
        "TAB" to "61",
        "POWER" to "26",
        "VOLUME_UP" to "24",
        "VOLUME_DOWN" to "25",
        "MEDIA_PLAY_PAUSE" to "85",
    )

    fun prepare(arguments: JSONObject): PreparedTermuxCommand = when (arguments.optString("operation")) {
        "adb_devices" -> PreparedTermuxCommand(
            executable = "$BIN/adb",
            arguments = listOf("devices", "-l"),
            summary = "Переглянути підключені ADB-пристрої через Termux",
            risk = RiskLevel.MEDIUM,
        )
        "adb_connect" -> {
            val target = requireTarget(arguments)
            PreparedTermuxCommand(
                "$BIN/adb",
                listOf("connect", target),
                "Підключити ADB-пристрій $target через Termux",
                RiskLevel.HIGH,
            )
        }
        "adb_disconnect" -> {
            val target = requireTarget(arguments)
            PreparedTermuxCommand(
                "$BIN/adb",
                listOf("disconnect", target),
                "Від'єднати ADB-пристрій $target",
                RiskLevel.HIGH,
            )
        }
        "adb_force_stop" -> {
            val target = requireSerial(arguments)
            val app = arguments.optString("package").also {
                require(packageName.matches(it)) { "Некоректний package name" }
            }
            PreparedTermuxCommand(
                "$BIN/adb",
                listOf("-s", target, "shell", "am", "force-stop", app),
                "Зупинити $app на ADB-пристрої $target",
                RiskLevel.HIGH,
            )
        }
        "adb_keyevent" -> {
            val target = requireSerial(arguments)
            val key = arguments.optString("key").uppercase()
            val code = keyEvents[key] ?: error("Ця клавіша не дозволена")
            PreparedTermuxCommand(
                "$BIN/adb",
                listOf("-s", target, "shell", "input", "keyevent", code),
                "Надіслати клавішу $key на ADB-пристрій $target",
                RiskLevel.HIGH,
            )
        }
        "run_script" -> {
            val name = arguments.optString("script").also {
                require(scriptName.matches(it)) { "Некоректна назва сценарію" }
            }
            val scriptArguments = arguments.optJSONArray("arguments").toStringList()
            PreparedTermuxCommand(
                "$ACTIONS/$name",
                scriptArguments,
                "Запустити підтверджений сценарій Termux $name",
                RiskLevel.HIGH,
            )
        }
        else -> error("Невідома або довільна Termux-команда заборонена")
    }

    private fun requireTarget(arguments: JSONObject): String =
        arguments.optString("target").also {
            val port = it.substringAfterLast(':').toIntOrNull()
            require(serial.matches(it) && port != null && port in 1..65535) {
                "Очікується адреса host:port"
            }
        }

    private fun requireSerial(arguments: JSONObject): String =
        arguments.optString("serial").also {
            require(serial.matches(it)) { "Некоректний ADB serial" }
        }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        require(length() <= 20) { "Забагато аргументів сценарію" }
        return (0 until length()).map { index ->
            getString(index).also {
                require(it.length <= 500 && '\u0000' !in it) { "Некоректний аргумент сценарію" }
            }
        }
    }
}
