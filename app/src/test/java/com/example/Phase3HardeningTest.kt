package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AppUsageInfo
import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import com.example.enforcement.KioskLockTaskHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3HardeningTest {

    @Test
    fun `midnight crossing - active session continues across day rollover`() {
        val sessionStartTime = 1700000000000L // 23:30
        val duration = TimeUnit.HOURS.toMillis(2) // 2 hours -> ends at 01:30

        val activeSession = FocusSession.create(
            type = FocusSessionType.MANUAL,
            durationMillis = duration,
            now = sessionStartTime
        )

        assertEquals("02:00:00", activeSession.formattedRemaining(sessionStartTime))
        assertFalse(activeSession.isFinished(sessionStartTime))

        // Midnight rollover occurs (30 minutes into session)
        val midnightTime = sessionStartTime + TimeUnit.MINUTES.toMillis(30)
        assertFalse("Session must NOT be finished at midnight", activeSession.isFinished(midnightTime))
        assertEquals(
            "Session has 1 hour 30 min left across midnight",
            TimeUnit.MINUTES.toMillis(90),
            activeSession.remainingMillis(midnightTime)
        )
        assertEquals("01:30:00", activeSession.formattedRemaining(midnightTime))

        // 1 hour after midnight (01:00)
        val oneAmTime = sessionStartTime + TimeUnit.MINUTES.toMillis(90)
        assertFalse(activeSession.isFinished(oneAmTime))
        assertEquals("00:30:00", activeSession.formattedRemaining(oneAmTime))

        // Session expiration at 01:30
        val finishTime = sessionStartTime + TimeUnit.HOURS.toMillis(2)
        assertTrue("Session completes at 01:30", activeSession.isFinished(finishTime))
    }

    @Test
    fun `seven-day lock expiration and reconfiguration lifecycle`() {
        val day1 = 1700000000000L
        val configA = FocusLockConfig.createLockedConfig(
            monitoredApps = mapOf("com.instagram.android" to TimeUnit.MINUTES.toMillis(30)),
            combinedThresholdMillis = TimeUnit.MINUTES.toMillis(60),
            focusDurationMillis = TimeUnit.HOURS.toMillis(2),
            now = day1,
            todayDate = "2026-10-01"
        )

        // Day 1 to Day 6: locked
        assertTrue(configA.isConfigurationLocked(day1))
        assertTrue(configA.isConfigurationLocked(day1 + TimeUnit.DAYS.toMillis(6)))

        // Day 8: lock has expired
        val day8 = day1 + TimeUnit.DAYS.toMillis(7) + 1000L
        assertFalse("Configuration A lock expired after 7 days", configA.isConfigurationLocked(day8))

        // Reconfiguration to Configuration B
        val configB = FocusLockConfig.createLockedConfig(
            monitoredApps = mapOf(
                "com.instagram.android" to TimeUnit.MINUTES.toMillis(20),
                "com.google.android.youtube" to TimeUnit.MINUTES.toMillis(15)
            ),
            combinedThresholdMillis = TimeUnit.MINUTES.toMillis(45),
            focusDurationMillis = TimeUnit.HOURS.toMillis(1),
            now = day8,
            todayDate = "2026-10-08"
        )

        // Configuration B initiates a new 7-day lock starting on Day 8
        assertTrue("New configuration is locked", configB.isConfigurationLocked(day8))
        assertTrue("Locked on Day 10", configB.isConfigurationLocked(day8 + TimeUnit.DAYS.toMillis(2)))
        assertEquals(2, configB.monitoredApps.size)
        assertEquals(TimeUnit.MINUTES.toMillis(45), configB.combinedThresholdMillis)
    }

    @Test
    fun `corrupted state protection against invalid timestamps`() {
        val startedAt = 100000L
        val invalidEndsAt = 50000L // ends before start!

        // If corrupted session data is passed
        val corruptedSession = FocusSession(
            type = FocusSessionType.AUTOMATIC,
            startedAt = startedAt,
            endsAt = invalidEndsAt,
            durationMillis = -50000L
        )

        // Protection: isFinished returns true so system does not loop or hang
        assertTrue("Corrupted session must be considered finished", corruptedSession.isFinished())
        assertEquals(0L, corruptedSession.remainingMillis())
    }

    @Test
    fun `kiosk helper runs safely on consumer device`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // Consumer devices do not have lock task permitted by default; must not throw
        val permitted = KioskLockTaskHelper.isLockTaskPermitted(activity)
        assertFalse(permitted)
        val started = KioskLockTaskHelper.startLockTaskIfPermitted(activity)
        assertFalse(started)
        KioskLockTaskHelper.stopLockTaskIfActive(activity)
    }
}
