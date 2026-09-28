package top.authorche.agent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentPromptCodecTest {
    @Test
    fun parsesToolDecisionWrappedInExtraText() {
        val decision = AgentPromptCodec.parseDecision(
            "result: {\"rationale\":\"Відкрити\",\"tool\":\"app.launch\",\"arguments\":{\"app\":\"Календар\"},\"final\":null}",
        )

        assertEquals("Відкрити", decision.rationale)
        assertEquals("app.launch", decision.tool)
        assertEquals("Календар", decision.arguments.getString("app"))
        assertNull(decision.finalAnswer)
    }

    @Test
    fun parsesFinalDecision() {
        val decision = AgentPromptCodec.parseDecision(
            "{\"rationale\":\"Готово\",\"tool\":null,\"arguments\":{},\"final\":\"Завдання виконано\"}",
        )

        assertNull(decision.tool)
        assertEquals("Завдання виконано", decision.finalAnswer)
    }
}
