package top.authorche.agent.agent

import org.json.JSONObject

interface AgentTool {
    val definition: ToolDefinition
    fun risk(arguments: JSONObject): RiskLevel
    fun summarize(arguments: JSONObject): String
    suspend fun execute(arguments: JSONObject): ToolResult
}
