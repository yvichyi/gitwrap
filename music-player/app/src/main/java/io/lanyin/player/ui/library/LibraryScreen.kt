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
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.music.Placeholder
import io.lanyin.player.ui.music.PlaylistRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.VipBadge

private data class Entry(val icon: LineIcon, val title: String, val detail: String, val onClick: (() -> Unit)? = null)

@Composable
fun LibraryScreen(
    onLogin: () -> Unit,
    account: AccountState = AccountState.LoggedOut,
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    onOpenRecent: () -> Unit = {},
    /** Stands in for the network in previews. */
    previewPlaylists: List<Playlist>? = null,
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    var shelf by rememberSaveable { mutableStateOf("创建的歌单") }
    val library = LocalAppServices.current?.library
    val loaded by (library?.playlists ?: remember { MutableStateFlow(previewPlaylists) }).collectAsState()
    val signedIn = account is AccountState.LoggedIn
    val uid = (account as? AccountState.LoggedIn)?.profile?.userId
    val liked = loaded?.firstOrNull { it.specialType == 5 }
    val recentCount = LocalAppServices.current?.recent?.songs?.collectAsState()?.value?.size
    val entries = listOf(
        Entry(
            LineIcon.Heart, "我喜欢的音乐",
            when {
                liked != null -> "${liked.trackCount} 首"
                signedIn -> "正在同步"
                else -> "登录后同步"
            },
            onClick = if (liked != null) ({ onOpenPlaylist(liked.id, "我喜欢的音乐") }) else if (signedIn) null else onLogin,
        ),
        Entry(LineIcon.History, "最近播放", recentCount?.let { if (it == 0) "还没有" else "$it 首 · 本机" } ?: "本机", onClick = onOpenRecent),
        Entry(LineIcon.Download, "本地缓存", "边听边存，最多 1 GB"),
        Entry(LineIcon.Cloud, "音乐云盘", if (signedIn) "下一版" else "登录后可用", onClick = if (signedIn) null else onLogin),
    )
    val shelfItems = loaded?.let { all ->
        if (shelf == "创建的歌单") all.filter { it.creatorId == uid && it.specialType != 5 } else all.filter { it.creatorId != uid }
    }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { PageTitle("我的", Modifier.padding(horizontal = m)) }
            item { Profile(account, onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("音乐库", index = 1)
                    EntryList(entries, Modifier.padding(top = 10.dp))
                }
            }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("歌单", index = 2)
                    ChoiceRow(listOf("创建的歌单", "收藏的歌单"), shelf, { shelf = it }, Modifier.padding(top = 14.dp))
                    when {
                        !signedIn && previewPlaylists == null -> Placeholder("登录后，这里会列出你$shelf")
                        shelfItems == null -> Placeholder("正在同步…")
                        shelfItems.isEmpty() -> Placeholder(if (shelf == "创建的歌单") "还没有自己建的歌单" else "还没有收藏歌单")
                    }
                }
            }
            items(shelfItems.orEmpty(), key = { it.id }) { p ->
                PlaylistRow(p, { onOpenPlaylist(p.id, p.name) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

@Composable
private fun Profile(account: AccountState, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Row(
        modifier
            .fillMaxWidth()
            .then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val profile = (account as? AccountState.LoggedIn)?.profile
        if (profile != null) {
            Avatar(profile.avatarUrl, profile.nickname, 56.dp)
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .then(if (hairline) Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape) else Modifier.themedSurface(shape = style.roundShape, tone = SurfaceTone.Flat)),
                contentAlignment = Alignment.Center,
            ) {
                ThemedIcon(LineIcon.User, null, tint = c.muted, size = 26.dp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            when {
                profile != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.nickname, style = style.type.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        VipBadge(profile.vipType, Modifier.padding(start = 8.dp))
                    }
                    Text("已连接网易云音乐", style = style.type.caption, color = c.muted, maxLines = 1)
                }
                account == AccountState.Loading -> {
                    Text("正在连接…", style = style.type.title, maxLines = 1)
                    Text("网易云音乐", style = style.type.caption, color = c.muted, maxLines = 1)
                }
                else -> {
                    Text("未登录", style = style.type.title, maxLines = 1)
                    Text("同步你的歌单与红心", style = style.type.caption, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        if (account == AccountState.LoggedOut) {
            Spacer(Modifier.width(12.dp))
            ThemedButton("登录", onLogin)
        }
    }
}

/** Rows ruled with hairlines; grouped on one card in themes that use cards. */
@Composable
private fun EntryList(entries: List<Entry>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(modifier.fillMaxWidth().then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
        entries.forEachIndexed { i, e ->
            if (i > 0 || hairline) Hairline()
            Row(
                Modifier.fillMaxWidth().then(e.onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier).padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedIcon(e.icon, null, size = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text(e.title, style = style.type.body, modifier = Modifier.weight(1f))
                Text(e.detail, style = style.type.caption, color = c.muted)
                if (e.onClick != null) ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (hairline) Hairline()
    }
}
