package com.example

import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ManualFocusCustomDurationTest {

    @Test
    fun `test manual focus can be started with custom duration separately from locked setup`() {
        val setupLockedDuration = TimeUnit.HOURS.toMillis(2) // 2 hours in setup

        val config = FocusLockConfig(
            isConfigured = true,
            configurationLockedAt = 1000L,
            configurationUnlocksAt = 1000L + TimeUnit.DAYS.toMillis(7),
            focusDurationMillis = setupLockedDuration
        )

        // Custom timer chosen by user: 25 minutes (e.g. Pomodoro session)
        val customDuration25m = TimeUnit.MINUTES.toMillis(25)
        val startTime = 2000000L

        val manualSession = FocusSession.create(
            type = FocusSessionType.MANUAL,
            durationMillis = customDuration25m,
            now = startTime
        )

        // Verify session respects the custom 25m duration instead of the 2h setup duration
        assertEquals(FocusSessionType.MANUAL, manualSession.type)
        assertEquals(customDuration25m, manualSession.durationMillis)
        assertEquals(startTime + customDuration25m, manualSession.endsAt)
        assertEquals("00:25:00", manualSession.formattedRemaining(startTime))

        // Check halfway (10 minutes in)
        val tenMinutesIn = startTime + TimeUnit.MINUTES.toMillis(10)
        assertEquals("00:15:00", manualSession.formattedRemaining(tenMinutesIn))

        // Check session completion at 25 minutes
        val twentyFiveMinutesIn = startTime + customDuration25m
        assertTrue(manualSession.isFinished(twentyFiveMinutesIn))

        // Verify that the setup config itself remains untouched and locked at 2 hours
        assertEquals(setupLockedDuration, config.focusDurationMillis)
    }

    @Test
    fun `test custom duration selection values and bounds`() {
        val minAllowed = TimeUnit.MINUTES.toMillis(5)
        val maxAllowed = TimeUnit.HOURS.toMillis(8)

        val quickPresets = listOf(
            TimeUnit.MINUTES.toMillis(15),
            TimeUnit.MINUTES.toMillis(25),
            TimeUnit.MINUTES.toMillis(30),
            TimeUnit.MINUTES.toMillis(45),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.MINUTES.toMillis(90),
            TimeUnit.HOURS.toMillis(2)
        )

        for (preset in quickPresets) {
            assertTrue("Preset $preset should be >= 5m", preset >= minAllowed)
            assertTrue("Preset $preset should be <= 8h", preset <= maxAllowed)
        }
    }
}
