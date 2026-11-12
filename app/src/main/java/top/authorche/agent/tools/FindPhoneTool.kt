package top.authorche.agent.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult

class FindPhoneTool(context: Context) : AgentTool {
    private val app = context.applicationContext
    private var player: MediaPlayer? = null
    override val definition = ToolDefinition("find_phone",
        "Відтворити вибрану користувачем пісню для пошуку телефону (до 45 секунд)", "{}")
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Подати звуковий сигнал для пошуку телефону"
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        runCatching {
            val file = app.getFileStreamPath("find_phone.mp3")
            require(file.isFile && file.length() > 0) {
                "Спочатку виберіть MP3 у блоці «Звук пошуку телефону»"
            }
            player?.release()
            val next = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
                start()
            }
            player = next
            Handler(Looper.getMainLooper()).postDelayed({
                if (player === next) { next.release(); player = null }
            }, 45_000)
            ToolResult(true, "Звук увімкнено на 45 секунд")
        }.getOrElse { ToolResult(false, it.message ?: "Не вдалося відтворити MP3") }
    }
}
