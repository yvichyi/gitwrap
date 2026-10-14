package io.lanyin.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Stand-in for the Media3 controller until M2: a fake track whose clock only advances while
 * "playing", so the skins, mini bar and lyric highlight can be exercised without audio.
 */
@Stable
class DemoPlayerState {
    val title = "澜音试播"
    val artist = "澜音"
    val album = "M0 演示"
    val durationMs = 3 * 60_000L + 3_000L

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set

    /** Placeholder lyric lines, one every four seconds, until real lyrics arrive in M4. */
    val lyrics = listOf(
        "潮水退去以后" to "after the tide goes out",
        "沙上留着谁的脚印" to "whose footprints stay on the sand",
        "把今晚调成横屏" to "turn tonight sideways",
        "让歌词慢慢亮起" to "and let the lyrics light up slowly",
        "唱片在掌心转动" to "a record spinning in my palm",
        "每一圈都是回音" to "every turn an echo",
        "澜起 · 音落" to "waves rise, sound falls",
    )

    val currentLyricIndex: Int
        get() = ((positionMs / LYRIC_STEP_MS).toInt()).coerceAtMost(lyrics.lastIndex)

    fun togglePlay() {
        isPlaying = !isPlaying
    }

    fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0L, durationMs)
    }

    internal fun tick(stepMs: Long) {
        positionMs = if (positionMs + stepMs >= durationMs) 0L else positionMs + stepMs
    }

    companion object {
        const val LYRIC_STEP_MS = 4_000L
    }
}

@Composable
fun rememberDemoPlayerState(): DemoPlayerState {
    val state = remember { DemoPlayerState() }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            delay(200)
            state.tick(200)
        }
    }
    return state
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
