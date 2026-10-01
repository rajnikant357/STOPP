package com.example.enforcement

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager

sealed class ConnectivityControlResult {
    object SUCCESS : ConnectivityControlResult()
    data class NotSupported(val reason: String) : ConnectivityControlResult()
}

object ConnectivityHelper {

    fun isMobileDataEnabled(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm != null) {
            try {
                return tm.isDataEnabled
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            try {
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun isHotspotEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm != null) {
            try {
                val method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
                method.isAccessible = true
                val result = method.invoke(wm) as? Boolean
                if (result != null) return result
            } catch (_: Exception) {
            }
        }
        return false
    }

    /**
     * Attempts legitimate direct toggle of mobile data if permitted by Android platform APIs.
     * On standard consumer Android (non-system, non-carrier, non-device-owner),
     * direct programmatic modification of cellular state is restricted by the OS
     * (requiring signature|privileged android.permission.MODIFY_PHONE_STATE).
     * Focus Lock strictly respects Android security guidelines and does not use hidden exploits or root.
     */
    fun requestDirectMobileDataToggle(context: Context): ConnectivityControlResult {
        return ConnectivityControlResult.NotSupported(
            "Direct control isn't available on this Android version (requires platform privileges)."
        )
    }

    /**
     * Attempts legitimate direct toggle of system tethering hotspot if permitted by Android platform APIs.
     * On standard consumer Android, toggling system internet tethering requires
     * signature|privileged android.permission.TETHER_PRIVILEGED.
     * Direct toggle is not available for normal third-party applications.
     */
    fun requestDirectHotspotToggle(context: Context): ConnectivityControlResult {
        return ConnectivityControlResult.NotSupported(
            "Direct control isn't available on this Android version (requires platform privileges)."
        )
    }
}
