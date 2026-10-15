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
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.music.Load
import io.lanyin.player.ui.music.PlaylistTile
import io.lanyin.player.ui.music.rememberLoad
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.theme.BackdropKind
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * One focal point (what you were listening to), a quiet line asking you to connect, then
 * shortcuts and recommendations. 墨 draws it with rules and whitespace instead of boxes.
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
) {
    val style = Lanyin.style
    val m = style.margin
    val now = remember { LocalDateTime.now() }
    val signedIn = account is AccountState.LoggedIn
    val library = LocalAppServices.current?.library
    val recommended = rememberLoad(signedIn) { library?.recommendedPlaylists().orEmpty() }
    // Each entry runs its own errand; most need a login first.
    val needsLogin: (() -> Unit) -> () -> Unit = { action -> if (signedIn) action else onLogin }
    val entryActions = listOf(
        needsLogin(onOpenDaily),
        needsLogin {
            player.playFm()
            onOpenPlayer()
        },
        needsLogin { library?.likedPlaylist()?.let { onOpenPlaylist(it.id, "我喜欢的音乐") } ?: library?.refresh() },
        onOpenCharts,
    )
    val showHero = !player.isEmpty || signedIn
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { Header(now, account, onOpenSettings, if (account is AccountState.LoggedIn) onOpenAccount else onLogin, Modifier.padding(horizontal = m)) }
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
            if (account == AccountState.LoggedOut) {
                item { ConnectLine(onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            }
            if (showHero) {
                item {
                    Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                        if (player.isEmpty) {
                            SectionTitle("今天听什么", index = 1)
                            DailyHero(now, onOpenDaily, Modifier.padding(top = 14.dp))
                        } else {
                            SectionTitle("继续播放", index = 1)
                            NowPlayingHero(player, onOpenPlayer, Modifier.padding(top = 14.dp))
                        }
                    }
                }
            }
            item { QuickEntries(now.dayOfMonth, entryActions, Modifier.padding(start = m, end = m, top = 36.dp)) }
            item {
                Column(Modifier.padding(top = 36.dp)) {
                    SectionTitle(
                        "推荐歌单", Modifier.padding(horizontal = m),
                        trailing = if (signedIn) "为你挑的" else "大家在听",
                        index = if (showHero) 2 else 1,
                    )
                    val lists = (recommended.state as? Load.Ready)?.value.orEmpty()
                    LazyRow(
                        contentPadding = PaddingValues(start = m, end = m, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (lists.isNotEmpty()) {
                            items(lists, key = { it.id }) { p -> PlaylistTile(p, { onOpenPlaylist(p.id, p.name) }, Modifier.width(132.dp)) }
                        } else {
                            // Until they arrive (or offline), the theme's own sleeves hold the space.
                            items(6) { i ->
                                CoverArt(10 + i, Modifier.size(132.dp).clip(style.artShape).pressable(onClick = recommended.retry))
                            }
                        }
                    }
                }
            }
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
        if (style.edge == EdgeKind.Hairline) {
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
                    if (style.edge == EdgeKind.Hairline) {
                        Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape).pressable(onClick = onLogin)
                    } else {
                        Modifier.themedSurface(shape = style.roundShape, onClick = onLogin)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (profile != null) {
                Avatar(profile.avatarUrl, profile.nickname, 40.dp, shape = if (style.edge == EdgeKind.Hairline) CircleShape else style.roundShape)
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

@Composable
private fun NowPlayingHero(player: PlayerState, onOpenPlayer: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    val seconds by remember { derivedStateOf { player.positionMs / 1000 } }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (hairline) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpenPlayer)
                } else {
                    Modifier.themedSurface(onClick = onOpenPlayer).padding(14.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(player.coverUrl, player.coverSeed, 88.dp, Modifier.size(88.dp).clip(style.artShape))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(player.title, style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(player.artist + " · " + player.album, style = style.type.caption, color = c.muted, maxLines = 1)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.weight(1f).height(2.dp)) {
                    val f = (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
                    val y = size.height / 2
                    drawLine(c.content.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    drawLine(c.content, Offset(0f, y), Offset(size.width * f, y), 1.5.dp.toPx())
                }
                Text(
                    formatTime(seconds * 1000),
                    style = style.type.caption.copy(fontFamily = style.numeric),
                    color = c.muted,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        PlayPauseButton(player.isPlaying, player::togglePlay, size = 48.dp)
    }
}

/** Nothing playing yet: today's picks, one tap away. */
@Composable
private fun DailyHero(now: LocalDateTime, onOpen: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (hairline) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                } else {
                    Modifier.themedSurface(onClick = onOpen).padding(14.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.size(88.dp).then(if (hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Flat)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (hairline) chineseNumber(now.dayOfMonth) else "${now.dayOfMonth}",
                style = style.type.display.copy(fontFamily = style.display, fontSize = if (hairline) 26.sp else 36.sp, lineHeight = 40.sp),
                color = c.accent,
            )
            Text("${now.monthValue}月", style = style.type.caption, color = c.muted)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("每日推荐", style = style.type.title, maxLines = 1)
            Text("照你的口味挑的三十首，每天 6:00 换新", style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 18.dp)
    }
}

/** Four shortcuts. Only the date carries colour; icons stay in the ink colour. */
@Composable
private fun QuickEntries(day: Int, actions: List<() -> Unit>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    val entries: List<Pair<String, @Composable () -> Unit>> = listOf(
        "每日推荐" to {
            Text(
                if (hairline) chineseNumber(day) else "$day",
                style = style.type.title.copy(
                    fontFamily = style.display,
                    fontSize = when {
                        hairline -> 20.sp
                        style.displayWeight <= FontWeight.Light -> 26.sp
                        else -> 22.sp
                    },
                    fontWeight = if (style.displayWeight <= FontWeight.Light) FontWeight.Light else FontWeight.SemiBold,
                ),
                color = c.accent,
            )
        },
        "私人 FM" to { ThemedIcon(LineIcon.Radio, null, size = 26.dp) },
        "我喜欢" to { ThemedIcon(LineIcon.Heart, null, size = 26.dp) },
        "排行榜" to { ThemedIcon(LineIcon.Chart, null, size = 26.dp) },
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (hairline) 0.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.forEachIndexed { i, (label, visual) ->
            if (hairline && i > 0) {
                Box(Modifier.width(1.dp).height(36.dp).background(c.outline))
            }
            Column(
                Modifier
                    .weight(1f)
                    .then(if (hairline) Modifier.pressable(onClick = actions[i]) else Modifier.heightIn(min = 76.dp).themedSurface(tone = SurfaceTone.Flat, onClick = actions[i]))
                    .padding(vertical = if (hairline) 4.dp else 12.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { visual() }
                Text(label, style = style.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
