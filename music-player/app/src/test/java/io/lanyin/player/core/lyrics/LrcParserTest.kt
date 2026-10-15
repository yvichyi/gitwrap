package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    private fun src(lrc: String?, translation: String? = null, instrumental: Boolean = false) =
        LyricsSource(lrc, translation, wordTimed = null, instrumental = instrumental)

    @Test
    fun `credits, stamps and translations line up`() {
        val lrc = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"方文山"}]}
            {"t":1000,"c":[{"tx":"作曲: "},{"tx":"周杰伦"}]}
            [ar:周杰伦]
            [00:30.542]故事的小黄花
            [00:34.1]从出生那年就飘着
            [00:38.00]
            [00:40.123]童年的荡秋千
        """.trimIndent()
        val translation = "[00:30.54]The little yellow flower of the story\n[00:40.12]The swing of childhood"
        val lines = LrcParser.parse(src(lrc, translation), durationMs = 269_000)
        assertEquals(listOf("作词: 方文山", "作曲: 周杰伦", "故事的小黄花", "从出生那年就飘着", "童年的荡秋千"), lines.map { it.text })
        assertEquals(listOf(0L, 1_000L, 30_542L, 34_100L, 40_123L), lines.map { it.startMs })
        // A blank stamp ends the line before it.
        assertEquals(38_000L, lines[3].endMs)
        assertEquals(269_000L, lines.last().endMs)
        assertEquals("The little yellow flower of the story", lines[2].translation)
        assertNull(lines[3].translation)
        assertEquals("The swing of childhood", lines[4].translation)
    }

    @Test
    fun `repeated stamps put a chorus in several places`() {
        val lines = LrcParser.parse(src("[00:10.00][01:10.00]副歌\n[00:20.00]主歌\n[01:20.00]尾声"), 90_000)
        assertEquals(listOf("副歌", "主歌", "副歌", "尾声"), lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L, 70_000L, 80_000L), lines.map { it.startMs })
    }

    @Test
    fun `instrumentals have no lines`() {
        assertTrue(LrcParser.parse(src("[00:00.00]纯音乐，请欣赏"), 60_000).isEmpty())
        assertTrue(LrcParser.parse(src("[00:01.00]anything", instrumental = true), 60_000).isEmpty())
        assertTrue(LrcParser.parse(src(null), 60_000).isEmpty())
        assertTrue(LrcParser.parse(src("[00:01.00]\n[00:02.00]"), 60_000).isEmpty())
    }

    @Test
    fun `fraction digits mean what they say`() {
        val t = LrcParser.timed("[01:02.5]a\n[01:02.50]b\n[01:02.500]c\n[01:02]d\n[01:02:50]e")
        assertEquals(listOf(62_000L, 62_500L, 62_500L, 62_500L, 62_500L), t.map { it.first })
    }
}
