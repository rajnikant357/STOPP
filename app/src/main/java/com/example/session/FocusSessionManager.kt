package com.example.session

import com.example.data.local.FocusLockDataStore
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import kotlinx.coroutines.flow.Flow

class FocusSessionManager(private val dataStore: FocusLockDataStore) {

    val activeSessionFlow: Flow<FocusSession?> = dataStore.activeSessionFlow

    suspend fun startManualFocus(durationMillis: Long, now: Long = System.currentTimeMillis()): FocusSession {
        val session = FocusSession.create(
            type = FocusSessionType.MANUAL,
            durationMillis = durationMillis,
            now = now
        )
        dataStore.saveActiveSession(session)
        return session
    }

    suspend fun triggerAutomaticFocus(durationMillis: Long, now: Long = System.currentTimeMillis()): FocusSession {
        val session = FocusSession.create(
            type = FocusSessionType.AUTOMATIC,
            durationMillis = durationMillis,
            now = now
        )
        dataStore.saveActiveSession(session)
        return session
    }

    suspend fun completeActiveSession(session: FocusSession) {
        val completedSession = session.copy(
            isActive = false,
            isCompleted = true
        )
        dataStore.saveActiveSession(completedSession)
        // Consume automatic focus for the day (both manual and auto focus count as today's focus)
        dataStore.setAutomaticFocusUsedToday(true)
    }

    suspend fun exitFocusPreview() {
        dataStore.saveActiveSession(null)
    }
}
