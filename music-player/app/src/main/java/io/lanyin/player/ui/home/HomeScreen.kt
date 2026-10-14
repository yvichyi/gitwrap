package io.lanyin.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.components.QrGlyph
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.theme.ButtonKind
import io.lanyin.player.ui.theme.Lanyin
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    player: DemoPlayerState,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val c = Lanyin.colors
    val now = remember { LocalDateTime.now() }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = pagePadding(),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item { Header(now, onOpenSettings, onLogin, Modifier.padding(horizontal = 20.dp)) }
            item { LoginInvite(onLogin, Modifier.padding(horizontal = 20.dp)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("为你准备")
                    QuickTiles(now.dayOfMonth, onLogin)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("继续播放")
                    ContinueCard(player, onOpenPlayer)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("推荐歌单", Modifier.padding(horizontal = 20.dp), trailing = "登录后解锁")
                    LockedPlaylists(onLogin)
                }
            }
        }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

@Composable
private fun Header(now: LocalDateTime, onOpenSettings: () -> Unit, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = MaterialTheme.typography.displaySmall)
            val weekday = now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
            Text(
                "${now.monthValue}月${now.dayOfMonth}日 · $weekday · 今天想听点什么？",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconAction(Icons.Filled.Settings, "设置", onOpenSettings, tint = c.muted)
        Box(
            Modifier
                .size(44.dp)
                .themedSurface(shape = CircleShape, onClick = onLogin),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "登录", tint = c.muted)
        }
    }
}

@Composable
private fun LoginInvite(onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val onFill = if (style.button == ButtonKind.NeonRing) c.accent else c.onAccent
    Row(
        modifier
            .fillMaxWidth()
            .themedSurface(tone = SurfaceTone.Accent, onClick = onLogin)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("连接网易云音乐", style = MaterialTheme.typography.titleLarge, color = onFill)
            Text(
                "扫码登录后，同步你的歌单、红心和每日推荐",
                style = MaterialTheme.typography.bodyMedium, color = onFill.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "扫码登录  →",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = onFill,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(style.chipShape)
                    .border(1.5.dp, onFill.copy(alpha = 0.7f), style.chipShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        QrGlyph(onFill.copy(alpha = 0.9f), Modifier.size(78.dp))
    }
}

@Composable
private fun QuickTiles(day: Int, onLogin: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickTile("每日推荐", onLogin, Modifier.weight(1f)) {
            Text(
                "$day",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold),
                color = c.accent,
            )
        }
        QuickTile("私人 FM", onLogin, Modifier.weight(1f)) { Icon(Icons.Filled.Radio, null, tint = c.accent, modifier = Modifier.size(30.dp)) }
        QuickTile("我喜欢", onLogin, Modifier.weight(1f)) { Icon(Icons.Filled.Favorite, null, tint = c.like, modifier = Modifier.size(30.dp)) }
        QuickTile("排行榜", onLogin, Modifier.weight(1f)) { Icon(Icons.Filled.Leaderboard, null, tint = c.accentAlt, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun QuickTile(label: String, onClick: () -> Unit, modifier: Modifier, visual: @Composable () -> Unit) {
    Column(
        modifier
            .aspectRatio(0.84f)
            .themedSurface(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) { visual() }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ContinueCard(player: DemoPlayerState, onOpenPlayer: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val seconds by remember { derivedStateOf { player.positionMs / 1000 } }
    Row(
        Modifier
            .fillMaxWidth()
            .themedSurface(onClick = onOpenPlayer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(player.coverSeed, Modifier.size(60.dp).clip(style.artShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(player.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(
                "${player.artist} · 听到 ${formatTime(seconds * 1000)}",
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        PlayPauseButton(player.isPlaying, player::togglePlay, size = 46.dp)
    }
}

@Composable
private fun LockedPlaylists(onLogin: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(6) { i ->
            Column(Modifier.width(132.dp).pressable(onClick = onLogin)) {
                Box {
                    CoverArt(10 + i, Modifier.size(132.dp).clip(style.artShape))
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(c.bg.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "需要登录", tint = c.content, modifier = Modifier.size(14.dp))
                    }
                }
                // Skeleton title lines in place of real names.
                Box(Modifier.padding(top = 10.dp).width(116.dp).height(10.dp).clip(style.chipShape).background(c.content.copy(alpha = 0.1f)))
                Box(Modifier.padding(top = 7.dp).width(72.dp).height(10.dp).clip(style.chipShape).background(c.content.copy(alpha = 0.06f)))
            }
        }
    }
}
