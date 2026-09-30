package top.authorche.agent.voice

import java.text.Normalizer

sealed interface VoiceCommand {
    data object FindPhone : VoiceCommand
    data object StopFindingPhone : VoiceCommand
    data object TellTime : VoiceCommand
    data object TellBattery : VoiceCommand
    data object StopSpeaking : VoiceCommand
    data class Media(val action: MediaAction) : VoiceCommand
    data class Volume(val action: VolumeAction) : VoiceCommand
    data class AgentTask(val text: String) : VoiceCommand
}

enum class MediaAction { PLAY, PAUSE, NEXT, PREVIOUS }
enum class VolumeAction { UP, DOWN, MUTE }

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

    private val stopSpeakingPhrases = listOf(
        "джарвіс замовкни", "джарвіс досить говорити", "зупини озвучення",
        "jarvis stop speaking", "jarvis sei still",
    )

    private val mediaPhrases = mapOf(
        MediaAction.PAUSE to listOf("постав на паузу", "зупини музику", "pause music", "musik pausieren"),
        MediaAction.PLAY to listOf("продовж музику", "віднови відтворення", "resume music", "musik fortsetzen"),
        MediaAction.NEXT to listOf("наступний трек", "наступна пісня", "next track", "nächstes lied"),
        MediaAction.PREVIOUS to listOf("попередній трек", "попередня пісня", "previous track", "vorheriges lied"),
    )

    private val volumePhrases = mapOf(
        VolumeAction.UP to listOf("зроби голосніше", "збільш гучність", "volume up", "lauter"),
        VolumeAction.DOWN to listOf("зроби тихіше", "зменш гучність", "volume down", "leiser"),
        VolumeAction.MUTE to listOf("вимкни звук", "без звуку", "mute", "ton aus"),
    )

    fun parse(text: String): VoiceCommand {
        val normalized = normalize(text)
        return when {
            stopSpeakingPhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.StopSpeaking
            stopPhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.StopFindingPhone
            findPhonePhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.FindPhone
            timePhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.TellTime
            batteryPhrases.any { normalized.contains(normalize(it)) } -> VoiceCommand.TellBattery
            else -> mediaPhrases.entries.firstOrNull { (_, phrases) ->
                phrases.any { normalized.contains(normalize(it)) }
            }?.let { VoiceCommand.Media(it.key) }
                ?: volumePhrases.entries.firstOrNull { (_, phrases) ->
                    phrases.any { normalized.contains(normalize(it)) }
                }?.let { VoiceCommand.Volume(it.key) }
                ?: VoiceCommand.AgentTask(stripWakeWord(text))
        }
    }

    private fun stripWakeWord(value: String): String = value.trim()
        .replace(Regex("^(джарвіс|джарвис|jarvis)[,;:!?\\s-]*", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { value.trim() }

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
