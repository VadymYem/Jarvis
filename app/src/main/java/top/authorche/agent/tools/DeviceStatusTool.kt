package top.authorche.agent.tools

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class DeviceStatusTool(context: Context) : AgentTool {
    private val app = context.applicationContext
    override val definition = ToolDefinition("device_status",
        "Показати заряд батареї, заряджання та доступне місце на телефоні без мережі", "{}")
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Перевірити стан телефону"
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val battery = app.getSystemService(BatteryManager::class.java)
            val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val charging = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in listOf(
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
            val storage = StatFs(Environment.getDataDirectory().absolutePath)
            val freeGiB = storage.availableBytes / 1024.0 / 1024.0 / 1024.0
            ToolResult(true, "Заряд $percent%; ${if (charging) "заряджається" else "без заряджання"}; вільно %.1f GiB".format(freeGiB))
        }.getOrElse { ToolResult(false, it.message ?: "Не вдалося прочитати стан телефону") }
    }
}

class OpenSettingsTool(context: Context) : AgentTool {
    private val app = context.applicationContext
    override val definition = ToolDefinition("open_settings",
        "Відкрити системні налаштування Wi-Fi, Bluetooth, батареї або загальні налаштування",
        "{\"page\":\"wifi|bluetooth|battery|general\"}")
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Відкрити налаштування ${arguments.optString("page")}" 
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        runCatching {
            val action = when (arguments.optString("page").lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "general" -> Settings.ACTION_SETTINGS
                else -> error("Підтримуються wifi, bluetooth, battery, general")
            }
            app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, "Відкрито системне вікно налаштувань")
        }.getOrElse { ToolResult(false, it.message ?: "Не вдалося відкрити налаштування") }
    }
}
