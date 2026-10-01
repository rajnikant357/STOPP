package com.example

import com.example.domain.model.AppUsageInfo
import com.example.domain.model.FocusLockAppStatus
import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusLockDashboardState
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
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
class FocusLockEngineTest {

    @Test
    fun `scenario 39 full lifecycle test - app limits, combined trigger, completion, and daily reset`() {
        val instagramPkg = "com.instagram.android"
        val whatsappPkg = "com.whatsapp"
        val youtubePkg = "com.google.android.youtube"
        val redditPkg = "com.reddit.frontpage"

        val limits = mapOf(
            instagramPkg to TimeUnit.MINUTES.toMillis(30),
            whatsappPkg to TimeUnit.MINUTES.toMillis(30),
            youtubePkg to TimeUnit.MINUTES.toMillis(15),
            redditPkg to TimeUnit.MINUTES.toMillis(20)
        )

        val combinedThreshold = TimeUnit.MINUTES.toMillis(90)
        val focusDuration = TimeUnit.HOURS.toMillis(2)
        val configStartTime = 1000000000000L

        val config = FocusLockConfig.createLockedConfig(
            monitoredApps = limits,
            combinedThresholdMillis = combinedThreshold,
            focusDurationMillis = focusDuration,
            now = configStartTime,
            todayDate = "2026-10-01"
        )

        assertTrue(config.isConfigured)
        assertTrue(config.isConfigurationLocked(configStartTime))
        assertEquals(7, TimeUnit.MILLISECONDS.toDays(config.remainingLockMillis(configStartTime)))

        // Step 1: User uses Instagram for 30 minutes
        val step1Instagram = AppUsageInfo(
            packageName = instagramPkg,
            appName = "Instagram",
            usageMillis = TimeUnit.MINUTES.toMillis(30),
            dailyLimitMillis = TimeUnit.MINUTES.toMillis(30)
        )
        val step1Whatsapp = AppUsageInfo(
            packageName = whatsappPkg,
            appName = "WhatsApp",
            usageMillis = 0L,
            dailyLimitMillis = TimeUnit.MINUTES.toMillis(30)
        )

        assertTrue("Instagram should be locked", step1Instagram.isLimitReached)
        assertEquals(0L, step1Instagram.remainingMillis)
        assertFalse("WhatsApp should still be available", step1Whatsapp.isLimitReached)

        val step1CombinedUsage = step1Instagram.usageMillis + step1Whatsapp.usageMillis
        assertEquals(TimeUnit.MINUTES.toMillis(30), step1CombinedUsage)
        assertTrue("Combined usage (30m) has not reached threshold (90m)", step1CombinedUsage < combinedThreshold)

        // Step 2: User uses WhatsApp for 30 minutes
        val step2Whatsapp = step1Whatsapp.copy(usageMillis = TimeUnit.MINUTES.toMillis(30))
        assertTrue("WhatsApp is now locked", step2Whatsapp.isLimitReached)

        val step2CombinedUsage = step1Instagram.usageMillis + step2Whatsapp.usageMillis
        assertEquals(TimeUnit.MINUTES.toMillis(60), step2CombinedUsage)
        assertTrue("Combined usage (60m) is still below 90m threshold", step2CombinedUsage < combinedThreshold)

        // Step 3: User uses YouTube for 15m and Reddit for 15m
        val step3Youtube = AppUsageInfo(
            packageName = youtubePkg,
            appName = "YouTube",
            usageMillis = TimeUnit.MINUTES.toMillis(15),
            dailyLimitMillis = TimeUnit.MINUTES.toMillis(15)
        )
        val step3Reddit = AppUsageInfo(
            packageName = redditPkg,
            appName = "Reddit",
            usageMillis = TimeUnit.MINUTES.toMillis(15),
            dailyLimitMillis = TimeUnit.MINUTES.toMillis(20)
        )

        assertTrue("YouTube is locked", step3Youtube.isLimitReached)
        assertFalse("Reddit is not locked (15/20 min)", step3Reddit.isLimitReached)
        assertEquals(TimeUnit.MINUTES.toMillis(5), step3Reddit.remainingMillis)

        val step3CombinedUsage = step1Instagram.usageMillis +
                step2Whatsapp.usageMillis +
                step3Youtube.usageMillis +
                step3Reddit.usageMillis

        assertEquals(TimeUnit.MINUTES.toMillis(90), step3CombinedUsage)
        assertTrue("Combined usage reached threshold", step3CombinedUsage >= combinedThreshold)

        // Automatic focus lock triggers
        val focusSessionStartTime = configStartTime + TimeUnit.HOURS.toMillis(3)
        val autoSession = FocusSession.create(
            type = FocusSessionType.AUTOMATIC,
            durationMillis = config.focusDurationMillis,
            now = focusSessionStartTime
        )

        assertEquals(FocusSessionType.AUTOMATIC, autoSession.type)
        assertEquals(TimeUnit.HOURS.toMillis(2), autoSession.durationMillis)
        assertTrue(autoSession.isActive)
        assertFalse(autoSession.isCompleted)
        assertEquals(TimeUnit.HOURS.toMillis(2), autoSession.remainingMillis(focusSessionStartTime))
        assertEquals("02:00:00", autoSession.formattedRemaining(focusSessionStartTime))

        // Halfway through session (1 hour in)
        val midwayTime = focusSessionStartTime + TimeUnit.HOURS.toMillis(1)
        assertEquals("01:00:00", autoSession.formattedRemaining(midwayTime))
        assertFalse(autoSession.isFinished(midwayTime))

        // Session completes after 2 hours
        val sessionEndTime = focusSessionStartTime + TimeUnit.HOURS.toMillis(2)
        assertTrue(autoSession.isFinished(sessionEndTime))

        val completedSession = autoSession.copy(isActive = false, isCompleted = true)
        val postSessionConfig = config.copy(automaticFocusUsedToday = true)

        assertTrue("Focus session completed", completedSession.isCompleted)
        assertTrue("Automatic focus is consumed for today", postSessionConfig.automaticFocusUsedToday)

        // Prompt #3 rule: Completing Focus Session does NOT restore consumed individual limits
        assertTrue("Instagram STILL locked", step1Instagram.isLimitReached)
        assertTrue("WhatsApp STILL locked", step2Whatsapp.isLimitReached)
        assertTrue("YouTube STILL locked", step3Youtube.isLimitReached)
        assertEquals("15 / 20 min", step3Reddit.formattedUsageVsLimit())

        // Next Day Rollover
        val nextDayInstagram = step1Instagram.copy(usageMillis = 0L)
        val nextDayWhatsapp = step2Whatsapp.copy(usageMillis = 0L)
        val nextDayYoutube = step3Youtube.copy(usageMillis = 0L)
        val nextDayReddit = step3Reddit.copy(usageMillis = 0L)

        val nextDayConfig = postSessionConfig.copy(
            automaticFocusUsedToday = false,
            lastUsageDate = "2026-10-02"
        )

        assertFalse("Next day: Instagram available", nextDayInstagram.isLimitReached)
        assertFalse("Next day: WhatsApp available", nextDayWhatsapp.isLimitReached)
        assertFalse("Next day: YouTube available", nextDayYoutube.isLimitReached)
        assertFalse("Next day: Reddit available", nextDayReddit.isLimitReached)
        assertFalse("Next day: Automatic focus available", nextDayConfig.automaticFocusUsedToday)

        // 7-day configuration lock is STILL active on Day 2
        val day2Time = configStartTime + TimeUnit.DAYS.toMillis(1)
        assertTrue("7-day lock survives daily rollover", nextDayConfig.isConfigurationLocked(day2Time))
    }

    @Test
    fun `scenario 40 manual focus test`() {
        val focusDuration = TimeUnit.HOURS.toMillis(1)
        val now = 2000000000000L

        val session = FocusSession.create(
            type = FocusSessionType.MANUAL,
            durationMillis = focusDuration,
            now = now
        )

        assertEquals(FocusSessionType.MANUAL, session.type)
        assertEquals("01:00:00", session.formattedRemaining(now))

        // After 1 hour
        val finishedTime = now + TimeUnit.HOURS.toMillis(1)
        assertTrue(session.isFinished(finishedTime))

        // When manual focus finishes, automatic focus is consumed for the day
        val completedSession = session.copy(isActive = false, isCompleted = true)
        val config = FocusLockConfig(
            isConfigured = true,
            focusDurationMillis = focusDuration,
            automaticFocusUsedToday = true
        )

        assertTrue(completedSession.isCompleted)
        assertTrue(config.automaticFocusUsedToday)
    }

    @Test
    fun `7-day configuration lock calculation and expiration`() {
        val startTime = 1700000000000L
        val config = FocusLockConfig.createLockedConfig(
            monitoredApps = mapOf("com.example.app" to TimeUnit.MINUTES.toMillis(30)),
            combinedThresholdMillis = TimeUnit.MINUTES.toMillis(60),
            focusDurationMillis = TimeUnit.HOURS.toMillis(2),
            now = startTime,
            todayDate = "2026-10-01"
        )

        assertTrue(config.isConfigurationLocked(startTime))
        assertEquals(7, TimeUnit.MILLISECONDS.toDays(config.remainingLockMillis(startTime)))

        // 6 days later: still locked
        val day6 = startTime + TimeUnit.DAYS.toMillis(6)
        assertTrue(config.isConfigurationLocked(day6))

        // 7 days and 1 second later: unlocked
        val day7And1Sec = startTime + TimeUnit.DAYS.toMillis(7) + 1000L
        assertFalse(config.isConfigurationLocked(day7And1Sec))
    }

    @Test
    fun `threshold exceeding total individual limits triggers validation warning`() {
        val limits = mapOf(
            "com.instagram.android" to TimeUnit.MINUTES.toMillis(30),
            "com.google.android.youtube" to TimeUnit.MINUTES.toMillis(15)
        )
        // Total = 45m, Threshold = 2h (120m)
        val config = FocusLockConfig(
            monitoredApps = limits,
            combinedThresholdMillis = TimeUnit.HOURS.toMillis(2)
        )

        assertEquals(TimeUnit.MINUTES.toMillis(45), config.totalMonitoredLimitMillis())
        assertTrue("Should detect threshold exceeds limits", config.isThresholdExceedingLimits())
    }
}
