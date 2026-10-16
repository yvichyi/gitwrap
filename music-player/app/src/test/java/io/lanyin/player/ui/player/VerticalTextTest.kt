package io.lanyin.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalTextTest {
    @Test
    fun `characters stand upright, words lie sideways, brackets turn`() {
        assertEquals(
            listOf("潮" to true, "汐" to true, "︵" to true, "伴" to true, "奏" to true, "︶" to true),
            verticalRuns("潮汐（伴奏）", 20),
        )
        assertEquals(
            listOf("turn" to false, "tonight" to false, "sideways" to false),
            verticalRuns("turn tonight sideways", 20),
        )
        assertEquals(listOf("︽" to true, "歌" to true, "︾" to true), verticalRuns("《歌》", 20))
    }

    @Test
    fun `a title too long for its column ends in a vertical ellipsis`() {
        val runs = verticalRuns("一首名字特别长的歌", 4)
        assertEquals(listOf("一", "首", "名", "⋮"), runs.map { it.first })
    }
}
