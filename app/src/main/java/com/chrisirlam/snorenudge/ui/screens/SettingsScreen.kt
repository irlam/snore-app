package com.chrisirlam.snorenudge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisirlam.snorenudge.ui.theme.*
import com.chrisirlam.snorenudge.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToBattery: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SnorePrimary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ── Detection ──────────────────────────────────────────────────────────
        SettingsSection("Detection")

        SliderSetting(
            label = "Sensitivity",
            value = settings.sensitivity,
            valueLabel = "${(settings.sensitivity * 100).roundToInt()}%",
            onValueChange = viewModel::setSensitivity,
            icon = Icons.Outlined.Tune
        )

        StepperSetting(
            label = "Trigger Duration",
            value = settings.triggerDurationSeconds,
            unit = "s",
            range = 2..30,
            onDecrease = { viewModel.setTriggerDuration((settings.triggerDurationSeconds - 1).coerceAtLeast(2)) },
            onIncrease = { viewModel.setTriggerDuration((settings.triggerDurationSeconds + 1).coerceAtMost(30)) },
            icon = Icons.Default.Timer
        )

        StepperSetting(
            label = "Cooldown Duration",
            value = settings.cooldownDurationSeconds,
            unit = "s",
            range = 10..300,
            onDecrease = { viewModel.setCooldownDuration((settings.cooldownDurationSeconds - 10).coerceAtLeast(10)) },
            onIncrease = { viewModel.setCooldownDuration((settings.cooldownDurationSeconds + 10).coerceAtMost(300)) },
            icon = Icons.Default.Timelapse
        )

        // ── Notifications ─────────────────────────────────────────────────────
        SettingsSection("Notifications")

        SwitchSetting(
            label = "Watch Vibration",
            checked = settings.watchVibrationEnabled,
            onCheckedChange = viewModel::setWatchVibration,
            icon = Icons.Default.Watch
        )

        SwitchSetting(
            label = "Phone Vibration",
            checked = settings.phoneVibrationEnabled,
            onCheckedChange = viewModel::setPhoneVibration,
            icon = Icons.Default.Vibration
        )

        SwitchSetting(
            label = "Phone Sound Alert",
            checked = settings.phoneSoundEnabled,
            onCheckedChange = viewModel::setPhoneSound,
            icon = Icons.Default.VolumeUp
        )

        SwitchSetting(
            label = "Strong Vibration",
            checked = settings.vibrationStrong,
            onCheckedChange = viewModel::setVibrationStrong,
            icon = Icons.Default.Bolt,
            subtitle = "Uses stronger repeating pattern"
        )

        // ── Device ────────────────────────────────────────────────────────────
        SettingsSection("Device")

        NavigationSetting(
            label = "Battery Optimisation",
            subtitle = "Keep app running overnight",
            icon = Icons.Default.BatteryAlert,
            onClick = onNavigateToBattery
        )

        // ── Developer ─────────────────────────────────────────────────────────
        SettingsSection("Developer")

        SwitchSetting(
            label = "Debug Mode",
            checked = settings.debugMode,
            onCheckedChange = viewModel::setDebugMode,
            icon = Icons.Default.BugReport,
            subtitle = "Shows debug tab and verbose logs"
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SnorePrimary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    HorizontalDivider(color = SnoreSurfaceVariant)
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = SnorePrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(valueLabel, color = SnorePrimary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = SnorePrimary, activeTrackColor = SnorePrimary)
            )
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SnorePrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SnoreOnSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = SnoreOnPrimary, checkedTrackColor = SnorePrimary)
            )
        }
    }
}

@Composable
private fun StepperSetting(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SnorePrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            IconButton(onClick = onDecrease, enabled = value > range.first) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text(
                "$value$unit",
                modifier = Modifier.widthIn(min = 48.dp),
                color = SnorePrimary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onIncrease, enabled = value < range.last) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@Composable
private fun NavigationSetting(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SnoreWarning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SnoreOnSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SnoreOnSurfaceVariant)
        }
    }
}
