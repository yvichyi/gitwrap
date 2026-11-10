package io.lanyin.player.ui.design

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.lanyinStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Backdrops that follow the clock, drawn at a few hours. Pictures land in build/outputs/roborazzi/time. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TimeOfDayTest {

    private fun draw(theme: AppTheme, dark: Boolean, hour: Float, w: Int = 540, h: Int = 1170): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1.4f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(w.toFloat(), h.toFloat())) {
            drawBackdropFrame(lanyinStyle(theme, dark), 3f, 1f, page = false, heard = null, progress = 0.3f, trackNumber = 1, measurer = null, hour = hour)
        }
        File("build/outputs/roborazzi/time").mkdirs()
        File("build/outputs/roborazzi/time/${theme.name.lowercase()}_${if (dark) "dark" else "light"}_${(hour * 10).toInt()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test
    fun `暮's desk lamp comes up as the evening draws in`() {
        val noon = draw(AppTheme.DUSK, false, 12f)
        val evening = draw(AppTheme.DUSK, false, 18.5f)
        draw(AppTheme.DUSK, false, 6.5f)
        draw(AppTheme.DUSK, false, 22f)
        // Up to the left, under the lamp: warmer in the evening than at noon, and warmer again at night.
        fun red(b: Bitmap) = android.graphics.Color.red(b.getPixel(110, 61))
        assert(red(evening) > red(noon)) { "no lamp in the evening" }
        val darkNoon = draw(AppTheme.DUSK, true, 12f)
        val darkNight = draw(AppTheme.DUSK, true, 22f)
        assert(red(darkNight) > red(darkNoon)) { "no lamp at night" }
    }

    @Test
    fun `墨 has its moon at night and not by day`() {
        val day = draw(AppTheme.INK, false, 12f)
        val night = draw(AppTheme.INK, false, 22f)
        // The moon's ring, up to the right.
        var differs = false
        for (x in 380 until 500 step 4) for (y in 60 until 180 step 4) if (day.getPixel(x, y) != night.getPixel(x, y)) differs = true
        assert(differs) { "no moon at night" }
        draw(AppTheme.NEON, true, 21f)
        draw(AppTheme.NEON, true, 2f)
    }

    @Test
    fun `nightness eases in and out round the evening and the small hours`() {
        assertEquals(0f, nightness(12f), 0f)
        assertEquals(1f, nightness(23f), 0f)
        assertEquals(1f, nightness(2f), 0f)
        assert(nightness(20f) in 0.1f..0.9f)
        assert(nightness(5f) in 0.1f..0.9f)
    }
}
