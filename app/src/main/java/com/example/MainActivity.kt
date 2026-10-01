package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.enforcement.KioskLockTaskHelper
import com.example.ui.FocusLockViewModel
import com.example.ui.ScreenRoute
import com.example.ui.screens.AddAppsScreen
import com.example.ui.screens.FocusLockScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SetAppLimitsScreen
import com.example.ui.screens.SetDurationScreen
import com.example.ui.screens.SetThresholdScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SummaryScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.FocusLockTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FocusLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure window for lockscreen display during active focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            FocusLockTheme(themeMode = themeMode) {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

                // Full-screen immersive mode during active Focus Lock
                LaunchedEffect(currentScreen) {
                    try {
                        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                        if (currentScreen == ScreenRoute.FOCUS_LOCK) {
                            windowInsetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                            // Optional lock-task if permitted on managed devices (Section 44)
                            KioskLockTaskHelper.startLockTaskIfPermitted(this@MainActivity)
                        } else {
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                            KioskLockTaskHelper.stopLockTaskIfActive(this@MainActivity)
                        }
                    } catch (_: Exception) {
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FocusLockApp(
                        viewModel = viewModel,
                        currentThemeMode = themeMode,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.ensureFocusLockVisible()
        viewModel.refreshDashboard()
        viewModel.checkAllPermissions()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Section 1.4: Bring Focus Lock back if user presses Home or Recents during active focus
        if (viewModel.dashboardState.value.isFocusActive &&
            com.example.enforcement.FocusEnforcementManager.enforcementState.value != com.example.enforcement.EnforcementState.TEMPORARILY_INTERRUPTED
        ) {
            com.example.enforcement.FocusEnforcementManager.bringFocusLockToFront(this)
            viewModel.onExitAttempted()
        }
    }
}

@Composable
fun FocusLockApp(
    viewModel: FocusLockViewModel,
    currentThemeMode: com.example.ui.theme.ThemeMode,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val setupDraft by viewModel.setupDraft.collectAsStateWithLifecycle()
    val liveFocusRemaining by viewModel.liveRemainingFocusTime.collectAsStateWithLifecycle()

    // Back handling for navigation states — Focus Lock CANNOT be dismissed by Back
    BackHandler(
        enabled = currentScreen != ScreenRoute.WELCOME &&
                currentScreen != ScreenRoute.HOME &&
                currentScreen != ScreenRoute.FOCUS_LOCK
    ) {
        viewModel.navigateBack()
    }

    // Show reassurance alert if back is pressed while focus is active
    BackHandler(
        enabled = currentScreen == ScreenRoute.FOCUS_LOCK ||
                (currentScreen == ScreenRoute.HOME && dashboardState.isFocusActive)
    ) {
        viewModel.onExitAttempted()
        if (currentScreen == ScreenRoute.HOME && dashboardState.isFocusActive) {
            viewModel.ensureFocusLockVisible()
        }
    }

    when (currentScreen) {
        ScreenRoute.WELCOME -> {
            WelcomeScreen(
                onGetStarted = { viewModel.onGetStarted() },
                modifier = modifier
            )
        }

        ScreenRoute.PERMISSIONS -> {
            PermissionsScreen(
                isUsageGranted = setupDraft.isUsageGranted,
                isAccessibilityGranted = setupDraft.isAccessibilityGranted,
                isBatteryOptimizationIgnored = setupDraft.isBatteryOptimizationIgnored,
                onCheckPermissions = { viewModel.checkAllPermissions() },
                onContinue = { viewModel.onPermissionContinue() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.ADD_APPS -> {
            AddAppsScreen(
                installedApps = setupDraft.installedApps,
                selectedPackages = setupDraft.selectedPackages,
                onToggleApp = { pkg -> viewModel.toggleAppSelection(pkg) },
                onNext = { viewModel.onAddAppsNext() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.SET_LIMITS -> {
            SetAppLimitsScreen(
                selectedPackages = setupDraft.selectedPackages,
                appLimits = setupDraft.appLimits,
                installedApps = setupDraft.installedApps,
                appNameResolver = { pkg -> viewModel.getAppName(pkg) },
                onUpdateLimit = { pkg, limit -> viewModel.updateAppLimit(pkg, limit) },
                onNext = { viewModel.onSetLimitsNext() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.SET_THRESHOLD -> {
            val totalIndividualLimits = setupDraft.appLimits.values.sum()
            SetThresholdScreen(
                combinedThresholdMillis = setupDraft.combinedThresholdMillis,
                totalIndividualLimitsMillis = totalIndividualLimits,
                showWarningDialog = setupDraft.showExceedingThresholdWarning,
                onThresholdChanged = { millis -> viewModel.updateCombinedThreshold(millis) },
                onNext = { viewModel.onSetThresholdNext() },
                onDismissWarningAndProceed = { viewModel.dismissThresholdWarningAndProceed() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.SET_DURATION -> {
            SetDurationScreen(
                focusDurationMillis = setupDraft.focusDurationMillis,
                onDurationChanged = { millis -> viewModel.updateFocusDuration(millis) },
                onNext = { viewModel.onSetDurationNext() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.SUMMARY -> {
            SummaryScreen(
                appLimits = setupDraft.appLimits,
                combinedThresholdMillis = setupDraft.combinedThresholdMillis,
                focusDurationMillis = setupDraft.focusDurationMillis,
                appNameResolver = { pkg -> viewModel.getAppName(pkg) },
                showLockDialog = setupDraft.showLockConfirmationDialog,
                onRequestLockConfirmation = { viewModel.requestLockConfirmation() },
                onDismissLockDialog = { viewModel.dismissLockConfirmation() },
                onConfirmAndLock = { viewModel.confirmAndLockConfiguration() },
                onBack = { viewModel.navigateBack() },
                modifier = modifier
            )
        }

        ScreenRoute.HOME -> {
            HomeScreen(
                dashboardState = dashboardState,
                onStartFocusNow = { customDuration -> viewModel.startManualFocus(customDuration) },
                onOpenSettings = { viewModel.openSettings() },
                onRefresh = { viewModel.refreshDashboard() },
                modifier = modifier
            )
        }

        ScreenRoute.SETTINGS -> {
            SettingsScreen(
                config = dashboardState.config,
                isUsageAccessGranted = dashboardState.isUsageAccessGranted,
                isAccessibilityGranted = setupDraft.isAccessibilityGranted,
                isBatteryOptimizationIgnored = setupDraft.isBatteryOptimizationIgnored,
                isFocusActive = dashboardState.isFocusActive,
                themeMode = currentThemeMode,
                onThemeModeChanged = { viewModel.setThemeMode(it) },
                appNameResolver = { pkg -> viewModel.getAppName(pkg) },
                onStartReconfiguration = { viewModel.startReconfiguration() },
                onBack = { viewModel.returnToHome() },
                modifier = modifier
            )
        }

        ScreenRoute.FOCUS_LOCK -> {
            FocusLockScreen(
                liveRemainingTime = liveFocusRemaining,
                activeSession = dashboardState.activeSession,
                exitAttemptTrigger = viewModel.exitAttemptTrigger,
                onExitAttempt = { viewModel.onExitAttempted() },
                modifier = modifier
            )
        }
    }
}
