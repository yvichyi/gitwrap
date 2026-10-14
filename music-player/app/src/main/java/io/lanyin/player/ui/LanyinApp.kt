package io.lanyin.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.LocalHazeState
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.LocalSharedPlayerScope
import io.lanyin.player.ui.design.SharedPlayerScope
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.neonGlow
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.retroShadow
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.home.HomeScreen
import io.lanyin.player.ui.library.LibraryScreen
import io.lanyin.player.ui.login.LoginScreen
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.MiniPlayer
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberDemoPlayerState
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.search.SearchScreen
import io.lanyin.player.ui.settings.SettingsScreen
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.NavKind
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute
@Serializable data object LoginRoute

private data class TopLevelTab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(HomeRoute, "首页", Icons.Filled.Home),
    TopLevelTab(SearchRoute, "搜索", Icons.Filled.Search),
    TopLevelTab(LibraryRoute, "我的", Icons.Filled.LibraryMusic),
)

/** Space pages leave at the bottom so their last item clears the floating mini player and tabs. */
val LocalBottomChromePadding = staticCompositionLocalOf { 0.dp }

private val BottomChromeHeight = 64.dp + 8.dp + 60.dp + 16.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LanyinApp(
    settings: UiSettings,
    settingsRepository: SettingsRepository,
    player: DemoPlayerState = rememberDemoPlayerState(),
    startExpanded: Boolean = false,
) {
    val navController = rememberNavController()
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val spin = rememberSpinState(player.isPlaying)
    val haze = rememberHazeState()
    val orientation = LocalOrientationController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val onTopLevel = destination == null || selectedTab >= 0

    LaunchedEffect(expanded, settings.autoLandscape) {
        if (!expanded) orientation.release() else if (settings.autoLandscape) orientation.lockLandscape()
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Lanyin.colors.bg)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                Backdrop(Modifier.fillMaxSize())
                CompositionLocalProvider(LocalBottomChromePadding provides BottomChromeHeight) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.97f) },
                        exitTransition = { fadeOut(tween(140)) },
                        popEnterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.97f) },
                        popExitTransition = { fadeOut(tween(140)) },
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                player = player,
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onLogin = { navController.navigate(LoginRoute) },
                                onOpenPlayer = { expanded = true },
                            )
                        }
                        composable<SearchRoute> { SearchScreen() }
                        composable<LibraryRoute> { LibraryScreen(onLogin = { navController.navigate(LoginRoute) }) }
                        composable<SettingsRoute> {
                            SettingsScreen(settings, settingsRepository, onBack = { navController.popBackStack() })
                        }
                        composable<LoginRoute> { LoginScreen(onBack = { navController.popBackStack() }) }
                    }
                }
            }

            AnimatedContent(
                targetState = expanded,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier.fillMaxSize(),
                label = "player",
            ) { isExpanded ->
                val sharedScope = remember(this) { SharedPlayerScope(this@SharedTransitionLayout, this) }
                CompositionLocalProvider(LocalSharedPlayerScope provides sharedScope) {
                    if (isExpanded) {
                        PlayerScreen(
                            state = player,
                            skin = settings.playerSkin,
                            spin = spin,
                            keepScreenOnInLandscape = settings.keepScreenOnInLandscape,
                            onCollapse = { expanded = false },
                        )
                    } else if (onTopLevel) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            CompositionLocalProvider(LocalHazeState provides haze) {
                                Column(
                                    Modifier
                                        .navigationBarsPadding()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MiniPlayer(player, settings.playerSkin, spin, onExpand = { expanded = true })
                                    TabBar(selectedTab.coerceAtLeast(0)) { i ->
                                        navController.navigate(tabs[i].route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome),
    ) {
        val itemWidth = maxWidth / tabs.size
        val x by animateDpAsState(itemWidth * selected, style.bouncy.spec(Dp.VisibilityThreshold), label = "tabIndicator")
        when (style.nav) {
            NavKind.Pill -> {
                val shape = style.chipShape
                val indicator = when (style.edge) {
                    EdgeKind.Retro -> Modifier.retroShadow(shape, c.content, 2.dp).clip(shape).background(c.accent).border(2.dp, c.content, shape)
                    EdgeKind.Glow -> Modifier.neonGlow(shape, c.accent, 0.7f).clip(shape).background(c.accent.copy(alpha = 0.12f)).border(1.dp, c.accent, shape)
                    else -> Modifier.clip(shape).background(c.accent.copy(alpha = if (style.dark) 0.22f else 0.14f))
                }
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(6.dp)
                        .then(indicator),
                )
            }
            NavKind.Text -> Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = x + itemWidth / 2 - 3.dp)
                    .padding(bottom = 7.dp)
                    .size(6.dp)
                    .background(c.accentAlt, CircleShape),
            )
        }
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val bump by animateFloatAsState(if (active) 1f else 0f, style.bouncy.spec(), label = "tabBump")
                val tint = when {
                    !active -> c.muted
                    style.edge == EdgeKind.Retro -> c.onAccent
                    style.nav == NavKind.Text -> c.content
                    else -> c.accent
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (style.nav == NavKind.Pill) {
                        Icon(
                            tab.icon, contentDescription = null, tint = tint,
                            modifier = Modifier.size(22.dp).graphicsLayer {
                                val s = 1f + 0.12f * bump
                                scaleX = s
                                scaleY = s
                            },
                        )
                    }
                    Text(
                        tab.label,
                        style = if (style.nav == NavKind.Text) {
                            MaterialTheme.typography.titleMedium.copy(fontFamily = style.display, letterSpacing = style.displayTracking)
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                    )
                }
            }
        }
    }
}
