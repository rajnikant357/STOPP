package com.example.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.FocusLockDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val dataStore = FocusLockDataStore(context.applicationContext)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val session = dataStore.activeSessionFlow.first()
                if (session != null && session.isActive) {
                    if (session.isFinished()) {
                        // Expired while device was off
                        val completed = session.copy(isActive = false, isCompleted = true)
                        dataStore.saveActiveSession(completed)
                        dataStore.setAutomaticFocusUsedToday(true)
                    } else {
                        // Still active: restore enforcement!
                        FocusEnforcementManager.startEnforcement(context.applicationContext, session)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
