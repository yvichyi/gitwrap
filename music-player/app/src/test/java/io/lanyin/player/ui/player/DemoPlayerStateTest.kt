package io.lanyin.player.ui.player

import io.lanyin.player.core.model.LyricLine
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
    fun `line progress follows the demo's sung characters within the active line`() {
        val state = DemoPlayerState()
        val second = state.lyrics[1]
        state.seekTo(second.startMs)
        assertEquals(0f, state.lineProgress(), 0.001f)
        // Sung over four fifths of the line: halfway through the time is five eighths through the text.
        state.seekTo((second.startMs + second.endMs) / 2)
        assertEquals(0.625f, state.lineProgress(), 0.05f)
        state.seekTo(second.endMs - 1)
        assertEquals(1f, state.lineProgress(), 0.001f)
    }

    @Test
    fun `a line without word timings lights up in a quick stroke, not a crawl to the next line`() {
        val line = io.lanyin.player.core.model.LyricLine("没有逐字时间的一行", null, 10_000, 16_000)
        assertEquals(0f, line.charProgress(10_000), 0.001f)
        assertEquals(0.5f, line.charProgress(10_210), 0.01f)
        assertEquals(1f, line.charProgress(10_420), 0.001f)
        assertEquals(1f, line.charProgress(12_000), 0.001f)
    }

    @Test
    fun `the end of a track moves on, or repeats in single-track mode, and seeking clamps`() {
        val state = DemoPlayerState()
        state.seekTo(state.durationMs - 10)
        state.advance(50)
        assertEquals(1, state.index)
        assertEquals(0L, state.positionMs)

        while (state.playMode != PlayMode.REPEAT_ONE) state.cyclePlayMode()
        state.seekTo(state.durationMs - 10)
        state.advance(50)
        assertEquals(1, state.index)
        assertEquals(0L, state.positionMs)

        state.seekTo(-5)
        assertEquals(0L, state.positionMs)
        state.seekTo(Long.MAX_VALUE)
        assertEquals(state.durationMs, state.positionMs)
    }

    @Test
    fun `skipping wraps round the queue and remembers the direction`() {
        val state = DemoPlayerState()
        state.skipPrevious()
        assertEquals(state.queue.lastIndex, state.index)
        assertEquals(-1, state.skipDirection)
        state.skipNext()
        assertEquals(0, state.index)
        assertEquals(1, state.skipDirection)
        assertEquals(state.queue[1], state.next)
    }

    @Test
    fun `previous restarts a song under way, unless asked to always change track`() {
        val state = DemoPlayerState()
        state.skipNext()
        state.seekTo(10_000)
        state.skipPrevious()
        assertEquals(1, state.index)
        assertEquals(0L, state.positionMs)
        state.seekTo(10_000)
        state.skipPrevious(restartFirst = false)
        assertEquals(0, state.index)
    }

    @Test
    fun `an instrumental has no current line and no sweep`() {
        val state = DemoPlayerState()
        while (state.lyrics.isNotEmpty()) state.skipNext()
        state.seekTo(5_000)
        assertEquals(null, state.currentLine)
        assertEquals(0f, state.lineProgress(), 0f)
    }

    @Test
    fun `a swipe skips when it travels far enough or is flung the same way`() {
        val width = 1000f
        assertEquals(false, isSkip(0f, 5_000f, width))
        assertEquals(false, isSkip(-200f, 0f, width))
        assertEquals(true, isSkip(-300f, 0f, width))
        assertEquals(true, isSkip(-80f, -2_000f, width))
        // Flung back the other way: no skip.
        assertEquals(false, isSkip(-80f, 2_000f, width))
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
