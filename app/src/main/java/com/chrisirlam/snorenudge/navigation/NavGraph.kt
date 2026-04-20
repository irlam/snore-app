package com.chrisirlam.snorenudge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.chrisirlam.snorenudge.ui.screens.*
import com.chrisirlam.snorenudge.viewmodel.*

@Composable
fun NavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    liveStatusViewModel: LiveStatusViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = mainViewModel,
                onNavigateToBattery = { navController.navigate(Screen.BatteryOptimisation.route) }
            )
        }
        composable(Screen.LiveStatus.route) {
            LiveStatusScreen(viewModel = liveStatusViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(viewModel = historyViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToBattery = { navController.navigate(Screen.BatteryOptimisation.route) }
            )
        }
        composable(Screen.Debug.route) {
            DebugScreen(mainViewModel = mainViewModel, liveStatusViewModel = liveStatusViewModel)
        }
        composable(Screen.BatteryOptimisation.route) {
            BatteryOptimisationScreen(onBack = { navController.popBackStack() })
        }
    }
}
