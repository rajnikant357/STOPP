package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FocusLockDataStore
import com.example.data.repository.FocusLockRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.AppInfo
import com.example.domain.model.FocusLockAppStatus
import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusLockDashboardState
import com.example.domain.model.FocusSession
import com.example.enforcement.FocusEnforcementManager
import com.example.monitoring.usage.DailyUsageManager
import com.example.monitoring.usage.UsageCalculator
import com.example.session.FocusSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class ScreenRoute {
    WELCOME,
    PERMISSIONS,
    ADD_APPS,
    SET_LIMITS,
    SET_THRESHOLD,
    SET_DURATION,
    SUMMARY,
    HOME,
    SETTINGS,
    FOCUS_LOCK
}

data class SetupDraftState(
    val selectedPackages: Set<String> = emptySet(),
    val appLimits: Map<String, Long> = emptyMap(), // pkg -> millis
    val combinedThresholdMillis: Long = TimeUnit.MINUTES.toMillis(90),
    val focusDurationMillis: Long = TimeUnit.HOURS.toMillis(2),
    val installedApps: List<AppInfo> = emptyList(),
    val isUsageGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val showExceedingThresholdWarning: Boolean = false,
    val showLockConfirmationDialog: Boolean = false
)

class FocusLockViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = FocusLockDataStore(application)
    private val installedAppsRepo = InstalledAppsRepository(application)
    private val usageCalculator = UsageCalculator(application)
    private val dailyUsageManager = DailyUsageManager(dataStore)
    private val sessionManager = FocusSessionManager(dataStore)

    val repository = FocusLockRepository(
        context = application,
        dataStore = dataStore,
        appsRepository = installedAppsRepo,
        usageCalculator = usageCalculator,
        dailyUsageManager = dailyUsageManager,
        sessionManager = sessionManager
    )

    private val _currentScreen = MutableStateFlow(ScreenRoute.WELCOME)
    val currentScreen: StateFlow<ScreenRoute> = _currentScreen.asStateFlow()

    private val _dashboardState = MutableStateFlow(FocusLockDashboardState())
    val dashboardState: StateFlow<FocusLockDashboardState> = _dashboardState.asStateFlow()

    private val _setupDraft = MutableStateFlow(SetupDraftState())
    val setupDraft: StateFlow<SetupDraftState> = _setupDraft.asStateFlow()

    private val _liveRemainingFocusTime = MutableStateFlow("02:00:00")
    val liveRemainingFocusTime: StateFlow<String> = _liveRemainingFocusTime.asStateFlow()

    private val _exitAttemptTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitAttemptTrigger: SharedFlow<Unit> = _exitAttemptTrigger.asSharedFlow()

    fun onExitAttempted() {
        _exitAttemptTrigger.tryEmit(Unit)
    }

    val themeMode: StateFlow<com.example.ui.theme.ThemeMode> = dataStore.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = com.example.ui.theme.ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: com.example.ui.theme.ThemeMode) {
        viewModelScope.launch {
            dataStore.setThemeMode(mode)
        }
    }

    private var monitorJob: Job? = null
    private var timerTickerJob: Job? = null

    init {
        checkInitialStatus()
        startPeriodicStateMonitoring()
    }

    private fun checkInitialStatus() {
        viewModelScope.launch {
            val config = repository.configFlow.first()
            val activeSession = repository.activeSessionFlow.first()
            val hasUsage = repository.isUsageAccessGranted()
            val hasAccessibility = repository.isAccessibilityAccessGranted()
            val isBatteryIgnored = repository.isBatteryOptimizationIgnored()

            _setupDraft.value = _setupDraft.value.copy(
                isUsageGranted = hasUsage,
                isAccessibilityGranted = hasAccessibility,
                isBatteryOptimizationIgnored = isBatteryIgnored,
                installedApps = repository.getInstalledApps()
            )

            if (!config.isConfigured) {
                _currentScreen.value = ScreenRoute.WELCOME
            } else if (activeSession != null && activeSession.isActive && !activeSession.isFinished()) {
                _currentScreen.value = ScreenRoute.FOCUS_LOCK
                startFocusCountdown(activeSession)
            } else {
                _currentScreen.value = ScreenRoute.HOME
            }

            refreshDashboard()
        }
    }

    private fun startPeriodicStateMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                refreshDashboard()
                delay(3000) // Poll every 3 seconds for usage updates
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val newState = repository.evaluateDashboardState()
            _dashboardState.value = newState

            // Check if automatic session just started
            if (newState.status == FocusLockAppStatus.FOCUS_LOCK_ACTIVE &&
                _currentScreen.value != ScreenRoute.FOCUS_LOCK
            ) {
                _currentScreen.value = ScreenRoute.FOCUS_LOCK
                newState.activeSession?.let { startFocusCountdown(it) }
            } else if (newState.status != FocusLockAppStatus.FOCUS_LOCK_ACTIVE &&
                _currentScreen.value == ScreenRoute.FOCUS_LOCK
            ) {
                // Focus session completed
                _currentScreen.value = ScreenRoute.HOME
            }
        }
    }

    /**
     * Section 1.6: Enforces that if an active Focus Session exists,
     * Focus Lock UI is visible, the ticker is running, and enforcement is active.
     */
    fun ensureFocusLockVisible() {
        viewModelScope.launch {
            val session = repository.activeSessionFlow.first()
            if (session != null && session.isActive) {
                if (session.isFinished()) {
                    repository.completeFocusSession(session)
                    refreshDashboard()
                    _currentScreen.value = ScreenRoute.HOME
                } else {
                    _currentScreen.value = ScreenRoute.FOCUS_LOCK
                    startFocusCountdown(session)
                    FocusEnforcementManager.startEnforcement(getApplication(), session)
                }
            }
        }
    }

    fun checkAllPermissions() {
        val usageGranted = repository.isUsageAccessGranted()
        val accessibilityGranted = repository.isAccessibilityAccessGranted()
        val batteryIgnored = repository.isBatteryOptimizationIgnored()

        _setupDraft.value = _setupDraft.value.copy(
            isUsageGranted = usageGranted,
            isAccessibilityGranted = accessibilityGranted,
            isBatteryOptimizationIgnored = batteryIgnored
        )

        viewModelScope.launch {
            refreshDashboard()
        }
    }

    // --- SETUP NAVIGATION & ACTIONS ---

    fun onGetStarted() {
        checkAllPermissions()
        _currentScreen.value = ScreenRoute.PERMISSIONS
    }

    fun onPermissionContinue() {
        viewModelScope.launch {
            val apps = repository.getInstalledApps()
            _setupDraft.value = _setupDraft.value.copy(
                installedApps = apps,
                isUsageGranted = repository.isUsageAccessGranted(),
                isAccessibilityGranted = repository.isAccessibilityAccessGranted(),
                isBatteryOptimizationIgnored = repository.isBatteryOptimizationIgnored()
            )
            _currentScreen.value = ScreenRoute.ADD_APPS
        }
    }

    fun toggleAppSelection(packageName: String) {
        val current = _setupDraft.value.selectedPackages.toMutableSet()
        val currentLimits = _setupDraft.value.appLimits.toMutableMap()
        if (current.contains(packageName)) {
            current.remove(packageName)
            currentLimits.remove(packageName)
        } else {
            current.add(packageName)
            if (!currentLimits.containsKey(packageName)) {
                // Default 30 min limit
                currentLimits[packageName] = TimeUnit.MINUTES.toMillis(30)
            }
        }
        _setupDraft.value = _setupDraft.value.copy(
            selectedPackages = current,
            appLimits = currentLimits
        )
    }

    fun onAddAppsNext() {
        if (_setupDraft.value.selectedPackages.isNotEmpty()) {
            _currentScreen.value = ScreenRoute.SET_LIMITS
        }
    }

    fun updateAppLimit(packageName: String, durationMillis: Long) {
        val clamped = durationMillis.coerceIn(
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(90) // 1.5 hours max
        )
        val currentLimits = _setupDraft.value.appLimits.toMutableMap()
        currentLimits[packageName] = clamped
        _setupDraft.value = _setupDraft.value.copy(appLimits = currentLimits)
    }

    fun onSetLimitsNext() {
        _currentScreen.value = ScreenRoute.SET_THRESHOLD
    }

    fun updateCombinedThreshold(thresholdMillis: Long) {
        _setupDraft.value = _setupDraft.value.copy(combinedThresholdMillis = thresholdMillis)
    }

    fun onSetThresholdNext() {
        val totalLimits = _setupDraft.value.appLimits.values.sum()
        if (_setupDraft.value.combinedThresholdMillis > totalLimits && totalLimits > 0) {
            _setupDraft.value = _setupDraft.value.copy(showExceedingThresholdWarning = true)
        } else {
            _currentScreen.value = ScreenRoute.SET_DURATION
        }
    }

    fun dismissThresholdWarningAndProceed() {
        _setupDraft.value = _setupDraft.value.copy(showExceedingThresholdWarning = false)
        _currentScreen.value = ScreenRoute.SET_DURATION
    }

    fun updateFocusDuration(durationMillis: Long) {
        _setupDraft.value = _setupDraft.value.copy(focusDurationMillis = durationMillis)
    }

    fun onSetDurationNext() {
        _currentScreen.value = ScreenRoute.SUMMARY
    }

    fun requestLockConfirmation() {
        _setupDraft.value = _setupDraft.value.copy(showLockConfirmationDialog = true)
    }

    fun dismissLockConfirmation() {
        _setupDraft.value = _setupDraft.value.copy(showLockConfirmationDialog = false)
    }

    private var isStartingFocus = false
    private var isSavingConfig = false

    fun confirmAndLockConfiguration() {
        _setupDraft.value = _setupDraft.value.copy(showLockConfirmationDialog = false)
        if (isSavingConfig) return
        isSavingConfig = true
        viewModelScope.launch {
            try {
                repository.saveSetupConfiguration(
                    monitoredApps = _setupDraft.value.appLimits,
                    thresholdMillis = _setupDraft.value.combinedThresholdMillis,
                    durationMillis = _setupDraft.value.focusDurationMillis
                )
                refreshDashboard()
                _currentScreen.value = ScreenRoute.HOME
            } finally {
                isSavingConfig = false
            }
        }
    }

    fun startReconfiguration() {
        val config = _dashboardState.value.config
        if (config.isConfigurationLocked()) return

        val clampedLimits = config.monitoredApps.mapValues { (_, limit) ->
            limit.coerceIn(TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(90))
        }
        _setupDraft.value = _setupDraft.value.copy(
            selectedPackages = config.monitoredApps.keys,
            appLimits = clampedLimits,
            combinedThresholdMillis = config.combinedThresholdMillis,
            focusDurationMillis = config.focusDurationMillis
        )
        _currentScreen.value = ScreenRoute.ADD_APPS
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            ScreenRoute.PERMISSIONS -> _currentScreen.value = ScreenRoute.WELCOME
            ScreenRoute.ADD_APPS -> {
                if (_dashboardState.value.config.isConfigured) {
                    _currentScreen.value = ScreenRoute.SETTINGS
                } else {
                    _currentScreen.value = ScreenRoute.PERMISSIONS
                }
            }
            ScreenRoute.SET_LIMITS -> _currentScreen.value = ScreenRoute.ADD_APPS
            ScreenRoute.SET_THRESHOLD -> _currentScreen.value = ScreenRoute.SET_LIMITS
            ScreenRoute.SET_DURATION -> _currentScreen.value = ScreenRoute.SET_THRESHOLD
            ScreenRoute.SUMMARY -> _currentScreen.value = ScreenRoute.SET_DURATION
            ScreenRoute.SETTINGS -> _currentScreen.value = ScreenRoute.HOME
            ScreenRoute.FOCUS_LOCK -> {
                // Focus Lock cannot be escaped via back navigation!
            }
            else -> {}
        }
    }

    // --- HOME & SESSION ACTIONS ---

    fun startManualFocus(customDurationMillis: Long? = null) {
        if (_dashboardState.value.isFocusActive) {
            _currentScreen.value = ScreenRoute.FOCUS_LOCK
            return
        }
        if (isStartingFocus) return
        isStartingFocus = true

        viewModelScope.launch {
            try {
                val session = repository.startManualFocus(customDurationMillis)
                _currentScreen.value = ScreenRoute.FOCUS_LOCK
                startFocusCountdown(session)
                refreshDashboard()
            } finally {
                isStartingFocus = false
            }
        }
    }

    private fun startFocusCountdown(session: FocusSession) {
        timerTickerJob?.cancel()
        timerTickerJob = viewModelScope.launch {
            while (isActive) {
                _liveRemainingFocusTime.value = session.formattedRemaining()
                if (session.isFinished()) {
                    repository.completeFocusSession(session)
                    refreshDashboard()
                    _currentScreen.value = ScreenRoute.HOME
                    break
                }
                delay(500)
            }
        }
    }

    fun openSettings() {
        _currentScreen.value = ScreenRoute.SETTINGS
    }

    fun returnToHome() {
        _currentScreen.value = ScreenRoute.HOME
    }

    fun getAppName(packageName: String): String {
        return installedAppsRepo.getAppName(packageName)
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
        timerTickerJob?.cancel()
    }
}
