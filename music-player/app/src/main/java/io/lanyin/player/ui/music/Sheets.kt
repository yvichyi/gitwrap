package io.lanyin.player.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.Artist
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.player.Track
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** A panel that slides up over everything. */
@Immutable
sealed interface Sheet {
    data object Queue : Sheet
    data class TrackActions(val track: Track) : Sheet
}

/** Opens a sheet from anywhere below the app shell. */
val LocalOpenSheet = staticCompositionLocalOf<(Sheet) -> Unit> { {} }

/** Holds the open sheet; draw [SheetLayer] above the content. */
class SheetState {
    var current by mutableStateOf<Sheet?>(null)
        private set
    /** Kept while the panel animates out. */
    var shown by mutableStateOf<Sheet?>(null)
        private set

    fun open(sheet: Sheet) {
        shown = sheet
        current = sheet
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberSheetState() = remember { SheetState() }

/** The scrim and the sliding panel; [content] draws what [SheetState.shown] holds. */
@Composable
fun SheetLayer(state: SheetState, content: @Composable (Sheet) -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    BackHandler(enabled = state.current != null) { state.dismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.current != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (style.dark) 0.5f else 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { state.dismiss() },
            )
        }
        AnimatedVisibility(
            state.current != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(style.motion.spec()) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            val shape = when {
                style.hardEdge || style.edge == EdgeKind.Hairline -> RectangleShape
                else -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.72f)
                        // An opaque base under the theme's surface: the player must not show through a list.
                        .background(c.bg, shape)
                        .then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(shape = shape, tone = io.lanyin.player.ui.design.SurfaceTone.Chrome))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .padding(top = 10.dp, bottom = 8.dp),
                ) {
                    if (style.edge == EdgeKind.Hairline) Hairline()
                    // A grab handle.
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .size(36.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.content.copy(alpha = 0.18f)),
                    )
                    CompositionLocalProvider(LocalContentColor provides c.content) {
                        state.shown?.let { content(it) }
                    }
                }
            }
        }
    }
}

/** What plays next; tap one to jump to it. */
@Composable
fun QueueSheet(player: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (player.index - 2).coerceAtLeast(0))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("播放队列", style = style.type.title)
        Spacer(Modifier.width(10.dp))
        Text("${player.trackCount} 首 · ${player.playlistName}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            player.playMode.label,
            style = style.type.caption.copy(fontWeight = FontWeight.Medium),
            color = c.accent,
            modifier = Modifier.pressable(onClick = player::cyclePlayMode).padding(8.dp),
        )
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(player.queue, key = { i, t -> "${t.id}-$i" }) { i, t ->
            val current = i == player.index
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable { player.playAt(i) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.title,
                        style = style.type.body.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (current) c.accent else c.content,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(" · ${t.artist}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current) ThemedIcon(LineIcon.Note, "正在播放", tint = c.accent, size = 16.dp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** The song's ⋯ menu: like it, go to its artists or album. */
@Composable
fun TrackActionsSheet(
    track: Track,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(track.coverUrl, track.coverSeed, 48.dp, Modifier.size(48.dp).clip(style.artShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Hairline(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    ActionRow(if (liked) LineIcon.HeartFilled else LineIcon.Heart, if (liked) "从「我喜欢的音乐」移除" else "收藏到「我喜欢的音乐」", onToggleLike, tint = if (liked) c.like else c.content)
    track.artists.filter { it.id != 0L }.take(4).forEach { a ->
        ActionRow(LineIcon.User, "歌手：${a.name}", { onOpenArtist(a) })
    }
    if (track.albumId != 0L) ActionRow(LineIcon.Library, "专辑：${track.album}", { onOpenAlbum(track.albumId, track.album) })
}

@Composable
private fun ActionRow(icon: LineIcon, label: String, onClick: () -> Unit, tint: Color = Lanyin.colors.content) {
    val style = Lanyin.style
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedIcon(icon, null, tint = tint, size = 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = style.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
