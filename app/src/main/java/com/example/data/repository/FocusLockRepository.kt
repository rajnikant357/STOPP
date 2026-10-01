package com.example.data.repository

import android.content.Context
import com.example.data.local.FocusLockDataStore
import com.example.domain.model.AppInfo
import com.example.domain.model.FocusLockAppStatus
import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusLockDashboardState
import com.example.domain.model.FocusSession
import com.example.enforcement.FocusEnforcementManager
import com.example.monitoring.usage.DailyUsageManager
import com.example.monitoring.usage.UsageCalculator
import com.example.permissions.BatteryOptimizationHelper
import com.example.permissions.UsagePermissionHelper
import com.example.session.FocusSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FocusLockRepository(
    private val context: Context,
    private val dataStore: FocusLockDataStore,
    private val appsRepository: InstalledAppsRepository,
    private val usageCalculator: UsageCalculator,
    private val dailyUsageManager: DailyUsageManager,
    private val sessionManager: FocusSessionManager
) {

    val configFlow: Flow<FocusLockConfig> = dataStore.configFlow
    val activeSessionFlow: Flow<FocusSession?> = dataStore.activeSessionFlow

    fun isUsageAccessGranted(): Boolean {
        return UsagePermissionHelper.isUsageAccessGranted(context)
    }

    fun isAccessibilityAccessGranted(): Boolean {
        return FocusEnforcementManager.isAccessibilityServiceEnabled(context)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    suspend fun getInstalledApps(): List<AppInfo> {
        return appsRepository.getInstalledUserApps()
    }

    suspend fun saveSetupConfiguration(
        monitoredApps: Map<String, Long>,
        thresholdMillis: Long,
        durationMillis: Long
    ) {
        val today = dailyUsageManager.getCurrentLocalDateString()
        val config = FocusLockConfig.createLockedConfig(
            monitoredApps = monitoredApps,
            combinedThresholdMillis = thresholdMillis,
            focusDurationMillis = durationMillis,
            todayDate = today
        )
        dataStore.saveConfiguration(config)
    }

    suspend fun evaluateDashboardState(): FocusLockDashboardState {
        val config = dataStore.configFlow.first()
        val activeSession = dataStore.activeSessionFlow.first()
        val isGranted = isUsageAccessGranted()
        val today = dailyUsageManager.getCurrentLocalDateString()

        // Daily reset check
        dailyUsageManager.checkAndPerformDailyResetIfNeeded(config.lastUsageDate)

        // If an active session is in progress, check if it has expired
        val now = System.currentTimeMillis()
        var currentSession = activeSession
        if (currentSession != null && currentSession.isActive) {
            if (currentSession.isFinished(now)) {
                sessionManager.completeActiveSession(currentSession)
                FocusEnforcementManager.stopEnforcement(context)
                currentSession = currentSession.copy(isActive = false, isCompleted = true)
            }
        }

        // Calculate usages
        val appUsages = if (isGranted && config.monitoredApps.isNotEmpty()) {
            usageCalculator.calculateUsageForMonitoredPackages(
                monitoredAppLimits = config.monitoredApps,
                appNameResolver = { pkg -> appsRepository.getAppName(pkg) },
                iconResolver = { pkg -> appsRepository.getAppIcon(pkg) }
            )
        } else {
            config.monitoredApps.map { (pkg, limit) ->
                com.example.domain.model.AppUsageInfo(
                    packageName = pkg,
                    appName = appsRepository.getAppName(pkg),
                    usageMillis = 0L,
                    dailyLimitMillis = limit,
                    icon = appsRepository.getAppIcon(pkg)
                )
            }
        }

        val combinedUsage = usageCalculator.calculateCombinedUsage(appUsages)

        // Determine status
        val status = when {
            !config.isConfigured -> FocusLockAppStatus.SETUP
            currentSession != null && currentSession.isActive && !currentSession.isCompleted ->
                FocusLockAppStatus.FOCUS_LOCK_ACTIVE
            else -> FocusLockAppStatus.MONITORING
        }

        val dashboardState = FocusLockDashboardState(
            status = status,
            config = config,
            activeSession = currentSession,
            appUsages = appUsages,
            combinedUsageMillis = combinedUsage,
            combinedThresholdMillis = config.combinedThresholdMillis,
            automaticFocusUsedToday = config.automaticFocusUsedToday,
            isUsageAccessGranted = isGranted,
            currentDateString = today
        )

        // Check if automatic focus lock should trigger
        // Rule: combinedUsage >= activationThreshold AND !automaticFocusUsedToday AND no active session
        if (status == FocusLockAppStatus.MONITORING &&
            config.isConfigured &&
            !config.automaticFocusUsedToday &&
            config.combinedThresholdMillis > 0 &&
            combinedUsage >= config.combinedThresholdMillis &&
            (currentSession == null || !currentSession.isActive)
        ) {
            // Persist session before starting enforcement
            val autoSession = sessionManager.triggerAutomaticFocus(config.focusDurationMillis, now)
            FocusEnforcementManager.startEnforcement(context, autoSession)
            return dashboardState.copy(
                status = FocusLockAppStatus.FOCUS_LOCK_ACTIVE,
                activeSession = autoSession
            )
        }

        return dashboardState
    }

    suspend fun startManualFocus(customDurationMillis: Long? = null): FocusSession {
        val config = dataStore.configFlow.first()
        val duration = customDurationMillis ?: config.focusDurationMillis
        val session = sessionManager.startManualFocus(duration)
        FocusEnforcementManager.startEnforcement(context, session)
        return session
    }

    suspend fun completeFocusSession(session: FocusSession) {
        sessionManager.completeActiveSession(session)
        FocusEnforcementManager.stopEnforcement(context)
    }

    suspend fun exitFocusPreview() {
        sessionManager.exitFocusPreview()
        FocusEnforcementManager.stopEnforcement(context)
    }
}
