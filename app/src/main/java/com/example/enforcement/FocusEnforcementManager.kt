package com.example.enforcement

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.MainActivity
import com.example.domain.model.FocusSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FocusEnforcementManager {

    private val _enforcementState = MutableStateFlow(EnforcementState.INACTIVE)
    val enforcementState: StateFlow<EnforcementState> = _enforcementState.asStateFlow()

    @Volatile
    var isAccessibilityServiceConnected: Boolean = false

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val expectedServiceName = FocusAccessibilityService::class.java.name
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == expectedServiceName
        }
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun startEnforcement(context: Context, session: FocusSession) {
        _enforcementState.value = EnforcementState.ENTERING

        // Start Foreground Service for ongoing session notification & countdown
        FocusForegroundService.startService(context, session)

        _enforcementState.value = EnforcementState.ACTIVE

        // Bring Focus Lock screen to front
        bringFocusLockToFront(context)
    }

    fun stopEnforcement(context: Context) {
        _enforcementState.value = EnforcementState.COMPLETING
        FocusForegroundService.stopService(context)
        _enforcementState.value = EnforcementState.COMPLETED
        _enforcementState.value = EnforcementState.INACTIVE
    }

    fun bringFocusLockToFront(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun getForegroundPackageName(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000L, now) ?: return null
            var lastPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    lastPackage = event.packageName
                }
            }
            lastPackage
        } catch (_: Exception) {
            null
        }
    }

    fun setTemporaryInterruption(interrupted: Boolean) {
        if (_enforcementState.value == EnforcementState.ACTIVE && interrupted) {
            _enforcementState.value = EnforcementState.TEMPORARILY_INTERRUPTED
        } else if (_enforcementState.value == EnforcementState.TEMPORARILY_INTERRUPTED && !interrupted) {
            _enforcementState.value = EnforcementState.ACTIVE
        }
    }
}
