package top.authorche.agent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasicIntentRouterTest {
    @Test fun routesLocalSearchWithoutModel() {
        val decision = BasicIntentRouter.decide("Знайди на телефоні Telegram", emptyList())!!
        assertEquals("search_device", decision.tool)
        assertEquals("Telegram", decision.arguments.getString("query"))
    }

    @Test fun routesReminderAndFinishesFromObservation() {
        val task = "Нагадай мені про зустріч через 30 хвилин"
        val decision = BasicIntentRouter.decide(task, emptyList())!!
        assertEquals("create_reminder", decision.tool)
        assertEquals(30, decision.arguments.getInt("delay_minutes"))
        assertEquals("мені про зустріч", decision.arguments.getString("message"))
        assertEquals("УСПІХ: створено", BasicIntentRouter.decide(task, listOf("УСПІХ: створено"))!!.finalAnswer)
    }

    @Test fun complexTasksStillUseModel() {
        assertNull(BasicIntentRouter.decide("Підсумуй нові листи", emptyList()))
    }
}
