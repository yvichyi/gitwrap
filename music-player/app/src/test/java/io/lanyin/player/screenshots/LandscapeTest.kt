package io.lanyin.player.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each theme's landscape player: with its bars, full screen (as after a tap on the stage), and
 * with the square cover, for reviewing the stages. Images land in build/outputs/roborazzi/land.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w850dp-h392dp-land-xhdpi")
class LandscapeTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.lightOnly) ThemeMode.LIGHT else if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, skin: PlayerSkin, settle: Long, fullScreen: Boolean = false) {
        val state = DemoPlayerState().apply {
            togglePlay()
            seekTo(14_000)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                PlayerScreen(state, skin, rememberSpinState(true), keepScreenOnInLandscape = true, onCollapse = {}, startFullScreen = fullScreen)
            }
        }
        compose.mainClock.advanceTimeBy(settle)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/land/${theme.name.lowercase()}_$name.png")
    }

    @Test fun vinyl() = shot("vinyl", PlayerSkin.VINYL, 1_500)
    @Test fun idle() = shot("idle", PlayerSkin.VINYL, 1_500, fullScreen = true)
    @Test fun cover() = shot("cover", PlayerSkin.COVER, 1_800)
}
