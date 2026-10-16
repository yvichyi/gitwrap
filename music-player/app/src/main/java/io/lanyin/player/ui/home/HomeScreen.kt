package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.Marquee
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.HomeFlourish
import io.lanyin.player.ui.design.hasHomeFlourish
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.music.Load
import io.lanyin.player.ui.music.PlaylistTile
import io.lanyin.player.ui.music.rememberLoad
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.data.library.Picks
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Stand-ins for the network in previews and screenshots. */
data class HomePreview(
    val recommended: List<Playlist>,
    val charts: List<ChartPreview>,
    val picks: Picks,
)

/**
 * Two doors in (today's picks and personal FM) drawn over their artwork, shortcuts to your own
 * music, then shelves to browse: playlists picked for you, the charts with their top three, and a
 * handful of songs to start from. 墨 draws it with rules and whitespace instead of boxes.
 */
@Composable
fun HomeScreen(
    player: PlayerState,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onOpenPlayer: () -> Unit,
    account: AccountState = AccountState.LoggedOut,
    onOpenAccount: () -> Unit = onLogin,
    onOpenDaily: () -> Unit = onLogin,
    onOpenCharts: () -> Unit = {},
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    onOpenRecent: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    preview: HomePreview? = null,
) {
    val style = Lanyin.style
    val m = style.margin
    val now = remember { LocalDateTime.now() }
    val signedIn = account is AccountState.LoggedIn
    val library = LocalAppServices.current?.library
    val recommended = preview?.let { Load.Ready(it.recommended) } ?: rememberLoad(signedIn) { library?.recommendedPlaylists().orEmpty() }.state
    val charts = preview?.let { Load.Ready(it.charts) } ?: rememberLoad("home-charts") { library?.chartPreviews() ?: error("offline") }.state
    val picks = preview?.let { Load.Ready(it.picks) } ?: rememberLoad(signedIn) { library?.picks() ?: error("offline") }.state
    val pickSongs = (picks as? Load.Ready)?.value?.songs.orEmpty()
    // Most doors need a login first.
    val needsLogin: (() -> Unit) -> () -> Unit = { action -> if (signedIn) action else onLogin }
    val openLiked = needsLogin { library?.likedPlaylist()?.let { onOpenPlaylist(it.id, "我喜欢的音乐") } ?: library?.refresh() }
    val playFm = needsLogin {
        player.playFm()
        onOpenPlayer()
    }
    val flourish = hasHomeFlourish
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { Header(now, account, onOpenSettings, if (signedIn) onOpenAccount else onLogin, Modifier.padding(horizontal = m)) }
            if (style.edge == EdgeKind.Brutal) {
                // 格 tapes a ribbon across the page, a little askew.
                item {
                    Marquee(
                        listOfNotNull(
                            "澜音 LANYIN",
                            "正在播放 ${player.title} — ${player.artist}".takeIf { !player.isEmpty },
                            "每日推荐 №%02d".format(now.dayOfMonth),
                            (account as? AccountState.LoggedIn)?.let { "欢迎回来 ${it.profile.nickname}" } ?: "扫码登录网易云",
                        ),
                        Modifier
                            .padding(top = 22.dp, bottom = 4.dp)
                            .graphicsLayer {
                                rotationZ = -1.6f
                                scaleX = 1.06f
                            },
                        height = 32.dp,
                    )
                }
            }
            if (flourish) {
                // The theme's own strip; 影's film runs edge to edge.
                item { HomeFlourish(player, Modifier.padding(start = if (style.backdrop == BackdropKind.Film) 0.dp else m, end = if (style.backdrop == BackdropKind.Film) 0.dp else m, top = 22.dp)) }
            }
            if (account == AccountState.LoggedOut) {
                item { ConnectLine(onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            }
            item {
                Row(Modifier.padding(start = m, end = m, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(
                        title = "每日推荐",
                        note = if (signedIn) "每天 6:00 更新" else "登录后生成",
                        onClick = needsLogin(onOpenDaily),
                        onPlay = needsLogin {
                            if (signedIn && pickSongs.isNotEmpty()) player.play(pickSongs, 0, "每日推荐") else onOpenDaily()
                        },
                        modifier = Modifier.weight(1f),
                        art = { DailyArt(if (signedIn) pickSongs else emptyList()) },
                        mark = { DayMark(now) },
                    )
                    FeatureTile(
                        title = "私人 FM",
                        note = if (signedIn) "越听越懂你" else "登录后开启",
                        onClick = playFm,
                        onPlay = playFm,
                        modifier = Modifier.weight(1f),
                        art = { CoverArt(77, Modifier.fillMaxSize()) },
                        mark = { ThemedIcon(LineIcon.Radio, null, tint = Color.White, size = 26.dp) },
                    )
                }
            }
            item {
                Shortcuts(
                    listOf(
                        Shortcut(LineIcon.Heart, "我喜欢", openLiked),
                        Shortcut(LineIcon.Chart, "排行榜", onOpenCharts),
                        Shortcut(LineIcon.History, "最近播放", onOpenRecent),
                        Shortcut(LineIcon.Download, "下载", onOpenDownloads),
                    ),
                    Modifier.padding(start = m, end = m, top = 18.dp),
                )
            }
            item {
                Column(Modifier.padding(top = 34.dp)) {
                    SectionTitle("推荐歌单", Modifier.padding(horizontal = m), trailing = if (signedIn) "为你挑的" else "大家在听", index = 1)
                    val lists = (recommended as? Load.Ready)?.value.orEmpty()
                    LazyRow(
                        contentPadding = PaddingValues(start = m, end = m, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (lists.isNotEmpty()) {
                            items(lists, key = { it.id }) { p -> PlaylistTile(p, { onOpenPlaylist(p.id, p.name) }, Modifier.width(132.dp)) }
                        } else {
                            // Until they arrive (or offline), the theme's own sleeves hold the space.
                            items(6) { i -> CoverArt(10 + i, Modifier.size(132.dp).clip(style.artShape)) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 34.dp)) {
                    SectionHeader("排行榜", 2, "全部", onOpenCharts, Modifier.padding(horizontal = m))
                    LazyRow(
                        contentPadding = PaddingValues(start = m, end = m, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (charts) {
                            is Load.Ready -> items(charts.value, key = { it.playlist.id }) { chart ->
                                ChartCard(chart, { onOpenPlaylist(chart.playlist.id, chart.playlist.name) }, Modifier.width(264.dp))
                            }
                            else -> items(3) { i -> ChartCard(null, onOpenCharts, Modifier.width(264.dp), seed = 30 + i) }
                        }
                    }
                }
            }
            val shown = picks as? Load.Ready
            if (shown != null && shown.value.songs.isNotEmpty()) {
                item {
                    SectionTitle(shown.value.title, Modifier.padding(start = m, end = m, top = 34.dp), trailing = shown.value.note, index = 3)
                }
                items(shown.value.songs.size) { i ->
                    val song = shown.value.songs[i]
                    PickRow(
                        song, playing = song.id == player.playingSongId,
                        onClick = { player.play(shown.value.songs, i, shown.value.title) },
                        modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 10.dp else 0.dp),
                    )
                }
            }
        }
    }
}

/** A section heading with a way to see everything. */
@Composable
private fun SectionHeader(title: String, index: Int, action: String, onAction: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title, Modifier.weight(1f), index = index)
        TextLink(action, onAction, color = Lanyin.colors.muted)
    }
}

/**
 * A door drawn over its artwork: the art fills the tile, a scrim darkens its foot, and the title
 * sits there in white with a play button beside it. [mark] goes in the top corner.
 */
@Composable
private fun FeatureTile(
    title: String,
    note: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
    art: @Composable () -> Unit,
    mark: @Composable () -> Unit,
) {
    val style = Lanyin.style
    val shape = style.cardShape
    Box(
        modifier
            .aspectRatio(0.84f)
            .then(if (style.edge == EdgeKind.Hairline) Modifier.border(1.dp, style.colors.outline, shape).pressable(onClick = onClick) else Modifier.themedSurface(shape = shape, onClick = onClick))
            .clip(shape),
    ) {
        Box(Modifier.fillMaxSize()) { art() }
        // 点's art is white on black at full contrast, so its foot needs nearly all of the dark.
        val foot = if (style.edge == EdgeKind.Dot) 0.94f else 0.74f
        Box(
            Modifier
                .fillMaxSize()
                // Dark at the top for the mark and at the foot for the title, whatever the art.
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.42f), 0.38f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = foot))),
        )
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { mark() }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = style.type.title.copy(fontFamily = style.display, fontWeight = FontWeight.SemiBold, shadow = TextShade), color = Color.White, maxLines = 1)
                Text(note, style = style.type.caption.copy(shadow = TextShade), color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            TilePlay(onPlay)
        }
    }
}

/** Keeps white type legible on light artwork. */
private val TextShade = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 1f), 8f)

/** A small play button that reads on any artwork. */
@Composable
private fun TilePlay(onClick: () -> Unit) {
    val style = Lanyin.style
    val brutal = style.edge == EdgeKind.Brutal
    Box(
        Modifier
            .size(34.dp)
            .pressable(onClick = onClick)
            .then(
                if (brutal) {
                    Modifier.background(io.lanyin.player.ui.theme.GridYellow).border(2.dp, style.colors.outline)
                } else {
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) { drawPlayPauseGlyph(0f, if (brutal) style.colors.content else Color(0xFF111111)) }
    }
}

/** Today's picks as a mosaic of their covers; the theme's own art until they're known. */
@Composable
private fun DailyArt(songs: List<io.lanyin.player.core.model.Song>) {
    val covers = songs.mapNotNull { s -> s.album.coverUrl?.let { it to (s.id % 97).toInt() } }.take(4)
    if (covers.size < 4) {
        CoverArt(3, Modifier.fillMaxSize())
        return
    }
    Column(Modifier.fillMaxSize()) {
        for (r in 0..1) {
            Row(Modifier.weight(1f)) {
                for (k in 0..1) {
                    val (url, seed) = covers[r * 2 + k]
                    CoverImage(url, seed, 90.dp, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

/** The day of the month, large, on the 每日推荐 tile. */
@Composable
private fun DayMark(now: LocalDateTime) {
    val style = Lanyin.style
    Column {
        Text(
            if (style.quietSections) chineseNumber(now.dayOfMonth) else "${now.dayOfMonth}",
            style = style.type.display.copy(fontFamily = style.display, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, shadow = TextShade),
            color = Color.White,
        )
        Text("${now.monthValue}月", style = style.type.caption.copy(shadow = TextShade), color = Color.White.copy(alpha = 0.85f))
    }
}

private class Shortcut(val icon: LineIcon, val label: String, val onClick: () -> Unit)

/** Your own music, one tap each. */
@Composable
private fun Shortcuts(items: List<Shortcut>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (hairline) 0.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, item ->
            if (hairline && i > 0) Box(Modifier.width(1.dp).height(20.dp).background(c.outline))
            Column(
                Modifier
                    .weight(1f)
                    .then(if (hairline) Modifier.pressable(onClick = item.onClick) else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Flat, onClick = item.onClick))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemedIcon(item.icon, null, size = 20.dp)
                Text(item.label, style = style.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A chart and its top three; a placeholder sleeve while it loads. */
@Composable
private fun ChartCard(chart: ChartPreview?, onClick: () -> Unit, modifier: Modifier, seed: Int = 0) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(
        modifier
            .then(if (hairline) Modifier.border(1.dp, c.outline).pressable(onClick = onClick) else Modifier.themedSurface(onClick = onClick))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(chart?.playlist?.coverUrl, if (chart != null) (chart.playlist.id % 97).toInt() else seed, 52.dp, Modifier.size(52.dp).clip(style.artShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chart?.playlist?.name ?: "排行榜", style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chart?.playlist?.updateNote ?: if (chart == null) "正在加载…" else "", style = style.type.caption, color = c.muted, maxLines = 1)
            }
        }
        Column(Modifier.padding(top = 10.dp).heightIn(min = 66.dp)) {
            chart?.top.orEmpty().take(3).forEachIndexed { i, song ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}",
                        style = style.type.caption.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold),
                        color = if (i == 0) c.accent else c.content,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        buildAnnotatedString {
                            append(song.name)
                            withStyle(SpanStyle(color = c.muted)) { append(" - ${song.artistLine}") }
                        },
                        style = style.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A song to start from: cover, title, artist, and its ⋯. */
@Composable
private fun PickRow(song: io.lanyin.player.core.model.Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(song.album.coverUrl, (song.id % 97).toInt(), 48.dp, Modifier.size(48.dp).clip(style.artShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body.copy(fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(song.artistLine, song.album.name).filter { it.isNotEmpty() }.joinToString(" · "),
                style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(width = 36.dp, height = 44.dp).pressable { openSheet(io.lanyin.player.ui.music.Sheet.TrackActions(song)) }, contentAlignment = Alignment.Center) {
            ThemedIcon(LineIcon.More, "更多", tint = c.muted, size = 20.dp)
        }
    }
}

internal fun greeting(hour: Int) = when (hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

private val chineseDigits = listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

/** 14 → 十四, 20 → 二十, 31 → 三十一. */
internal fun chineseNumber(n: Int): String = when {
    n <= 10 -> chineseDigits[n]
    n < 20 -> "十" + chineseDigits[n % 10]
    n % 10 == 0 -> chineseDigits[n / 10] + "十"
    else -> chineseDigits[n / 10] + "十" + chineseDigits[n % 10]
}

@Composable
private fun Header(now: LocalDateTime, account: AccountState, onOpenSettings: () -> Unit, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val weekday = now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
    if (style.backdrop == BackdropKind.Paper) {
        Masthead(now, weekday, account, onOpenSettings, onLogin, modifier)
        return
    }
    // The literary theme writes the date out in characters.
    val date = if (style.quietSections) {
        "${chineseNumber(now.monthValue)}月${chineseNumber(now.dayOfMonth)}日 · $weekday"
    } else if (style.edge == EdgeKind.Brutal) {
        // 格 dates the page like a print run.
        "№ %d.%02d.%02d · %s".format(now.year, now.monthValue, now.dayOfMonth, now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.CHINA))
    } else {
        "${now.monthValue}月${now.dayOfMonth}日 · $weekday"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = style.type.display)
            run {
                val brutal = style.edge == EdgeKind.Brutal
                Text(
                    date,
                    style = if (brutal) style.type.caption.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold) else style.type.caption,
                    color = if (brutal) c.content else c.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        HeaderActions(account, onOpenSettings, onLogin)
    }
}

/**
 * 报's front page: the paper's name across the top in heavy type, the edition and the actions
 * above it, a rule, and the dateline with the issue number under it.
 */
@Composable
private fun Masthead(now: LocalDateTime, weekday: String, account: AccountState, onOpenSettings: () -> Unit, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LANYIN DAILY · ${if (now.hour in 5..14) "早报" else "晚报"}",
                style = style.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.14.em),
                color = c.muted, maxLines = 1, modifier = Modifier.weight(1f),
            )
            HeaderActions(account, onOpenSettings, onLogin)
        }
        Text(
            "澜音日报",
            style = style.type.display.copy(fontSize = 42.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, letterSpacing = 0.18.em, textAlign = TextAlign.Center),
            color = c.content,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        DoubleRule(Modifier.padding(top = 6.dp))
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${now.year}年${now.monthValue}月${now.dayOfMonth}日　$weekday", style = style.type.caption, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${greeting(now.hour)} · 第${now.dayOfYear}期", style = style.type.caption, color = c.muted, maxLines = 1)
        }
        Hairline()
    }
}

/** Settings and the account, at the header's end: bare in 墨 and 报, on the theme's surfaces elsewhere. */
@Composable
private fun HeaderActions(account: AccountState, onOpenSettings: () -> Unit, onLogin: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val bare = style.edge == EdgeKind.Hairline || style.edge == EdgeKind.Rule
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (bare) {
            IconAction(LineIcon.Sliders, "设置", onOpenSettings, tint = c.muted)
        } else {
            Box(
                Modifier.size(40.dp).themedSurface(shape = style.roundShape, tone = SurfaceTone.Flat, onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) { ThemedIcon(LineIcon.Sliders, "设置", tint = c.muted, size = 20.dp) }
            Spacer(Modifier.width(10.dp))
        }
        val profile = (account as? AccountState.LoggedIn)?.profile
        Box(
            Modifier
                .size(40.dp)
                .then(
                    if (bare) {
                        Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape).pressable(onClick = onLogin)
                    } else {
                        Modifier.themedSurface(shape = style.roundShape, onClick = onLogin)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (profile != null) {
                Avatar(profile.avatarUrl, profile.nickname, 40.dp, shape = if (bare) CircleShape else style.roundShape)
            } else {
                ThemedIcon(LineIcon.User, "登录", tint = c.muted, size = 20.dp)
            }
        }
    }
}

/** Not logged in yet: one calm line, not a billboard. */
@Composable
private fun ConnectLine(onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(modifier) {
        if (hairline) Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat))
                .padding(horizontal = if (hairline) 0.dp else 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A small seal, the one spot of colour on the page.
            Box(Modifier.size(8.dp).background(c.accentAlt, if (hairline) androidx.compose.ui.graphics.RectangleShape else style.roundShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("未连接网易云音乐", style = style.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("登录后同步歌单、红心和每日推荐", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            TextLink("扫码登录", onLogin)
        }
        if (hairline) Hairline()
    }
}
