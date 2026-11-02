package io.lanyin.player.data.library

import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.core.model.LyricWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricOffsetsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val lines = listOf(
        LyricLine("第一句", null, 200, 2_000, words = listOf(LyricWord("第一", 200, 600), LyricWord("句", 800, 400))),
        LyricLine("第二句", "second", 2_000, 0),
    )

    @Test
    fun `shifting moves lines and words, never before the start`() {
        val later = lines.shifted(300)
        assertEquals(500L, later[0].startMs)
        assertEquals(2_300L, later[0].endMs)
        assertEquals(listOf(500L, 1_100L), later[0].words!!.map { it.startMs })
        assertEquals(0L, later[1].endMs)
        assertEquals("second", later[1].translation)
        val earlier = lines.shifted(-500)
        assertEquals(0L, earlier[0].startMs)
        assertEquals(listOf(0L, 300L), earlier[0].words!!.map { it.startMs })
        assertEquals(1_500L, earlier[1].startMs)
        assertSame(lines, lines.shifted(0))
    }

    @Test
    fun `offsets are remembered per song, and a song set back to zero is forgotten`() {
        val file = tmp.newFile("offsets.json")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val first = LyricOffsets(file, scope)
        first.set(42, -300)
        first.set(7, 500)
        assertEquals(-300L, first[42])
        assertEquals(0L, first[99])
        // Written off the main thread; give it a moment.
        repeat(50) { if (file.readText().contains("500")) return@repeat; Thread.sleep(20) }
        val again = LyricOffsets(file, scope)
        assertEquals(-300L, again[42])
        assertEquals(500L, again[7])
        again.set(42, 0)
        repeat(50) { if (!file.readText().contains("42")) return@repeat; Thread.sleep(20) }
        assertEquals(0L, LyricOffsets(file, scope)[42])
    }
}
