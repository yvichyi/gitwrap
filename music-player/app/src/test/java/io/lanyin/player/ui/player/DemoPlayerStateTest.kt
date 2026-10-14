package io.lanyin.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoPlayerStateTest {

    private val lines = listOf(
        LyricLine("a", null, 0, 1_000),
        LyricLine("b", null, 1_000, 3_000),
        LyricLine("c", null, 3_000, 4_000),
    )

    @Test
    fun `lyric index follows line starts`() {
        assertEquals(0, lyricIndexAt(lines, 0))
        assertEquals(0, lyricIndexAt(lines, 999))
        assertEquals(1, lyricIndexAt(lines, 1_000))
        assertEquals(2, lyricIndexAt(lines, 10_000))
    }

    @Test
    fun `line progress runs from 0 to 1 within the active line`() {
        val state = DemoPlayerState()
        val second = state.lyrics[1]
        state.seekTo(second.startMs)
        assertEquals(0f, state.lineProgress(), 0.001f)
        state.seekTo((second.startMs + second.endMs) / 2)
        assertEquals(0.5f, state.lineProgress(), 0.01f)
    }

    @Test
    fun `playback loops at the end and seeking clamps`() {
        val state = DemoPlayerState()
        state.seekTo(state.durationMs - 10)
        state.advance(50)
        assertEquals(0L, state.positionMs)
        state.seekTo(-5)
        assertEquals(0L, state.positionMs)
        state.seekTo(Long.MAX_VALUE)
        assertEquals(state.durationMs, state.positionMs)
    }

    @Test
    fun `play mode cycles through every mode`() {
        val state = DemoPlayerState()
        val seen = (0 until PlayMode.entries.size).map { state.playMode.also { state.cyclePlayMode() } }
        assertEquals(PlayMode.entries.toSet(), seen.toSet())
        assertEquals(PlayMode.REPEAT_ALL, state.playMode)
    }

    @Test
    fun `lyric geometry interpolates between line centres`() {
        val g = LyricGeometry(3)
        intArrayOf(0, 100, 250).forEachIndexed { i, top -> g.tops[i] = top }
        intArrayOf(40, 60, 40).forEachIndexed { i, h -> g.heights[i] = h }
        assertEquals(20f, g.centerAt(0f), 0.01f)
        assertEquals(130f, g.centerAt(1f), 0.01f)
        assertEquals(75f, g.centerAt(0.5f), 0.01f)
        assertEquals(270f, g.centerAt(5f), 0.01f)
    }
}
