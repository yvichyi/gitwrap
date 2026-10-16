package io.lanyin.player.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.CloudPage as CloudPageData
import io.lanyin.player.core.model.CloudSong
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.RankedSong
import io.lanyin.player.core.model.RecentPlay
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.library.describeDocument
import io.lanyin.player.data.library.uploadFile
import io.lanyin.player.playback.DownloadItem
import io.lanyin.player.playback.DownloadState
import io.lanyin.player.playback.label
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.LocalBottomChromePadding
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.Lanyin
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val noLikes = MutableStateFlow<Set<Long>>(emptySet())
private val noRevision = MutableStateFlow(0)

fun <T, R> Load<T>.map(f: (T) -> R): Load<R> = when (this) {
    Load.Loading -> Load.Loading
    is Load.Failed -> this
    is Load.Ready -> Load.Ready(f(value))
}

/** 「下载全部」 for a list: asks first, since a playlist can be large. */
@Composable
private fun RowScope.DownloadAll(songs: Load<List<Song>>, title: String) {
    val list = (songs as? Load.Ready)?.value ?: return
    if (list.isEmpty()) return
    val openSheet = LocalOpenSheet.current
    IconAction(LineIcon.Download, "下载全部", { openSheet(Sheet.ConfirmDownload(list, title)) }, tint = Lanyin.colors.muted)
}

/** A playlist, fetched whole. One's own can be renamed, deleted and edited; others' collected. */
@Composable
fun PlaylistPage(id: Long, name: String, player: PlayerState, onBack: () -> Unit) {
    val services = LocalAppServices.current
    val library = services?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val revision by (library?.revision ?: noRevision).collectAsState()
    val load = rememberLoad(id, refresh = revision) { library?.playlist(id) ?: error("offline") }
    val detail = (load.state as? Load.Ready)?.value
    val playlist = detail?.playlist
    val own = playlist != null && library?.isOwn(playlist) == true
    val collectable = playlist != null && library?.canCollect(playlist) == true
    val openSheet = LocalOpenSheet.current
    val scope = rememberCoroutineScope()
    val songs = load.state.map { it.songs }
    val title = playlist?.name ?: name
    SongListScreen(
        title = title,
        subtitle = playlist?.let { p ->
            listOfNotNull(p.creatorName?.takeIf { p.specialType != 5 }, p.description?.lineSequence()?.firstOrNull()).joinToString(" · ").ifEmpty { null }
        },
        header = ListHeader.Cover(playlist?.coverUrl, (id % 97).toInt()),
        songs = songs,
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, title, sourceId = id) },
        onRetry = load.retry,
        likedIds = liked,
        songContext = if (own) SongContext.OwnPlaylist(id, title) else SongContext.None,
        topActions = {
            if (own) IconAction(LineIcon.More, "歌单操作", { openSheet(Sheet.PlaylistActions(playlist!!)) })
        },
        actions = {
            if (collectable) {
                val subscribed = playlist!!.subscribed
                ThemedChip(if (subscribed) "已收藏" else "收藏", subscribed, {
                    scope.launch {
                        val error = library!!.setSubscribed(playlist, !subscribed)
                        services.notices.show(error ?: if (subscribed) "已取消收藏" else "已收藏到「我的 · 收藏的歌单」")
                    }
                })
            }
            DownloadAll(songs, title)
        },
    )
}

/** Today's picks. */
@Composable
fun DailyPage(player: PlayerState, onBack: () -> Unit) {
    val library = LocalAppServices.current?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val today = LocalDate.now()
    val load = rememberLoad(today) { library?.dailySongs() ?: error("offline") }
    SongListScreen(
        title = "每日推荐",
        subtitle = "根据你的口味生成，每天 6:00 更新",
        header = ListHeader.Day(today.dayOfMonth, "${today.monthValue}月 · ${today.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.CHINA)}"),
        songs = load.state,
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { songs, i -> player.play(songs, i, "每日推荐") },
        onRetry = load.retry,
        likedIds = liked,
        actions = { DownloadAll(load.state, "每日推荐") },
    )
}

/** The official charts as tiles. */
@Composable
fun ChartsPage(onBack: () -> Unit, onOpen: (Playlist) -> Unit) {
    val style = Lanyin.style
    val m = style.margin
    val library = LocalAppServices.current?.library
    val load = rememberLoad("charts") { library?.charts() ?: error("offline") }
    val bottom = LocalBottomChromePadding.current
    CompositionLocalProvider(LocalContentColor provides style.colors.content) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = m, end = m, top = io.lanyin.player.ui.components.pagePadding(extraTop = 4.dp).calculateTopPadding(), bottom = bottom + 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(LineIcon.Back, "返回", onBack, Modifier.padding(end = 4.dp))
                    PageTitle("排行榜")
                }
            }
            when (val s = load.state) {
                Load.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { Placeholder("正在加载…") }
                is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { Placeholder(s.message, "重试", load.retry) }
                is Load.Ready -> items(s.value, key = { it.id }) { chart -> PlaylistTile(chart, { onOpen(chart) }) }
            }
        }
    }
}

/** An artist's best-known songs. */
@Composable
fun ArtistPage(id: Long, name: String, player: PlayerState, onBack: () -> Unit) {
    val library = LocalAppServices.current?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val load = rememberLoad("artist-$id") { library?.artist(id) ?: error("offline") }
    val artist = (load.state as? Load.Ready)?.value
    val songs = load.state.map { it.hotSongs }
    SongListScreen(
        title = artist?.name ?: name,
        subtitle = artist?.let { a -> listOfNotNull("${a.songCount} 首歌 · ${a.albumCount} 张专辑", a.brief?.lineSequence()?.firstOrNull()).joinToString("\n") },
        header = ListHeader.Cover(artist?.pictureUrl, (id % 97).toInt(), round = true),
        songs = songs,
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, "${artist?.name ?: name} · 热门") },
        onRetry = load.retry,
        likedIds = liked,
        actions = { DownloadAll(songs, "${artist?.name ?: name} · 热门") },
    )
}

/** An album, track by track. */
@Composable
fun AlbumPage(id: Long, name: String, player: PlayerState, onBack: () -> Unit) {
    val library = LocalAppServices.current?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val load = rememberLoad("album-$id") { library?.album(id) ?: error("offline") }
    val album = (load.state as? Load.Ready)?.value
    val songs = load.state.map { it.songs }
    SongListScreen(
        title = album?.name ?: name,
        subtitle = album?.let { a ->
            val year = a.publishTime.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).year }
            listOfNotNull(a.artistName.ifEmpty { null }, year?.let { "$it 年" }).joinToString(" · ")
        },
        header = ListHeader.Cover(album?.coverUrl, (id % 97).toInt()),
        songs = songs,
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, album?.name ?: name, sourceId = id) },
        onRetry = load.retry,
        likedIds = liked,
        actions = { DownloadAll(songs, album?.name ?: name) },
    )
}

/**
 * What was played lately: on this phone, or (signed in) on the account across its devices,
 * which includes what 澜音 reported.
 */
@Composable
fun RecentPage(
    player: PlayerState,
    onBack: () -> Unit,
    preview: List<Song>? = null,
    previewAccount: List<RecentPlay>? = null,
    now: Long = System.currentTimeMillis(),
) {
    val services = LocalAppServices.current
    val songs by (services?.recent?.songs ?: remember { MutableStateFlow(preview.orEmpty()) }).collectAsState()
    val liked by (services?.library?.likedIds ?: noLikes).collectAsState()
    val signedIn = services?.library?.signedIn == true || previewAccount != null
    var tab by rememberSaveable { mutableStateOf(if (previewAccount != null) ACCOUNT else PHONE) }
    val accountLoader = if (tab == ACCOUNT && previewAccount == null) rememberLoad("recent-account") { services?.library?.recentPlays() ?: error("offline") } else null
    val account = when {
        tab != ACCOUNT -> Load.Ready(emptyList())
        previewAccount != null -> Load.Ready(previewAccount)
        else -> accountLoader!!.state
    }
    val playedAt = (account as? Load.Ready)?.value.orEmpty().associate { it.song.id to it.playedAt }
    val onPhone = tab == PHONE
    SongListScreen(
        title = "最近播放",
        subtitle = if (onPhone) "这台手机上最近听过的 ${songs.size} 首" else "网易云账号在各个设备上听过的歌",
        header = ListHeader.Glyph(LineIcon.History),
        songs = if (onPhone) Load.Ready(songs) else account.map { list -> list.map { it.song } },
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, "最近播放") },
        onRetry = { accountLoader?.retry?.invoke() },
        likedIds = liked,
        tabs = if (signedIn) ({ ChoiceRow(listOf(PHONE, ACCOUNT), tab, { tab = it }) }) else null,
        trailing = if (onPhone) null else ({ song -> playedAt[song.id]?.let { TrailingNote(relativeTime(it, now)) } }),
        emptyText = if (onPhone) "还没有在这台手机上听过歌" else "网易云上还没有播放记录",
    )
}

private const val PHONE = "本机"
private const val ACCOUNT = "网易云"

/** 听歌排行: the account's most played songs, this week or all time. */
@Composable
fun RankingPage(player: PlayerState, onBack: () -> Unit, preview: List<RankedSong>? = null) {
    val library = LocalAppServices.current?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    var week by rememberSaveable { mutableStateOf(true) }
    val loader = if (preview == null) rememberLoad("ranking-$week") { library?.ranking(week) ?: error("offline") } else null
    val load = preview?.let { Load.Ready(it) } ?: loader!!.state
    val counts = (load as? Load.Ready)?.value.orEmpty().associate { it.song.id to it.playCount }
    SongListScreen(
        title = "听歌排行",
        subtitle = "网易云账号里听得最多的歌，在澜音听的也算",
        header = ListHeader.Glyph(LineIcon.Chart),
        songs = load.map { list -> list.map { it.song } },
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, if (week) "听歌排行 · 最近一周" else "听歌排行 · 所有时间") },
        onRetry = { loader?.retry?.invoke() },
        likedIds = liked,
        tabs = { ChoiceRow(listOf("最近一周", "所有时间"), if (week) "最近一周" else "所有时间", { week = it == "最近一周" }) },
        trailing = { song -> counts[song.id]?.let { TrailingNote("$it 次") } },
        emptyText = if (week) "这一周还没有听歌记录" else "还没有听歌记录",
    )
}

/** The listener's cloud drive, a page at a time; how full it is up top. */
@Composable
fun CloudPage(
    player: PlayerState,
    onBack: () -> Unit,
    preview: CloudPageData? = null,
    previewUploads: List<io.lanyin.player.data.library.UploadJob> = emptyList(),
) {
    val services = LocalAppServices.current
    val library = services?.library
    val uploads = services?.uploads
    val jobs by (uploads?.jobs ?: remember { MutableStateFlow(previewUploads) }).collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uploads == null) return@rememberLauncherForActivityResult
        if (library?.signedIn != true) {
            services.notices.show("登录后才能上传到云盘")
            return@rememberLauncherForActivityResult
        }
        uris.forEach { uri ->
            val (name, size) = context.describeDocument(uri)
            uploads.add(name, size) { context.uploadFile(uri, name) }
        }
    }
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val revision by (library?.revision ?: noRevision).collectAsState()
    var songs by remember { mutableStateOf(preview?.songs.orEmpty()) }
    var info by remember { mutableStateOf(preview) }
    var state by remember { mutableStateOf<Load<Unit>>(if (preview != null) Load.Ready(Unit) else Load.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    // Set when a further page failed: stop asking (no request loop while offline) until 重试.
    var moreFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    suspend fun fetch(offset: Int): Boolean = try {
        val page = library?.cloud(offset) ?: error("offline")
        info = page
        songs = if (offset == 0) page.songs else (songs + page.songs).distinctBy { it.song.id }
        state = Load.Ready(Unit)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (offset == 0) state = Load.Failed(describe(e))
        false
    }

    LaunchedEffect(attempt, revision) {
        if (preview == null) fetch(0)
    }
    val scope = rememberCoroutineScope()
    val used = info?.usedBytes ?: 0
    val max = info?.maxBytes ?: 0
    SongListScreen(
        title = "音乐云盘",
        subtitle = info?.let { "已用 ${byteSize(used)} / ${byteSize(max)} · 共 ${it.count} 首" },
        header = ListHeader.Glyph(LineIcon.Cloud, fill = if (max > 0) used.toFloat() / max else null),
        songs = state.map { songs.map(CloudSong::song) },
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, "音乐云盘") },
        onRetry = { moreFailed = false; attempt++ },
        likedIds = liked,
        songContext = SongContext.Cloud,
        actions = {
            ThemedChip("上传", false, { pick.launch(arrayOf("audio/*")) })
        },
        tabs = if (jobs.isEmpty()) null else ({
            UploadPanel(jobs, onCancel = { uploads?.cancel(it) }, onClearFinished = { uploads?.clearFinished() })
        }),
        emptyText = "云盘里还没有歌。点「上传」把手机里的歌放上来",
        countText = { list -> if (info?.hasMore == true) "已加载 ${list.size} 首" else "${list.size} 首" },
        loadingMore = loadingMore,
        onEndReached = if (info?.hasMore == true && !loadingMore && !moreFailed) ({
            loadingMore = true
            scope.launch {
                if (!fetch(songs.size)) moreFailed = true
                loadingMore = false
            }
        }) else null,
        moreFailed = moreFailed,
        onRetryMore = { moreFailed = false },
    )
}

/** Songs on the phone, and those on their way. */
@Composable
fun DownloadsPage(
    player: PlayerState,
    onBack: () -> Unit,
    preview: List<DownloadItem>? = null,
    previewWaitingForWifi: Boolean = false,
) {
    val services = LocalAppServices.current
    val items by (services?.downloads?.items ?: remember { MutableStateFlow(preview.orEmpty()) }).collectAsState()
    val waiting by (services?.downloads?.waitingForWifi ?: remember { MutableStateFlow(previewWaitingForWifi) }).collectAsState()
    val liked by (services?.library?.likedIds ?: noLikes).collectAsState()
    val done = items.filter { it.state == DownloadState.Done }
    val active = items.size - done.size
    val byId = items.associateBy { it.song.id }
    SongListScreen(
        title = "下载",
        subtitle = listOfNotNull(
            "已下载 ${done.size} 首 · ${byteSize(done.sumOf { it.bytes })}",
            if (active > 0) (if (waiting) "$active 首等待 Wi-Fi" else "正在下载 $active 首") else null,
        ).joinToString("\n"),
        header = ListHeader.Glyph(LineIcon.Download),
        songs = Load.Ready(items.map { it.song }),
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { list, i -> player.play(list, i, "下载") },
        onRetry = {},
        likedIds = liked,
        downloadedIds = done.mapTo(HashSet()) { it.song.id },
        trailing = { song ->
            byId[song.id]?.takeIf { it.state != DownloadState.Done }?.let { item ->
                TrailingNote(item.label(waiting), accent = item.state == DownloadState.Downloading, warn = item.state == DownloadState.Failed)
            }
        },
        emptyText = "还没有下载的歌。在歌的 ⋯ 里点「下载」，或在歌单里下载全部",
    )
}

/** A short note at a row's end: 「12 次」「昨天」「42%」. */
@Composable
private fun TrailingNote(text: String, accent: Boolean = false, warn: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    Text(
        text,
        style = style.type.caption.copy(fontFamily = style.numeric, fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal),
        color = when {
            warn -> c.like
            accent -> c.accent
            else -> c.muted
        },
        maxLines = 1,
    )
}
