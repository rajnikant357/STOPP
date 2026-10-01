package com.example.domain.model

import android.graphics.drawable.Drawable
import java.util.Locale

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
)

data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val dailyLimitMillis: Long
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val dailyLimitMillis: Long,
    val icon: Drawable? = null
) {
    val remainingMillis: Long
        get() = (dailyLimitMillis - usageMillis).coerceAtLeast(0L)

    val isLimitReached: Boolean
        get() = usageMillis >= dailyLimitMillis

    fun formattedUsage(): String {
        return formatMinutesOrHours(usageMillis)
    }

    fun formattedLimit(): String {
        return formatMinutesOrHours(dailyLimitMillis)
    }

    fun formattedUsageVsLimit(): String {
        val usedMin = usageMillis / 60000
        val limitMin = dailyLimitMillis / 60000
        return "$usedMin / $limitMin min"
    }

    companion object {
        fun formatMinutesOrHours(millis: Long): String {
            val totalMinutes = millis / 60000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes} min"
            }
        }

        fun formatDurationClock(millis: Long): String {
            val totalSeconds = (millis + 999) / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
