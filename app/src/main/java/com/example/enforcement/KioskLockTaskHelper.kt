package com.example.enforcement

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Cleanly isolates Android LockTask/kiosk mode capabilities for managed devices (Section 44).
 * On standard consumer devices, this is completely optional and harmlessly returns false without
 * pretending to have device-owner privileges.
 */
object KioskLockTaskHelper {

    fun isLockTaskPermitted(activity: Activity): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                if (dpm != null && dpm.isLockTaskPermitted(activity.packageName)) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isLockTaskActive(activity: Activity): Boolean {
        return try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                am.isInLockTaskMode
            }
        } catch (_: Exception) {
            false
        }
    }

    fun startLockTaskIfPermitted(activity: Activity): Boolean {
        return try {
            if (isLockTaskPermitted(activity) && !isLockTaskActive(activity)) {
                activity.startLockTask()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun stopLockTaskIfActive(activity: Activity) {
        try {
            if (isLockTaskActive(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
        }
    }
}
