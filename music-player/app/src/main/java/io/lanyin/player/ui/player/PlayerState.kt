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
) {
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
        return ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
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

    /** Replaces the queue with [songs] and plays from [startIndex]; [source] names where they came from. */
    open fun play(songs: List<io.lanyin.player.core.model.Song>, startIndex: Int, source: String) = Unit

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
