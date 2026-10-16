package io.lanyin.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.lanyin.player.LanyinApplication
import io.lanyin.player.MainActivity
import io.lanyin.player.playback.MediaPlayerState.Companion.toMediaItem
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Plays in the background: ExoPlayer behind a MediaSession, which brings the notification, the
 * lock screen, Bluetooth and headset buttons. Takes audio focus and pauses when headphones are
 * unplugged.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = kotlinx.coroutines.MainScope()
    private var statusLyrics: StatusLyrics? = null
    private var destroying = false
    private lateinit var queueStore: QueueStore
    private var saving: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LanyinApplication).container
        val audio = container.audio
        val player = ExoPlayer.Builder(this, container.engine.renderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(audio.dataSourceFactory).setLoadErrorHandlingPolicy(audio.loadErrorPolicy))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        queueStore = QueueStore(java.io.File(filesDir, "queue.json"))
        // Killed with music queued: bring the queue back, paused where it was, so the app opens on
        // the last song and pressing play carries on.
        queueStore.read()?.let { q ->
            player.setMediaItems(q.songs.map { it.toMediaItem() }, q.index, q.positionMs)
            player.setPlaylistMetadata(MediaMetadata.Builder().setStation(q.source).build())
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                    saveQueueSoon()
                }
            }
        })
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items from a controller arrive without their address; give them ours (or the one
                // they asked for, when reloading at another quality).
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri ?: AudioPipeline.uriFor(it.mediaId)).build() }.toMutableList(),
                )

                // The system's media controls or a headset button, after the app was killed: what was playing.
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val q = queueStore.read() ?: return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(q.songs.map { it.toMediaItem() }, q.index, q.positionMs))
                }
            })
            .build()

        // 状态栏歌词: the line being sung, in the media notification's ticker (Flyme and the lyric
        // modules) and, on Android 16, a Live Update capsule.
        val ticker = TickerLyrics(androidx.media3.session.DefaultMediaNotificationProvider(this))
        setMediaNotificationProvider(ticker)
        ticker.repost = { if (!destroying) session?.let { onUpdateNotification(it, player.playWhenReady) } }
        val capsule = LyricCapsule(this)
        statusLyrics = StatusLyrics(
            scope, container.nowLyrics.current,
            position = { player.currentPosition },
            playing = {
                ticker.enabled = container.tickerLyrics
                capsule.enabled = container.capsuleLyrics
                player.isPlaying
            },
            sinks = listOf(ticker, capsule),
        ).also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Saves the queue a moment after it changes, and every so often while playing, off the main thread. */
    private fun saveQueueSoon() {
        if (destroying) return
        saving?.cancel()
        saving = scope.launch {
            kotlinx.coroutines.delay(800)
            while (true) {
                val snapshot = snapshotQueue() ?: return@launch
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { queueStore.write(snapshot) }
                if (session?.player?.isPlaying != true) return@launch
                kotlinx.coroutines.delay(15_000)
            }
        }
    }

    /** The queue as it stands, read on the main thread; null when empty. */
    private fun snapshotQueue(): SavedQueue? {
        val p = session?.player ?: return null
        if (p.mediaItemCount == 0) return null
        val songs = List(p.mediaItemCount) { with(MediaPlayerState) { p.getMediaItemAt(it).toTrack() }.toSong() }
        return SavedQueue(songs, p.currentMediaItemIndex, p.currentPosition, p.playlistMetadata.station?.toString().orEmpty())
    }

    /** Swiped away from recents: keep playing if music is playing, otherwise go. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Where the music stopped, for next time.
        snapshotQueue()?.let(queueStore::write)
        destroying = true
        // Clear the capsule, but don't post the media notification again on the way out.
        statusLyrics?.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
