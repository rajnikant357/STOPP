package com.example.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.local.FocusLockDataStore
import com.example.monitoring.usage.UsageCalculator
import com.example.ui.AppLimitBlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dataStore: FocusLockDataStore
    private lateinit var usageCalculator: UsageCalculator

    private var lastRedirectTime = 0L
    private var lastBlockedPackage = ""

    override fun onCreate() {
        super.onCreate()
        dataStore = FocusLockDataStore(applicationContext)
        usageCalculator = UsageCalculator(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusEnforcementManager.isAccessibilityServiceConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank() || packageName == applicationContext.packageName) {
            return
        }

        val now = SystemClock.uptimeMillis()

        serviceScope.launch {
            try {
                val activeSession = dataStore.activeSessionFlow.first()
                val isFocusActive = activeSession != null && activeSession.isActive && !activeSession.isFinished()

                if (isFocusActive) {
                    // During active Focus Lock: only allow safe allowlist packages (phone/dialer/system UI)
                    val isAllowed = SafeAllowlist.isPackageAllowedDuringFocus(
                        context = applicationContext,
                        packageName = packageName
                    )

                    if (!isAllowed) {
                        // Debounce consecutive events slightly to prevent thrashing
                        if (now - lastRedirectTime > 250L || packageName != lastBlockedPackage) {
                            lastRedirectTime = now
                            lastBlockedPackage = packageName
                            bringFocusLockToFrontFromService()
                        }
                    }
                } else {
                    // Outside Focus Lock: enforce individual app daily limits
                    val config = dataStore.configFlow.first()
                    val limitMillis = config.monitoredApps[packageName]

                    if (limitMillis != null && limitMillis > 0) {
                        val usages = usageCalculator.calculateUsageForMonitoredPackages(
                            monitoredAppLimits = mapOf(packageName to limitMillis),
                            appNameResolver = { it }
                        )
                        val appUsage = usages.firstOrNull()

                        if (appUsage != null && appUsage.isLimitReached) {
                            if (now - lastRedirectTime > 500L || packageName != lastBlockedPackage) {
                                lastRedirectTime = now
                                lastBlockedPackage = packageName

                                val blockedIntent = AppLimitBlockedActivity.createIntent(
                                    context = this@FocusAccessibilityService,
                                    packageName = packageName
                                )
                                startActivity(blockedIntent)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun bringFocusLockToFrontFromService() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            FocusEnforcementManager.bringFocusLockToFront(applicationContext)
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        FocusEnforcementManager.isAccessibilityServiceConnected = false
        serviceScope.cancel()
    }
}
