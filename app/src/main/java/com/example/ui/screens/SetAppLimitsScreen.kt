package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AppInfo
import com.example.domain.model.AppUsageInfo
import com.example.ui.components.AppIconView
import java.util.concurrent.TimeUnit

object AppLimitConstants {
    val MIN_LIMIT_MILLIS: Long = TimeUnit.MINUTES.toMillis(5) // 5 minutes
    val MAX_LIMIT_MILLIS: Long = TimeUnit.MINUTES.toMillis(90) // 1.5 hours (90 minutes)
    val STEP_MILLIS: Long = TimeUnit.MINUTES.toMillis(5) // 5-minute increments

    val PRESETS: List<Pair<Long, String>> = listOf(
        TimeUnit.MINUTES.toMillis(5) to "5m",
        TimeUnit.MINUTES.toMillis(15) to "15m",
        TimeUnit.MINUTES.toMillis(30) to "30m",
        TimeUnit.MINUTES.toMillis(45) to "45m",
        TimeUnit.MINUTES.toMillis(60) to "1h",
        TimeUnit.MINUTES.toMillis(90) to "1.5h"
    )
}

@Composable
fun SetAppLimitsScreen(
    selectedPackages: Set<String>,
    appLimits: Map<String, Long>,
    installedApps: List<AppInfo>,
    appNameResolver: (String) -> String,
    onUpdateLimit: (String, Long) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAppList = selectedPackages.map { pkg ->
        val appInfo = installedApps.find { it.packageName == pkg }
        val name = appInfo?.appName ?: appNameResolver(pkg)
        val icon = appInfo?.icon
        Triple(pkg, name, icon)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("set_limits_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Individual Limits",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose daily limits from 5 minutes to 1.5 hours max. When an app reaches its limit, it locks for the day.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pill tag indicating allowed range
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allowed range: 5 min – 1.5 hr max",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("app_limits_list")
            ) {
                items(selectedAppList, key = { it.first }) { (pkg, name, icon) ->
                    val currentLimit = (appLimits[pkg] ?: TimeUnit.MINUTES.toMillis(30)).coerceIn(
                        AppLimitConstants.MIN_LIMIT_MILLIS,
                        AppLimitConstants.MAX_LIMIT_MILLIS
                    )
                    AppLimitConfigRow(
                        packageName = pkg,
                        appName = name,
                        icon = icon,
                        currentLimitMillis = currentLimit,
                        onSelectPreset = { presetMillis ->
                            onUpdateLimit(pkg, presetMillis)
                        },
                        onIncrement = {
                            val newLimit = (currentLimit + AppLimitConstants.STEP_MILLIS)
                                .coerceAtMost(AppLimitConstants.MAX_LIMIT_MILLIS)
                            onUpdateLimit(pkg, newLimit)
                        },
                        onDecrement = {
                            val newLimit = (currentLimit - AppLimitConstants.STEP_MILLIS)
                                .coerceAtLeast(AppLimitConstants.MIN_LIMIT_MILLIS)
                            onUpdateLimit(pkg, newLimit)
                        }
                    )
                }
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .align(Alignment.BottomCenter)
                .testTag("set_limits_next_button"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "Next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppLimitConfigRow(
    packageName: String,
    appName: String,
    icon: android.graphics.drawable.Drawable?,
    currentLimitMillis: Long,
    onSelectPreset: (Long) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val canDecrement = currentLimitMillis > AppLimitConstants.MIN_LIMIT_MILLIS
    val canIncrement = currentLimitMillis < AppLimitConstants.MAX_LIMIT_MILLIS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("limit_row_$packageName")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIconView(icon = icon, appName = appName)

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppUsageInfo.formatDurationClock(currentLimitMillis),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stepper controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDecrement,
                    enabled = canDecrement,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (canDecrement) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                        .border(
                            1.dp,
                            if (canDecrement) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .testTag("decrement_limit_$packageName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease limit",
                        tint = if (canDecrement) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = AppUsageInfo.formatMinutesOrHours(currentLimitMillis),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("limit_value_$packageName")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onIncrement,
                    enabled = canIncrement,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (canIncrement) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                        .border(
                            1.dp,
                            if (canIncrement) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .testTag("increment_limit_$packageName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase limit",
                        tint = if (canIncrement) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick presets from 5 min to 1.5 hr max (90 min)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLimitConstants.PRESETS.forEach { (presetMillis, label) ->
                val isSelected = currentLimitMillis == presetMillis
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectPreset(presetMillis) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("preset_${label}_$packageName"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
