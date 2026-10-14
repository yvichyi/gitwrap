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
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * One focal point (what you were listening to), a quiet line asking you to connect, then
 * shortcuts and recommendations. 墨 draws it with rules and whitespace instead of boxes.
 */
@Composable
fun HomeScreen(
    player: DemoPlayerState,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val style = Lanyin.style
    val m = style.margin
    val now = remember { LocalDateTime.now() }
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { Header(now, onOpenSettings, onLogin, Modifier.padding(horizontal = m)) }
            item { ConnectLine(onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("继续播放")
                    NowPlayingHero(player, onOpenPlayer, Modifier.padding(top = 14.dp))
                }
            }
            item { QuickEntries(now.dayOfMonth, onLogin, Modifier.padding(start = m, end = m, top = 36.dp)) }
            item {
                Column(Modifier.padding(top = 36.dp)) {
                    SectionTitle("推荐歌单", Modifier.padding(horizontal = m), trailing = "登录后可见")
                    LazyRow(
                        contentPadding = PaddingValues(start = m, end = m, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(6) { i ->
                            CoverArt(10 + i, Modifier.size(132.dp).clip(style.artShape).pressable(onClick = onLogin))
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
private fun Header(now: LocalDateTime, onOpenSettings: () -> Unit, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val weekday = now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
    // The literary theme writes the date out in characters.
    val date = if (style.quietSections) {
        "${chineseNumber(now.monthValue)}月${chineseNumber(now.dayOfMonth)}日 · $weekday"
    } else {
        "${now.monthValue}月${now.dayOfMonth}日 · $weekday"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = style.type.display)
            Text(date, style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
        if (style.edge == EdgeKind.Hairline) {
            IconAction(LineIcon.Sliders, "设置", onOpenSettings, tint = c.muted)
        } else {
            Box(
                Modifier.size(40.dp).themedSurface(shape = CircleShape, tone = SurfaceTone.Flat, onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) { ThemedIcon(LineIcon.Sliders, "设置", tint = c.muted, size = 20.dp) }
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .size(40.dp)
                .then(
                    if (style.edge == EdgeKind.Hairline) {
                        Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape).pressable(onClick = onLogin)
                    } else {
                        Modifier.themedSurface(shape = CircleShape, onClick = onLogin)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ThemedIcon(LineIcon.User, "登录", tint = c.muted, size = 20.dp)
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
            Box(Modifier.size(8.dp).background(c.accentAlt, if (hairline) androidx.compose.ui.graphics.RectangleShape else CircleShape))
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
private fun NowPlayingHero(player: DemoPlayerState, onOpenPlayer: () -> Unit, modifier: Modifier) {
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
        CoverArt(player.coverSeed, Modifier.size(88.dp).clip(style.artShape))
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

/** Four shortcuts. Only the date carries colour; icons stay in the ink colour. */
@Composable
private fun QuickEntries(day: Int, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    val entries: List<Pair<String, @Composable () -> Unit>> = listOf(
        "每日推荐" to {
            Text(
                if (hairline) chineseNumber(day) else "$day",
                style = style.type.title.copy(fontFamily = style.display, fontSize = if (hairline) 20.sp else 22.sp, fontWeight = FontWeight.SemiBold),
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
                    .then(if (hairline) Modifier.pressable(onClick = onLogin) else Modifier.heightIn(min = 76.dp).themedSurface(tone = SurfaceTone.Flat, onClick = onLogin))
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
