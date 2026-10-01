package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.FocusLockConfig
import com.example.domain.model.FocusSession
import com.example.domain.model.FocusSessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_lock_preferences")

class FocusLockDataStore(private val context: Context) {

    private object PreferencesKeys {
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val CONFIG_LOCKED_AT = longPreferencesKey("config_locked_at")
        val CONFIG_UNLOCKS_AT = longPreferencesKey("config_unlocks_at")
        val MONITORED_APPS_JSON = stringPreferencesKey("monitored_apps_json")
        val COMBINED_THRESHOLD_MILLIS = longPreferencesKey("combined_threshold_millis")
        val FOCUS_DURATION_MILLIS = longPreferencesKey("focus_duration_millis")
        val AUTOMATIC_FOCUS_USED_TODAY = booleanPreferencesKey("automatic_focus_used_today")
        val LAST_USAGE_DATE = stringPreferencesKey("last_usage_date")
        val ACTIVE_SESSION_JSON = stringPreferencesKey("active_session_json")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeModeFlow: Flow<com.example.ui.theme.ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeString = preferences[PreferencesKeys.THEME_MODE]
            com.example.ui.theme.ThemeMode.fromString(modeString)
        }

    suspend fun setThemeMode(mode: com.example.ui.theme.ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    val configFlow: Flow<FocusLockConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isConfigured = preferences[PreferencesKeys.IS_CONFIGURED] ?: false
            val lockedAt = preferences[PreferencesKeys.CONFIG_LOCKED_AT] ?: 0L
            val unlocksAt = preferences[PreferencesKeys.CONFIG_UNLOCKS_AT] ?: 0L
            val appsJson = preferences[PreferencesKeys.MONITORED_APPS_JSON] ?: "{}"
            val threshold = preferences[PreferencesKeys.COMBINED_THRESHOLD_MILLIS]
                ?: TimeUnit.MINUTES.toMillis(90)
            val duration = preferences[PreferencesKeys.FOCUS_DURATION_MILLIS]
                ?: TimeUnit.HOURS.toMillis(2)
            val autoFocusUsed = preferences[PreferencesKeys.AUTOMATIC_FOCUS_USED_TODAY] ?: false
            val lastDate = preferences[PreferencesKeys.LAST_USAGE_DATE] ?: ""

            FocusLockConfig(
                isConfigured = isConfigured,
                configurationLockedAt = lockedAt,
                configurationUnlocksAt = unlocksAt,
                monitoredApps = parseMonitoredApps(appsJson),
                combinedThresholdMillis = threshold,
                focusDurationMillis = duration,
                automaticFocusUsedToday = autoFocusUsed,
                lastUsageDate = lastDate
            )
        }

    val activeSessionFlow: Flow<FocusSession?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val sessionJson = preferences[PreferencesKeys.ACTIVE_SESSION_JSON]
            parseFocusSession(sessionJson)
        }

    suspend fun saveConfiguration(config: FocusLockConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CONFIGURED] = config.isConfigured
            preferences[PreferencesKeys.CONFIG_LOCKED_AT] = config.configurationLockedAt
            preferences[PreferencesKeys.CONFIG_UNLOCKS_AT] = config.configurationUnlocksAt
            preferences[PreferencesKeys.MONITORED_APPS_JSON] = serializeMonitoredApps(config.monitoredApps)
            preferences[PreferencesKeys.COMBINED_THRESHOLD_MILLIS] = config.combinedThresholdMillis
            preferences[PreferencesKeys.FOCUS_DURATION_MILLIS] = config.focusDurationMillis
            preferences[PreferencesKeys.AUTOMATIC_FOCUS_USED_TODAY] = config.automaticFocusUsedToday
            preferences[PreferencesKeys.LAST_USAGE_DATE] = config.lastUsageDate
        }
    }

    suspend fun setAutomaticFocusUsedToday(used: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATIC_FOCUS_USED_TODAY] = used
        }
    }

    suspend fun updateLastUsageDate(dateString: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_USAGE_DATE] = dateString
        }
    }

    suspend fun resetDailyLimits(newDateString: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_USAGE_DATE] = newDateString
            preferences[PreferencesKeys.AUTOMATIC_FOCUS_USED_TODAY] = false
        }
    }

    suspend fun saveActiveSession(session: FocusSession?) {
        context.dataStore.edit { preferences ->
            if (session == null) {
                preferences.remove(PreferencesKeys.ACTIVE_SESSION_JSON)
            } else {
                preferences[PreferencesKeys.ACTIVE_SESSION_JSON] = serializeFocusSession(session)
            }
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private fun serializeMonitoredApps(apps: Map<String, Long>): String {
        val json = JSONObject()
        apps.forEach { (pkg, limit) ->
            json.put(pkg, limit)
        }
        return json.toString()
    }

    private fun parseMonitoredApps(jsonString: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        if (jsonString.isBlank()) return result
        try {
            val json = JSONObject(jsonString)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optLong(key, 0L)
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun serializeFocusSession(session: FocusSession): String {
        val json = JSONObject()
        json.put("id", session.id)
        json.put("type", session.type.name)
        json.put("startedAt", session.startedAt)
        json.put("endsAt", session.endsAt)
        json.put("durationMillis", session.durationMillis)
        json.put("isActive", session.isActive)
        json.put("isCompleted", session.isCompleted)
        json.put("startedElapsedRealtime", session.startedElapsedRealtime)
        json.put("endsElapsedRealtime", session.endsElapsedRealtime)
        return json.toString()
    }

    private fun parseFocusSession(jsonString: String?): FocusSession? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonString)
            val startedAt = json.optLong("startedAt", 0L)
            val endsAt = json.optLong("endsAt", 0L)
            val durationMillis = json.optLong("durationMillis", 0L)

            // Section 43: Validate timestamps against corrupted state
            if (startedAt <= 0L || endsAt <= startedAt || durationMillis <= 0L) {
                return null
            }

            FocusSession(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = FocusSessionType.valueOf(json.optString("type", FocusSessionType.MANUAL.name)),
                startedAt = startedAt,
                endsAt = endsAt,
                durationMillis = durationMillis,
                isActive = json.optBoolean("isActive", true),
                isCompleted = json.optBoolean("isCompleted", false),
                startedElapsedRealtime = json.optLong("startedElapsedRealtime", 0L),
                endsElapsedRealtime = json.optLong("endsElapsedRealtime", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
