package io.lanyin.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemeMotif
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.accentBrush
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

private data class Entry(val icon: LineIcon, val title: String, val detail: String)

private val entries = listOf(
    Entry(LineIcon.Heart, "我喜欢的音乐", "登录后同步"),
    Entry(LineIcon.History, "最近播放", "1 首 · 本机"),
    Entry(LineIcon.Download, "本地缓存", "边听边存"),
    Entry(LineIcon.Cloud, "音乐云盘", "登录后可用"),
)

@Composable
fun LibraryScreen(onLogin: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    var shelf by rememberSaveable { mutableStateOf("创建的歌单") }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { PageTitle("我的", Modifier.padding(horizontal = m)) }
            item { Profile(onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("音乐库", index = 1)
                    EntryList(onLogin, Modifier.padding(top = 10.dp))
                }
            }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("歌单", index = 2)
                    ChoiceRow(listOf("创建的歌单", "收藏的歌单"), shelf, { shelf = it }, Modifier.padding(top = 14.dp))
                    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ThemeMotif(c.content.copy(alpha = 0.18f), Modifier.size(64.dp))
                        Text("登录后，这里会列出你$shelf", style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Profile(onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Row(
        modifier
            .fillMaxWidth()
            .then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .then(if (hairline) Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape) else Modifier.themedSurface(shape = style.roundShape, tone = SurfaceTone.Flat)),
            contentAlignment = Alignment.Center,
        ) {
            ThemedIcon(LineIcon.User, null, tint = c.muted, size = 26.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("未登录", style = style.type.title, maxLines = 1)
            Text("同步你的歌单与红心", style = style.type.caption, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        ThemedButton("登录", onLogin)
    }
}

/** Rows ruled with hairlines; grouped on one card in themes that use cards. */
@Composable
private fun EntryList(onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(modifier.fillMaxWidth().then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
        entries.forEachIndexed { i, e ->
            if (i > 0 || hairline) Hairline()
            Row(
                Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedIcon(e.icon, null, size = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text(e.title, style = style.type.body, modifier = Modifier.weight(1f))
                Text(e.detail, style = style.type.caption, color = c.muted)
                ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (hairline) Hairline()
    }
}
