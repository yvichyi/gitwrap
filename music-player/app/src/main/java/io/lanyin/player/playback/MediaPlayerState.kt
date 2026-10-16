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
import io.lanyin.player.core.model.CommentSort
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
    /** Whether to report plays to NetEase (the 同步播放记录 setting). */
    private val syncPlays: () -> Boolean = { false },
    /** Listens to what plays, for the visuals. */
    private val feed: io.lanyin.player.core.audio.AudioFeed? = null,
    /** Makes [Quality] the one streams are asked for (and remembers it). */
    private val setPreferred: (Quality) -> Unit = {},
    /** Where the lyrics of what plays are published for the status bar. */
    private val nowLyrics: NowPlayingLyrics? = null,
) : PlayerState() {

    override val audioFeed: io.lanyin.player.core.audio.AudioFeed get() = feed ?: super.audioFeed

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
    override var lyricsGap by mutableStateOf(io.lanyin.player.ui.player.LyricsGap.Instrumental)
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
    private var reloading = false
    /** Direction of a skip we asked for, applied when the player reports the change. */
    private var pendingDirection = 0
    private var errorsInARow = 0
    /** The song already given a second try after a network error, so a dead stream isn't retried forever. */
    private var retried: String? = null
    private var lyricsJob: Job? = null
    private var lyricsFor = 0L
    private val lyricsCache = object : LinkedHashMap<Long, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<LyricLine>>) = size > 40
    }

    // Play records: what is being listened to, for how long, and from which list.
    private var sourceId = 0L
    private var listeningTo: Track? = null
    private var listenedMs = 0L
    private var playingSince = 0L
    private var commentCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
    private val countsAsked = HashSet<Long>()

    override val commentCount: Int? get() = commentCounts[track.id]

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
            // A song that got going ends a run of failures.
            if (player.playbackState == Player.STATE_READY) {
                errorsInARow = 0
                retried = null
            }
            if (player.duration > 0) playerDuration = player.duration
            positionMs = player.currentPosition
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) {
                if (playingSince == 0L) playingSince = clock()
            } else if (playingSince != 0L) {
                listenedMs += clock() - playingSince
                playingSince = 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            // The same song, reloaded at another quality: not a new play.
            if (reloading) {
                reloading = false
                index = c.currentMediaItemIndex
                playerDuration = 0
                return
            }
            finishListening(if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) "playend" else "ui")
            // The queue is otherwise only read in onEvents, which comes after this: a new list
            // would still be the old one here, and the old list's song would get the lyrics.
            val previous = index
            syncQueue(c)
            val newIndex = index
            skipDirection = when {
                pendingDirection != 0 -> pendingDirection
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || newIndex >= previous -> 1
                else -> -1
            }
            pendingDirection = 0
            playerDuration = 0
            positionMs = 0
            loadLyrics(track)
            listeningTo = track.takeIf { !isEmpty }
            playingSince = if (c.isPlaying) clock() else 0L
            if (!isEmpty) recent?.record(track.toSong())
            if (fm && index >= queue.size - 2) topUpFm()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val cause = generateSequence(error as Throwable?) { it.cause }.firstOrNull { it is UnplayableException }
            if (cause == null) resolver.invalidate(track.id)
            val id = c.currentMediaItem?.mediaId
            // A network hiccup (a tunnel, a lift): give the same song one more go from where it
            // stopped before skipping it.
            if (cause == null && id != null && retried != id) {
                retried = id
                notice = "网络不太好，正在重试「${track.title}」"
                val at = c.currentPosition
                scope.launch {
                    delay(RETRY_DELAY_MS)
                    // Unless the listener has moved on meanwhile.
                    if (c.currentMediaItem?.mediaId == id && c.playbackState == Player.STATE_IDLE) {
                        c.seekTo(c.currentMediaItemIndex, at)
                        c.prepare()
                        c.play()
                    }
                }
                return
            }
            notice = cause?.message?.let { "「${track.title}」$it，已跳过" } ?: "网络不太好，「${track.title}」没能播放"
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
        listeningTo = track.takeIf { !isEmpty }
        playingSince = if (c.isPlaying) clock() else 0L
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

    override fun play(songs: List<Song>, startIndex: Int, source: String, sourceId: Long) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        fm = false
        this.sourceId = sourceId
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
            sourceId = 0
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

    override fun switchQuality(quality: Quality) {
        setPreferred(quality)
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        val i = c.currentMediaItemIndex
        val position = c.currentPosition
        // A different address makes the player load the song afresh, at the new quality.
        val uri = AudioPipeline.uriFor(item.mediaId, quality.level)
        reloading = true
        resolver.invalidate(item.mediaId.toLongOrNull() ?: 0L)
        c.replaceMediaItem(i, item.buildUpon().setUri(uri).setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build()).build())
        c.seekTo(i, position)
        c.prepare()
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

    override fun loadCommentCount() {
        val id = track.id.takeIf { !isEmpty && it != 0L } ?: return
        if (!countsAsked.add(id)) return
        scope.launch {
            runCatching { api.comments(id, CommentSort.RECOMMENDED, pageSize = 1).total }
                .onSuccess { commentCounts = commentCounts + (id to it) }
                .onFailure { countsAsked.remove(id) }
        }
    }

    // —— Play records ——

    /**
     * Reports the song that just stopped being the current one, if it was really listened to (half
     * a minute, or half of a short song) and the listener lets 澜音 sync plays.
     */
    private fun finishListening(end: String) {
        val t = listeningTo ?: return
        val heard = listenedMs + if (playingSince != 0L) clock() - playingSince else 0L
        listeningTo = null
        listenedMs = 0L
        playingSince = 0L
        if (!worthReporting(heard, t.durationMs)) return
        if (!syncPlays() || api.session.value?.loggedIn != true) return
        val source = sourceId
        scope.launch { runCatching { api.scrobble(t.id, heard / 1000, source, end) } }
    }

    private fun clock() = android.os.SystemClock.elapsedRealtime()

    // —— Lyrics ——

    override fun retryLyrics() {
        lyricsFor = 0L
        if (!isEmpty) loadLyrics(track)
    }

    private fun loadLyrics(t: Track) {
        if (t.id == 0L || t.id == lyricsFor) return
        lyricsFor = t.id
        lyricsJob?.cancel()
        lyricsCache[t.id]?.let {
            lyrics = it
            lyricsLoading = false
            nowLyrics?.publish(NowPlayingLyrics.Lyrics(t.id, t.title, t.artist, it))
            return
        }
        lyrics = emptyList()
        lyricsLoading = true
        nowLyrics?.publish(NowPlayingLyrics.Lyrics(t.id, t.title, t.artist, emptyList()))
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
                lyricsGap = when {
                    source == null -> io.lanyin.player.ui.player.LyricsGap.Failed
                    source.missing -> io.lanyin.player.ui.player.LyricsGap.Missing
                    else -> io.lanyin.player.ui.player.LyricsGap.Instrumental
                }
                // Let the next look at this song (or a tap on 重试) fetch them again.
                if (source == null) lyricsFor = 0L
                nowLyrics?.publish(NowPlayingLyrics.Lyrics(t.id, t.title, t.artist, parsed))
            }
        }
    }

    companion object {
        /** How long to wait before trying a song again after a network error. */
        private const val RETRY_DELAY_MS = 2_500L
        private const val EXTRA_DURATION = "lanyin.duration"
        private const val EXTRA_ALBUM = "lanyin.album"
        private const val EXTRA_ARTIST_IDS = "lanyin.artistIds"
        private const val EXTRA_ARTIST_NAMES = "lanyin.artistNames"

        /** Half a minute, or half of a song shorter than a minute. */
        fun worthReporting(heardMs: Long, durationMs: Long): Boolean =
            heardMs >= 30_000 || (durationMs in 1 until 60_000 && heardMs >= durationMs / 2)

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
