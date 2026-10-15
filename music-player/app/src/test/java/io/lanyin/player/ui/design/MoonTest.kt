package io.lanyin.player.ui.design

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonTest {

    private fun near(expected: Float, actual: Float, days: Float = 1.2f) {
        // Distance round the cycle, in days.
        val d = minOf(kotlin.math.abs(actual - expected), 1f - kotlin.math.abs(actual - expected)) * 29.53f
        assertTrue("phase $actual is $d days from $expected", d <= days)
    }

    @Test
    fun `known new and full moons`() {
        near(0f, moonPhase(LocalDate.of(2024, 4, 8))) // the total solar eclipse
        near(0.5f, moonPhase(LocalDate.of(2024, 4, 23)))
        near(0f, moonPhase(LocalDate.of(2025, 9, 21)))
        near(0.5f, moonPhase(LocalDate.of(2025, 10, 7)))
    }

    @Test
    fun `names and tides follow the phase`() {
        assertEquals("新月", moonName(0.01f))
        assertEquals("上弦月", moonName(0.25f))
        assertEquals("满月", moonName(0.5f))
        assertEquals("下弦月", moonName(0.75f))
        assertEquals("新月", moonName(0.99f))
        assertEquals("大潮", tideName(0.5f))
        assertEquals("大潮", tideName(0.98f))
        assertEquals("小潮", tideName(0.25f))
        assertEquals("中潮", tideName(0.38f))
    }

    @Test
    fun `illumination runs from dark to full and back`() {
        assertEquals(0f, moonIllumination(0f), 0.001f)
        assertEquals(0.5f, moonIllumination(0.25f), 0.001f)
        assertEquals(1f, moonIllumination(0.5f), 0.001f)
        assertEquals(0.5f, moonIllumination(0.75f), 0.001f)
    }
}
