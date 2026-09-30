package top.authorche.agent.agent

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentOrchestrator(
    private val provider: AgentProvider,
    private val tools: ToolRegistry,
    private val policy: PolicyEngine,
) {
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()

    private val confirmationIds = AtomicLong(0)
    private var confirmationResponse: CompletableDeferred<Boolean>? = null

    suspend fun run(task: String) {
        if (mutableState.value.running || task.isBlank()) return

        val observations = mutableListOf<String>()
        mutableState.value = AgentUiState(
            running = true,
            task = task.trim(),
            status = "Аналізую завдання…",
        )

        try {
            repeat(MAX_STEPS) { index ->
                updateStatus("Крок ${index + 1}: планую наступну дію…")
                val decision = provider.decide(task.trim(), tools.definitions, observations)

                decision.finalAnswer?.takeIf { it.isNotBlank() }?.let { answer ->
                    mutableState.value = mutableState.value.copy(
                        running = false,
                        status = "Завдання завершено",
                        finalAnswer = answer,
                        confirmation = null,
                    )
                    return
                }

                val toolName = decision.tool
                    ?: error("Модель не повернула ні інструмент, ні фінальну відповідь")
                val tool = tools.find(toolName)
                    ?: error("Невідомий інструмент: $toolName")
                val risk = tool.risk(decision.arguments)
                val summary = tool.summarize(decision.arguments)

                appendTrace("План", decision.rationale.ifBlank { summary })

                when (policy.decide(risk)) {
                    PolicyDecision.DENY -> {
                        val observation = "ВІДМОВЛЕНО ПОЛІТИКОЮ: $summary"
                        observations += observation
                        appendTrace("Заблоковано", observation, false)
                        return@repeat
                    }
                    PolicyDecision.ASK -> {
                        if (!awaitConfirmation(toolName, summary, risk)) {
                            val observation = "Користувач відхилив дію: $summary"
                            observations += observation
                            appendTrace("Відхилено", observation, false)
                            return@repeat
                        }
                    }
                    PolicyDecision.ALLOW -> Unit
                }

                updateStatus("Виконую: $summary")
                val result = tool.execute(decision.arguments)
                val observation = buildString {
                    append(if (result.success) "УСПІХ" else "ПОМИЛКА")
                    append(": ")
                    append(result.message)
                    if (result.data.length() > 0) append("; дані=${result.data}")
                }
                observations += observation
                appendTrace(summary, result.message, result.success)
            }

            mutableState.value = mutableState.value.copy(
                running = false,
                status = "Досягнуто ліміт кроків",
                finalAnswer = "Агент зупинився після $MAX_STEPS кроків. Перевірте журнал і уточніть завдання.",
                confirmation = null,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            confirmationResponse?.cancel()
            confirmationResponse = null
            mutableState.value = mutableState.value.copy(
                running = false, status = "Зупинено користувачем", confirmation = null,
            )
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(
                running = false,
                status = "Виконання зупинено",
                confirmation = null,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    fun respondToConfirmation(approved: Boolean) {
        confirmationResponse?.complete(approved)
        confirmationResponse = null
        mutableState.value = mutableState.value.copy(confirmation = null)
    }

    fun reset() {
        if (!mutableState.value.running) mutableState.value = AgentUiState()
    }

    private suspend fun awaitConfirmation(
        tool: String,
        summary: String,
        risk: RiskLevel,
    ): Boolean {
        val response = CompletableDeferred<Boolean>()
        confirmationResponse = response
        mutableState.value = mutableState.value.copy(
            status = "Очікую підтвердження",
            confirmation = PendingConfirmation(
                id = confirmationIds.incrementAndGet(),
                tool = tool,
                summary = summary,
                risk = risk,
            ),
        )
        return response.await()
    }

    private fun updateStatus(status: String) {
        mutableState.value = mutableState.value.copy(status = status)
    }

    private fun appendTrace(title: String, detail: String, successful: Boolean? = null) {
        mutableState.value = mutableState.value.copy(
            trace = mutableState.value.trace + AgentTraceEntry(title, detail, successful),
        )
    }

    companion object {
        private const val MAX_STEPS = 16
    }
}
