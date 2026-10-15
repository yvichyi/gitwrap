package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingingPaceTest {

    @Test
    fun `units count characters of Chinese and words of everything else`() {
        assertEquals(5, SingingPace.units("故事的小黄花"[0].toString() + "事的小黄"))
        assertEquals(6, SingingPace.units("Let the lyrics light up line"))
        assertEquals(4, SingingPace.units("我爱 you so"))
        assertEquals(0, SingingPace.units("…"))
    }

    @Test
    fun `the pace comes from the quicker lines, so pauses don't slow it`() {
        // Ten lines of eight characters sung in two seconds (4/s), half of them followed by a long pause.
        val lines = (0 until 10).map { i ->
            val start = i * 10_000L
            val gap = if (i % 2 == 0) 2_000L else 8_000L
            LyricLine("一二三四五六七八", null, start, start + gap)
        }
        assertEquals(4f, SingingPace.rate(lines), 0.01f)
        val paced = SingingPace.apply(lines)
        // A line with a pause after it is still sung in about two seconds.
        assertEquals(2_000L, paced[1].sungMs)
        // A line that runs straight into the next can't take longer than most of its time.
        assertEquals(1_840L, paced[0].sungMs)
    }

    @Test
    fun `few lines fall back to a typical pace, and the estimate stays inside the line`() {
        assertEquals(SingingPace.DEFAULT_RATE, SingingPace.rate(listOf(LyricLine("一二三四", null, 0, 1_000))), 0f)
        assertTrue(SingingPace.estimate("一", 10_000) >= 330)
        assertEquals(920L, SingingPace.estimate("一二三四五六七八九十一二三四五六", 1_000))
    }

    @Test
    fun `parsed LRC lines carry their sung time, word-timed ones don't need it`() {
        val lrc = (0 until 8).joinToString("\n") { i -> "[00:%02d.00]一二三四五六七八".format(i * 5) }
        val lines = LrcParser.parse(LyricsSource(lrc, null, null, false), 60_000)
        assertTrue(lines.all { it.sungMs in 330..((it.endMs - it.startMs) * 0.92).toLong() })
        // Halfway through the singing, half the characters are lit; long before the next line, all of them.
        val l = lines[2]
        assertEquals(0.5f, l.charProgress(l.startMs + l.sungMs / 2), 0.02f)
        assertEquals(1f, l.charProgress(l.startMs + l.sungMs + 100), 0f)
    }
}
