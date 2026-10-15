package io.lanyin.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import io.lanyin.player.core.model.LyricLine
import kotlinx.coroutines.isActive

enum class PlayMode(val label: String) { REPEAT_ALL("列表循环"), REPEAT_ONE("单曲循环"), SHUFFLE("随机播放") }

/** A song as the player shows it. */
@Immutable
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    /** Picks the generated sleeve shown until (or instead of) the real cover. */
    val coverSeed: Int,
    val durationMs: Long,
    val coverUrl: String? = null,
    /** For going to an artist's or the album's page; empty in the demo. */
    val artists: List<io.lanyin.player.core.model.Artist> = emptyList(),
    val albumId: Long = 0,
) {
    /** Back to a song, to play or keep. */
    fun toSong() = io.lanyin.player.core.model.Song(
        id = id, name = title,
        artists = artists.ifEmpty { listOf(io.lanyin.player.core.model.Artist(0, artist)) },
        album = io.lanyin.player.core.model.Album(albumId, album, coverUrl),
        durationMs = durationMs,
    )

    companion object {
        val Empty = Track(0, "", "", "", coverSeed = 0, durationMs = 1)
    }
}

/**
 * What the player UI reads and the controls it calls. [DemoPlayerState] drives previews and
 * screenshots; the real one sits on a Media3 controller. Everything read here is snapshot
 * state, so the UI recomposes on change; the position updates every frame through [onFrame]
 * and is read by draw-phase code.
 */
@Stable
abstract class PlayerState {
    abstract val queue: List<Track>
    abstract val index: Int
    abstract val playlistName: String

    /** +1 after skipping forward, -1 after skipping back, so artwork can slide the right way. */
    abstract val skipDirection: Int
    abstract val isPlaying: Boolean
    abstract val positionMs: Long

    /** Timed lines; empty for an instrumental (or while they load). */
    abstract val lyrics: List<LyricLine>

    /** 「无损」, 「极高」… for the stream actually playing. */
    abstract val quality: String
    abstract val format: String
    abstract val liked: Boolean
    abstract val playMode: PlayMode

    /** Waiting for the network. */
    open val buffering: Boolean get() = false

    /** Lyrics are on their way; [lyrics] is empty meanwhile but this isn't an instrumental. */
    open val lyricsLoading: Boolean get() = false

    /** Something to tell the listener (a song skipped as unplayable, say), or null. */
    open val notice: String? get() = null

    val isEmpty: Boolean get() = queue.isEmpty()
    val track: Track get() = queue.getOrElse(index) { Track.Empty }
    /** What plays after this one (in shuffle order when shuffling). */
    open val next: Track get() = if (queue.isEmpty()) Track.Empty else queue[(index + 1) % queue.size]
    val title: String get() = track.title
    val artist: String get() = track.artist
    val album: String get() = track.album
    val coverSeed: Int get() = track.coverSeed
    val coverUrl: String? get() = track.coverUrl
    open val durationMs: Long get() = track.durationMs.coerceAtLeast(1)
    val trackNumber: Int get() = index + 1
    val trackCount: Int get() = queue.size

    val currentLyricIndex: Int by derivedStateOf { lyricIndexAt(lyrics, positionMs) }

    /** The line being sung, or null for an instrumental. */
    val currentLine: LyricLine? get() = lyrics.getOrNull(currentLyricIndex)

    /** 0..1 through the current line; call from draw code. */
    fun lineProgress(): Float {
        val line = currentLine ?: return 0f
        // Word-timed lines follow the singing, by characters; others sweep evenly through the line.
        return line.charProgress(positionMs)
    }

    abstract fun togglePlay()
    abstract fun seekTo(ms: Long)
    abstract fun cyclePlayMode()
    abstract fun skipNext()

    /**
     * Like most players, the previous button first restarts a song that is under way
     * ([restartFirst]); a swipe always goes to the previous track.
     */
    abstract fun skipPrevious(restartFirst: Boolean = true)
    abstract fun toggleLike()

    /** Jumps to a song already in the queue. */
    open fun playAt(index: Int) = Unit

    /**
     * Replaces the queue with [songs] and plays from [startIndex]; [source] names where they came
     * from and [sourceId] is that playlist's or album's id, for the play records sent to NetEase.
     */
    open fun play(songs: List<io.lanyin.player.core.model.Song>, startIndex: Int, source: String, sourceId: Long = 0) = Unit

    /**
     * What is being heard, for the spectrum and beat ripples. By default a stand-in that follows
     * [positionMs] with a steady beat, so previews move; the real player listens to the audio.
     */
    open val audioFeed: io.lanyin.player.core.audio.AudioFeed = object : io.lanyin.player.core.audio.AudioFeed {
        override fun levels(out: FloatArray, lagMs: Long): Boolean {
            demoSpectrum((positionMs - lagMs) / 1000f, out)
            return isPlaying
        }

        override fun sinceBeat(): Float = (positionMs / 1000f) % (1f / DEMO_BEATS_PER_SECOND)
    }

    /** How many comments the current song has, once known. */
    open val commentCount: Int? get() = null

    /** Asks for [commentCount] if it isn't known yet; the player calls this while it shows. */
    open fun loadCommentCount() = Unit

    /** Plays on at [quality]: the current song reloads from where it is. */
    open fun switchQuality(quality: io.lanyin.player.core.model.Quality) = Unit

    /** Starts 私人 FM, which keeps topping itself up. */
    open fun playFm() = Unit

    /** The id of the song playing, or null; for lists marking it. */
    val playingSongId: Long? get() = track.id.takeIf { !isEmpty }

    /** Called on every frame while playing and on screen. */
    abstract fun onFrame(frameTimeMs: Long)

    open fun dismissNotice() = Unit
}

internal fun lyricIndexAt(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)

/** Drives [PlayerState.onFrame] while playing. */
@Composable
fun PlayerFrameDriver(state: PlayerState) {
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying && isActive) withFrameMillis(state::onFrame)
    }
}
