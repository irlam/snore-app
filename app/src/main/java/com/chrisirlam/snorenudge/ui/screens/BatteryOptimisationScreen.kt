package com.chrisirlam.snorenudge.ui.screens

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.chrisirlam.snorenudge.ui.theme.*

/**
 * Guides the user through disabling battery optimisation for SnoreNudge.
 *
 * Required for reliable overnight monitoring on all Android devices, and
 * especially critical on Samsung (One UI Device Care), Huawei (Battery
 * Manager), Xiaomi (MIUI Battery Saver), and OnePlus (Battery Optimization).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimisationScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val pm = context.getSystemService(PowerManager::class.java)
    val isExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Optimisation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SnoreBackground,
                    titleContentColor = SnorePrimary,
                    navigationIconContentColor = SnorePrimary
                )
            )
        },
        containerColor = SnoreBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Status banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExempt) SnoreSuccess.copy(alpha = 0.15f)
                    else SnoreWarning.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isExempt) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isExempt) SnoreSuccess else SnoreWarning,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isExempt) "Battery optimisation disabled ✓"
                            else "Battery optimisation is active",
                            fontWeight = FontWeight.Bold,
                            color = if (isExempt) SnoreSuccess else SnoreWarning
                        )
                        Text(
                            if (isExempt) "SnoreNudge can run uninterrupted overnight."
                            else "The app may be stopped during overnight monitoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnoreOnSurfaceVariant
                        )
                    }
                }
            }

            // Why this matters
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Why is this needed?", fontWeight = FontWeight.Bold, color = SnorePrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android's Doze mode and manufacturer battery savers (Samsung Device Care, " +
                                "Huawei Battery Manager, MIUI Battery Saver) can suspend SnoreNudge " +
                                "after a few minutes of screen-off time. This would stop snore detection " +
                                "during overnight sleep.\n\n" +
                                "Disabling battery optimisation for SnoreNudge allows the microphone " +
                                "service to keep running all night. The app is designed to consume " +
                                "minimal CPU and memory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SnoreOnSurface
                    )
                }
            }

            // Step 1 — Android system setting
            if (!isExempt) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fall back to general battery optimisation settings
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnorePrimary)
                ) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Disable Battery Optimisation", fontWeight = FontWeight.Bold)
                }
            }

            // Samsung-specific guidance
            ManufacturerGuidanceCard(
                manufacturer = "Samsung (One UI)",
                steps = listOf(
                    "Open Settings → Battery and device care → Battery",
                    "Tap 'App power management'",
                    "Tap 'Apps that won't be put to sleep'",
                    "Tap '+' and add SnoreNudge",
                    "Also tap 'Never sleeping apps' and add SnoreNudge"
                ),
                color = SnoreSecondary
            )

            // Huawei / Honor guidance
            ManufacturerGuidanceCard(
                manufacturer = "Huawei / Honor",
                steps = listOf(
                    "Open Phone Manager → App launch",
                    "Find SnoreNudge and set to 'Manage manually'",
                    "Enable 'Auto-launch', 'Secondary launch', 'Run in background'"
                ),
                color = SnorePrimary
            )

            // Xiaomi / MIUI guidance
            ManufacturerGuidanceCard(
                manufacturer = "Xiaomi / MIUI",
                steps = listOf(
                    "Open Settings → Apps → Manage apps → SnoreNudge",
                    "Tap 'Battery saver' → select 'No restrictions'",
                    "Also enable 'Autostart' for SnoreNudge"
                ),
                color = SnoreWarning
            )

            // OnePlus / OxygenOS guidance
            ManufacturerGuidanceCard(
                manufacturer = "OnePlus / OxygenOS",
                steps = listOf(
                    "Open Settings → Battery → Battery optimisation",
                    "Find SnoreNudge → select 'Don't optimize'"
                ),
                color = SnoreError
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ManufacturerGuidanceCard(
    manufacturer: String,
    steps: List<String>,
    color: androidx.compose.ui.graphics.Color
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SnoreSurfaceVariant),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(manufacturer, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = SnoreOnSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(step, style = MaterialTheme.typography.bodySmall, color = SnoreOnSurface)
                    }
                }
            }
        }
    }
}
