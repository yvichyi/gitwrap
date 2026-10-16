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
 * Each theme's player in the moments around the music: paused (a stamp, a dimmed screen, a sign
 * switched off…), in portrait and landscape. Images land in build/outputs/roborazzi/states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayerStatesTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.lightOnly) ThemeMode.LIGHT else if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private fun paused(name: String) {
        // Played a while, then stopped: the moment the stage marks.
        val state = DemoPlayerState().apply {
            togglePlay()
            seekTo(14_000)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                PlayerScreen(state, PlayerSkin.VINYL, rememberSpinState(state.isPlaying), keepScreenOnInLandscape = true, onCollapse = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        state.togglePlay()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/states/${theme.name.lowercase()}_$name.png")
    }

    @Config(qualifiers = "w392dp-h850dp-port-xhdpi")
    @Test fun portPaused() = paused("port_paused")

    @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    @Test fun landPaused() = paused("land_paused")

    @Config(qualifiers = "w392dp-h850dp-port-xhdpi")
    @Test fun nextTrack() {
        // The moment a track gives way to the next.
        val state = DemoPlayerState().apply { togglePlay() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                PlayerScreen(state, PlayerSkin.VINYL, rememberSpinState(true), keepScreenOnInLandscape = true, onCollapse = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        state.skipNext()
        compose.mainClock.advanceTimeBy(450)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/states/${theme.name.lowercase()}_next.png")
    }
}
