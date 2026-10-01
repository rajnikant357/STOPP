package com.example.monitoring.usage

import com.example.data.local.FocusLockDataStore
import java.time.LocalDate
import java.time.ZoneId

class DailyUsageManager(private val dataStore: FocusLockDataStore) {

    fun getCurrentLocalDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    suspend fun checkAndPerformDailyResetIfNeeded(lastRecordedDate: String): Boolean {
        val today = getCurrentLocalDateString()
        if (lastRecordedDate.isBlank()) {
            dataStore.updateLastUsageDate(today)
            return false
        }

        if (today != lastRecordedDate) {
            // New local calendar day has started
            dataStore.resetDailyLimits(today)
            return true
        }

        return false
    }
}
