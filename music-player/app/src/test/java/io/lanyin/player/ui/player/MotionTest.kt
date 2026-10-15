package io.lanyin.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `entrance runs each element in turn and lands everything`() {
        // Nothing has moved at the start, everything has arrived at the end.
        (0..6).forEach { i ->
            assertEquals(0f, entranceFraction(0f, i), 0f)
            assertEquals(1f, entranceFraction(1f, i), 0f)
        }
        // Mid-way, earlier elements are further along than later ones.
        val mid = (0..6).map { entranceFraction(0.4f, it) }
        assertTrue(mid.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(mid.first() > mid.last())
    }

    @Test
    fun `each element only ever moves forward`() {
        val samples = (0..100).map { entranceFraction(it / 100f, 3) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }
}
