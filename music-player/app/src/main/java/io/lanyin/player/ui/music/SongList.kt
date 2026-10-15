package io.lanyin.player.ui.music

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlin.math.sin

/** What heads a song list: a cover, or for 每日推荐 the day itself. */
sealed interface ListHeader {
    data class Cover(val url: String?, val seed: Int, val round: Boolean = false) : ListHeader
    data class Day(val day: Int, val caption: String) : ListHeader
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
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(extraTop = 4.dp)) {
            item {
                Row(Modifier.padding(horizontal = m - 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(LineIcon.Back, "返回", onBack)
                }
            }
            item {
                Row(Modifier.padding(start = m, end = m, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderArt(header)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = style.type.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
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
                            Text("${list.size} 首", style = style.type.caption, color = c.muted)
                        }
                        if (style.edge == EdgeKind.Hairline) Hairline(Modifier.padding(horizontal = m))
                    }
                    if (list.isEmpty()) item { Placeholder("这里还没有歌") }
                    itemsIndexed(list, key = { i, s -> "${s.id}-$i" }) { i, song ->
                        SongRow(
                            song = song,
                            number = i + 1,
                            playing = song.id == playingId,
                            isPlaying = isPlaying,
                            liked = song.id in likedIds,
                            onClick = { onPlay(list, i) },
                            modifier = Modifier.padding(horizontal = m),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderArt(header: ListHeader) {
    val style = Lanyin.style
    val c = style.colors
    when (header) {
        is ListHeader.Cover -> CoverImage(header.url, header.seed, 112.dp, Modifier.size(112.dp).clip(if (header.round) style.roundShape else style.artShape))
        is ListHeader.Day -> Column(
            Modifier
                .size(112.dp)
                .then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Card)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${header.day}",
                style = style.type.display.copy(fontFamily = style.display, fontSize = 48.sp, lineHeight = 52.sp),
                color = c.accent,
            )
            Text(header.caption, style = style.type.caption, color = c.muted, maxLines = 1)
        }
    }
}

/** One song: its number (or bars while it plays), title and alias, artists and album. */
@Composable
fun SongRow(
    song: Song,
    number: Int,
    playing: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
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
                Text("$number", style = style.type.caption.copy(fontFamily = style.numeric), color = c.muted, textAlign = TextAlign.Start)
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
        if (liked) ThemedIcon(LineIcon.HeartFilled, "已收藏", tint = c.like, size = 16.dp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Three bars bouncing while the song plays, still when paused. */
@Composable
private fun PlayingBars(isPlaying: Boolean) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(isPlaying)
    Canvas(Modifier.size(16.dp)) {
        val w = size.width / 5f
        for (i in 0 until 3) {
            val h = if (isPlaying) 0.35f + 0.65f * ((sin(clock.floatValue * (5f + i * 1.7f) + i * 1.3f) + 1f) / 2f) else 0.35f + i * 0.15f
            val x = w / 2 + i * w * 2
            drawLine(c.accent, Offset(x, size.height), Offset(x, size.height * (1f - h)), w, StrokeCap.Round)
        }
    }
}

/** A calm line in place of content: loading, empty, or what went wrong. */
@Composable
fun Placeholder(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ThemeMotif(c.content.copy(alpha = 0.18f), Modifier.size(56.dp))
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
