package io.lanyin.player.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.ui.share.CardHeight
import io.lanyin.player.ui.share.CardLyric
import io.lanyin.player.ui.share.CardWidth
import io.lanyin.player.ui.share.LyricCard
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lyric card each theme shares, at its real size. Images land in build/outputs/roborazzi/cards. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h450dp-port-xxhdpi")
class ShareCardTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private val lyric = CardLyric(
        line = "让歌词一行一行亮起", translation = "let the lyrics light up line by line",
        title = "澜音试播", artist = "澜音", album = "潮汐", coverUrl = null, coverSeed = 7,
    )

    @Test fun card() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                Box { LyricCard(lyric, Modifier.requiredSize(CardWidth, CardHeight)) }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/cards/${theme.name.lowercase()}.png")
    }
}
