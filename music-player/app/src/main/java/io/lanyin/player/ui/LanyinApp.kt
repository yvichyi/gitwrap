package io.lanyin.player.ui

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.home.HomeScreen
import io.lanyin.player.ui.library.LibraryScreen
import io.lanyin.player.ui.login.LoginScreen
import io.lanyin.player.ui.player.MiniPlayerBar
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberDemoPlayerState
import io.lanyin.player.ui.search.SearchScreen
import io.lanyin.player.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object PlayerRoute
@Serializable data object SettingsRoute
@Serializable data object LoginRoute

private data class TopLevelTab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(HomeRoute, "首页", Icons.Filled.Home),
    TopLevelTab(SearchRoute, "搜索", Icons.Filled.Search),
    TopLevelTab(LibraryRoute, "我的", Icons.Filled.LibraryMusic),
)

@Composable
fun LanyinApp(settings: UiSettings, settingsRepository: SettingsRepository) {
    val navController = rememberNavController()
    val player = rememberDemoPlayerState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onTopLevel = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    Scaffold(
        bottomBar = {
            if (onTopLevel) {
                Column {
                    MiniPlayerBar(player, onOpen = { navController.navigate(PlayerRoute) })
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(if (onTopLevel) innerPadding else androidx.compose.foundation.layout.PaddingValues()),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onLogin = { navController.navigate(LoginRoute) },
                )
            }
            composable<SearchRoute> { SearchScreen() }
            composable<LibraryRoute> { LibraryScreen(onLogin = { navController.navigate(LoginRoute) }) }
            composable<PlayerRoute>(
                enterTransition = { slideInVertically { it } },
                exitTransition = { slideOutVertically { it } },
                popExitTransition = { slideOutVertically { it } },
            ) {
                PlayerScreen(player, settings.playerSkin, onCollapse = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsScreen(settings, settingsRepository, onBack = { navController.popBackStack() })
            }
            composable<LoginRoute> { LoginScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
