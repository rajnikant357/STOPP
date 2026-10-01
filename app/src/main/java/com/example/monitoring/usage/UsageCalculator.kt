package com.example.monitoring.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.domain.model.AppUsageInfo
import java.time.LocalDate
import java.time.ZoneId

class UsageCalculator(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun getTodayStartAndEndEpochMillis(): Pair<Long, Long> {
        val zoneId = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zoneId)
        val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return Pair(startOfDay, now)
    }

    fun calculateUsageForMonitoredPackages(
        monitoredAppLimits: Map<String, Long>,
        appNameResolver: (String) -> String,
        iconResolver: (String) -> android.graphics.drawable.Drawable? = { null }
    ): List<AppUsageInfo> {
        if (monitoredAppLimits.isEmpty()) return emptyList()

        val (startOfDay, now) = getTodayStartAndEndEpochMillis()
        val usageMap = queryUsageStats(startOfDay, now)

        return monitoredAppLimits.map { (pkg, limitMillis) ->
            val usedMillis = usageMap[pkg] ?: 0L
            AppUsageInfo(
                packageName = pkg,
                appName = appNameResolver(pkg),
                usageMillis = usedMillis,
                dailyLimitMillis = limitMillis,
                icon = iconResolver(pkg)
            )
        }
    }

    fun calculateCombinedUsage(appUsages: List<AppUsageInfo>): Long {
        return appUsages.sumOf { it.usageMillis }
    }

    private fun queryUsageStats(startTime: Long, endTime: Long): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val manager = usageStatsManager ?: return result

        try {
            // Daily interval query
            val statsList = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (!statsList.isNullOrEmpty()) {
                for (stat in statsList) {
                    val current = result[stat.packageName] ?: 0L
                    result[stat.packageName] = current + stat.totalTimeInForeground
                }
            }
        } catch (_: Exception) {
            // Usage access permission might not be granted yet
        }

        return result
    }
}
