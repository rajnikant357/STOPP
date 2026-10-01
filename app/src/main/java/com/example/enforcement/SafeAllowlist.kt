package com.example.enforcement

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager

object SafeAllowlist {

    private val commonEmergencyAndSystemPackages = setOf(
        "com.android.systemui",
        "com.google.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.sec.android.app.phone"
    )

    fun getDefaultDialerPackage(context: Context): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        if (!defaultDialer.isNullOrBlank()) {
            return defaultDialer
        }

        // Fallback: resolve dialer intent
        val dialIntent = Intent(Intent.ACTION_DIAL)
        val resolveInfo = context.packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    fun getAllDialerPackages(context: Context): Set<String> {
        val dialers = mutableSetOf<String>()
        val defaultDialer = getDefaultDialerPackage(context)
        if (!defaultDialer.isNullOrBlank()) {
            dialers.add(defaultDialer)
        }

        val dialIntent = Intent(Intent.ACTION_DIAL)
        val matches = context.packageManager.queryIntentActivities(dialIntent, PackageManager.MATCH_ALL)
        for (info in matches) {
            info.activityInfo?.packageName?.let { dialers.add(it) }
        }

        return dialers
    }

    fun isPackageAllowedDuringFocus(context: Context, packageName: String): Boolean {
        // 1. Focus Lock's own package is always allowed
        if (packageName == context.packageName) {
            return true
        }

        // 2. Emergency & system UI components (lockscreen, status bar, active phone call UI)
        if (commonEmergencyAndSystemPackages.contains(packageName)) {
            return true
        }

        // 3. Dynamically resolved phone / dialer apps
        if (getAllDialerPackages(context).contains(packageName)) {
            return true
        }

        // Everything else (including Android Settings) is strictly blocked during Focus Lock
        return false
    }

    fun isSettingsPackage(packageName: String): Boolean {
        return packageName == "com.android.settings" ||
                packageName.startsWith("com.android.settings.") ||
                packageName == "com.google.android.settings" ||
                packageName == "com.samsung.android.settings"
    }
}
