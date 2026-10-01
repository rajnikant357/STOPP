package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import com.example.enforcement.EnforcementState
import com.example.enforcement.FocusEnforcementManager
import com.example.enforcement.SafeAllowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2EnforcementTest {

    @Test
    fun `safe allowlist permits phone and system UI but blocks distracting apps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Focus Lock itself must be allowed
        assertTrue(
            "Own app must be allowed",
            SafeAllowlist.isPackageAllowedDuringFocus(context, context.packageName)
        )

        // 2. Emergency & System UI components must be allowed
        assertTrue(
            "System UI must be allowed",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.systemui")
        )
        assertTrue(
            "Phone system service must be allowed",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.phone")
        )
        assertTrue(
            "Telecom service must be allowed",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.server.telecom")
        )

        // 3. Distracting apps must be strictly BLOCKED
        assertFalse(
            "Instagram must be blocked during Focus Lock",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.instagram.android")
        )
        assertFalse(
            "YouTube must be blocked during Focus Lock",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.google.android.youtube")
        )
        assertFalse(
            "WhatsApp must be blocked during Focus Lock",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.whatsapp")
        )
        assertFalse(
            "Chrome must be blocked during Focus Lock",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.chrome")
        )
        assertFalse(
            "Reddit must be blocked during Focus Lock",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.reddit.frontpage")
        )
    }

    @Test
    fun `settings is strictly blocked during Focus Lock without bypass`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsPkg = "com.android.settings"

        assertFalse(
            "Settings must be strictly blocked during Focus Lock with zero escape path",
            SafeAllowlist.isPackageAllowedDuringFocus(context, settingsPkg)
        )
    }

    @Test
    fun `clock manipulation defense using elapsed realtime`() {
        val wallStartTime = 1700000000000L
        val elapsedStartTime = 100000L
        val duration = TimeUnit.HOURS.toMillis(2)

        val session = FocusSession.create(
            type = FocusSessionType.MANUAL,
            durationMillis = duration,
            now = wallStartTime,
            nowElapsed = elapsedStartTime
        )

        // 1. Normal progression: 30 minutes pass
        val elapsed30Min = elapsedStartTime + TimeUnit.MINUTES.toMillis(30)
        val wall30Min = wallStartTime + TimeUnit.MINUTES.toMillis(30)
        val remaining1 = session.remainingMillis(wall30Min, elapsed30Min)
        assertEquals(TimeUnit.MINUTES.toMillis(90), remaining1)

        // 2. User manipulates clock: shifts wall-clock forward by 3 hours to escape focus early
        val fakeManipulatedWallTime = wallStartTime + TimeUnit.HOURS.toMillis(3)
        // Actual elapsed time is still only 30 minutes into the session
        val remainingDefended = session.remainingMillis(fakeManipulatedWallTime, elapsed30Min)

        // The session must NOT be finished! ElapsedRealtime preserves the remaining 90 minutes.
        assertEquals(
            "Manipulating wall-clock forward does not terminate session early",
            TimeUnit.MINUTES.toMillis(90),
            remainingDefended
        )
        assertFalse(session.isFinished(fakeManipulatedWallTime, elapsed30Min))

        // 3. User manipulates clock backwards: shifts wall-clock back by 5 hours
        val fakeManipulatedWallTimePast = wallStartTime - TimeUnit.HOURS.toMillis(5)
        val remainingDefendedPast = session.remainingMillis(fakeManipulatedWallTimePast, elapsed30Min)

        // The session must NOT be extended indefinitely!
        assertEquals(
            "Manipulating wall-clock backwards does not extend session indefinitely",
            TimeUnit.MINUTES.toMillis(90),
            remainingDefendedPast
        )
    }

    @Test
    fun `enforcement state transitions and temporary interruption`() {
        assertEquals(EnforcementState.INACTIVE, FocusEnforcementManager.enforcementState.value)

        // Setting temporary interruption during phone call
        FocusEnforcementManager.setTemporaryInterruption(true)
        // If not active, doesn't force interrupted
        assertEquals(EnforcementState.INACTIVE, FocusEnforcementManager.enforcementState.value)
    }

    @Test
    fun `connectivity helper checks status safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Ensure checking status does not throw uncaught exceptions
        val isMobileDataOn = com.example.enforcement.ConnectivityHelper.isMobileDataEnabled(context)
        val isHotspotOn = com.example.enforcement.ConnectivityHelper.isHotspotEnabled(context)
        // Values are booleans
        assertTrue(isMobileDataOn || !isMobileDataOn)
        assertTrue(isHotspotOn || !isHotspotOn)
    }
}
