package top.authorche.agent.termux

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import top.authorche.agent.agent.RiskLevel

class TermuxCommandPolicyTest {
    @Test
    fun preparesAdbAsArgumentArray() {
        val command = TermuxCommandPolicy.prepare(
            JSONObject()
                .put("operation", "adb_force_stop")
                .put("serial", "192.168.1.20:5555")
                .put("package", "org.example.app"),
        )

        assertEquals("/data/data/com.termux/files/usr/bin/adb", command.executable)
        assertEquals(
            listOf("-s", "192.168.1.20:5555", "shell", "am", "force-stop", "org.example.app"),
            command.arguments,
        )
        assertEquals(RiskLevel.HIGH, command.risk)
    }

    @Test
    fun acceptsOnlyScriptsFromJarvisActions() {
        val command = TermuxCommandPolicy.prepare(
            JSONObject()
                .put("operation", "run_script")
                .put("script", "backup-phone.sh")
                .put("arguments", JSONArray().put("photos")),
        )

        assertEquals(
            "/data/data/com.termux/files/home/.jarvis/actions/backup-phone.sh",
            command.executable,
        )
        assertEquals(listOf("photos"), command.arguments)
    }

    @Test
    fun rejectsShellInjectionAndUnknownOperations() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandPolicy.prepare(
                JSONObject()
                    .put("operation", "adb_force_stop")
                    .put("serial", "device;id")
                    .put("package", "org.example.app"),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            TermuxCommandPolicy.prepare(JSONObject().put("operation", "raw_shell"))
        }
    }
}
