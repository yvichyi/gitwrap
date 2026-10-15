package io.lanyin.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.lanyin.player.core.lyrics.LrcParser
import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.core.model.LyricsSource
import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.Stream
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.data.library.LibraryRepository
import io.lanyin.player.ui.player.PlayMode
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.player.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The player the app shows, backed by a Media3 controller connected to [PlaybackService]. The
 * queue lives in the service (so it survives the UI); this mirrors it into snapshot state,
 * fetches lyrics for whatever plays, and skips songs NetEase won't serve.
 */
@Stable
class MediaPlayerState(
    context: Context,
    private val api: NeteaseApi,
    private val resolver: StreamResolver,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val recent: io.lanyin.player.data.library.RecentRepository? = null,
) : PlayerState() {

    private val appContext = context.applicationContext
    private var controller: MediaController? = null

    override var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    override var index by mutableIntStateOf(0)
        private set
    override var playlistName by mutableStateOf("")
        private set
    override var skipDirection by mutableIntStateOf(0)
        private set
    override var isPlaying by mutableStateOf(false)
        private set
    override var positionMs by mutableLongStateOf(0L)
        private set
    override var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
        private set
    override var lyricsLoading by mutableStateOf(false)
        private set
    override var buffering by mutableStateOf(false)
        private set
    override var notice by mutableStateOf<String?>(null)
        private set
    override var playMode by mutableStateOf(PlayMode.REPEAT_ALL)
        private set

    private var playerDuration by mutableLongStateOf(0L)
    private var nextIndex by mutableIntStateOf(-1)
    private var streams by mutableStateOf<Map<Long, Stream>>(emptyMap())
    private var likedIds by mutableStateOf<Set<Long>>(emptySet())

    override val durationMs: Long get() = playerDuration.takeIf { it > 0 } ?: track.durationMs.coerceAtLeast(1)
    override val next: Track get() = queue.getOrNull(nextIndex) ?: super.next
    override val liked: Boolean get() = track.id in likedIds
    override val quality: String get() = streams[track.id]?.let(::qualityLabel) ?: ""
    override val format: String get() = streams[track.id]?.let(::formatLabel) ?: ""

    private var fm = false
    /** Direction of a skip we asked for, applied when the player reports the change. */
    private var pendingDirection = 0
    private var errorsInARow = 0
    private var lyricsJob: Job? = null
    private var lyricsFor = 0L
    private val lyricsCache = object : LinkedHashMap<Long, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<LyricLine>>) = size > 40
    }

    init {
        scope.launch { resolver.resolved.collect { streams = it } }
        scope.launch { library.likedIds.collect { likedIds = it } }
    }

    /** Connects to the service; call once. Picks up whatever is already playing. */
    fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            syncAll(c)
        }, ContextCompat.getMainExecutor(appContext))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                syncQueue(player)
            }
            isPlaying = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (player.duration > 0) playerDuration = player.duration
            positionMs = player.currentPosition
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val newIndex = c.currentMediaItemIndex
            skipDirection = when {
                pendingDirection != 0 -> pendingDirection
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || newIndex >= index -> 1
                else -> -1
            }
            pendingDirection = 0
            index = newIndex
            playerDuration = 0
            positionMs = 0
            loadLyrics(track)
            if (!isEmpty) recent?.record(track.toSong())
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) errorsInARow = 0
            if (fm && index >= queue.size - 2) topUpFm()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val cause = generateSequence(error as Throwable?) { it.cause }.firstOrNull { it is UnplayableException }
            notice = cause?.message?.let { "「${track.title}」$it，已跳过" } ?: "网络不太好，「${track.title}」没能播放"
            if (cause == null) resolver.invalidate(track.id)
            errorsInARow++
            if (errorsInARow < 3 && c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) = syncMode()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncMode()
    }

    private fun syncAll(c: MediaController) {
        syncQueue(c)
        syncMode()
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        if (c.duration > 0) playerDuration = c.duration
        if (!isEmpty) loadLyrics(track)
        playlistName = c.playlistMetadata.station?.toString().orEmpty().ifEmpty { playlistName }
    }

    private fun syncQueue(p: Player) {
        queue = List(p.mediaItemCount) { p.getMediaItemAt(it).toTrack() }
        index = p.currentMediaItemIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        nextIndex = p.nextMediaItemIndex
    }

    private fun syncMode() {
        val c = controller ?: return
        playMode = when {
            c.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
            c.shuffleModeEnabled -> PlayMode.SHUFFLE
            else -> PlayMode.REPEAT_ALL
        }
        nextIndex = c.nextMediaItemIndex
    }

    // —— Controls ——

    override fun play(songs: List<Song>, startIndex: Int, source: String) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        fm = false
        start(c, songs, startIndex, source)
    }

    override fun playFm() {
        val c = controller ?: return
        scope.launch {
            val songs = runCatching { library.personalFm() }.getOrElse {
                notice = "私人 FM 没能打开：${it.message ?: "网络不好"}"
                return@launch
            }
            if (songs.isEmpty()) return@launch
            fm = true
            c.shuffleModeEnabled = false
            c.repeatMode = Player.REPEAT_MODE_OFF
            start(c, songs, 0, "私人 FM")
        }
    }

    private fun topUpFm() = scope.launch {
        val c = controller ?: return@launch
        val more = runCatching { library.personalFm() }.getOrNull().orEmpty().filter { s -> queue.none { it.id == s.id } }
        if (more.isNotEmpty()) c.addMediaItems(more.map { it.toMediaItem() })
    }

    private fun start(c: MediaController, songs: List<Song>, startIndex: Int, source: String) {
        playlistName = source
        errorsInARow = 0
        notice = null
        if (!fm && c.repeatMode == Player.REPEAT_MODE_OFF) c.repeatMode = Player.REPEAT_MODE_ALL
        c.setPlaylistMetadata(MediaMetadata.Builder().setStation(source).build())
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.size - 1), 0)
        c.prepare()
        c.play()
    }

    override fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    override fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms.coerceIn(0, durationMs))
        positionMs = ms
    }

    override fun skipNext() {
        val c = controller ?: return
        pendingDirection = 1
        if (fm) {
            // Skipping in FM tells NetEase you didn't want it.
            val id = track.id
            scope.launch { runCatching { api.fmTrash(id) } }
        }
        c.seekToNextMediaItem()
    }

    override fun skipPrevious(restartFirst: Boolean) {
        val c = controller ?: return
        if (restartFirst && c.currentPosition > 3_000) {
            seekTo(0)
        } else {
            pendingDirection = -1
            if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
        }
    }

    override fun playAt(index: Int) {
        val c = controller ?: return
        pendingDirection = if (index >= this.index) 1 else -1
        c.seekTo(index, 0)
        c.play()
    }

    override fun cyclePlayMode() {
        val c = controller ?: return
        when (playMode) {
            PlayMode.REPEAT_ALL -> c.repeatMode = Player.REPEAT_MODE_ONE
            PlayMode.REPEAT_ONE -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
            PlayMode.SHUFFLE -> c.shuffleModeEnabled = false
        }
    }

    override fun toggleLike() {
        val id = track.id.takeIf { !isEmpty } ?: return
        scope.launch { library.toggleLike(id)?.let { notice = it } }
    }

    override fun onFrame(frameTimeMs: Long) {
        controller?.let { positionMs = it.currentPosition }
    }

    override fun dismissNotice() {
        notice = null
    }

    // —— Lyrics ——

    private fun loadLyrics(t: Track) {
        if (t.id == 0L || t.id == lyricsFor) return
        lyricsFor = t.id
        lyricsJob?.cancel()
        lyricsCache[t.id]?.let {
            lyrics = it
            lyricsLoading = false
            return
        }
        lyrics = emptyList()
        lyricsLoading = true
        lyricsJob = scope.launch {
            var source: LyricsSource? = null
            for (attempt in 0 until 3) {
                source = runCatching { api.lyrics(t.id) }.getOrNull()
                if (source != null) break
                delay(1_500L * (attempt + 1))
            }
            val parsed = source?.let { LrcParser.parse(it, t.durationMs) }.orEmpty()
            if (source != null) lyricsCache[t.id] = parsed
            if (lyricsFor == t.id) {
                lyrics = parsed
                lyricsLoading = false
            }
        }
    }

    companion object {
        private const val EXTRA_DURATION = "lanyin.duration"
        private const val EXTRA_ALBUM = "lanyin.album"
        private const val EXTRA_ARTIST_IDS = "lanyin.artistIds"
        private const val EXTRA_ARTIST_NAMES = "lanyin.artistNames"

        fun qualityLabel(s: Stream): String = when {
            s.trialOnly -> "试听"
            else -> s.quality?.label ?: Quality.STANDARD.label
        }

        fun formatLabel(s: Stream): String {
            val type = s.format?.uppercase() ?: return ""
            return if (s.bitrate > 0) "$type · ${s.bitrate / 1000}kbps" else type
        }

        fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(AudioPipeline.uriFor(id.toString()))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistLine)
                    .setAlbumTitle(album.name)
                    .setArtworkUri(album.coverUrl?.let { Uri.parse(io.lanyin.player.ui.design.sized(it, 600)) })
                    .setDurationMs(durationMs.takeIf { it > 0 })
                    .setExtras(
                        Bundle().apply {
                            putLong(EXTRA_DURATION, durationMs)
                            putLong(EXTRA_ALBUM, album.id)
                            putLongArray(EXTRA_ARTIST_IDS, artists.map { it.id }.toLongArray())
                            putStringArray(EXTRA_ARTIST_NAMES, artists.map { it.name }.toTypedArray())
                        },
                    )
                    .build(),
            )
            .build()

        fun MediaItem.toTrack(): Track {
            val m = mediaMetadata
            val id = mediaId.toLongOrNull() ?: 0L
            return Track(
                id = id,
                title = m.title?.toString().orEmpty(),
                artist = m.artist?.toString().orEmpty(),
                album = m.albumTitle?.toString().orEmpty(),
                coverSeed = (id % 97).toInt(),
                durationMs = m.durationMs ?: m.extras?.getLong(EXTRA_DURATION) ?: 0L,
                coverUrl = m.artworkUri?.toString(),
                artists = m.extras?.let { x ->
                    val ids = x.getLongArray(EXTRA_ARTIST_IDS) ?: LongArray(0)
                    val names = x.getStringArray(EXTRA_ARTIST_NAMES) ?: emptyArray()
                    ids.indices.mapNotNull { i -> names.getOrNull(i)?.let { io.lanyin.player.core.model.Artist(ids[i], it) } }
                }.orEmpty(),
                albumId = m.extras?.getLong(EXTRA_ALBUM) ?: 0L,
            )
        }
    }
}
