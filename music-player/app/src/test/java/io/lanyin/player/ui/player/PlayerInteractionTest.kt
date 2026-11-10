package io.lanyin.player.ui.player

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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

    private fun show(state: DemoPlayerState, theme: AppTheme = AppTheme.INK, onCollapse: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = ThemeMode.LIGHT) {
                PlayerScreen(state, PlayerSkin.VINYL, rememberSpinState(false), keepScreenOnInLandscape = false, onCollapse = onCollapse)
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

    @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    @Test
    fun `left alone in landscape, the bars stay`() {
        show(DemoPlayerState().apply { togglePlay() })
        // Well past the five seconds after which it used to go full screen by itself.
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithContentDescription("收起").assertIsDisplayed()
    }

    /** Opens the lyrics from the artwork, then taps the empty space above the first line. */
    private fun lyricsAndBack(theme: AppTheme, shown: String = "显示封面", hidden: String = "显示歌词") {
        val state = DemoPlayerState()
        show(state, theme)
        compose.onNodeWithContentDescription(hidden).assertExists()
        compose.onAllNodesWithTag("swipeToSkip")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription(shown).assertExists()

        // A tap on a line jumps there and keeps the lyrics.
        val line = state.lyrics[2]
        compose.onNodeWithText(line.text).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(line.startMs, state.positionMs)
        compose.onNodeWithContentDescription(shown).assertExists()
        compose.mainClock.advanceTimeBy(4_000)

        // Above the lines, where nothing is: back to the cover.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.13f)) }
        // Robolectric's paused looper holds back the snapshot's apply notice a phone sends at once.
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription(hidden).assertExists()
    }

    @Test
    fun `in portrait, a tap beside the lyrics brings the cover back`() = lyricsAndBack(AppTheme.INK)

    @Test
    fun `in portrait, a tap beside the lyrics on 机's screen brings the cover back`() = lyricsAndBack(AppTheme.DEVICE)

    @Test
    fun `in portrait, a tap beside the lyrics on 游's screen brings the cover back`() = lyricsAndBack(AppTheme.GAME)

    /** Pulls the top bar down by [by] of the screen's height over [ms]; how many times it closed. */
    private fun pull(by: Float, ms: Long): Int {
        var closed = 0
        show(DemoPlayerState(), onCollapse = { closed++ })
        compose.onNodeWithContentDescription("收起").performTouchInput {
            val screen = 1_700f
            swipeDown(startY = centerY, endY = centerY + screen * by, durationMillis = ms)
        }
        compose.mainClock.advanceTimeBy(1_000)
        return closed
    }

    @Test
    fun `pulled down a little, the player springs back`() = assertEquals(0, pull(0.05f, 600))

    @Test
    fun `pulled down past a fifth of the screen, the player closes`() = assertEquals(1, pull(0.3f, 700))

    @Test
    fun `flicked down, the player closes`() = assertEquals(1, pull(0.1f, 60))

    private fun countCollapse(): Int =
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("收起")).fetchSemanticsNodes().size
}
