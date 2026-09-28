package top.authorche.agent.shizuku

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test
import top.authorche.agent.agent.RiskLevel

class CommandPolicyTest {
    @Test
    fun preparesForceStopWithoutShellInterpolation() {
        val command = CommandPolicy.prepare(
            JSONObject()
                .put("operation", "force_stop")
                .put("package", "org.example.app"),
        )

        assertEquals(listOf("am", "force-stop", "org.example.app"), command.command)
        assertEquals(RiskLevel.HIGH, command.risk)
    }

    @Test
    fun rejectsInjectedPackageName() {
        assertFailsWith<IllegalArgumentException> {
            CommandPolicy.prepare(
                JSONObject()
                    .put("operation", "force_stop")
                    .put("package", "org.example.app;id"),
            )
        }
    }

    @Test
    fun rejectsUnknownOperation() {
        assertFailsWith<IllegalStateException> {
            CommandPolicy.prepare(JSONObject().put("operation", "raw_shell"))
        }
    }
}
