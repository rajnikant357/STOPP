package com.example.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.FocusLockDataStore
import com.example.domain.model.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private lateinit var dataStore: FocusLockDataStore

    override fun onCreate() {
        super.onCreate()
        dataStore = FocusLockDataStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground immediately with safe icon and try-catch
        try {
            val initialNotification = buildNotification("Focus Lock active", "Initializing focus session...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {
            try {
                val fallbackNotification = buildNotification("Focus Lock active", "Initializing focus session...")
                startForeground(NOTIFICATION_ID, fallbackNotification)
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startCountdownTicker()

        return START_STICKY
    }

    private fun startCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val session = dataStore.activeSessionFlow.first()
                if (session == null || !session.isActive || session.isFinished()) {
                    // Session expired or completed: finalize state
                    if (session != null && session.isActive) {
                        val completed = session.copy(isActive = false, isCompleted = true)
                        dataStore.saveActiveSession(completed)
                        dataStore.setAutomaticFocusUsedToday(true)
                    }
                    stopSelf()
                    break
                }

                val remainingText = "${session.formattedRemaining()} remaining"
                updateNotification("Focus Lock active", remainingText)

                // Active Enforcement Check (Section 1.2):
                // If user is not in a phone call, check current foreground app
                if (FocusEnforcementManager.enforcementState.value != EnforcementState.TEMPORARILY_INTERRUPTED) {
                    val fgPackage = FocusEnforcementManager.getForegroundPackageName(applicationContext)
                    if (fgPackage != null && fgPackage != packageName) {
                        val isAllowed = SafeAllowlist.isPackageAllowedDuringFocus(applicationContext, fgPackage)
                        if (!isAllowed) {
                            FocusEnforcementManager.bringFocusLockToFront(applicationContext)
                        }
                    }
                }

                delay(1000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Lock Active Session",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows remaining time during an active Focus Lock session"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        // Tapping the notification brings the user back to the Focus Lock screen
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "focus_lock_active_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.action.STOP_FOCUS_SERVICE"

        fun startService(context: Context, session: FocusSession) {
            try {
                val intent = Intent(context, FocusForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, FocusForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
