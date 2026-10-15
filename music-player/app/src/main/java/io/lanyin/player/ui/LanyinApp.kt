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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.LocalHazeState
import io.lanyin.player.ui.design.ThemedIcon
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
import io.lanyin.player.ui.login.LoginRoute as LoginRouteScreen
import io.lanyin.player.data.account.AccountState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.player.MiniPlayer
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberDemoPlayerState
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.player.PlayerFrameDriver
import io.lanyin.player.ui.music.ChartsPage
import io.lanyin.player.ui.music.AlbumPage
import io.lanyin.player.ui.music.ArtistPage
import io.lanyin.player.ui.music.RecentPage
import io.lanyin.player.ui.music.LocalOpenSheet
import io.lanyin.player.ui.music.QueueSheet
import io.lanyin.player.ui.music.Sheet
import io.lanyin.player.ui.music.SheetLayer
import io.lanyin.player.ui.music.TrackActionsSheet
import io.lanyin.player.ui.music.rememberSheetState
import io.lanyin.player.ui.music.DailyPage
import io.lanyin.player.ui.music.PlaylistPage
import androidx.navigation.toRoute
import io.lanyin.player.ui.search.SearchScreen
import io.lanyin.player.ui.settings.SettingsScreen
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.NavKind
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute
@Serializable data object LoginRoute
@Serializable data class PlaylistRoute(val id: Long, val name: String)
@Serializable data object DailyRoute
@Serializable data object ChartsRoute
@Serializable data class ArtistRoute(val id: Long, val name: String)
@Serializable data class AlbumRoute(val id: Long, val name: String)
@Serializable data object RecentRoute

private data class TopLevelTab(val route: Any, val label: String, val icon: LineIcon)

private val tabs = listOf(
    TopLevelTab(HomeRoute, "首页", LineIcon.Home),
    TopLevelTab(SearchRoute, "搜索", LineIcon.Search),
    TopLevelTab(LibraryRoute, "我的", LineIcon.Library),
)

/** Space pages leave at the bottom so their last item clears the mini player and tabs. */
val LocalBottomChromePadding = staticCompositionLocalOf { 0.dp }

private fun bottomChromeHeight(dock: DockKind, mini: Boolean, tabs: Boolean): Dp = when (dock) {
    DockKind.Floating -> {
        val parts = listOfNotNull(64.dp.takeIf { mini }, 60.dp.takeIf { tabs })
        if (parts.isEmpty()) 0.dp else parts.fold(0.dp) { a, b -> a + b } + 8.dp * (parts.size - 1) + 16.dp
    }
    DockKind.Docked -> (if (mini) 1.dp + 60.dp else 0.dp) + (if (tabs) 1.dp + 52.dp else 0.dp)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LanyinApp(
    settings: UiSettings,
    settingsRepository: SettingsRepository,
    player: PlayerState = LocalAppServices.current?.player ?: rememberDemoPlayerState(),
    startExpanded: Boolean = false,
    /** Overrides the live account, for previews and screenshots. */
    accountOverride: AccountState? = null,
) {
    val services = LocalAppServices.current
    val liveAccount = services?.account?.state?.collectAsState()?.value
    val account = accountOverride ?: liveAccount ?: AccountState.LoggedOut
    val appScope = rememberCoroutineScope()
    val navController = rememberNavController()
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val spin = rememberSpinState(player.isPlaying)
    val haze = rememberHazeState()
    val orientation = LocalOrientationController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val onTopLevel = destination == null || selectedTab >= 0
    // The mini player follows you into lists, but not into settings or login.
    val showMini = !player.isEmpty && destination?.hierarchy?.any { it.hasRoute(SettingsRoute::class) || it.hasRoute(LoginRoute::class) } != true
    PlayerFrameDriver(player)
    // Nothing to show: an empty queue closes the player.
    LaunchedEffect(player.isEmpty) { if (player.isEmpty) expanded = false }
    val openPlaylist: (Long, String) -> Unit = { id, name -> navController.navigate(PlaylistRoute(id, name)) }
    // From the player's sheets: fold the player away, then go.
    val openArtist: (Long, String) -> Unit = { id, name ->
        expanded = false
        navController.navigate(ArtistRoute(id, name))
    }
    val openAlbum: (Long, String) -> Unit = { id, name ->
        expanded = false
        navController.navigate(AlbumRoute(id, name))
    }
    val sheets = rememberSheetState()

    LaunchedEffect(expanded, settings.autoLandscape) {
        if (!expanded) orientation.release() else if (settings.autoLandscape) orientation.lockLandscape()
    }

    CompositionLocalProvider(LocalOpenSheet provides sheets::open) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Lanyin.colors.bg)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                Backdrop(Modifier.fillMaxSize())
                CompositionLocalProvider(LocalBottomChromePadding provides bottomChromeHeight(Lanyin.style.dock, showMini, onTopLevel)) {
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
                                account = account,
                                onOpenDaily = { navController.navigate(DailyRoute) },
                                onOpenCharts = { navController.navigate(ChartsRoute) },
                                onOpenPlaylist = openPlaylist,
                                onOpenAccount = {
                                    navController.navigate(LibraryRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                        composable<SearchRoute> { SearchScreen(player, onOpenArtist = openArtist, onOpenAlbum = openAlbum, onOpenPlaylist = openPlaylist) }
                        composable<LibraryRoute> {
                            LibraryScreen(
                                onLogin = { navController.navigate(LoginRoute) }, account = account, onOpenPlaylist = openPlaylist,
                                onOpenRecent = { navController.navigate(RecentRoute) },
                            )
                        }
                        composable<PlaylistRoute> { entry ->
                            val route = entry.toRoute<PlaylistRoute>()
                            PlaylistPage(route.id, route.name, player, onBack = { navController.popBackStack() })
                        }
                        composable<DailyRoute> { DailyPage(player, onBack = { navController.popBackStack() }) }
                        composable<ArtistRoute> { entry ->
                            val route = entry.toRoute<ArtistRoute>()
                            ArtistPage(route.id, route.name, player, onBack = { navController.popBackStack() })
                        }
                        composable<AlbumRoute> { entry ->
                            val route = entry.toRoute<AlbumRoute>()
                            AlbumPage(route.id, route.name, player, onBack = { navController.popBackStack() })
                        }
                        composable<RecentRoute> { RecentPage(player, onBack = { navController.popBackStack() }) }
                        composable<ChartsRoute> { ChartsPage(onBack = { navController.popBackStack() }, onOpen = { openPlaylist(it.id, it.name) }) }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings, settingsRepository, onBack = { navController.popBackStack() },
                                account = account,
                                onLogin = { navController.navigate(LoginRoute) },
                                onLogout = { appScope.launch { services?.account?.logout() } },
                            )
                        }
                        composable<LoginRoute> {
                            LoginRouteScreen(onBack = { navController.popBackStack() }, onLoggedIn = { navController.popBackStack() })
                        }
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
                    if (isExpanded && !player.isEmpty) {
                        PlayerScreen(
                            state = player,
                            skin = settings.playerSkin,
                            spin = spin,
                            keepScreenOnInLandscape = settings.keepScreenOnInLandscape,
                            onCollapse = { expanded = false },
                            lyricScale = settings.lyricSize.scale,
                        )
                    } else if (onTopLevel || showMini) {
                        val onSelectTab: (Int) -> Unit = { i ->
                            navController.navigate(tabs[i].route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            if (Lanyin.style.dock == DockKind.Docked) {
                                // A plain bar on the page colour, ruled off with hairlines.
                                Column(Modifier.background(Lanyin.colors.bg).navigationBarsPadding()) {
                                    NoticeBanner(player)
                                    Hairline()
                                    if (showMini) MiniPlayer(player, settings.playerSkin, spin, onExpand = { expanded = true })
                                    if (showMini && onTopLevel) Hairline(Modifier.padding(horizontal = Lanyin.style.margin))
                                    if (onTopLevel) TabBar(selectedTab.coerceAtLeast(0), onSelectTab)
                                }
                            } else {
                                CompositionLocalProvider(LocalHazeState provides haze) {
                                    Column(
                                        Modifier
                                            .navigationBarsPadding()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        NoticeBanner(player)
                                        if (showMini) MiniPlayer(player, settings.playerSkin, spin, onExpand = { expanded = true })
                                        if (onTopLevel) TabBar(selectedTab.coerceAtLeast(0), onSelectTab)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SheetLayer(sheets) { sheet ->
                when (sheet) {
                    Sheet.Queue -> QueueSheet(player)
                    is Sheet.TrackActions -> TrackActionsSheet(
                        sheet.track,
                        liked = player.liked && player.track.id == sheet.track.id,
                        onToggleLike = {
                            player.toggleLike()
                            sheets.dismiss()
                        },
                        onOpenArtist = {
                            sheets.dismiss()
                            openArtist(it.id, it.name)
                        },
                        onOpenAlbum = { id, name ->
                            sheets.dismiss()
                            openAlbum(id, name)
                        },
                    )
                }
            }
        }
    }
    }
}

/** A short message from the player (a song skipped, a like that didn't stick), gone after a few seconds. */
@Composable
private fun NoticeBanner(player: PlayerState) {
    val notice = player.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(4_000)
            player.dismissNotice()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        notice != null,
        enter = fadeIn() + androidx.compose.animation.expandVertically(),
        exit = fadeOut() + androidx.compose.animation.shrinkVertically(),
    ) {
        val style = Lanyin.style
        var shown by remember { mutableStateOf("") }
        if (notice != null) shown = notice
        Text(
            shown,
            style = style.type.caption,
            color = style.colors.content,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (style.dock == DockKind.Docked) style.margin else 0.dp, vertical = 6.dp)
                .then(if (style.dock == DockKind.Docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
                .pressable { player.dismissNotice() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val docked = style.dock == DockKind.Docked
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(if (docked) 52.dp else 60.dp)
            .then(if (docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome)),
    ) {
        val itemWidth = maxWidth / tabs.size
        val x by animateDpAsState(itemWidth * selected, style.bouncy.spec(Dp.VisibilityThreshold), label = "tabIndicator")
        when (style.nav) {
            NavKind.Pill -> {
                val shape = style.chipShape
                val indicator = when (style.edge) {
                    EdgeKind.Retro -> Modifier.retroShadow(shape, c.outline, 2.dp).clip(shape).background(c.accent).border(2.dp, c.outline, shape)
                    // 格: the current tab is a yellow index card.
                    EdgeKind.Brutal -> Modifier.retroShadow(shape, c.outline, 3.dp).background(io.lanyin.player.ui.theme.GridYellow).border(2.dp, c.outline, shape)
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
                    .offset(x = x + itemWidth / 2 - 2.5.dp)
                    .padding(bottom = 6.dp)
                    .size(5.dp)
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
                    style.edge == EdgeKind.Brutal -> c.content
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
                        ThemedIcon(
                            tab.icon, contentDescription = null, tint = tint, size = 22.dp,
                            modifier = Modifier.graphicsLayer {
                                val s = 1f + 0.12f * bump
                                scaleX = s
                                scaleY = s
                            },
                        )
                    }
                    Text(
                        tab.label,
                        style = if (style.nav == NavKind.Text) {
                            style.type.body.copy(fontFamily = style.display, fontSize = 16.sp, letterSpacing = 0.12.em)
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
