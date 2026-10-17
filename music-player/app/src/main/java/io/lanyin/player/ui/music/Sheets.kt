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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.playback.DownloadItem
import io.lanyin.player.playback.DownloadState
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.ThemedSwitch
import io.lanyin.player.ui.design.ThemedTextField
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import androidx.compose.ui.graphics.drawscope.inset
import io.lanyin.player.ui.design.drawAirmailStripes
import io.lanyin.player.ui.design.drawTubeLine

/** Where a song's ⋯ was opened from, for the actions that only make sense there. */
@Immutable
sealed interface SongContext {
    data object None : SongContext
    /** A playlist of the listener's own: the song can be taken out of it. */
    data class OwnPlaylist(val id: Long, val name: String) : SongContext
    /** The cloud drive: the upload can be deleted. */
    data object Cloud : SongContext
}

/** A panel that slides up over everything. */
@Immutable
sealed interface Sheet {
    data object Queue : Sheet
    data class TrackActions(val song: Song, val context: SongContext = SongContext.None) : Sheet
    data class AddToPlaylist(val songs: List<Song>) : Sheet
    /** Name a new playlist; [songs] go into it once made. */
    data class NewPlaylist(val songs: List<Song> = emptyList()) : Sheet
    data class RenamePlaylist(val playlist: Playlist) : Sheet
    data class PlaylistActions(val playlist: Playlist) : Sheet
    data class ConfirmDownload(val songs: List<Song>, val title: String) : Sheet
    data object Effects : Sheet
    data object Quality : Sheet
    /** A line of the song playing, to share as a card. */
    data class ShareLyric(val line: String, val translation: String? = null) : Sheet
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
            BoxWithConstraints(Modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.72f)
                        // An opaque base under the theme's surface: the player must not show through a list.
                        .background(c.bg, shape)
                        .then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(shape = shape, tone = SurfaceTone.Chrome))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .padding(top = 10.dp, bottom = 8.dp),
                ) {
                    if (style.edge == EdgeKind.Hairline) Hairline()
                    SheetGrip(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                    CompositionLocalProvider(LocalContentColor provides c.content) {
                        state.shown?.let { content(it) }
                    }
                }
            }
        }
    }
}

/**
 * The handle a sheet is pulled by, in the theme's hand: a stroke of the brush (墨), a short lit
 * tube (霓), airmail stripes (暮), a black bar (格), sprocket holes (影), a row of dots (点), the
 * ridges of a grip (机), a perforation to tear along (报), pixels (游), a capsule of gel (酸).
 */
@Composable
private fun SheetGrip(modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    androidx.compose.foundation.Canvas(modifier.size(120.dp, 8.dp)) {
        val mid = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val faint = c.content.copy(alpha = 0.22f)
        when (style.backdrop) {
            BackdropKind.Ink -> drawPath(io.lanyin.player.ui.design.brushStrokePath(androidx.compose.ui.geometry.Offset(mid.x - 24.dp.toPx(), mid.y), 48.dp.toPx(), 4.dp.toPx()), c.content.copy(alpha = 0.45f))
            BackdropKind.Neon -> drawTubeLine(mid - androidx.compose.ui.geometry.Offset(18.dp.toPx(), 0f), mid + androidx.compose.ui.geometry.Offset(18.dp.toPx(), 0f), c.accent, 2.5.dp.toPx())
            BackdropKind.Dusk -> inset(size.width / 2f - 26.dp.toPx(), 1.5.dp.toPx()) { drawAirmailStripes(6.dp.toPx()) }
            BackdropKind.Grid -> drawRect(c.outline, mid - androidx.compose.ui.geometry.Offset(22.dp.toPx(), 3.dp.toPx()), androidx.compose.ui.geometry.Size(44.dp.toPx(), 6.dp.toPx()))
            BackdropKind.Film -> for (k in -2..2) drawRoundRect(faint, mid + androidx.compose.ui.geometry.Offset(k * 11.dp.toPx() - 3.dp.toPx(), -2.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            BackdropKind.Dot -> for (k in -3..3) drawCircle(if (k == 0) c.accentAlt else faint, 2.dp.toPx(), mid + androidx.compose.ui.geometry.Offset(k * 7.dp.toPx(), 0f))
            BackdropKind.Device -> for (k in -1..1) drawLine(faint, mid + androidx.compose.ui.geometry.Offset(-16.dp.toPx(), k * 2.5.dp.toPx()), mid + androidx.compose.ui.geometry.Offset(16.dp.toPx(), k * 2.5.dp.toPx()), 1.dp.toPx())
            BackdropKind.Paper -> drawLine(c.content.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(0f, mid.y), androidx.compose.ui.geometry.Offset(size.width, mid.y), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            BackdropKind.Game -> for (k in -1..1) drawRect(c.content.copy(alpha = 0.5f), mid + androidx.compose.ui.geometry.Offset(k * 8.dp.toPx() - 2.5.dp.toPx(), -2.5.dp.toPx()), androidx.compose.ui.geometry.Size(5.dp.toPx(), 5.dp.toPx()))
            BackdropKind.Acid -> {
                val top = mid - androidx.compose.ui.geometry.Offset(20.dp.toPx(), 3.dp.toPx())
                val sz = androidx.compose.ui.geometry.Size(40.dp.toPx(), 6.dp.toPx())
                drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFE2FF6B), c.accent, androidx.compose.ui.graphics.Color(0xFF8FC400)), top.y, top.y + sz.height), top, sz, androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f), top + androidx.compose.ui.geometry.Offset(3.dp.toPx(), 1.dp.toPx()), androidx.compose.ui.geometry.Size(sz.width - 6.dp.toPx(), 1.5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}

/** A sheet's heading, with an optional note after it. */
@Composable
private fun SheetTitle(title: String, note: String? = null, trailing: @Composable () -> Unit = {}) {
    val style = Lanyin.style
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (note != null) {
            Spacer(Modifier.width(10.dp))
            Text(note, style = style.type.caption, color = style.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing()
    }
}

/** What plays next; tap one to jump to it. */
@Composable
fun QueueSheet(player: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (player.index - 2).coerceAtLeast(0))
    SheetTitle("播放队列", "${player.trackCount} 首 · ${player.playlistName}") {
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
                // The number in the theme's hand, or the bars while it plays.
                Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                    if (current) {
                        val clock = io.lanyin.player.ui.design.rememberFrameClock(player.isPlaying)
                        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) { drawPlayingBars(clock.floatValue, player.isPlaying, style.backdrop, c.accent) }
                    } else {
                        RowNumber(i + 1)
                    }
                }
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
            }
        }
    }
}

/**
 * A song's ⋯ menu: like it, put it in a playlist, download it, read its comments, go to its
 * artists or album; and, depending on where it was opened, take it out of a playlist or the cloud.
 */
@Composable
fun TrackActionsSheet(
    song: Song,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    context: SongContext = SongContext.None,
    download: DownloadItem? = null,
    onAddToPlaylist: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onComments: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onDeleteFromCloud: () -> Unit = {},
    /** Only for the song playing, with a line being sung. */
    onShareLyric: (() -> Unit)? = null,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(song.album.coverUrl, (song.id % 97).toInt(), 48.dp, Modifier.size(48.dp).clip(style.artShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Hairline(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ActionRow(if (liked) LineIcon.HeartFilled else LineIcon.Heart, if (liked) "从「我喜欢的音乐」移除" else "收藏到「我喜欢的音乐」", onToggleLike, tint = if (liked) c.like else c.content)
        ActionRow(LineIcon.Plus, "添加到歌单", onAddToPlaylist)
        when (download?.state) {
            null -> ActionRow(LineIcon.Download, "下载", onDownload)
            DownloadState.Failed -> ActionRow(LineIcon.Download, "下载失败，再试一次", onDownload)
            DownloadState.Queued, DownloadState.Downloading -> ConfirmRow(
                LineIcon.Close,
                if (download.state == DownloadState.Queued) "等待下载 · 取消" else "下载中 ${(download.progress * 100).toInt()}% · 取消",
                "再点一次，取消下载", onRemoveDownload,
            )
            DownloadState.Done -> ConfirmRow(LineIcon.Check, "已下载 · 删除下载", "再点一次，删除下载的文件", onRemoveDownload, tint = c.accent)
        }
        ActionRow(LineIcon.Comment, "评论", onComments)
        if (onShareLyric != null) ActionRow(LineIcon.Share, "分享这句歌词", onShareLyric)
        song.artists.filter { it.id != 0L }.take(4).forEach { a ->
            ActionRow(LineIcon.User, "歌手：${a.name}", { onOpenArtist(a) })
        }
        if (song.album.id != 0L) ActionRow(LineIcon.Library, "专辑：${song.album.name}", { onOpenAlbum(song.album.id, song.album.name) })
        when (context) {
            is SongContext.OwnPlaylist -> ConfirmRow(LineIcon.Trash, "从「${context.name}」中删除", "再点一次，从歌单删除", onRemoveFromPlaylist)
            SongContext.Cloud -> ConfirmRow(LineIcon.Trash, "从云盘删除", "再点一次，从云盘删除这个文件", onDeleteFromCloud)
            SongContext.None -> Unit
        }
    }
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

/** An action that can't be undone: the first tap asks, the second does it. */
@Composable
private fun ConfirmRow(icon: LineIcon, label: String, confirmLabel: String, onConfirm: () -> Unit, tint: Color = Lanyin.colors.content) {
    var armed by remember { mutableStateOf(false) }
    val c = Lanyin.colors
    ActionRow(
        icon, if (armed) confirmLabel else label,
        onClick = { if (armed) onConfirm() else armed = true },
        tint = if (armed) c.like else tint,
    )
}

/** Pick one of the listener's playlists to put [count] songs in, or make a new one. */
@Composable
fun AddToPlaylistSheet(count: Int, playlists: List<Playlist>?, onPick: (Playlist) -> Unit, onNew: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    SheetTitle("添加到歌单", if (count > 1) "$count 首" else null)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().pressable(onClick = onNew).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).then(if (style.edge == EdgeKind.Hairline) Modifier.background(c.content.copy(alpha = 0.06f)) else Modifier.themedSurface(shape = style.artShape, tone = SurfaceTone.Flat)),
                    contentAlignment = Alignment.Center,
                ) { ThemedIcon(LineIcon.Plus, null, tint = c.accent, size = 22.dp) }
                Spacer(Modifier.width(14.dp))
                Text("新建歌单", style = style.type.body)
            }
        }
        when {
            playlists == null -> item { Text("正在同步你的歌单…", style = style.type.caption, color = c.muted, modifier = Modifier.padding(20.dp)) }
            playlists.isEmpty() -> item { Text("还没有自己建的歌单", style = style.type.caption, color = c.muted, modifier = Modifier.padding(20.dp)) }
            else -> items(playlists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().pressable { onPick(p) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(p.coverUrl, (p.id % 97).toInt(), 48.dp, Modifier.size(48.dp).clip(style.artShape))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = style.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackCount} 首", style = style.type.caption, color = c.muted)
                    }
                }
            }
        }
    }
}

/**
 * A name to type: a new playlist (with the choice to keep it private) or a new name for one.
 * [onConfirm] gets the name and whether it's private.
 */
@Composable
fun NameSheet(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String, Boolean) -> Unit,
    askPrivate: Boolean = false,
    busy: Boolean = false,
) {
    val style = Lanyin.style
    val c = style.colors
    var name by remember { mutableStateOf(initial) }
    var private by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val valid = name.isNotBlank() && name.length <= 40 && !busy
    SheetTitle(title)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        ThemedTextField(
            name, { name = it.take(40) }, "歌单名字",
            Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.trim(), private) }),
            onSurface = true,
        )
        if (askPrivate) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("隐私歌单", style = style.type.body)
                    Text("只有你自己看得到", style = style.type.caption, color = c.muted)
                }
                ThemedSwitch(private, { private = it })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${name.length} / 40", style = style.type.caption, color = c.muted, modifier = Modifier.weight(1f))
            ThemedButton(if (busy) "请稍候…" else confirmLabel, { onConfirm(name.trim(), private) }, enabled = valid)
        }
    }
}

/** The ⋯ of a playlist of one's own. */
@Composable
fun PlaylistActionsSheet(playlist: Playlist, onRename: () -> Unit, onDelete: () -> Unit) {
    SheetTitle(playlist.name, "${playlist.trackCount} 首")
    ActionRow(LineIcon.Edit, "改名", onRename)
    ConfirmRow(LineIcon.Trash, "删除歌单", "再点一次，删除「${playlist.name}」", onDelete)
}

/** Asks before downloading a whole list. */
@Composable
fun ConfirmDownloadSheet(title: String, count: Int, quality: String, wifiOnly: Boolean, onConfirm: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    SheetTitle("下载「$title」")
    Text(
        buildString {
            append("$count 首，按「$quality」音质下载，无损的歌每首约 30 MB。")
            if (wifiOnly) append("只在连着 Wi-Fi 时下载。")
            append("下载的歌只能在澜音里播放。")
        },
        style = style.type.body, color = c.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Spacer(Modifier.weight(1f))
        ThemedButton("下载 $count 首", onConfirm, enabled = count > 0)
    }
}
