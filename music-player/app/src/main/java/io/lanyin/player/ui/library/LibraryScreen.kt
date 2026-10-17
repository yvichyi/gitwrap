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

@Composable
fun LibraryScreen(
    onLogin: () -> Unit,
    account: AccountState = AccountState.LoggedOut,
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    onOpenRecent: () -> Unit = {},
    onOpenCloud: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenRanking: () -> Unit = {},
    /** Stands in for the network in previews. */
    previewPlaylists: List<Playlist>? = null,
    previewDownloads: Int? = null,
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    var shelf by rememberSaveable { mutableStateOf("创建的歌单") }
    val library = LocalAppServices.current?.library
    val loaded by (library?.playlists ?: remember { MutableStateFlow(previewPlaylists) }).collectAsState()
    val signedIn = account is AccountState.LoggedIn
    val syncFailed by (library?.syncFailed ?: remember { MutableStateFlow(false) }).collectAsState()
    val uid = (account as? AccountState.LoggedIn)?.profile?.userId
    val liked = loaded?.firstOrNull { it.specialType == 5 }
    val recentCount = LocalAppServices.current?.recent?.songs?.collectAsState()?.value?.size
    val downloadItems = LocalAppServices.current?.downloads?.items?.collectAsState()?.value
    val downloaded = previewDownloads ?: downloadItems?.count { it.state == io.lanyin.player.playback.DownloadState.Done }
    val downloading = downloadItems?.count {
        it.state == io.lanyin.player.playback.DownloadState.Queued || it.state == io.lanyin.player.playback.DownloadState.Downloading
    } ?: 0
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    val entries = listOf(
        Entry(
            LineIcon.Heart, "我喜欢的音乐",
            when {
                liked != null -> "${liked.trackCount} 首"
                signedIn && syncFailed -> "没能同步，点一下重试"
                signedIn -> "正在同步"
                else -> "登录后同步"
            },
            onClick = when {
                liked != null -> ({ onOpenPlaylist(liked.id, "我喜欢的音乐") })
                signedIn && syncFailed -> ({ library?.refresh(); Unit })
                signedIn -> null
                else -> onLogin
            },
        ),
        Entry(LineIcon.History, "最近播放", recentCount?.let { if (it == 0) "还没有" else "$it 首 · 本机" } ?: "本机", onClick = onOpenRecent),
        Entry(LineIcon.Chart, "听歌排行", if (signedIn) "最近一周" else "登录后可用", onClick = if (signedIn) onOpenRanking else onLogin),
        Entry(
            LineIcon.Download, "下载",
            when {
                downloading > 0 -> "正在下载 $downloading 首"
                downloaded != null && downloaded > 0 -> "$downloaded 首"
                else -> "还没有"
            },
            onClick = onOpenDownloads,
        ),
        Entry(LineIcon.Cloud, "音乐云盘", if (signedIn) "上传的歌" else "登录后可用", onClick = if (signedIn) onOpenCloud else onLogin),
    )
    val shelfItems = loaded?.let { all ->
        if (shelf == "创建的歌单") all.filter { it.creatorId == uid && it.specialType != 5 } else all.filter { it.creatorId != uid }
    }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        val syncing by (library?.syncing ?: remember { MutableStateFlow(false) }).collectAsState()
        io.lanyin.player.ui.design.ThemedPullRefresh(syncing, onRefresh = { library?.refresh() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { PageTitle("我的", Modifier.padding(horizontal = m)) }
            item { ThemedProfile(account, onLogin, Modifier.padding(start = m, end = m, top = 24.dp)) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("音乐库", index = 1)
                    ThemedEntries(entries, Modifier.padding(top = 12.dp))
                }
            }
            item {
                Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                    SectionTitle("歌单", index = 2)
                    ChoiceRow(listOf("创建的歌单", "收藏的歌单"), shelf, { shelf = it }, Modifier.padding(top = 14.dp))
                    when {
                        !signedIn && previewPlaylists == null -> Placeholder("登录后，这里会列出你$shelf")
                        shelfItems == null && syncFailed -> Placeholder("没能同步歌单，网络好了再试试", "重试") { library?.refresh() }
                        shelfItems == null -> Placeholder("正在同步…")
                        shelfItems.isEmpty() && shelf != "创建的歌单" -> Placeholder("还没有收藏歌单。在别人的歌单里点「收藏」")
                    }
                }
            }
            if (signedIn && shelf == "创建的歌单" && shelfItems != null) {
                item(key = "new") { NewPlaylistRow({ openSheet(io.lanyin.player.ui.music.Sheet.NewPlaylist()) }, Modifier.padding(horizontal = m)) }
            }
            items(shelfItems.orEmpty(), key = { it.id }) { p ->
                PlaylistRow(p, { onOpenPlaylist(p.id, p.name) }, Modifier.padding(horizontal = m))
            }
        }
        }
    }
}

/** 「新建歌单」 at the top of the listener's own playlists. */
@Composable
private fun NewPlaylistRow(onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .then(if (style.edge == EdgeKind.Hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Flat)),
            contentAlignment = Alignment.Center,
        ) { ThemedIcon(LineIcon.Plus, null, tint = c.accent, size = 24.dp) }
        Spacer(Modifier.width(14.dp))
        Text("新建歌单", style = style.type.body)
    }
}

@Composable
internal fun Profile(account: AccountState, onLogin: () -> Unit, modifier: Modifier) {
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
