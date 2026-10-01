package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.FocusSession
import com.example.enforcement.ConnectivityControlResult
import com.example.enforcement.ConnectivityHelper
import com.example.enforcement.FocusEnforcementManager
import com.example.enforcement.SafeAllowlist
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

enum class ConnectivityInfoType { MOBILE_DATA, HOTSPOT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusLockScreen(
    liveRemainingTime: String,
    activeSession: FocusSession?,
    exitAttemptTrigger: SharedFlow<Unit>? = null,
    onExitAttempt: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    var showAppsSheet by remember { mutableStateOf(false) }
    var showSessionInfoDialog by remember { mutableStateOf(false) }
    var showExitAlert by remember { mutableStateOf(false) }
    var exitAlertJob by remember { mutableStateOf<Job?>(null) }

    fun triggerExitAlert() {
        showExitAlert = true
        exitAlertJob?.cancel()
        exitAlertJob = coroutineScope.launch {
            delay(4000)
            showExitAlert = false
        }
    }

    LaunchedEffect(exitAttemptTrigger) {
        exitAttemptTrigger?.collect {
            triggerExitAlert()
        }
    }

    var connectivityInfoDialog by remember { mutableStateOf<ConnectivityInfoType?>(null) }
    var isMobileDataOn by remember { mutableStateOf(ConnectivityHelper.isMobileDataEnabled(context)) }
    var isHotspotOn by remember { mutableStateOf(ConnectivityHelper.isHotspotEnabled(context)) }

    // Synchronize actual connectivity state whenever sheet opens
    LaunchedEffect(showAppsSheet) {
        if (showAppsSheet) {
            isMobileDataOn = ConnectivityHelper.isMobileDataEnabled(context)
            isHotspotOn = ConnectivityHelper.isHotspotEnabled(context)
        }
    }

    // Refresh connectivity states on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isMobileDataOn = ConnectivityHelper.isMobileDataEnabled(context)
                isHotspotOn = ConnectivityHelper.isHotspotEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Section 1.3: Back button must NOT close Focus Lock. Show reassurance alert instead.
    BackHandler {
        onExitAttempt?.invoke()
        triggerExitAlert()
    }

    // Connectivity Limitation Info Dialog (Educational, In-App only, Focus Lock remains strictly active)
    if (connectivityInfoDialog != null) {
        val infoType = connectivityInfoDialog!!
        val isCurrentOn = if (infoType == ConnectivityInfoType.MOBILE_DATA) isMobileDataOn else isHotspotOn
        AlertDialog(
            onDismissRequest = { connectivityInfoDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "System restriction",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = if (infoType == ConnectivityInfoType.MOBILE_DATA) "Mobile Data" else "Hotspot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current State",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isCurrentOn) "ON (Active)" else "OFF (Disabled)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (infoType == ConnectivityInfoType.MOBILE_DATA) {
                            "Android does not allow ordinary consumer apps to directly change mobile-data state.\n\nFocus Lock remains active."
                        } else {
                            "Android does not allow ordinary consumer apps to directly control system internet tethering.\n\nFocus Lock remains active."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { connectivityInfoDialog = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("connectivity_info_dismiss_button")
                ) {
                    Text("Got it")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Accessible Apps Bottom Sheet (Strictly Essential Functions only)
    if (showAppsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = {
                showAppsSheet = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val sheetScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
                    .verticalScroll(sheetScrollState)
            ) {
                Text(
                    text = "Accessible Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Only essential communication is accessible during Focus Lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Phone item — Dialer is allowlisted; timer continues uninterrupted
                AccessibleAppRow(
                    iconText = "☎",
                    title = "Phone",
                    subtitle = "Make or receive calls",
                    actionLabel = "Open Dialer",
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(dialIntent)
                        } catch (_: Exception) {
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Mobile Data item — Truthful system state display (action unavailable)
                AccessibleConnectivityRow(
                    iconText = "📶",
                    title = "Mobile Data",
                    isEnabled = isMobileDataOn,
                    onInfoClick = {
                        connectivityInfoDialog = ConnectivityInfoType.MOBILE_DATA
                    },
                    testTag = "mobile_data_toggle"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Hotspot item — Truthful system state display (action unavailable)
                AccessibleConnectivityRow(
                    iconText = "📡",
                    title = "Hotspot",
                    isEnabled = isHotspotOn,
                    onInfoClick = {
                        connectivityInfoDialog = ConnectivityInfoType.HOTSPOT
                    },
                    testTag = "hotspot_toggle"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Note: Distracting applications (Instagram, WhatsApp, YouTube, Chrome, etc.) remain strictly unavailable until this session ends.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Session Info Dialog — NO CANCEL/STOP/SKIP BUTTON
    if (showSessionInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSessionInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Active Lock",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Focus Lock Active",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Session Type: ${activeSession?.type?.name ?: "MANUAL"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Time Remaining: $liveRemainingTime",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Phone navigation and non-essential applications are locked until the session ends. There is no option to cancel or skip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSessionInfoDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("resume_focus_button")
                ) {
                    Text("Resume Focus")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Minimal layout supporting both Portrait and Landscape & Dark Mode (Sections 1, 2.5):
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = if (isLandscape) 32.dp else 24.dp,
                vertical = if (isLandscape) 12.dp else 20.dp
            )
            .testTag("focus_lock_screen")
    ) {
        // Top navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showAppsSheet = true },
                modifier = Modifier.testTag("focus_apps_button")
            ) {
                Text(
                    text = "Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = { showSessionInfoDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("focus_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Session Info & Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Center display responsive for both Portrait and Landscape & Dark Mode
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Work/Study you have",
                style = if (isLandscape) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

            // Tabular monospaced numerals ensuring no horizontal wobble as seconds tick
            Text(
                text = liveRemainingTime,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isLandscape) 64.sp else 52.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-1).sp,
                modifier = Modifier.testTag("focus_countdown_timer")
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

            Text(
                text = "Time left",
                style = if (isLandscape) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
        }

        // Bottom alert when user attempts to exit the app
        AnimatedVisibility(
            visible = showExitAlert,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .testTag("exit_attempt_alert")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { showExitAlert = false }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Focus Active",
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Just focus on your work, the app will exit automaticlly when your work is done.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibleAppRow(
    iconText: String,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = iconText,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun AccessibleConnectivityRow(
    iconText: String,
    title: String,
    isEnabled: Boolean,
    onInfoClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onInfoClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = iconText,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Currently ${if (isEnabled) "ON" else "OFF"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Direct control unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // State indicator badge (informative presentation, non-toggle)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    1.dp,
                    if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(6.dp)
                )
                .clickable { onInfoClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(testTag)
        ) {
            Text(
                text = if (isEnabled) "ON" else "OFF",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
