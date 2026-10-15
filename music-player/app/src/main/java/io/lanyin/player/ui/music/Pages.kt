package io.lanyin.player.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.LocalBottomChromePadding
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.Lanyin
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

private val noLikes = MutableStateFlow<Set<Long>>(emptySet())

fun <T, R> Load<T>.map(f: (T) -> R): Load<R> = when (this) {
    Load.Loading -> Load.Loading
    is Load.Failed -> this
    is Load.Ready -> Load.Ready(f(value))
}

/** A playlist, fetched whole. */
@Composable
fun PlaylistPage(id: Long, name: String, player: PlayerState, onBack: () -> Unit) {
    val library = LocalAppServices.current?.library
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val load = rememberLoad(id) { library?.playlist(id) ?: error("offline") }
    val detail = (load.state as? Load.Ready)?.value
    val playlist = detail?.playlist
    SongListScreen(
        title = playlist?.name ?: name,
        subtitle = playlist?.let { p ->
            listOfNotNull(p.creatorName?.takeIf { p.specialType != 5 }, p.description?.lineSequence()?.firstOrNull()).joinToString(" · ").ifEmpty { null }
        },
        header = ListHeader.Cover(playlist?.coverUrl, (id % 97).toInt()),
        songs = load.state.map { it.songs },
        playingId = player.playingSongId,
        isPlaying = player.isPlaying,
        onBack = onBack,
        onPlay = { songs, i -> player.play(songs, i, playlist?.name ?: name) },
        onRetry = load.retry,
        likedIds = liked,
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
