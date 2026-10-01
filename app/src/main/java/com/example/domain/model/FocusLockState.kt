package com.example.domain.model

enum class FocusLockAppStatus {
    SETUP,
    MONITORING,
    FOCUS_LOCK_ACTIVE
}

data class FocusLockDashboardState(
    val status: FocusLockAppStatus = FocusLockAppStatus.SETUP,
    val config: FocusLockConfig = FocusLockConfig(),
    val activeSession: FocusSession? = null,
    val appUsages: List<AppUsageInfo> = emptyList(),
    val combinedUsageMillis: Long = 0L,
    val combinedThresholdMillis: Long = 0L,
    val automaticFocusUsedToday: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val currentDateString: String = ""
) {
    val remainingActivationMillis: Long
        get() = (combinedThresholdMillis - combinedUsageMillis).coerceAtLeast(0L)

    val isCombinedThresholdReached: Boolean
        get() = combinedThresholdMillis > 0 && combinedUsageMillis >= combinedThresholdMillis

    val lockedAppsCount: Int
        get() = appUsages.count { it.isLimitReached }

    val totalMonitoredAppsCount: Int
        get() = appUsages.size

    val isFocusActive: Boolean
        get() = activeSession != null && activeSession.isActive && !activeSession.isCompleted
}
