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
            HomeScreen(viewModel = mainViewModel)
        }
        composable(Screen.LiveStatus.route) {
            LiveStatusScreen(viewModel = liveStatusViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(viewModel = historyViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }
        composable(Screen.Debug.route) {
            DebugScreen(mainViewModel = mainViewModel, liveStatusViewModel = liveStatusViewModel)
        }
    }
}
