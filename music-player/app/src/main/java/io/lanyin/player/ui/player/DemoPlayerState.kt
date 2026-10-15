package io.lanyin.player.ui.player

import io.lanyin.player.core.model.LyricLine
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One track of the demo queue, with the lyrics and stream it pretends to have. */
@Immutable
data class DemoTrack(
    val title: String,
    val artist: String,
    val album: String,
    val coverSeed: Int,
    val durationMs: Long,
    /** Empty for instrumentals. */
    val lyrics: List<LyricLine>,
    val quality: String = "无损",
    val format: String = "FLAC · 44.1kHz",
) {
    fun toTrack(id: Long) = Track(id, title, artist, album, coverSeed, durationMs)
}

/**
 * The player for previews and screenshots: a small queue whose clock advances every frame while
 * playing. Readers that only need seconds or the lyric index use derived state, so the per-frame
 * position only drives draw-phase work (seek bar, lyric sweep).
 */
@Stable
class DemoPlayerState(private val demo: List<DemoTrack> = demoQueue) : PlayerState() {
    override val playlistName = "演示歌单"
    override val queue: List<Track> = demo.mapIndexed { i, t -> t.toTrack(i + 1L) }

    override var index by mutableIntStateOf(0)
        private set
    override var skipDirection by mutableIntStateOf(0)
        private set
    override var isPlaying by mutableStateOf(false)
        private set
    override var positionMs by mutableLongStateOf(0L)
        private set
    override var liked by mutableStateOf(false)
        private set
    override var playMode by mutableStateOf(PlayMode.REPEAT_ALL)
        private set

    override val lyrics: List<LyricLine> get() = demo.getOrNull(index)?.lyrics.orEmpty()
    override val quality: String get() = demo.getOrNull(index)?.quality.orEmpty()
    override val format: String get() = demo.getOrNull(index)?.format.orEmpty()

    private var lastFrame = -1L

    override fun togglePlay() {
        isPlaying = !isPlaying
        lastFrame = -1L
    }

    override fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0L, durationMs)
    }

    override fun cyclePlayMode() {
        playMode = PlayMode.entries[(playMode.ordinal + 1) % PlayMode.entries.size]
    }

    override fun toggleLike() {
        liked = !liked
    }

    /** Next track, wrapping round the queue. */
    override fun skipNext() {
        if (queue.isNotEmpty()) skipTo((index + 1) % queue.size, forward = true)
    }

    override fun skipPrevious(restartFirst: Boolean) {
        if (queue.isEmpty()) return
        if (restartFirst && positionMs > 3_000) seekTo(0) else skipTo((index - 1 + queue.size) % queue.size, forward = false)
    }

    override fun playAt(index: Int) = skipTo(index, forward = index >= this.index)

    private fun skipTo(i: Int, forward: Boolean) {
        skipDirection = if (forward) 1 else -1
        index = i
        positionMs = 0L
    }

    override fun onFrame(frameTimeMs: Long) {
        if (lastFrame >= 0) advance(frameTimeMs - lastFrame)
        lastFrame = frameTimeMs
    }

    internal fun advance(deltaMs: Long) {
        val next = positionMs + deltaMs
        when {
            next < durationMs -> positionMs = next
            playMode == PlayMode.REPEAT_ONE -> positionMs = 0L
            else -> skipNext()
        }
    }
}

@Composable
fun rememberDemoPlayerState(): DemoPlayerState {
    val state = remember { DemoPlayerState() }
    PlayerFrameDriver(state)
    return state
}

/** Record angle with turntable inertia: it spins up when playback starts and coasts to a stop. */
@Stable
class SpinState {
    var angle by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberSpinState(running: Boolean, secondsPerTurn: Float = 14f): SpinState {
    val spin = remember { SpinState() }
    val speed = remember { Animatable(0f) }
    LaunchedEffect(running) {
        launch {
            speed.animateTo(
                if (running) 360f / secondsPerTurn else 0f,
                tween(if (running) 600 else 1400, easing = FastOutSlowInEasing),
            )
        }
        var last = -1L
        while (isActive && (running || speed.value != 0f)) {
            withFrameNanos { now ->
                if (last >= 0) spin.angle = (spin.angle + speed.value * (now - last) / 1_000_000_000f) % 360f
                last = now
            }
        }
    }
    return spin
}

private fun timed(starts: LongArray, endMs: Long, lines: List<Pair<String, String?>>): List<LyricLine> =
    lines.mapIndexed { i, (text, translation) ->
        LyricLine(text, translation, starts[i], if (i + 1 < starts.size) starts[i + 1] else endMs)
    }

private val demoLyrics = timed(
    longArrayOf(0, 3_800, 8_200, 12_400, 17_000, 21_600, 25_600, 30_200, 34_800, 38_800, 43_600, 46_200, 49_000, 54_200),
    60_000,
    listOf(
        "潮水退去以后" to "after the tide goes out",
        "沙上留着谁的脚印" to "whose footprints stay on the sand",
        "把今晚调成横屏" to "turn tonight sideways",
        "让歌词一行一行亮起" to "let the lyrics light up line by line",
        "唱片在掌心慢慢转动" to "a record turning slowly in my palm",
        "每一圈都是回音" to "every turn an echo",
        "如果风把旋律吹散" to "if the wind scatters the melody",
        "就让浪替我记住" to "let the waves remember for me",
        "那些没说出口的" to "the things left unsaid",
        "都折进了副歌里" to "folded into the chorus",
        "澜起" to "waves rise",
        "音落" to "sound falls",
        "我们在节拍之间相遇" to "we meet between the beats",
        "再一起 回到第一句" to "and return together to the first line",
    ),
)

// No translations, as most Chinese songs come.
private val sunsetLyrics = timed(
    longArrayOf(0, 4_200, 8_600, 13_000, 17_800, 22_400, 27_000, 32_000, 37_200, 42_000),
    48_000,
    listOf(
        "磁带转到第二面" to null,
        "窗外的天慢慢变橙" to null,
        "收音机里有人在唱" to null,
        "七十年代的晚风" to null,
        "把影子拉得很长很长" to null,
        "我们骑车穿过黄昏" to null,
        "车铃响了一整条街" to null,
        "落日也舍不得走" to null,
        "按下倒带" to null,
        "再听一遍" to null,
    ),
)

// Long title, long names and a line long enough to wrap: what real data will throw at the layout.
private val longLyrics = timed(
    longArrayOf(0, 5_000, 12_000, 18_000, 24_000, 30_000),
    36_000,
    listOf(
        "这首歌的名字很长" to "this song has a very long name",
        "长到要在屏幕上折成两行，看看歌词会不会把封面挤到一边去" to "long enough to wrap onto a second line and see if it pushes the artwork aside",
        "歌手名也很长" to "and so does the artist",
        "专辑名也不短" to "the album too",
        "但排版应该依然整齐" to "but the layout should stay tidy",
        "这就是测试的意义" to "which is the point of the test",
    ),
)

internal val demoQueue = listOf(
    DemoTrack("澜音试播", "澜音", "潮汐 · 演示", coverSeed = 3, durationMs = 64_000, lyrics = demoLyrics),
    DemoTrack("潮汐（伴奏）", "澜音", "潮汐 · 演示", coverSeed = 6, durationMs = 46_000, lyrics = emptyList()),
    DemoTrack("落日磁带", "澜音乐队", "黄昏 · 演示", coverSeed = 5, durationMs = 50_000, lyrics = sunsetLyrics, quality = "极高", format = "MP3 · 320kbps"),
    DemoTrack(
        "一首名字特别长、用来看看排版会不会被挤坏的歌", "澜音 / 很多很多位合作歌手", "排版测试 · 一张名字同样不短的专辑",
        coverSeed = 8, durationMs = 40_000, lyrics = longLyrics, quality = "Hi-Res", format = "FLAC · 24bit/96kHz",
    ),
)
