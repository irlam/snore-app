package com.chrisirlam.snorenudge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisirlam.snorenudge.data.SnoreEvent
import com.chrisirlam.snorenudge.ui.theme.*
import com.chrisirlam.snorenudge.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Delete all snore event records? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) { Text("Clear", color = SnoreError) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SnorePrimary
                )
            )
            if (events.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history", tint = SnoreError)
                }
            }
        }

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NightlightRound,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = SnoreOnSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No events yet", color = SnoreOnSurfaceVariant)
                    Text("Start monitoring overnight to see results", color = SnoreOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(
                "${events.size} event(s) recorded",
                style = MaterialTheme.typography.bodySmall,
                color = SnoreOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    SnoreEventRow(event)
                }
            }
        }
    }
}

@Composable
private fun SnoreEventRow(event: SnoreEvent) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd MMM HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val timeStr = remember(event.timestampMs) {
        formatter.format(Instant.ofEpochMilli(event.timestampMs))
    }
    val confidencePct = (event.confidence * 100).roundToInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (event.triggerSource) {
                    "test" -> Icons.Default.BugReport
                    "manual" -> Icons.Default.TouchApp
                    else -> Icons.Default.Hearing
                },
                contentDescription = null,
                tint = SnorePrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(timeStr, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Confidence: $confidencePct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = SnoreOnSurfaceVariant
                    )
                    if (event.watchCommandSent) {
                        Text(
                            "⌚ Sent",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnoreSuccess
                        )
                    }
                    if (event.phoneVibrated) {
                        Text(
                            "📳 Phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnoreSecondary
                        )
                    }
                }
            }
            // Source badge
            Surface(
                color = when (event.triggerSource) {
                    "test" -> SnoreWarning.copy(alpha = 0.2f)
                    else -> SnorePrimary.copy(alpha = 0.15f)
                },
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Text(
                    event.triggerSource.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.triggerSource == "test") SnoreWarning else SnorePrimary
                )
            }
        }
    }
}
