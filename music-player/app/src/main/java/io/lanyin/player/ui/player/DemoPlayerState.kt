package io.lanyin.player.ui.player

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class LyricLine(val text: String, val translation: String?, val startMs: Long, val endMs: Long)

enum class PlayMode(val label: String) { REPEAT_ALL("列表循环"), REPEAT_ONE("单曲循环"), SHUFFLE("随机播放") }

/**
 * Stand-in for the Media3 controller until M2: one fake track whose clock advances every frame
 * while playing. Readers that only need seconds or the lyric index use derived state, so the
 * per-frame position only drives draw-phase work (seek bar, lyric sweep).
 */
@Stable
class DemoPlayerState {
    val title = "澜音试播"
    val artist = "澜音"
    val album = "潮汐 · 演示"
    val coverSeed = 3
    val durationMs = 64_000L
    val lyrics: List<LyricLine> = demoLyrics

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var liked by mutableStateOf(false)
    var playMode by mutableStateOf(PlayMode.REPEAT_ALL)
        private set

    val currentLyricIndex: Int by derivedStateOf { lyricIndexAt(lyrics, positionMs) }

    /** 0..1 through the current line; call from draw code. */
    fun lineProgress(): Float {
        val line = lyrics[currentLyricIndex]
        return ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs)).coerceIn(0f, 1f)
    }

    fun togglePlay() {
        isPlaying = !isPlaying
    }

    fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0L, durationMs)
    }

    fun cyclePlayMode() {
        playMode = PlayMode.entries[(playMode.ordinal + 1) % PlayMode.entries.size]
    }

    internal fun advance(deltaMs: Long) {
        val next = positionMs + deltaMs
        positionMs = if (next >= durationMs) 0L else next
    }
}

internal fun lyricIndexAt(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)

@Composable
fun rememberDemoPlayerState(): DemoPlayerState {
    val state = remember { DemoPlayerState() }
    LaunchedEffect(state.isPlaying) {
        var last = -1L
        while (state.isPlaying && isActive) {
            withFrameMillis { now ->
                if (last >= 0) state.advance(now - last)
                last = now
            }
        }
    }
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

private val demoLyrics = listOf(
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
).let { pairs ->
    val starts = longArrayOf(0, 3_800, 8_200, 12_400, 17_000, 21_600, 25_600, 30_200, 34_800, 38_800, 43_600, 46_200, 49_000, 54_200)
    pairs.mapIndexed { i, (text, translation) ->
        LyricLine(text, translation, starts[i], if (i + 1 < starts.size) starts[i + 1] else 60_000)
    }
}
