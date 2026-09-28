package top.authorche.agent.agent

import org.json.JSONObject

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class PolicyDecision {
    ALLOW,
    ASK,
    DENY,
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val arguments: String,
)

data class AgentDecision(
    val rationale: String,
    val tool: String?,
    val arguments: JSONObject,
    val finalAnswer: String?,
)

data class AgentTraceEntry(
    val title: String,
    val detail: String,
    val successful: Boolean? = null,
)

data class PendingConfirmation(
    val id: Long,
    val tool: String,
    val summary: String,
    val risk: RiskLevel,
)

data class AgentUiState(
    val running: Boolean = false,
    val task: String = "",
    val status: String = "Готовий до роботи",
    val finalAnswer: String? = null,
    val trace: List<AgentTraceEntry> = emptyList(),
    val confirmation: PendingConfirmation? = null,
    val error: String? = null,
)

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: JSONObject = JSONObject(),
)
