package top.authorche.agent.shizuku

import android.content.Context
import android.os.Process
import java.util.concurrent.TimeUnit

class PrivilegedService() : IPrivilegedService.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun uid(): Int = Process.myUid()

    override fun execute(command: Array<out String>): String {
        require(command.isNotEmpty()) { "Порожня команда" }
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Команда перевищила ліміт 15 секунд")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.take(32_000)
        check(process.exitValue() == 0) {
            "Код ${process.exitValue()}: ${output.ifBlank { "без повідомлення" }}"
        }
        return output.ifBlank { "OK" }
    }
}
