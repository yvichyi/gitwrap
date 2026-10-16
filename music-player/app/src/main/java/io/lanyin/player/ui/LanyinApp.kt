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
import androidx.compose.ui.draw.drawBehind
import io.lanyin.player.ui.design.drawGloss
import io.lanyin.player.ui.design.drawTubeLine
import io.lanyin.player.ui.design.drawMenuCursor
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
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.LocalSharedPlayerScope
import io.lanyin.player.ui.design.SharedPlayerScope
import io.lanyin.player.ui.design.SurfaceTone
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
import io.lanyin.player.ui.music.AppSheets
import io.lanyin.player.ui.music.CloudPage
import io.lanyin.player.ui.music.DownloadsPage
import io.lanyin.player.ui.music.RankingPage
import io.lanyin.player.ui.music.rememberSheetState
import io.lanyin.player.ui.comments.CommentsPage
import androidx.compose.foundation.layout.statusBarsPadding
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
@Serializable data object CloudRoute
@Serializable data object DownloadsRoute
@Serializable data object RankingRoute
@Serializable data class CommentsRoute(val songId: Long, val name: String, val artist: String, val coverUrl: String?)

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
    /** Stands in for the network on the home page, for previews and screenshots. */
    homePreview: io.lanyin.player.ui.home.HomePreview? = null,
) {
    val services = LocalAppServices.current
    val liveAccount = services?.account?.state?.collectAsState()?.value
    val account = accountOverride ?: liveAccount ?: AccountState.LoggedOut
    val appScope = rememberCoroutineScope()
    val navController = rememberNavController()
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val spin = rememberSpinState(player.isPlaying)
    val orientation = LocalOrientationController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val onTopLevel = destination == null || selectedTab >= 0
    // The mini player follows you into lists, but not into settings, login or comments (which has its own bar).
    val showMini = !player.isEmpty && destination?.hierarchy?.any {
        it.hasRoute(SettingsRoute::class) || it.hasRoute(LoginRoute::class) || it.hasRoute(CommentsRoute::class)
    } != true
    val notice = player.notice ?: services?.notices?.current?.collectAsState()?.value
    val onDismiss: () -> Unit = {
        if (player.notice != null) player.dismissNotice() else services?.notices?.dismiss()
    }
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
    val navigator = remember(navController) {
        AppNavigator(
            back = { navController.popBackStack() },
            openArtist = openArtist,
            openAlbum = openAlbum,
            openComments = { song ->
                expanded = false
                navController.navigate(CommentsRoute(song.id, song.name, song.artistLine, song.album.coverUrl))
            },
        )
    }
    val sheets = rememberSheetState()

    LaunchedEffect(expanded, settings.autoLandscape) {
        if (!expanded) orientation.release() else if (settings.autoLandscape) orientation.lockLandscape()
    }

    CompositionLocalProvider(LocalOpenSheet provides sheets::open, LocalNavigator provides navigator) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Lanyin.colors.bg)) {
            Box(Modifier.fillMaxSize()) {
                Backdrop(Modifier.fillMaxSize())
                CompositionLocalProvider(
                    LocalBottomChromePadding provides bottomChromeHeight(Lanyin.style.dock, showMini, onTopLevel),
                    // Under the full player the page can't be seen: its animations stand still.
                    io.lanyin.player.ui.design.LocalFramesPaused provides (expanded && !player.isEmpty),
                ) {
                    // Each theme has its own way between pages.
                    val backdrop = Lanyin.style.backdrop
                    val motion = remember(backdrop) { pageMotion(backdrop) }
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = { motion.enter },
                        exitTransition = { motion.exit },
                        popEnterTransition = { motion.popEnter },
                        popExitTransition = { motion.popExit },
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
                                onOpenRecent = { navController.navigate(RecentRoute) },
                                onOpenDownloads = { navController.navigate(DownloadsRoute) },
                                preview = homePreview,
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
                                onOpenCloud = { navController.navigate(CloudRoute) },
                                onOpenDownloads = { navController.navigate(DownloadsRoute) },
                                onOpenRanking = { navController.navigate(RankingRoute) },
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
                        composable<CloudRoute> { CloudPage(player, onBack = { navController.popBackStack() }) }
                        composable<DownloadsRoute> { DownloadsPage(player, onBack = { navController.popBackStack() }) }
                        composable<RankingRoute> { RankingPage(player, onBack = { navController.popBackStack() }) }
                        composable<CommentsRoute> { entry ->
                            val route = entry.toRoute<CommentsRoute>()
                            val song = remember(route) {
                                io.lanyin.player.core.model.Song(
                                    route.songId, route.name, listOf(io.lanyin.player.core.model.Artist(0, route.artist)),
                                    io.lanyin.player.core.model.Album(0, "", route.coverUrl), 0,
                                )
                            }
                            CommentsPage(song, onBack = { navController.popBackStack() })
                        }
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
                        Box(Modifier.fillMaxSize()) {
                        io.lanyin.player.ui.theme.TintedBy(if (settings.followCover) io.lanyin.player.ui.theme.rememberCoverSeeds(player.coverUrl) else null) {
                            PlayerScreen(
                                state = player,
                                skin = settings.playerSkin,
                                spin = spin,
                                keepScreenOnInLandscape = settings.keepScreenOnInLandscape,
                                onCollapse = { expanded = false },
                                lyricScale = settings.lyricSize.scale,
                            )
                        }
                            // Over the player, notices drop in from the top instead.
                            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 56.dp)) {
                                NoticeBanner(notice, onDismiss, floating = true)
                            }
                        }
                    } else if (onTopLevel || showMini || notice != null) {
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
                                    NoticeBanner(notice, onDismiss)
                                    Hairline()
                                    if (showMini) MiniPlayer(player, settings.playerSkin, spin, onExpand = { expanded = true })
                                    if (showMini && onTopLevel) Hairline(Modifier.padding(horizontal = Lanyin.style.margin))
                                    if (onTopLevel) TabBar(selectedTab.coerceAtLeast(0), onSelectTab)
                                }
                            } else {
                                run {
                                    Column(
                                        Modifier
                                            .navigationBarsPadding()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        NoticeBanner(notice, onDismiss)
                                        if (showMini) MiniPlayer(player, settings.playerSkin, spin, onExpand = { expanded = true })
                                        if (onTopLevel) TabBar(selectedTab.coerceAtLeast(0), onSelectTab)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AppSheets(
                sheets, player, quality = settings.quality, wifiOnly = settings.downloadOnWifiOnly,
                effects = settings.effects,
                onEffects = { fx -> appScope.launch { settingsRepository.setEffects(fx) } },
            )
        }
    }
    }
}

/** A short message (a song skipped, a playlist made, a like that didn't stick), gone after a few seconds. */
@Composable
private fun NoticeBanner(notice: String?, onDismiss: () -> Unit, floating: Boolean = false) {
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(4_000)
            onDismiss()
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
        val plain = style.dock == DockKind.Docked && !floating
        Text(
            shown,
            style = style.type.caption,
            color = style.colors.content,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (plain) style.margin else 0.dp, vertical = 6.dp)
                // Over the player even docked themes give it a surface, to read over the lyrics.
                .then(if (plain) Modifier else Modifier.background(style.colors.bg, style.chipShape).themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
                .pressable(onClick = onDismiss)
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
            .then(if (docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
            // 影's bar is a strip of film: sprocket holes along its top edge.
            .then(if (style.edge == EdgeKind.Film) Modifier.drawBehind {
                val hole = androidx.compose.ui.geometry.Size(5.dp.toPx(), 3.dp.toPx())
                val pitch = 11.dp.toPx()
                var x = (size.width % pitch) / 2f
                while (x + hole.width <= size.width) {
                    drawRoundRect(c.content.copy(alpha = 0.09f), androidx.compose.ui.geometry.Offset(x, 3.dp.toPx()), hole, androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                    x += pitch
                }
            } else Modifier),
    ) {
        val itemWidth = maxWidth / tabs.size
        val x by animateDpAsState(itemWidth * selected, style.bouncy.spec(Dp.VisibilityThreshold), label = "tabIndicator")
        when (style.nav) {
            NavKind.Pill -> {
                val shape = style.chipShape
                val indicator = when (style.edge) {
                    // 格: the current tab is a yellow index card.
                    EdgeKind.Brutal -> Modifier.retroShadow(shape, c.outline, 3.dp).background(io.lanyin.player.ui.theme.GridYellow).border(2.dp, c.outline, shape)
                    // 霓: the current tab is lit: a short pink tube under it, its light spilling up the bar.
                    EdgeKind.Glow -> Modifier.drawBehind {
                        val y = size.height - 3.dp.toPx()
                        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.accent.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent), androidx.compose.ui.geometry.Offset(size.width / 2f, y), size.width * 0.5f))
                        drawTubeLine(androidx.compose.ui.geometry.Offset(size.width * 0.38f, y), androidx.compose.ui.geometry.Offset(size.width * 0.62f, y), c.accent, 2.5.dp.toPx())
                    }
                    // 影: the current tab is an exposed frame between two frame lines, marked in the
                    // orange of the date stamp.
                    EdgeKind.Film -> Modifier.drawBehind {
                        drawRect(c.accent.copy(alpha = 0.07f))
                        val line = c.content.copy(alpha = 0.16f)
                        drawLine(line, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1.dp.toPx())
                        drawLine(line, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
                        drawLine(
                            io.lanyin.player.ui.theme.FilmStamp,
                            androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height - 1.dp.toPx()),
                            androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height - 1.dp.toPx()),
                            2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                    // 酸: the current tab is a lime gel capsule, glossed across the top.
                    EdgeKind.Chrome -> Modifier
                        .clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFE2FF6B), c.accent, androidx.compose.ui.graphics.Color(0xFF8FC400))))
                        .drawBehind { drawGloss(0.5f) }
                    // 机: the current tab is a key held down, sunk into the bar.
                    EdgeKind.Panel -> Modifier.clip(shape).background(c.bgDeep).drawBehind {
                        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (style.dark) 0.3f else 0.12f), androidx.compose.ui.graphics.Color.Transparent), endY = 6.dp.toPx()))
                    }
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
            NavKind.Text -> when (style.edge) {
                // 报: the current section underlined in red pen.
                EdgeKind.Rule -> Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = x + itemWidth / 2 - 12.dp)
                        .padding(bottom = 7.dp)
                        .size(24.dp, 2.5.dp)
                        .background(c.accent),
                )
                // 游: a menu cursor beside the current item.
                EdgeKind.Bezel -> androidx.compose.foundation.Canvas(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = x + itemWidth / 2 - 31.dp)
                        .size(8.dp, 10.dp),
                ) { drawMenuCursor(c.accent) }
                else -> Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = x + itemWidth / 2 - 2.5.dp)
                        .padding(bottom = 6.dp)
                        .size(5.dp)
                        .background(c.accentAlt, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val bump by animateFloatAsState(if (active) 1f else 0f, style.bouncy.spec(), label = "tabBump")
                val tint = when {
                    !active -> c.muted
                    style.edge == EdgeKind.Brutal -> c.content
                    style.edge == EdgeKind.Chrome -> c.onAccent
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
