package com.chrisirlam.snorenudge.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisirlam.snorenudge.ui.theme.*
import com.chrisirlam.snorenudge.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToBattery: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsUpdated() }

    val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val canStartMonitoring = state.hasMicPermission &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || state.hasNotificationPermission)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SnoreNudge",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = SnorePrimary
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "On-device snore detection",
                style = MaterialTheme.typography.bodyMedium,
                color = SnoreOnSurfaceVariant
            )
        }

        // Status cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusCard(
                title = "Monitoring",
                value = if (state.isMonitoring) "Active" else "Stopped",
                valueColor = if (state.isMonitoring) SnoreSuccess else SnoreOnSurfaceVariant,
                icon = if (state.isMonitoring) Icons.Default.Hearing else Icons.Default.HearingDisabled
            )
            StatusCard(
                title = "Watch",
                value = if (state.watchConnected) "Connected (${state.watchNodeCount})" else "Not connected",
                valueColor = if (state.watchConnected) SnoreSuccess else SnoreWarning,
                icon = Icons.Default.Watch
            )
            if (!state.hasMicPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SnoreError.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = SnoreError)
                        Spacer(Modifier.width(8.dp))
                        Text("Microphone permission required", color = SnoreError)
                    }
                }
            }

            if (!state.batteryOptimisationIgnored || state.isSamsungDevice) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SnoreWarning.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = SnoreWarning)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (state.batteryOptimisationIgnored) "Samsung background protection recommended"
                                else "Disable battery optimisation for overnight use",
                                color = SnoreWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (state.isSamsungDevice) {
                                    "Add SnoreNudge to Never sleeping apps to reduce One UI background kills."
                                } else {
                                    "Battery saving can stop the microphone service during sleep."
                                },
                                color = SnoreOnSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Main action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!state.hasMicPermission || !state.hasNotificationPermission) {
                Button(
                    onClick = { permissionsLauncher.launch(requiredPermissions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreWarning)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Permissions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!state.isMonitoring) {
                Button(
                    onClick = { viewModel.startMonitoring() },
                    enabled = canStartMonitoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreSuccess)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Monitoring", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.stopMonitoring() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreError)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Monitoring", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.sendTestVibrate() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Watch")
                }

                OutlinedButton(
                    onClick = onNavigateToBattery,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SnoreWarning),
                    border = BorderStroke(1.dp, SnoreWarning.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Battery")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SnorePrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = SnoreOnSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge.copy(color = valueColor, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
