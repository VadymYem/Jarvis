package top.authorche.agent.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyEngineTest {
    private val policy = PolicyEngine()

    @Test
    fun lowRiskIsAllowed() {
        assertEquals(PolicyDecision.ALLOW, policy.decide(RiskLevel.LOW))
    }

    @Test
    fun mediumAndHighRiskRequireConfirmation() {
        assertEquals(PolicyDecision.ASK, policy.decide(RiskLevel.MEDIUM))
        assertEquals(PolicyDecision.ASK, policy.decide(RiskLevel.HIGH))
    }

    @Test
    fun criticalRiskIsDenied() {
        assertEquals(PolicyDecision.DENY, policy.decide(RiskLevel.CRITICAL))
    }
}
