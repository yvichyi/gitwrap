package io.lanyin.player.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import io.lanyin.player.ui.LocalAppServices
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.ThemeMotif
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** What heads a song list: a cover, for 每日推荐 the day itself, or an icon for the listener's own collections. */
sealed interface ListHeader {
    data class Cover(val url: String?, val seed: Int, val round: Boolean = false) : ListHeader
    data class Day(val day: Int, val caption: String) : ListHeader
    /** [fill], 0..1, draws a gauge under the icon (how full the cloud drive is). */
    data class Glyph(val icon: LineIcon, val fill: Float? = null) : ListHeader
}

/**
 * A list of songs under a header: the playlist, today's picks, search results. Tap a song to
 * play the list from there; the one playing is marked with moving bars.
 */
@Composable
fun SongListScreen(
    title: String,
    subtitle: String?,
    header: ListHeader,
    songs: Load<List<Song>>,
    playingId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRetry: () -> Unit,
    likedIds: Set<Long> = emptySet(),
    /** Where the rows' ⋯ menus are opened from. */
    songContext: SongContext = SongContext.None,
    /** Marks songs kept on the phone; read from the app when null. */
    downloadedIds: Set<Long>? = null,
    /** Beside the back button, at the far end: a playlist's ⋯. */
    topActions: @Composable RowScope.() -> Unit = {},
    /** After 播放全部: 收藏, 下载. */
    actions: @Composable RowScope.() -> Unit = {},
    /** Between the header and the list: a choice of what to list, or what is under way. */
    tabs: (@Composable () -> Unit)? = null,
    /** Extra detail at a row's end: play counts, download progress. */
    trailing: (@Composable (Song) -> Unit)? = null,
    emptyText: String = "这里还没有歌",
    /** Called when the last row comes into view, to fetch the next page. */
    onEndReached: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    /** The last further page failed; shows a 重试 row at the end instead of asking again. */
    moreFailed: Boolean = false,
    onRetryMore: () -> Unit = {},
    countText: (List<Song>) -> String = { "${it.size} 首" },
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    val openSheet = LocalOpenSheet.current
    val services = LocalAppServices.current
    val liveDownloaded = services?.downloads?.done?.collectAsState()?.value
    val downloaded = downloadedIds ?: liveDownloaded ?: emptySet()
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(extraTop = 4.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = m - 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(LineIcon.Back, "返回", onBack)
                    Spacer(Modifier.weight(1f))
                    topActions()
                }
            }
            item { ListHero(title, subtitle, header, Modifier.padding(start = m, end = m, top = 8.dp)) }
            if (tabs != null) item { Box(Modifier.padding(start = m, end = m, top = 20.dp)) { tabs() } }
            when (songs) {
                Load.Loading -> item { Placeholder("正在加载…") }
                is Load.Failed -> item { Placeholder(songs.message, actionLabel = "重试", onAction = onRetry) }
                is Load.Ready -> {
                    val list = songs.value
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = m, end = m, top = 22.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThemedButton("播放全部", { onPlay(list, 0) }, enabled = list.isNotEmpty())
                            Spacer(Modifier.width(14.dp))
                            Text(countText(list), style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            actions()
                        }
                        if (style.edge == EdgeKind.Hairline) Hairline(Modifier.padding(horizontal = m))
                    }
                    if (list.isEmpty()) item { Placeholder(emptyText) }
                    itemsIndexed(list, key = { i, s -> "${s.id}-$i" }) { i, song ->
                        SongRow(
                            song = song,
                            number = i + 1,
                            playing = song.id == playingId,
                            isPlaying = isPlaying,
                            liked = song.id in likedIds,
                            downloaded = song.id in downloaded,
                            onClick = { onPlay(list, i) },
                            onMore = { openSheet(Sheet.TrackActions(song, songContext)) },
                            trailing = trailing?.let { t -> { t(song) } },
                            modifier = Modifier.padding(start = m, end = m - 10.dp),
                        )
                    }
                    if (list.isNotEmpty() && onEndReached == null && !loadingMore && !moreFailed) {
                        item(key = "end") { io.lanyin.player.ui.design.ListEnd(list.size) }
                    }
                    if (onEndReached != null || loadingMore || moreFailed) {
                        item(key = "more-${list.size}") {
                            if (onEndReached != null) LaunchedEffect(list.size) { onEndReached() }
                            Text(
                                when {
                                    loadingMore -> "正在加载更多…"
                                    moreFailed -> "没能加载更多，点一下重试"
                                    else -> ""
                                },
                                style = style.type.caption, color = if (moreFailed) c.content else c.muted, textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (moreFailed) Modifier.pressable(onClick = onRetryMore) else Modifier)
                                    .padding(vertical = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A list's art at [size]: its cover, its glyph on a tile, or the day. */
@Composable
internal fun HeaderArt(header: ListHeader, size: Dp = 112.dp) {
    val style = Lanyin.style
    val c = style.colors
    when (header) {
        is ListHeader.Cover -> CoverImage(header.url, header.seed, size, Modifier.size(size).clip(if (header.round) style.roundShape else style.artShape))
        is ListHeader.Glyph -> Box(
            Modifier
                .size(size)
                .then(if (style.edge == EdgeKind.Hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Card)),
            contentAlignment = Alignment.Center,
        ) {
            ThemedIcon(header.icon, null, tint = c.accent, size = size * 0.4f)
            header.fill?.let { f ->
                // How full: a short gauge under the icon.
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.16f).size(size * 0.57f, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.content.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.02f, 1f)).background(c.accent))
                }
            }
        }
        is ListHeader.Day -> Column(
            Modifier
                .size(size)
                .then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Card)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${header.day}",
                style = style.type.display.copy(fontFamily = style.display, fontSize = 48.sp * (size / 112.dp), lineHeight = 52.sp * (size / 112.dp)),
                color = c.accent,
            )
            Text(header.caption, style = style.type.caption, color = c.muted, maxLines = 1)
        }
    }
}

/** One song: its number (or bars while it plays), title and alias, artists and album, and its ⋯. */
@Composable
fun SongRow(
    song: Song,
    number: Int,
    playing: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    downloaded: Boolean = false,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).pressable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                PlayingBars(isPlaying)
            } else {
                RowNumber(number)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append(song.name)
                    song.alias?.let { withStyle(SpanStyle(color = c.muted)) { append("（$it）") } }
                },
                style = style.type.body.copy(fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal),
                color = if (playing) c.accent else c.content,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) ThemedIcon(LineIcon.Download, "已下载", tint = c.accent, size = 13.dp, modifier = Modifier.padding(end = 4.dp))
                if (song.fee == 1) {
                    Text(
                        "VIP",
                        style = style.type.label.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = c.accent,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    listOf(song.artistLine, song.album.name).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) Box(Modifier.padding(start = 10.dp)) { trailing() }
        if (liked) ThemedIcon(LineIcon.HeartFilled, "已收藏", tint = c.like, size = 16.dp, modifier = Modifier.padding(start = 10.dp))
        if (onMore != null) {
            Box(Modifier.size(width = 40.dp, height = 48.dp).pressable(onClick = onMore), contentAlignment = Alignment.Center) {
                ThemedIcon(LineIcon.More, "更多", tint = c.muted, size = 20.dp)
            }
        }
    }
}

/** Three bars bouncing while the song plays, still when paused, drawn in the theme's hand. */
@Composable
private fun PlayingBars(isPlaying: Boolean) {
    val style = Lanyin.style
    val clock = rememberFrameClock(isPlaying)
    Canvas(Modifier.size(16.dp)) { drawPlayingBars(clock.floatValue, isPlaying, style.backdrop, style.colors.accent) }
}

/** A calm line in place of content: loading, empty, or what went wrong. */
@Composable
fun Placeholder(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        io.lanyin.player.ui.design.StatusMark(io.lanyin.player.ui.design.statusKindOf(text, actionLabel != null))
        Text(text, style = style.type.caption, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp))
        if (actionLabel != null) TextLink(actionLabel, onAction, Modifier.padding(top = 4.dp))
    }
}

/** A playlist on a shelf: cover, name, track count. */
@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(playlist.coverUrl, (playlist.id % 97).toInt(), 56.dp, Modifier.size(56.dp).clip(style.artShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = style.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("${playlist.trackCount} 首", playlist.creatorName?.takeIf { playlist.subscribed }?.let { "by $it" }).joinToString(" · "),
                style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp)
    }
}

/** A playlist as a tile for a horizontal shelf. */
@Composable
fun PlaylistTile(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    Column(modifier.pressable(onClick = onClick)) {
        CoverImage(playlist.coverUrl, (playlist.id % 97).toInt(), 180.dp, Modifier.fillMaxWidth().aspectRatio(1f).clip(style.artShape))
        Text(
            playlist.name,
            style = style.type.caption.copy(color = style.colors.content),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
