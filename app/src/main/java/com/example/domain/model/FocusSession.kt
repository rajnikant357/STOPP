package com.example.domain.model

import android.os.SystemClock
import java.util.Locale
import java.util.UUID

enum class FocusSessionType {
    MANUAL,
    AUTOMATIC
}

data class FocusSession(
    val id: String = UUID.randomUUID().toString(),
    val type: FocusSessionType,
    val startedAt: Long,
    val endsAt: Long,
    val durationMillis: Long,
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,
    val startedElapsedRealtime: Long = 0L,
    val endsElapsedRealtime: Long = 0L
) {
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        if (!isActive || isCompleted) return 0L
        val remainingWall = (endsAt - now).coerceAtLeast(0L)

        // Only evaluate elapsedRealtime defense if evaluating live current time
        val currentWall = System.currentTimeMillis()
        val isEvaluatingCurrentTime = Math.abs(now - currentWall) < 2000L

        if (isEvaluatingCurrentTime && startedElapsedRealtime > 0 && endsElapsedRealtime > 0) {
            val currentElapsed = safeElapsedRealtime()
            if (currentElapsed >= startedElapsedRealtime) {
                val remainingElapsed = (endsElapsedRealtime - currentElapsed).coerceAtLeast(0L)
                val discrepancy = Math.abs(remainingWall - remainingElapsed)
                if (discrepancy > 60_000L) {
                    return remainingElapsed
                }
            }
        }

        return remainingWall
    }

    /**
     * Overload accepting explicit wall and elapsed times, primarily for testing clock manipulation defense.
     */
    fun remainingMillis(nowWallClock: Long, nowElapsed: Long): Long {
        if (!isActive || isCompleted) return 0L
        val remainingWall = (endsAt - nowWallClock).coerceAtLeast(0L)

        if (startedElapsedRealtime > 0 && endsElapsedRealtime > 0 && nowElapsed >= startedElapsedRealtime) {
            val remainingElapsed = (endsElapsedRealtime - nowElapsed).coerceAtLeast(0L)
            val discrepancy = Math.abs(remainingWall - remainingElapsed)
            if (discrepancy > 60_000L) {
                return remainingElapsed
            }
        }

        return remainingWall
    }

    fun isFinished(now: Long = System.currentTimeMillis()): Boolean {
        if (!isActive || isCompleted) return true
        return remainingMillis(now) <= 0L
    }

    fun isFinished(nowWallClock: Long, nowElapsed: Long): Boolean {
        if (!isActive || isCompleted) return true
        return remainingMillis(nowWallClock, nowElapsed) <= 0L
    }

    fun formattedRemaining(now: Long = System.currentTimeMillis()): String {
        val remaining = remainingMillis(now)
        val totalSeconds = (remaining + 999) / 1000 // Ceil to next second for smooth 02:00:00
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        fun safeElapsedRealtime(): Long {
            return try {
                SystemClock.elapsedRealtime()
            } catch (_: Throwable) {
                0L
            }
        }

        fun create(
            type: FocusSessionType,
            durationMillis: Long,
            now: Long = System.currentTimeMillis(),
            nowElapsed: Long = safeElapsedRealtime()
        ): FocusSession {
            val elapsed = if (nowElapsed > 0) nowElapsed else safeElapsedRealtime()
            return FocusSession(
                id = UUID.randomUUID().toString(),
                type = type,
                startedAt = now,
                endsAt = now + durationMillis,
                durationMillis = durationMillis,
                isActive = true,
                isCompleted = false,
                startedElapsedRealtime = elapsed,
                endsElapsedRealtime = if (elapsed > 0) elapsed + durationMillis else 0L
            )
        }
    }
}
