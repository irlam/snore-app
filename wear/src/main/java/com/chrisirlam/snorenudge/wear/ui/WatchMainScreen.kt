package com.chrisirlam.snorenudge.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.*
import com.chrisirlam.snorenudge.wear.WatchCommand
import com.chrisirlam.snorenudge.wear.viewmodel.WatchMainViewModel

@Composable
fun WatchMainScreen(viewModel: WatchMainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val backgroundColor = when (state.lastCommand) {
        WatchCommand.VIBRATE_STRONG, WatchCommand.VIBRATE_MEDIUM -> Color(0xFF1A0000)
        WatchCommand.TEST -> Color(0xFF001A10)
        else -> Color.Black
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            // App title
            Text(
                "SnoreNudge",
                fontSize = 14.sp,
                color = Color(0xFF4FC3F7),
                fontWeight = FontWeight.Bold
            )

            // Status
            when (state.lastCommand) {
                WatchCommand.VIBRATE_STRONG, WatchCommand.VIBRATE_MEDIUM -> {
                    Text(
                        "Roll Over!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        state.lastCommandTime,
                        fontSize = 11.sp,
                        color = Color(0xFF888888)
                    )
                }
                WatchCommand.TEST -> {
                    Text(
                        "Test OK",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF66BB6A)
                    )
                }
                else -> {
                    Text(
                        "Monitoring",
                        fontSize = 16.sp,
                        color = Color(0xFF888888)
                    )
                    Text(
                        "Waiting…",
                        fontSize = 11.sp,
                        color = Color(0xFF555555)
                    )
                }
            }

            Text(
                text = if (state.isPhoneConnected) "Phone connected (${state.connectedPhoneCount})" else "Phone disconnected",
                fontSize = 10.sp,
                color = if (state.isPhoneConnected) Color(0xFF66BB6A) else Color(0xFFFF8A65),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Test vibration button
            Button(
                onClick = { viewModel.testVibration() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF1E1E1E)
                )
            ) {
                Text("Test Vibrate", fontSize = 11.sp, color = Color(0xFF4FC3F7))
            }

            if (state.lastCommand != null && state.lastCommand != WatchCommand.STOP) {
                Button(
                    onClick = { viewModel.stopVibration() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2A1010)
                    )
                ) {
                    Text("Stop", fontSize = 11.sp, color = Color(0xFFFF6B6B))
                }
            }
        }
    }
}
