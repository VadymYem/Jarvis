package top.authorche.agent.voice

import java.text.Normalizer

sealed interface VoiceCommand {
    data object FindPhone : VoiceCommand
    data object StopFindingPhone : VoiceCommand
    data object TellTime : VoiceCommand
    data object TellBattery : VoiceCommand
    data class AgentTask(val text: String) : VoiceCommand
}

object VoiceCommandParser {
    private val findPhonePhrases = listOf(
        "джарвіс де ти",
        "джарвис где ты",
        "джарвіс знайди телефон",
        "джарвис найди телефон",
        "де мій телефон",
        "где мой телефон",
        "jarvis where are you",
        "jarvis find my phone",
        "find my phone",
        "wo ist mein handy",
        "jarvis wo bist du",
    )

    private val stopPhrases = listOf(
        "джарвіс досить",
        "джарвіс зупини сигнал",
        "джарвис останови сигнал",
        "зупини пошук телефона",
        "останови поиск телефона",
        "jarvis stop",
        "stop finding my phone",
        "jarvis stopp",
    )

    private val timePhrases = listOf(
        "котра година",
        "скільки зараз часу",
        "сколько времени",
        "what time is it",
        "wie spät ist es",
    )

    private val batteryPhrases = listOf(
        "який заряд батареї",
        "скільки заряду",
        "сколько заряда",
        "battery level",
        "wie voll ist der akku",
    )

    fun parse(text: String): VoiceCommand {
        val normalized = normalize(text)
        return when {
            stopPhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.StopFindingPhone
            findPhonePhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.FindPhone
            timePhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.TellTime
            batteryPhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.TellBattery
            else -> VoiceCommand.AgentTask(text.trim())
        }
    }

    internal fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase()
            .replace('’', '\'')
            .replace('`', '\'')
            .replace('|', ' '),
        Normalizer.Form.NFD,
    )
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^\\p{L}\\p{N}']+".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")
}
