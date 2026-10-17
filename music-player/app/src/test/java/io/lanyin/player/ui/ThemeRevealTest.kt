package io.lanyin.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
class ThemeRevealTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a new theme opens from where it was chosen, and the old picture is let go`() {
        val reveal = ThemeReveal()
        var theme by mutableStateOf(AppTheme.INK)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ThemeRevealHost(reveal, key = theme) {
                LanyinTheme(theme = theme, mode = ThemeMode.LIGHT) { Backdrop(androidx.compose.ui.Modifier.fillMaxSize()) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        // Drawn once (the test only draws when asked), as the app draws every frame.
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/theme_reveal_0.png")
        compose.runOnIdle { runBlocking { reveal.before(Offset(600f, 900f)) } }
        assertNotNull("the old screen is held", reveal.picture)
        theme = AppTheme.NEON
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/theme_reveal_1.png")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNull("the picture is let go once the new theme is open", reveal.picture)
    }
}
