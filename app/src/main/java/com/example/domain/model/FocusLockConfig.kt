package com.example.domain.model

import java.util.concurrent.TimeUnit

data class FocusLockConfig(
    val isConfigured: Boolean = false,
    val configurationLockedAt: Long = 0L,
    val configurationUnlocksAt: Long = 0L,
    val monitoredApps: Map<String, Long> = emptyMap(), // packageName -> dailyLimitMillis
    val combinedThresholdMillis: Long = TimeUnit.MINUTES.toMillis(90),
    val focusDurationMillis: Long = TimeUnit.HOURS.toMillis(2),
    val automaticFocusUsedToday: Boolean = false,
    val lastUsageDate: String = ""
) {
    fun isConfigurationLocked(now: Long = System.currentTimeMillis()): Boolean {
        return isConfigured && (now < configurationUnlocksAt)
    }

    fun remainingLockMillis(now: Long = System.currentTimeMillis()): Long {
        return (configurationUnlocksAt - now).coerceAtLeast(0L)
    }

    fun remainingLockDurationFormatted(now: Long = System.currentTimeMillis()): String {
        val remaining = remainingLockMillis(now)
        if (remaining <= 0) return "Unlocked"

        val totalHours = TimeUnit.MILLISECONDS.toHours(remaining)
        val days = totalHours / 24
        val hours = totalHours % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60

        return when {
            days > 0 -> "$days ${if (days == 1L) "day" else "days"} $hours ${if (hours == 1L) "hour" else "hours"}"
            hours > 0 -> "$hours ${if (hours == 1L) "hour" else "hours"} $minutes min"
            else -> "$minutes min"
        }
    }

    fun totalMonitoredLimitMillis(): Long {
        return monitoredApps.values.sum()
    }

    fun isThresholdExceedingLimits(): Boolean {
        val total = totalMonitoredLimitMillis()
        return total > 0 && combinedThresholdMillis > total
    }

    companion object {
        const val LOCK_PERIOD_DAYS = 7L
        val SEVEN_DAYS_MILLIS = TimeUnit.DAYS.toMillis(LOCK_PERIOD_DAYS)

        fun createLockedConfig(
            monitoredApps: Map<String, Long>,
            combinedThresholdMillis: Long,
            focusDurationMillis: Long,
            now: Long = System.currentTimeMillis(),
            todayDate: String
        ): FocusLockConfig {
            return FocusLockConfig(
                isConfigured = true,
                configurationLockedAt = now,
                configurationUnlocksAt = now + SEVEN_DAYS_MILLIS,
                monitoredApps = monitoredApps,
                combinedThresholdMillis = combinedThresholdMillis,
                focusDurationMillis = focusDurationMillis,
                automaticFocusUsedToday = false,
                lastUsageDate = todayDate
            )
        }
    }
}
