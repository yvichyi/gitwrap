package io.lanyin.player.ui.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.LocalNavigator
import io.lanyin.player.ui.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val nothingDownloaded = MutableStateFlow<List<io.lanyin.player.playback.DownloadItem>>(emptyList())
private val noLikes = MutableStateFlow<Set<Long>>(emptySet())

/**
 * The app's sheets and what their buttons do: the library, downloads and navigation behind them.
 * Results come back as a notice over the bottom bar.
 */
@Composable
fun AppSheets(
    sheets: SheetState,
    player: PlayerState,
    quality: io.lanyin.player.core.model.Quality,
    wifiOnly: Boolean,
    effects: io.lanyin.player.core.audio.EffectsSettings = io.lanyin.player.core.audio.EffectsSettings(),
    onEffects: (io.lanyin.player.core.audio.EffectsSettings) -> Unit = {},
) {
    val services = LocalAppServices.current
    val library = services?.library
    val downloads = services?.downloads
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val liked by (library?.likedIds ?: noLikes).collectAsState()
    val downloadItems by (downloads?.items ?: nothingDownloaded).collectAsState()
    val playlists by (library?.playlists ?: remember { MutableStateFlow(null) }).collectAsState()
    val askForNotifications = rememberNotificationPermission()
    var busy by remember { mutableStateOf(false) }

    fun notify(text: String?) {
        if (text != null) services?.notices?.show(text)
    }

    fun download(songs: List<Song>) {
        askForNotifications()
        val queued = downloads?.download(songs) ?: 0
        notify(
            when {
                queued == 0 -> "已经在下载列表里了"
                wifiOnly && downloads?.wifiMissing() == true -> "已加入下载：$queued 首，连上 Wi-Fi 后开始"
                else -> "已加入下载：$queued 首"
            },
        )
    }

    SheetLayer(sheets) { sheet ->
        when (sheet) {
            Sheet.Queue -> QueueSheet(player)
            is Sheet.ShareLyric -> io.lanyin.player.ui.share.ShareLyricSheet(
                io.lanyin.player.ui.share.CardLyric(
                    sheet.line, sheet.translation, player.title, player.artist, player.track.album, player.coverUrl, player.coverSeed,
                ),
                onDone = sheets::dismiss,
                notify = ::notify,
            )
            is Sheet.TrackActions -> {
                val song = sheet.song
                TrackActionsSheet(
                    song,
                    liked = song.id in liked,
                    context = sheet.context,
                    download = downloadItems.firstOrNull { it.song.id == song.id },
                    onToggleLike = {
                        sheets.dismiss()
                        if (player.track.id == song.id) player.toggleLike() else scope.launch { notify(library?.toggleLike(song.id)) }
                    },
                    onAddToPlaylist = {
                        if (library?.signedIn == true) sheets.open(Sheet.AddToPlaylist(listOf(song))) else notify("登录后才能添加到歌单")
                    },
                    onDownload = {
                        sheets.dismiss()
                        download(listOf(song))
                    },
                    onRemoveDownload = {
                        sheets.dismiss()
                        downloads?.remove(song.id)
                        notify("已删除「${song.name}」的下载")
                    },
                    onComments = {
                        sheets.dismiss()
                        nav?.openComments?.invoke(song)
                    },
                    onShareLyric = player.currentLine?.text?.takeIf { player.track.id == song.id && it.isNotBlank() }?.let { line ->
                        { sheets.open(Sheet.ShareLyric(line, player.currentLine?.translation)) }
                    },
                    onOpenArtist = {
                        sheets.dismiss()
                        nav?.openArtist?.invoke(it.id, it.name)
                    },
                    onOpenAlbum = { id, name ->
                        sheets.dismiss()
                        nav?.openAlbum?.invoke(id, name)
                    },
                    onRemoveFromPlaylist = {
                        sheets.dismiss()
                        val ctx = sheet.context as? SongContext.OwnPlaylist ?: return@TrackActionsSheet
                        scope.launch { notify(library?.removeFromPlaylist(ctx.id, song.id) ?: "已从「${ctx.name}」删除") }
                    },
                    onDeleteFromCloud = {
                        sheets.dismiss()
                        scope.launch { notify(library?.deleteFromCloud(song.id) ?: "已从云盘删除「${song.name}」") }
                    },
                )
            }
            is Sheet.AddToPlaylist -> AddToPlaylistSheet(
                count = sheet.songs.size,
                playlists = playlists?.let { library?.created() },
                onPick = { p ->
                    sheets.dismiss()
                    scope.launch { notify(library?.addToPlaylist(p, sheet.songs) ?: "已添加到「${p.name}」") }
                },
                onNew = { sheets.open(Sheet.NewPlaylist(sheet.songs)) },
            )
            is Sheet.NewPlaylist -> NameSheet(
                "新建歌单", "", "创建",
                askPrivate = true,
                busy = busy,
                onConfirm = { name, private ->
                    val lib = library ?: return@NameSheet
                    busy = true
                    scope.launch {
                        lib.createPlaylist(name, private, sheet.songs).fold(
                            onSuccess = { notify(if (sheet.songs.isEmpty()) "已新建「${it.name}」" else "已新建「${it.name}」并添加 ${sheet.songs.size} 首") },
                            onFailure = { notify(it.message) },
                        )
                        busy = false
                        sheets.dismiss()
                    }
                },
            )
            is Sheet.RenamePlaylist -> NameSheet(
                "改名", sheet.playlist.name, "保存",
                busy = busy,
                onConfirm = { name, _ ->
                    val lib = library ?: return@NameSheet
                    busy = true
                    scope.launch {
                        notify(lib.renamePlaylist(sheet.playlist.id, name) ?: "已改名为「$name」")
                        busy = false
                        sheets.dismiss()
                    }
                },
            )
            is Sheet.PlaylistActions -> PlaylistActionsSheet(
                sheet.playlist,
                onRename = { sheets.open(Sheet.RenamePlaylist(sheet.playlist)) },
                onDelete = {
                    sheets.dismiss()
                    scope.launch {
                        val error = library?.deletePlaylist(sheet.playlist.id)
                        notify(error ?: "已删除「${sheet.playlist.name}」")
                        if (error == null) nav?.back?.invoke()
                    }
                },
            )
            Sheet.Effects -> {
                // Heard at once; written to settings when a gesture ends.
                var live by remember(effects) { mutableStateOf(effects) }
                io.lanyin.player.ui.effects.EffectsSheet(
                    live,
                    onChange = {
                        live = it
                        services?.engine?.effects?.settings = it
                    },
                    onDone = { onEffects(live) },
                )
            }
            Sheet.Quality -> io.lanyin.player.ui.effects.QualitySheet(
                quality, listOf(player.quality, player.format).filter { it.isNotEmpty() }.joinToString(" · "),
            ) { q ->
                sheets.dismiss()
                player.switchQuality(q)
                notify("换成「${q.label}」，从这里接着放")
            }
            is Sheet.ConfirmDownload -> ConfirmDownloadSheet(sheet.title, sheet.songs.size, quality.label, wifiOnly) {
                sheets.dismiss()
                download(sheet.songs)
            }
        }
    }
}

/** Asks once for leave to post notifications (Android 13+), so download progress can show. */
@Composable
private fun rememberNotificationPermission(): () -> Unit {
    if (Build.VERSION.SDK_INT < 33) return {}
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
