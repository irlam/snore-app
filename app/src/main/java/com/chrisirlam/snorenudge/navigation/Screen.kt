package com.chrisirlam.snorenudge.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object LiveStatus : Screen("live_status", "Live", Icons.Default.GraphicEq)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Debug : Screen("debug", "Debug", Icons.Default.BugReport)

    companion object {
        val bottomNavItems = listOf(Home, LiveStatus, History, Settings)
    }
}
