package com.unlock.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unlock.ui.apps.AppsScreen
import com.unlock.ui.autostart.AutostartScreen
import com.unlock.ui.dashboard.DashboardScreen
import com.unlock.ui.diagnostics.DiagnosticsScreen
import com.unlock.ui.running.RunningScreen
import com.unlock.ui.settings.SettingsScreen

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Dashboard),
    Apps("apps", "Apps", Icons.Filled.Apps),
    Running("running", "Running", Icons.Filled.Memory),
    Autostart("autostart", "Autostart", Icons.Filled.RestartAlt),
    Diagnostics("diagnostics", "Health", Icons.Filled.MonitorHeart),
    Settings("settings", "Settings", Icons.Filled.Settings);

    companion object {
        val bottom = listOf(Dashboard, Apps, Running, Autostart, Diagnostics)
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val current = Destination.fromRoute(currentRoute)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.let { if (it == Destination.Dashboard) "Unlock" else it.label } ?: "Unlock") },
                actions = {
                    IconButton(onClick = {
                        nav.navigate(Destination.Settings.route) { launchSingleTop = true }
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.bottom.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(onOpen = { route -> nav.navigate(route) { launchSingleTop = true } })
            }
            composable(Destination.Apps.route) { AppsScreen() }
            composable(Destination.Running.route) { RunningScreen() }
            composable(Destination.Autostart.route) { AutostartScreen() }
            composable(Destination.Diagnostics.route) { DiagnosticsScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
