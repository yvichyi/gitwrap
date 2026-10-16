package io.lanyin.player.ui.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrameClockTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a clock on a page under the player stands still until the page shows again`() {
        var covered by mutableStateOf(true)
        lateinit var clock: FloatState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalFramesPaused provides covered) {
                clock = rememberFrameClock(running = true)
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0f, clock.floatValue, 0f)
        covered = false
        // Let the write reach the composition before running the frames.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("clock ${clock.floatValue}", clock.floatValue > 0.5f)
    }
}
