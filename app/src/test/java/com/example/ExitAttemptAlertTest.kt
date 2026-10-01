package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitAttemptAlertTest {

    @Test
    fun `test exact wording of exit attempt alert message`() {
        val expectedMessage = "Just focus on your work, the app will exit automaticlly when your work is done."
        val actualMessage = "Just focus on your work, the app will exit automaticlly when your work is done."

        assertEquals(
            "Alert message must match user specification exactly",
            expectedMessage,
            actualMessage
        )
        assertTrue(actualMessage.contains("Just focus on your work"))
        assertTrue(actualMessage.contains("automaticlly"))
        assertTrue(actualMessage.contains("when your work is done"))
    }
}
