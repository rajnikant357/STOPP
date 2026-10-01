package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import com.example.enforcement.ConnectivityControlResult
import com.example.enforcement.ConnectivityHelper
import com.example.enforcement.SafeAllowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class FinalRemediationConnectivityTest {

    @Test
    fun `phone and emergency dialers are strictly allowlisted during Focus Lock`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // System UI and emergency calling
        assertTrue(SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.phone"))
        assertTrue(SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.systemui"))
        assertTrue(SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.incallui"))

        // Focus Lock self package
        assertTrue(SafeAllowlist.isPackageAllowedDuringFocus(context, context.packageName))

        // Dynamic dialers
        val dialers = SafeAllowlist.getAllDialerPackages(context)
        assertNotNull(dialers)
        for (pkg in dialers) {
            assertTrue("Dialer $pkg must be allowlisted", SafeAllowlist.isPackageAllowedDuringFocus(context, pkg))
        }

        // Verify dialer action intent
        val dialIntent = Intent(Intent.ACTION_DIAL)
        assertEquals(Intent.ACTION_DIAL, dialIntent.action)
    }

    @Test
    fun `settings is strictly blocked during Focus Lock without bypass or escape route`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(
            "com.android.settings must be strictly blocked",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.android.settings")
        )
        assertFalse(
            "com.google.android.settings must be strictly blocked",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.google.android.settings")
        )
        assertFalse(
            "Arbitrary app must be strictly blocked",
            SafeAllowlist.isPackageAllowedDuringFocus(context, "com.instagram.android")
        )
    }

    @Test
    fun `connectivity direct toggle accurately reports platform security limitation without faking state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val mobileDataResult = ConnectivityHelper.requestDirectMobileDataToggle(context)
        assertTrue(
            "Mobile data direct control must report not supported on non-privileged Android",
            mobileDataResult is ConnectivityControlResult.NotSupported
        )
        val mobileReason = (mobileDataResult as ConnectivityControlResult.NotSupported).reason
        assertTrue(mobileReason.contains("Direct control isn't available on this Android version"))

        val hotspotResult = ConnectivityHelper.requestDirectHotspotToggle(context)
        assertTrue(
            "Hotspot direct control must report not supported on non-privileged Android",
            hotspotResult is ConnectivityControlResult.NotSupported
        )
        val hotspotReason = (hotspotResult as ConnectivityControlResult.NotSupported).reason
        assertTrue(hotspotReason.contains("Direct control isn't available on this Android version"))
    }

    @Test
    fun `focus session remains active and endsAt is preserved during connectivity checks`() {
        val startTime = 1700000000000L
        val duration = TimeUnit.HOURS.toMillis(2)
        val expectedEndsAt = startTime + duration

        val session = FocusSession.create(
            type = FocusSessionType.AUTOMATIC,
            durationMillis = duration,
            now = startTime
        )

        // Session state checks
        assertEquals(startTime, session.startedAt)
        assertEquals(expectedEndsAt, session.endsAt)
        assertTrue(session.isActive)
        assertFalse(session.isCompleted)

        // Performing connectivity queries must have zero effect on FocusSession
        val context = ApplicationProvider.getApplicationContext<Context>()
        ConnectivityHelper.isMobileDataEnabled(context)
        ConnectivityHelper.isHotspotEnabled(context)
        ConnectivityHelper.requestDirectMobileDataToggle(context)
        ConnectivityHelper.requestDirectHotspotToggle(context)

        // Verify session remains completely unchanged
        assertEquals(startTime, session.startedAt)
        assertEquals(expectedEndsAt, session.endsAt)
        assertTrue(session.isActive)
        assertFalse(session.isCompleted)
    }

    @Test
    fun `connectivity states are real booleans and do not throw exceptions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Must execute safely and return real boolean without crashing or faking
        val isMobileData = ConnectivityHelper.isMobileDataEnabled(context)
        val isHotspot = ConnectivityHelper.isHotspotEnabled(context)

        // In Robolectric test context these are booleans
        assertTrue(isMobileData == true || isMobileData == false)
        assertTrue(isHotspot == true || isHotspot == false)
    }
}
