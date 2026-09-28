package top.authorche.agent.agent

class PolicyEngine {
    fun decide(risk: RiskLevel): PolicyDecision = when (risk) {
        RiskLevel.LOW -> PolicyDecision.ALLOW
        RiskLevel.MEDIUM,
        RiskLevel.HIGH,
        -> PolicyDecision.ASK
        RiskLevel.CRITICAL -> PolicyDecision.DENY
    }
}
