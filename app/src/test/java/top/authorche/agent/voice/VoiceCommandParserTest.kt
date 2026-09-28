package top.authorche.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    @Test
    fun recognizesUkrainianFindPhoneCommand() {
        assertEquals(VoiceCommand.FindPhone, VoiceCommandParser.parse("Джарвіс, де ти?"))
    }

    @Test
    fun recognizesGermanFindPhoneCommand() {
        assertEquals(VoiceCommand.FindPhone, VoiceCommandParser.parse("Jarvis, wo bist du?"))
    }

    @Test
    fun recognizesStopCommandBeforeGenericAgentTask() {
        assertEquals(
            VoiceCommand.StopFindingPhone,
            VoiceCommandParser.parse("Джарвіс, зупини сигнал"),
        )
    }

    @Test
    fun keepsUnknownSpeechAsAgentTask() {
        val command = VoiceCommandParser.parse("Відкрий календар і створи нагадування")
        assertTrue(command is VoiceCommand.AgentTask)
        assertEquals(
            "Відкрий календар і створи нагадування",
            (command as VoiceCommand.AgentTask).text,
        )
    }
}
