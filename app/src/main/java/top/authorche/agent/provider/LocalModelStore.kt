package top.authorche.agent.provider

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalModelStore(
    private val context: Context,
    private val settings: AgentSettingsStore,
) {
    suspend fun import(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.takeIf { it.endsWith(".gguf", ignoreCase = true) }
            ?: "authorai-model.gguf"
        val modelsDir = File(context.filesDir, "models").apply {
            check(exists() || mkdirs()) { "Не вдалося створити каталог моделей" }
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(modelsDir, safeName)
        val temporary = File(modelsDir, "$safeName.part")
        val previous = settings.load().localModelPath

        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не вдалося відкрити GGUF-файл" }
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
            val magic = temporary.inputStream().use { stream ->
                ByteArray(4).also { bytes ->
                    require(stream.read(bytes) == bytes.size) { "GGUF-файл пошкоджений або порожній" }
                }
            }
            require(magic.contentEquals(GGUF_MAGIC)) { "Файл не має заголовка GGUF" }

            if (target.exists()) check(target.delete()) { "Не вдалося замінити попередню модель" }
            check(temporary.renameTo(target)) { "Не вдалося завершити імпорт моделі" }
            settings.saveLocalModel(target.absolutePath)
            File(previous).takeIf { old ->
                previous.isNotBlank() && old != target && old.parentFile == modelsDir
            }?.delete()
            target
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        val GGUF_MAGIC = byteArrayOf(
            'G'.code.toByte(),
            'G'.code.toByte(),
            'U'.code.toByte(),
            'F'.code.toByte(),
        )
    }
}
