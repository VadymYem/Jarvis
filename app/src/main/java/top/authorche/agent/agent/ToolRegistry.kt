package top.authorche.agent.agent

class ToolRegistry(tools: List<AgentTool>) {
    private val toolsByName = tools.associateBy { it.definition.name }

    val definitions: List<ToolDefinition> = toolsByName.values.map { it.definition }

    fun find(name: String): AgentTool? = toolsByName[name]
}
