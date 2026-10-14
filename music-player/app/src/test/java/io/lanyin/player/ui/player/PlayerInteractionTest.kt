package io.lanyin.player.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
class PlayerInteractionTest {

    @get:Rule val compose = createComposeRule()

    private fun show(state: DemoPlayerState) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = AppTheme.INK, mode = ThemeMode.LIGHT) {
                PlayerScreen(state, PlayerSkin.VINYL, rememberSpinState(false), keepScreenOnInLandscape = false, onCollapse = {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `swiping the artwork changes track both ways`() {
        val state = DemoPlayerState()
        show(state)
        compose.onAllNodesWithTag("swipeToSkip")[0].performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, state.index)
        compose.onAllNodesWithTag("swipeToSkip")[0].performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, state.index)
    }

    @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    @Test
    fun `tapping the empty stage toggles full screen in landscape`() {
        show(DemoPlayerState())
        compose.onNodeWithContentDescription("收起").assertIsDisplayed()
        // The gap between the artwork and the lyrics, halfway down.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.41f, height * 0.5f)) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, countCollapse())
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.41f, height * 0.5f)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("收起").assertIsDisplayed()
    }

    private fun countCollapse(): Int =
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("收起")).fetchSemanticsNodes().size
}
