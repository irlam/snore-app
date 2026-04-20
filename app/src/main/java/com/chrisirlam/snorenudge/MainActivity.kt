package com.chrisirlam.snorenudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chrisirlam.snorenudge.navigation.NavGraph
import com.chrisirlam.snorenudge.navigation.Screen
import com.chrisirlam.snorenudge.ui.theme.*
import com.chrisirlam.snorenudge.viewmodel.*

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val liveStatusViewModel: LiveStatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SnoreNudgeTheme {
                SnoreNudgeApp(
                    mainViewModel = mainViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel,
                    liveStatusViewModel = liveStatusViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoreNudgeApp(
    mainViewModel: MainViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    liveStatusViewModel: LiveStatusViewModel
) {
    val navController = rememberNavController()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SnoreSurfaceVariant
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SnorePrimary,
                            selectedTextColor = SnorePrimary,
                            indicatorColor = SnorePrimary.copy(alpha = 0.15f),
                            unselectedIconColor = SnoreOnSurfaceVariant,
                            unselectedTextColor = SnoreOnSurfaceVariant
                        )
                    )
                }
                // Debug tab — only visible when debug mode is enabled
                if (settings.debugMode) {
                    val selected = currentDestination?.hierarchy?.any { it.route == Screen.Debug.route } == true
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BugReport, contentDescription = "Debug") },
                        label = { Text("Debug", fontWeight = FontWeight.Bold, color = SnoreError) },
                        selected = selected,
                        onClick = {
                            navController.navigate(Screen.Debug.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SnoreError,
                            selectedTextColor = SnoreError,
                            indicatorColor = SnoreError.copy(alpha = 0.15f),
                            unselectedIconColor = SnoreError.copy(alpha = 0.5f),
                            unselectedTextColor = SnoreError.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavGraph(
            navController = navController,
            mainViewModel = mainViewModel,
            historyViewModel = historyViewModel,
            settingsViewModel = settingsViewModel,
            liveStatusViewModel = liveStatusViewModel,
            modifier = Modifier.padding(padding)
        )
    }
}
