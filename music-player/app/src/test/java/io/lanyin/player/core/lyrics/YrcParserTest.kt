package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YrcParserTest {

    private val yrc = """
        {"t":0,"c":[{"tx":"作词: "},{"tx":"澜音"}]}
        [16210,3460](16210,670,0)潮水(16880,1200,0)退去(18080,1590,0)以后
        [20000,2000](20000,1000,0)沙上(21000,1000,0)脚印
        [23000,3000](23000,3000,0)Hello world
    """.trimIndent()

    @Test
    fun `words become lines with their timings`() {
        val lines = YrcParser.parse(yrc, 60_000)
        assertEquals(listOf("作词: 澜音", "潮水退去以后", "沙上脚印", "Hello world"), lines.map { it.text })
        val first = lines[1]
        assertEquals(16_210L, first.startMs)
        assertEquals(listOf("潮水", "退去", "以后"), first.words!!.map { it.text })
        assertNull(lines[0].words)
        // The credit holds until the first sung line.
        assertEquals(16_210L, lines[0].endMs)
    }

    @Test
    fun `progress follows the words, not the clock`() {
        val line = YrcParser.parse(yrc, 60_000)[1]
        // "潮水" (2 of 6 characters) takes 670 ms of the line's 3460: done after 670 ms.
        assertEquals(2f / 6f, line.charProgress(16_880), 0.001f)
        // Halfway through "退去".
        assertEquals(3f / 6f, line.charProgress(16_880 + 600), 0.001f)
        assertEquals(0f, line.charProgress(16_000), 0.001f)
        assertEquals(1f, line.charProgress(30_000), 0.001f)
    }

    @Test
    fun `parse prefers word timings and keeps the translation`() {
        val source = LyricsSource(
            lrc = "[00:16.21]潮水退去以后\n[00:20.00]沙上脚印\n[00:23.00]Hello world",
            translation = "[00:16.30]After the tide goes out\n[00:20.10]Footprints on the sand",
            wordTimed = yrc,
            instrumental = false,
        )
        val lines = LrcParser.parse(source, 60_000)
        assertNotNull(lines[1].words)
        assertEquals("After the tide goes out", lines[1].translation)
        assertEquals("Footprints on the sand", lines[2].translation)
    }
}
