package io.lanyin.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverColorsTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    private fun hueOf(c: Int): Float {
        val r = (c shr 16 and 0xff) / 255f
        val g = (c shr 8 and 0xff) / 255f
        val b = (c and 0xff) / 255f
        val hi = maxOf(r, g, b)
        val d = hi - minOf(r, g, b)
        val h = when (hi) {
            r -> ((g - b) / d).mod(6f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return h * 60f
    }

    @Test
    fun `a small vivid subject wins over a large dull field`() {
        // 85% dusty grey-brown, 15% saturated blue.
        val pixels = IntArray(1000) { if (it < 850) rgb(120, 104, 96) else rgb(30, 90, 230) }
        val seeds = CoverColors.seeds(pixels)!!
        assertEquals(222f, hueOf(seeds.primary), 12f)
    }

    @Test
    fun `two distinct hues give a second seed, one hue does not`() {
        val two = IntArray(1000) { if (it < 600) rgb(220, 60, 50) else rgb(40, 170, 160) }
        val seeds = CoverColors.seeds(two)!!
        assertEquals(4f, hueOf(seeds.primary), 10f)
        assertEquals(176f, hueOf(seeds.secondary!!), 12f)

        val one = IntArray(1000) { rgb(220, 60 + it % 20, 50) }
        assertNull(CoverColors.seeds(one)!!.secondary)
    }

    @Test
    fun `black and white and grey covers have no seeds`() {
        val grey = IntArray(1000) { val v = it % 256; rgb(v, v, (v + 6).coerceAtMost(255)) }
        assertNull(CoverColors.seeds(grey))
        assertNull(CoverColors.seeds(IntArray(0)))
        // Fully transparent pixels are ignored.
        assertNull(CoverColors.seeds(IntArray(100) { 0x00FF0000 }))
    }

    @Test
    fun `fitted accents read against light and dark backgrounds and keep their hue`() {
        val paper = rgb(0xF4, 0xF1, 0xEA)
        val night = rgb(0x0A, 0x0F, 0x1C)
        for (seed in listOf(rgb(255, 230, 0), rgb(30, 90, 230), rgb(250, 160, 190), rgb(20, 20, 60), rgb(0, 200, 80))) {
            val light = CoverColors.fit(seed, paper, dark = false)
            val dark = CoverColors.fit(seed, night, dark = true)
            assertTrue("light ${Integer.toHexString(light)}", CoverColors.contrast(light, paper) >= 4.5f)
            assertTrue("dark ${Integer.toHexString(dark)}", CoverColors.contrast(dark, night) >= 4.5f)
            assertEquals(hueOf(seed), hueOf(light), 8f)
            assertEquals(hueOf(seed), hueOf(dark), 8f)
        }
    }

    @Test
    fun `text on an accent fill is white or near black, whichever reads`() {
        assertEquals(0xFFFFFFFF.toInt(), CoverColors.onColor(rgb(20, 60, 160)))
        assertEquals(0xFF101114.toInt(), CoverColors.onColor(rgb(250, 220, 90)))
        assertNotNull(CoverColors.contrast(rgb(0, 0, 0), rgb(255, 255, 255)).takeIf { it > 20.9f })
    }
}
