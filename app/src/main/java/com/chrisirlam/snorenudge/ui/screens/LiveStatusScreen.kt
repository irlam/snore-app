package com.chrisirlam.snorenudge.ui.screens

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
import com.chrisirlam.snorenudge.audio.TriggerDecisionEngine
import com.chrisirlam.snorenudge.ui.theme.*
import com.chrisirlam.snorenudge.viewmodel.LiveStatusViewModel
import kotlin.math.roundToInt

@Composable
fun LiveStatusScreen(viewModel: LiveStatusViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Live Status",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SnorePrimary
            )
        )

        // Big confidence dial
        ConfidenceIndicator(confidence = state.rollingConfidence)

        // Engine state
        EngineStateChip(state = state.engineState)

        // Info cards
        InfoCard("Last Trigger", state.lastTriggerTime, Icons.Default.Alarm)

        if (state.cooldownRemainingMs > 0) {
            InfoCard(
                "Cooldown Remaining",
                "${(state.cooldownRemainingMs / 1000.0).roundToInt()} s",
                Icons.Default.Timer,
                valueColor = SnoreWarning
            )
        }

        InfoCard(
            "Service",
            if (state.isMonitoring) "Running" else "Stopped",
            Icons.Default.FiberManualRecord,
            valueColor = if (state.isMonitoring) SnoreSuccess else SnoreOnSurfaceVariant
        )
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Float) {
    val pct = (confidence * 100).roundToInt()
    val color = when {
        confidence >= 0.7f -> SnoreError
        confidence >= 0.4f -> SnoreWarning
        else -> SnoreSuccess
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Snore Confidence", style = MaterialTheme.typography.labelLarge, color = SnoreOnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "$pct%",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    fontSize = 56.sp
                )
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = color,
                trackColor = SnoreSurface
            )
        }
    }
}

@Composable
private fun EngineStateChip(state: TriggerDecisionEngine.State) {
    val (label, color) = when (state) {
        TriggerDecisionEngine.State.IDLE -> "Idle" to SnoreOnSurfaceVariant
        TriggerDecisionEngine.State.ACCUMULATING -> "Accumulating…" to SnoreWarning
        TriggerDecisionEngine.State.TRIGGERED -> "TRIGGERED!" to SnoreError
        TriggerDecisionEngine.State.COOLDOWN -> "Cooldown" to SnoreSecondary
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.wrapContentWidth()
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: androidx.compose.ui.graphics.Color = SnoreOnSurface
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SnorePrimary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = SnoreOnSurfaceVariant)
                Text(value, color = valueColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
