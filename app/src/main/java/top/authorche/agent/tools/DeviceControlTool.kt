package top.authorche.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.view.KeyEvent
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class DeviceControlTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)

    override val definition = ToolDefinition(
        name = "device_control",
        description = "Прочитати стан пристрою та батареї, встановити/змінити гучність або керувати відтворенням медіа",
        arguments = "{\"operation\":\"device_status|battery_status|set_volume|adjust_volume|media_control\",\"stream\":\"media|ring|alarm|notification|voice_call\",\"percent\":50,\"direction\":\"up|down|mute|unmute\",\"action\":\"play_pause|play|pause|stop|next|previous\"}",
    )

    override fun risk(arguments: JSONObject): RiskLevel = when (arguments.optString("operation")) {
        "device_status", "battery_status" -> RiskLevel.LOW
        "set_volume", "adjust_volume", "media_control" -> RiskLevel.MEDIUM
        else -> RiskLevel.CRITICAL
    }

    override fun summarize(arguments: JSONObject): String = when (arguments.optString("operation")) {
        "device_status" -> "Прочитати основний стан пристрою"
        "battery_status" -> "Прочитати детальний стан батареї"
        "set_volume" -> "Встановити ${arguments.optString("stream", "media")} гучність на ${arguments.optInt("percent")}%"
        "adjust_volume" -> "Змінити ${arguments.optString("stream", "media")} гучність: ${arguments.optString("direction") }"
        "media_control" -> "Керувати медіа: ${arguments.optString("action") }"
        else -> "Некоректна дія пристрою"
    }

    override suspend fun execute(arguments: JSONObject): ToolResult = runCatching {
        when (arguments.optString("operation")) {
            "device_status" -> deviceStatus()
            "battery_status" -> batteryStatus()
            "set_volume" -> setVolume(arguments)
            "adjust_volume" -> adjustVolume(arguments)
            "media_control" -> mediaControl(arguments)
            else -> error("Непідтримувана дія пристрою")
        }
    }.getOrElse { ToolResult(false, it.message ?: "Дію пристрою не виконано") }

    private fun deviceStatus(): ToolResult {
        val media = volumePercent(AudioManager.STREAM_MUSIC)
        val ring = volumePercent(AudioManager.STREAM_RING)
        return ToolResult(
            true,
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
                "пристрій ${Build.MANUFACTURER} ${Build.MODEL}; медіа $media%; дзвінок $ring%",
        )
    }

    private fun batteryStatus(): ToolResult {
        val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else -1
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature = (status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val charging = plugged != 0
        return ToolResult(true, "Батарея $percent%; заряджання: ${if (charging) "так" else "ні"}; температура $temperature °C")
    }

    private fun setVolume(arguments: JSONObject): ToolResult {
        val stream = stream(arguments.optString("stream", "media"))
        val percent = arguments.optInt("percent", -1)
        require(percent in 0..100) { "Гучність має бути від 0 до 100%" }
        val value = (audio.getStreamMaxVolume(stream) * percent / 100f).toInt()
        audio.setStreamVolume(stream, value, AudioManager.FLAG_SHOW_UI)
        return ToolResult(true, "Гучність встановлено на ${volumePercent(stream)}%")
    }

    private fun adjustVolume(arguments: JSONObject): ToolResult {
        val stream = stream(arguments.optString("stream", "media"))
        val direction = when (arguments.optString("direction")) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> error("Некоректний напрям зміни гучності")
        }
        audio.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        return ToolResult(true, "Поточна гучність ${volumePercent(stream)}%")
    }

    private fun mediaControl(arguments: JSONObject): ToolResult {
        val code = when (arguments.optString("action")) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> error("Некоректна медіадія")
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return ToolResult(true, "Медіадію виконано")
    }

    private fun stream(value: String): Int = when (value) {
        "media" -> AudioManager.STREAM_MUSIC
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "voice_call" -> AudioManager.STREAM_VOICE_CALL
        else -> error("Непідтримуваний аудіопотік")
    }

    private fun volumePercent(stream: Int): Int =
        (audio.getStreamVolume(stream) * 100f / audio.getStreamMaxVolume(stream).coerceAtLeast(1)).toInt()
}
