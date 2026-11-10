package io.lanyin.player.ui

import androidx.compose.ui.graphics.drawscope.inset
import io.lanyin.player.ui.design.drawLed
import io.lanyin.player.ui.design.drawKeycap
import io.lanyin.player.ui.design.drawUnitPlate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import io.lanyin.player.ui.design.drawGloss
import io.lanyin.player.ui.design.drawMenuCursor
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
import io.lanyin.player.ui.design.keyColors
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
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.gameWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import io.lanyin.player.ui.theme.BackdropKind

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
    // Where the person is, in their words, for the diagnostics.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(destination, expanded, landscape) {
        io.lanyin.player.diagnostics.Diag.current?.where = screenName(destination, expanded && !player.isEmpty) + if (landscape) "（横屏）" else ""
    }
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
                    val reduced = io.lanyin.player.ui.design.LocalReduceMotion.current
                    val motion = remember(backdrop, reduced) { if (reduced) quietPageMotion else pageMotion(backdrop) }
                    // Between two tabs, the pages move the way the tabs lie.
                    fun tabOf(entry: androidx.navigation.NavBackStackEntry) = tabs.indexOfFirst { tab -> entry.destination.hierarchy.any { it.hasRoute(tab.route::class) } }
                    fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.tabSwitch(): TabMotion? {
                        val from = tabOf(initialState)
                        val to = tabOf(targetState)
                        return if (from >= 0 && to >= 0 && from != to) tabMotion(backdrop, to - from, reduced) else null
                    }
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = { tabSwitch()?.enter ?: motion.enter },
                        exitTransition = { tabSwitch()?.exit ?: motion.exit },
                        popEnterTransition = { tabSwitch()?.enter ?: motion.popEnter },
                        popExitTransition = { tabSwitch()?.exit ?: motion.popExit },
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
                                // 暮: the darker edge of the mat.
                                // 霓: the car radio's grey body.
                                // 机: a brushed plate along the foot of the stack.
                                val chromeStyle = Lanyin.style
                                Column(
                                    Modifier
                                        .then(
                                            if (chromeStyle.edge == EdgeKind.Panel) Modifier.drawBehind { drawUnitPlate(chromeStyle, screws = false) }
                                            else Modifier.background(when (chromeStyle.edge) { EdgeKind.Soft -> Lanyin.colors.bgDeep; EdgeKind.Glow -> Color(0xFF20283A); else -> Lanyin.colors.bg }),
                                        )
                                        .navigationBarsPadding(),
                                ) {
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
        val c = style.colors
        val container = when (style.backdrop) {
            // 游: a game's message window.
            BackdropKind.Game -> Modifier.gameWindow(c.surface, c.content)
            // 酸: a capsule of grape gel.
            BackdropKind.Acid -> Modifier.candyPlastic(io.lanyin.player.ui.design.Flavor.Grape, style.chipShape)
            else -> if (plain) Modifier else Modifier.background(c.bg, style.chipShape).themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome)
        }
        val ink = when (style.backdrop) {
            BackdropKind.Acid -> androidx.compose.ui.graphics.Color.White
            else -> c.content
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (plain) style.margin else 0.dp, vertical = 6.dp)
                // Over the player even docked themes give it a surface, to read over the lyrics.
                .then(container)
                .pressable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            NoticeLead()
            Text(shown, style = style.type.caption, color = ink, maxLines = 2)
        }
    }
}

/**
 * What a notice starts with, in the theme's hand: a seal for 墨, a lit dot for 霓, a stamp for
 * 暮, a yellow tag for 格, the red dot for 点, a printed label for 机, 「快讯」 for 报, a cursor
 * for 游, a sparkle for 酸.
 */
@Composable
private fun NoticeLead() {
    val style = Lanyin.style
    val c = style.colors
    val gap = Modifier.padding(end = 10.dp)
    val mono = androidx.compose.ui.text.TextStyle(fontFamily = io.lanyin.player.ui.theme.SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.1.em)
    when (style.backdrop) {
        BackdropKind.Ink -> Box(gap.size(18.dp).background(c.accentAlt, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            Text("讯", style = androidx.compose.ui.text.TextStyle(fontFamily = style.display, fontSize = 11.sp, lineHeight = 12.sp), color = c.bg)
        }
        BackdropKind.Neon -> androidx.compose.foundation.Canvas(gap.size(10.dp)) {
            drawCircle(c.accent.copy(alpha = 0.3f), size.minDimension / 2f)
            drawCircle(io.lanyin.player.ui.design.neonCore(c.accent), size.minDimension / 4f)
        }
        BackdropKind.Dusk -> Box(gap) { io.lanyin.player.ui.design.InkMark(1) }
        BackdropKind.Grid -> Text("NOTE", style = mono, color = c.content, modifier = gap.background(io.lanyin.player.ui.theme.GridYellow).border(2.dp, c.outline).padding(horizontal = 4.dp))
        BackdropKind.Dot -> Box(gap.size(7.dp).background(c.accentAlt, CircleShape))
        BackdropKind.Device -> Text("MSG", style = mono, color = c.bg, modifier = gap.background(c.content, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
        BackdropKind.Paper -> Text("快讯", style = style.type.caption.copy(fontWeight = FontWeight.Black), color = c.accent, modifier = gap)
        BackdropKind.Game -> androidx.compose.foundation.Canvas(gap.size(8.dp, 10.dp)) { drawMenuCursor(c.accent) }
        BackdropKind.Acid -> androidx.compose.foundation.Canvas(gap.size(12.dp)) {
            drawPath(io.lanyin.player.ui.design.starPath(androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, size.minDimension * 0.14f, 4), androidx.compose.ui.graphics.Color.White)
        }
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
            // 游: the game's menu is a bar of ink, its words in paper.
            .then(if (style.edge == EdgeKind.Bezel) Modifier.background(c.content) else Modifier)
            // 酸: a strip of copier paper.
            .then(if (style.edge == EdgeKind.Chrome) Modifier.background(io.lanyin.player.ui.design.Acid.Paper) else Modifier)
            .then(if (docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
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
                    // 酸: pills drawn round every tab below, the current one filled lime.
                    EdgeKind.Chrome -> Modifier
                    // 机: the source keys draw themselves, below.
                    EdgeKind.Panel -> Modifier
                    else -> Modifier.clip(shape).background(c.accent.copy(alpha = if (style.dark) 0.22f else 0.14f))
                }
                if (style.edge == EdgeKind.Chrome) {
                    // 酸: a strip of copier paper with a pill drawn round each tab, the current one lime.
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width / tabs.size
                        val ph = 30.dp.toPx()
                        val pw = minOf(w - 24.dp.toPx(), 76.dp.toPx())
                        for (i in tabs.indices) {
                            val tl = androidx.compose.ui.geometry.Offset(i * w + (w - pw) / 2f, (size.height - ph) / 2f)
                            val r = androidx.compose.ui.geometry.CornerRadius(ph / 2f)
                            if (i == selected) drawRoundRect(io.lanyin.player.ui.design.Acid.Lime, tl, androidx.compose.ui.geometry.Size(pw, ph), r)
                            drawRoundRect(io.lanyin.player.ui.design.Acid.Black, tl, androidx.compose.ui.geometry.Size(pw, ph), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
                        }
                    }
                } else if (style.edge == EdgeKind.Panel) {
                    // 机: the amplifier's source keys, the one selected held down with its light on.
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width / tabs.size
                        for (i in tabs.indices) {
                            val down = i == selected
                            inset(i * w + 8.dp.toPx(), 5.dp.toPx(), size.width - (i + 1) * w + 8.dp.toPx(), 9.dp.toPx()) {
                                drawKeycap(RoundedCornerShape(4.dp), style.keyColors(), press = if (down) 1f else 0f)
                                val lift = if (down) io.lanyin.player.ui.design.keyTravel.toPx() else 0f
                                drawLed(androidx.compose.ui.geometry.Offset(9.dp.toPx(), 8.dp.toPx() + lift), c.accent, on = down, r = 2.dp.toPx())
                            }
                        }
                    }
                } else Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(6.dp)
                        .then(indicator),
                )
            }
            NavKind.Text -> when (style.edge) {
                // 霓: the radio's preset keys, a little amber light on the one pressed.
                EdgeKind.Glow -> {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width / tabs.size
                        for (i in tabs.indices) {
                            translate(i * w + 4.dp.toPx(), 5.dp.toPx()) {
                                drawPixelKeyArea(androidx.compose.ui.geometry.Size(w - 8.dp.toPx(), size.height - 10.dp.toPx()), i == selected)
                            }
                        }
                    }
                    androidx.compose.foundation.Canvas(Modifier.offset(x = x).width(itemWidth).fillMaxHeight()) {
                        val px = 3.dp.toPx()
                        drawRect(Color(0xFFFFC874), androidx.compose.ui.geometry.Offset(size.width / 2f - px, 8.dp.toPx()), androidx.compose.ui.geometry.Size(px * 2, px))
                    }
                }
                // 暮: a pencil loop round the current tab, the others ringed faintly in dashes.
                EdgeKind.Soft -> {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width / tabs.size
                        for (i in tabs.indices) {
                            val rect = androidx.compose.ui.geometry.Rect(i * w + w * 0.2f, size.height * 0.2f, (i + 1) * w - w * 0.2f, size.height * 0.8f)
                            drawOval(c.content.copy(alpha = 0.22f), rect.topLeft, rect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                        }
                    }
                    androidx.compose.foundation.Canvas(Modifier.offset(x = x).width(itemWidth).fillMaxHeight()) {
                        val rect = androidx.compose.ui.geometry.Rect(size.width * 0.17f, size.height * 0.17f, size.width * 0.83f, size.height * 0.83f)
                        drawOval(c.accent.copy(alpha = 0.18f), rect.topLeft, rect.size)
                        drawOval(c.accent, rect.topLeft, rect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
                        // The loop goes round a second time, a little off.
                        drawOval(c.accent.copy(alpha = 0.7f), rect.topLeft + androidx.compose.ui.geometry.Offset(2.dp.toPx(), -1.5.dp.toPx()), androidx.compose.ui.geometry.Size(rect.width - 3.dp.toPx(), rect.height + 1.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                }
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
                ) { drawMenuCursor(c.bg) }
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
                    style.edge == EdgeKind.Bezel -> c.bg
                    style.edge == EdgeKind.Chrome -> io.lanyin.player.ui.design.Acid.Black
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
                        .pressable { onSelect(i) }
                        .then(if (style.edge == EdgeKind.Panel && active) Modifier.offset(y = io.lanyin.player.ui.design.keyTravel - 1.dp) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (style.nav == NavKind.Pill && style.edge != EdgeKind.Chrome) {
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
                        } else if (style.edge == EdgeKind.Chrome) {
                            androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        fontWeight = if (style.edge == EdgeKind.Chrome) FontWeight.Black else if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                    )
                }
            }
        }
    }
}

/** A radio key [area] big at the canvas origin, sunk a little while it's [down]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelKeyArea(area: androidx.compose.ui.geometry.Size, down: Boolean) {
    val px = 3.dp.toPx()
    val rim = Color(0xFF05060A)
    drawRect(rim, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Size(area.width - 2 * px, area.height))
    drawRect(rim, androidx.compose.ui.geometry.Offset(0f, px), androidx.compose.ui.geometry.Size(area.width, area.height - 2 * px))
    drawRect(Color(0xFF171D2B), androidx.compose.ui.geometry.Offset(px, px), androidx.compose.ui.geometry.Size(area.width - 2 * px, area.height - 2 * px))
    val lip = if (down) px * 0.5f else px * 1.5f
    drawRect(if (down) Color(0xFF20283A) else Color(0xFF2B354B), androidx.compose.ui.geometry.Offset(px, px), androidx.compose.ui.geometry.Size(area.width - 2 * px, area.height - 2 * px - lip))
    drawRect(Color(0xFF4F5D78), androidx.compose.ui.geometry.Offset(px * 2, px), androidx.compose.ui.geometry.Size(area.width - 4 * px, px))
}

/** The page as the person would name it, for the diagnostics. */
private fun screenName(destination: androidx.navigation.NavDestination?, playerOpen: Boolean): String {
    if (playerOpen) return "播放页"
    val d = destination ?: return "首页"
    fun on(route: kotlin.reflect.KClass<*>) = d.hierarchy.any { it.hasRoute(route) }
    return when {
        on(HomeRoute::class) -> "首页"
        on(SearchRoute::class) -> "搜索"
        on(LibraryRoute::class) -> "我的"
        on(SettingsRoute::class) -> "设置"
        on(LoginRoute::class) -> "登录"
        on(PlaylistRoute::class) -> "歌单"
        on(DailyRoute::class) -> "每日推荐"
        on(ChartsRoute::class) -> "排行榜"
        on(ArtistRoute::class) -> "歌手"
        on(AlbumRoute::class) -> "专辑"
        on(RecentRoute::class) -> "最近播放"
        on(CloudRoute::class) -> "云盘"
        on(DownloadsRoute::class) -> "下载"
        on(RankingRoute::class) -> "听歌排行"
        on(CommentsRoute::class) -> "评论"
        else -> "其他"
    }
}
