package com.example

import com.example.ui.screens.AppLimitConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class IndividualAppLimitRangeTest {

    @Test
    fun `test minimum and maximum limits are 5 minutes and 1_5 hours`() {
        val expectedMin = TimeUnit.MINUTES.toMillis(5)
        val expectedMax = TimeUnit.MINUTES.toMillis(90) // 1.5 hours = 90 minutes

        assertEquals(300_000L, expectedMin)
        assertEquals(5_400_000L, expectedMax)

        assertEquals(expectedMin, AppLimitConstants.MIN_LIMIT_MILLIS)
        assertEquals(expectedMax, AppLimitConstants.MAX_LIMIT_MILLIS)
    }

    @Test
    fun `test presets are all within 5 minutes and 1_5 hours`() {
        for ((presetMillis, label) in AppLimitConstants.PRESETS) {
            assertTrue(
                "Preset $label ($presetMillis ms) should be >= 5 minutes",
                presetMillis >= AppLimitConstants.MIN_LIMIT_MILLIS
            )
            assertTrue(
                "Preset $label ($presetMillis ms) should be <= 1.5 hours",
                presetMillis <= AppLimitConstants.MAX_LIMIT_MILLIS
            )
        }

        // Verify the 6 preset labels
        val labels = AppLimitConstants.PRESETS.map { it.second }
        assertEquals(listOf("5m", "15m", "30m", "45m", "1h", "1.5h"), labels)
    }

    @Test
    fun `test clamping logic enforces bounds strictly`() {
        val minLimit = AppLimitConstants.MIN_LIMIT_MILLIS
        val maxLimit = AppLimitConstants.MAX_LIMIT_MILLIS

        // Values below 5 minutes clamp to 5 minutes
        val zeroClamped = 0L.coerceIn(minLimit, maxLimit)
        assertEquals(minLimit, zeroClamped)

        val oneMinClamped = TimeUnit.MINUTES.toMillis(1).coerceIn(minLimit, maxLimit)
        assertEquals(minLimit, oneMinClamped)

        // Values above 1.5 hours clamp to 90 minutes
        val twoHoursClamped = TimeUnit.HOURS.toMillis(2).coerceIn(minLimit, maxLimit)
        assertEquals(maxLimit, twoHoursClamped)

        val fiveHoursClamped = TimeUnit.HOURS.toMillis(5).coerceIn(minLimit, maxLimit)
        assertEquals(maxLimit, fiveHoursClamped)

        // Valid values remain unchanged
        val thirtyMin = TimeUnit.MINUTES.toMillis(30)
        assertEquals(thirtyMin, thirtyMin.coerceIn(minLimit, maxLimit))

        val oneAndHalfHours = TimeUnit.MINUTES.toMillis(90)
        assertEquals(oneAndHalfHours, oneAndHalfHours.coerceIn(minLimit, maxLimit))
    }

    @Test
    fun `test stepping up and down by 5 minutes within bounds`() {
        val minLimit = AppLimitConstants.MIN_LIMIT_MILLIS
        val maxLimit = AppLimitConstants.MAX_LIMIT_MILLIS
        val step = AppLimitConstants.STEP_MILLIS

        // Stepping down from 5 minutes stays at 5 minutes
        val stepDownFromMin = (minLimit - step).coerceAtLeast(minLimit)
        assertEquals(minLimit, stepDownFromMin)

        // Stepping up from 1.5 hours stays at 1.5 hours
        val stepUpFromMax = (maxLimit + step).coerceAtMost(maxLimit)
        assertEquals(maxLimit, stepUpFromMax)

        // Stepping down from 10 minutes gives 5 minutes
        val tenMin = TimeUnit.MINUTES.toMillis(10)
        val stepDownFromTen = (tenMin - step).coerceAtLeast(minLimit)
        assertEquals(minLimit, stepDownFromTen)

        // Stepping up from 85 minutes gives 90 minutes (1.5h)
        val eightyFiveMin = TimeUnit.MINUTES.toMillis(85)
        val stepUpFromEightyFive = (eightyFiveMin + step).coerceAtMost(maxLimit)
        assertEquals(maxLimit, stepUpFromEightyFive)
    }
}
