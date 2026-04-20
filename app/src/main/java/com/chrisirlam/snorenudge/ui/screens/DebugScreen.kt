package com.chrisirlam.snorenudge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.chrisirlam.snorenudge.viewmodel.LiveStatusViewModel
import com.chrisirlam.snorenudge.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun DebugScreen(
    mainViewModel: MainViewModel,
    liveStatusViewModel: LiveStatusViewModel
) {
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val liveState by liveStatusViewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Debug",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SnoreError
            )
        )

        // Live detection readout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Live Detection", fontWeight = FontWeight.Bold, color = SnorePrimary)
                Spacer(Modifier.height(8.dp))

                DebugRow("Engine State", liveState.engineState.name)
                DebugRow("Frame Confidence", "${(liveState.frameConfidence * 100).roundToInt()}%")
                DebugRow("Rolling Confidence", "${(liveState.rollingConfidence * 100).roundToInt()}%")
                DebugRow("Trigger Threshold", "${(liveState.triggerThreshold * 100).roundToInt()}%")
                DebugRow("Last Trigger", liveState.lastTriggerTime)
                DebugRow(
                    "Cooldown",
                    if (liveState.cooldownRemainingMs > 0) "${liveState.cooldownRemainingMs / 1000}s remaining" else "None"
                )
                DebugRow("Monitoring", if (liveState.isMonitoring) "Active" else "Stopped")
            }
        }

        // Live audio signal readout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Audio Signal", fontWeight = FontWeight.Bold, color = SnorePrimary)
                Spacer(Modifier.height(8.dp))

                DebugRow("RMS Level", "%.5f".format(liveState.rmsLevel))
                DebugRow("Zero Crossing Rate", "%.4f".format(liveState.zeroCrossingRate))
                DebugRow("Low-Frequency Ratio", "${(liveState.lowFrequencyRatio * 100).roundToInt()}%")

                Spacer(Modifier.height(6.dp))
                Text("RMS", style = MaterialTheme.typography.labelSmall, color = SnoreOnSurfaceVariant)
                LinearProgressIndicator(
                    progress = { liveState.rmsLevel.coerceIn(0f, 0.3f) / 0.3f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = SnoreSuccess,
                    trackColor = SnoreSurface
                )
                Spacer(Modifier.height(4.dp))
                Text("Frame Confidence", style = MaterialTheme.typography.labelSmall, color = SnoreOnSurfaceVariant)
                LinearProgressIndicator(
                    progress = { liveState.frameConfidence.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when {
                        liveState.frameConfidence >= liveState.triggerThreshold && liveState.triggerThreshold > 0f -> SnoreError
                        liveState.frameConfidence >= 0.4f -> SnoreWarning
                        else -> SnorePrimary
                    },
                    trackColor = SnoreSurface
                )
                Spacer(Modifier.height(4.dp))
                Text("Confidence", style = MaterialTheme.typography.labelSmall, color = SnoreOnSurfaceVariant)
                LinearProgressIndicator(
                    progress = { liveState.rollingConfidence.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when {
                        liveState.rollingConfidence >= 0.7f -> SnoreError
                        liveState.rollingConfidence >= 0.4f -> SnoreWarning
                        else -> SnorePrimary
                    },
                    trackColor = SnoreSurface
                )
            }
        }

        // Watch status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Watch Connection", fontWeight = FontWeight.Bold, color = SnorePrimary)
                Spacer(Modifier.height(8.dp))
                DebugRow("Connected Nodes", mainState.watchNodeCount.toString())
                DebugRow("Watch Reachable", if (mainState.watchConnected) "Yes" else "No")
            }
        }

        // Action buttons
        Text("Test Actions", fontWeight = FontWeight.SemiBold, color = SnoreOnSurfaceVariant, fontSize = 13.sp)

        Button(
            onClick = { mainViewModel.fireFakeSnore() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SnoreWarning)
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Fire Fake Trigger")
        }

        OutlinedButton(
            onClick = { mainViewModel.sendTestVibrate() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Vibration, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Send Test Vibrate to Watch")
        }

        if (!liveState.isMonitoring) {
            Button(
                onClick = { mainViewModel.startMonitoring() },
                modifier = Modifier.fillMaxWidth(),
                enabled = mainState.hasMicPermission,
                colors = ButtonDefaults.buttonColors(containerColor = SnoreSuccess)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Monitoring")
            }
        } else {
            Button(
                onClick = { mainViewModel.stopMonitoring() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SnoreError)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop Monitoring")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
        ) {
            Text(
                "Tip: Use 'adb logcat -s SnoreMonitoringService TriggerDecisionEngine AudioCaptureManager WatchCommandSender' " +
                        "to observe real-time logs from the detection pipeline.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = SnoreOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SnoreOnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
