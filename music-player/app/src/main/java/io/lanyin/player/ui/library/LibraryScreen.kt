package io.lanyin.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.accentBrush
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin

@Composable
fun LibraryScreen(onLogin: () -> Unit) {
    val c = Lanyin.colors
    var shelf by rememberSaveable { mutableStateOf("创建的歌单") }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = pagePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { PageTitle("我的", Modifier.padding(horizontal = 20.dp)) }
            item { ProfileCard(onLogin, Modifier.padding(horizontal = 20.dp)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryTile(Icons.Filled.Favorite, "我喜欢的音乐", "登录后同步", c.like, onLogin, Modifier.weight(1f))
                        LibraryTile(Icons.Filled.History, "最近播放", "1 首 · 本机", c.accent, onLogin, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryTile(Icons.Filled.DownloadForOffline, "本地缓存", "边听边存", c.accentAlt, onLogin, Modifier.weight(1f))
                        LibraryTile(Icons.Filled.Cloud, "音乐云盘", "登录后可用", c.accent, onLogin, Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("歌单")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("创建的歌单", "收藏的歌单").forEach { name ->
                            ThemedChip(name, selected = shelf == name, onClick = { shelf = name })
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = c.muted, modifier = Modifier.size(40.dp))
                        Text(
                            "登录后，这里会列出你$shelf",
                            style = MaterialTheme.typography.bodyMedium, color = c.muted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth().themedSurface().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(style.accentBrush()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("未登录", style = MaterialTheme.typography.titleLarge)
                Text("登录网易云音乐，同步你的音乐库", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = c.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat("—", "喜欢", Modifier.weight(1f))
            Stat("—", "歌单", Modifier.weight(1f))
            Stat("—", "关注", Modifier.weight(1f))
            ThemedButton("扫码登录", onLogin)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val style = Lanyin.style
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = style.numeric))
        Text(label, style = MaterialTheme.typography.labelSmall, color = style.colors.muted)
    }
}

@Composable
private fun LibraryTile(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    Row(
        modifier.themedSurface(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
        }
    }
}
