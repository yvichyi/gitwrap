package io.lanyin.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }

    @Test
    fun `counts down, fades over the last half minute, silent at the end`() {
        timer.start(15)
        assertEquals(15 * 60_000L, timer.remainingMs())
        assertEquals(1f, timer.volumeAt(), 0f)
        now += 15 * 60_000L - SleepTimer.FADE_MS
        assertEquals(1f, timer.volumeAt(), 0f)
        now += SleepTimer.FADE_MS / 2
        assertEquals(0.25f, timer.volumeAt(), 0.001f)
        now += SleepTimer.FADE_MS
        assertEquals(0L, timer.remainingMs())
        assertEquals(0f, timer.volumeAt(), 0f)
    }

    @Test
    fun `end of track has no countdown, and cancelling clears it`() {
        timer.endOfTrack()
        assertTrue(timer.state.value!!.endOfTrack)
        assertNull(timer.remainingMs())
        assertEquals(1f, timer.volumeAt(), 0f)
        timer.cancel()
        assertNull(timer.state.value)
    }
}
