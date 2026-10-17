package io.lanyin.player.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.ui.desktop.DesktopControls
import io.lanyin.player.ui.desktop.DesktopLine
import io.lanyin.player.ui.desktop.DesktopLyric
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 桌面歌词 in every theme, over a bright wallpaper and a dark one (it floats over anything), part
 * sung, then with its controls. Images land in build/outputs/roborazzi/desktop.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h560dp-port-xxhdpi")
class DesktopLyricTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private val sung = DesktopLine("让歌词一行一行亮起", "let the lyrics light up line by line", timed = true)
    private val next = DesktopLine("唱片在掌心慢慢转动", "下一句：晚风吹过海面", timed = true)

    @Test fun strip() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode, applySystemBars = false) {
                Column(Modifier.fillMaxSize()) {
                    // A bright, busy wallpaper: a sunny sky.
                    Box(
                        Modifier.weight(1f).fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFF3C4), Color(0xFF9AD8FF), Color(0xFFFFB3C7)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        DesktopLyric(sung, { 0.45f }, size = 2, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth())
                    }
                    // A dark one, with the controls out.
                    Box(
                        Modifier.weight(1.3f).fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0B1026), Color(0xFF3A1C4F), Color(0xFF102A2A)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DesktopLyric(next, { 1f }, size = 3, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth())
                            DesktopControls(true, {}, {}, {}, {}, {}, {}, {}, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/desktop/${theme.name.lowercase()}.png")
    }
}
