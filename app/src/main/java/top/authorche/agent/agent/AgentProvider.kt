package top.authorche.agent.agent

interface AgentProvider {
    suspend fun decide(
        task: String,
        tools: List<ToolDefinition>,
        observations: List<String>,
    ): AgentDecision
}
