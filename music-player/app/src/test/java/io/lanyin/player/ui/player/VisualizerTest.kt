package io.lanyin.player.ui.player

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerTest {

    @Test
    fun `demo spectrum stays in range and leans on the bass`() {
        val levels = FloatArray(SPECTRUM_BANDS)
        var bass = 0f
        var treble = 0f
        repeat(400) { frame ->
            demoSpectrum(frame / 60f, levels)
            levels.forEach { assertTrue(it in 0f..1f) }
            bass += levels.take(6).average().toFloat()
            treble += levels.takeLast(6).average().toFloat()
        }
        assertTrue("bass $bass should outweigh treble $treble", bass > treble * 1.5f)
    }

    @Test
    fun `a circle is sampled evenly with radial normals, starting at the top`() {
        val s = superellipseSamples(cx = 100f, cy = 100f, a = 50f, b = 50f, n = 2f, count = 64)
        assertEquals(64, s.count)
        // First point is straight up.
        assertEquals(100f, s.points[0], 0.5f)
        assertEquals(50f, s.points[1], 0.5f)
        for (i in 0 until s.count) {
            val dx = s.points[i * 2] - 100f
            val dy = s.points[i * 2 + 1] - 100f
            assertEquals(50f, hypot(dx, dy), 0.5f)
            // Unit normal pointing away from the centre.
            assertEquals(1f, hypot(s.normals[i * 2], s.normals[i * 2 + 1]), 1e-3f)
            assertEquals(dx / 50f, s.normals[i * 2], 0.02f)
            assertEquals(dy / 50f, s.normals[i * 2 + 1], 0.02f)
        }
        // Clockwise on screen: a quarter of the way round is the rightmost point.
        assertEquals(150f, s.points[16 * 2], 0.5f)
    }

    @Test
    fun `a rounded square keeps marks spread along its edges, not bunched in the corners`() {
        val s = superellipseSamples(cx = 0f, cy = 0f, a = 50f, b = 50f, n = 10f, count = 80)
        val gaps = (0 until s.count).map { i ->
            val j = (i + 1) % s.count
            hypot(s.points[j * 2] - s.points[i * 2], s.points[j * 2 + 1] - s.points[i * 2 + 1])
        }
        assertTrue(gaps.max() / gaps.min() < 1.1f)
        // Mid-edge normals are axis-aligned.
        assertEquals(-1f, s.normals[1], 1e-3f)
    }
}
