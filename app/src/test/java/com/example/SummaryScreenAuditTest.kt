package com.example

import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SummaryScreenAuditTest {

    @Test
    fun `test configuration review screen values and 7-day lock boundaries`() {
        val now = 1000000000L
        val sevenDaysMillis = TimeUnit.DAYS.toMillis(7)
        val unlocksAt = now + sevenDaysMillis

        val config = FocusLockConfig(
            isConfigured = true,
            configurationLockedAt = now,
            configurationUnlocksAt = unlocksAt,
            monitoredApps = mapOf(
                "com.instagram.android" to TimeUnit.MINUTES.toMillis(30),
                "com.google.android.youtube" to TimeUnit.HOURS.toMillis(1)
            ),
            combinedThresholdMillis = TimeUnit.MINUTES.toMillis(90),
            focusDurationMillis = TimeUnit.HOURS.toMillis(2)
        )

        // Verification of 7-day lock enforcement
        assertTrue("Configuration must be locked before 7 days", config.isConfigurationLocked(now + TimeUnit.DAYS.toMillis(3)))
        assertTrue("Configuration must be locked on day 6", config.isConfigurationLocked(now + TimeUnit.DAYS.toMillis(6)))
        assertFalse("Configuration unlocks after 7 days", config.isConfigurationLocked(now + sevenDaysMillis + 1000L))

        assertEquals(2, config.monitoredApps.size)
        assertEquals(TimeUnit.MINUTES.toMillis(90), config.combinedThresholdMillis)
        assertEquals(TimeUnit.HOURS.toMillis(2), config.focusDurationMillis)
    }

    @Test
    fun `test midnight crossing maintains active session`() {
        val sessionStart = 1700000000000L // e.g. 23:30
        val sessionDuration = TimeUnit.HOURS.toMillis(2) // ends at 01:30
        val session = FocusSession.create(
            type = FocusSessionType.AUTOMATIC,
            durationMillis = sessionDuration,
            now = sessionStart
        )

        val midnight = sessionStart + TimeUnit.MINUTES.toMillis(30)
        // At midnight, session must still be active and not finished
        assertTrue(session.isActive)
        assertFalse(session.isFinished(midnight))
        assertEquals("01:30:00", session.formattedRemaining(midnight))

        // Session completes only when full duration elapses (01:30)
        val sessionEnd = sessionStart + sessionDuration
        assertTrue(session.isFinished(sessionEnd))
    }
}
