package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.lanyin.player.core.audio.AudioFeed
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.lanyinStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * What each theme's player backdrop costs to draw, frame after frame, with music playing: a
 * relative measure on the build machine's CPU renderer, not a phone's, printed for comparison.
 * It also fails if a frame takes absurdly long, which would mean something unbounded crept in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BackdropCostTest {

    /** Music of a sort: levels that move and a beat twice a second. */
    private class FakeFeed : AudioFeed {
        var t = 0f
        override fun levels(out: FloatArray, lagMs: Long): Boolean {
            for (i in out.indices) out[i] = (0.5f + 0.4f * sin(t * 3f + i * 0.4f)) * (1f - i / 48f)
            return true
        }
        override fun sinceBeat(): Float = t % 0.5f
        override fun beatPeriod(): Float = 0.5f
    }

    /** Counts what's drawn (on the platform canvas, so drawing that goes to it directly counts too), passing it all through. */
    private class CountingCanvas(bitmap: android.graphics.Bitmap) : android.graphics.Canvas(bitmap) {
        var calls = 0
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) { calls++; super.drawRect(left, top, right, bottom, paint) }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: android.graphics.Paint) { calls++; super.drawRoundRect(left, top, right, bottom, rx, ry, paint) }
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) { calls++; super.drawOval(left, top, right, bottom, paint) }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: android.graphics.Paint) { calls++; super.drawCircle(cx, cy, radius, paint) }
        override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: android.graphics.Paint) { calls++; super.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint) }
        override fun drawPath(path: android.graphics.Path, paint: android.graphics.Paint) { calls++; super.drawPath(path, paint) }
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: android.graphics.Paint) { calls++; super.drawLine(startX, startY, stopX, stopY, paint) }
        override fun drawBitmap(bitmap: android.graphics.Bitmap, left: Float, top: Float, paint: android.graphics.Paint?) { calls++; super.drawBitmap(bitmap, left, top, paint) }
        override fun drawBitmap(bitmap: android.graphics.Bitmap, src: android.graphics.Rect?, dst: android.graphics.Rect, paint: android.graphics.Paint?) { calls++; super.drawBitmap(bitmap, src, dst, paint) }
        override fun drawPoints(pts: FloatArray, offset: Int, count: Int, paint: android.graphics.Paint) { calls++; super.drawPoints(pts, offset, count, paint) }
        override fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: android.graphics.Paint) { calls++; super.drawLines(pts, offset, count, paint) }
        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: android.graphics.Paint) { calls++; super.drawText(text, index, count, x, y, paint) }
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { calls++; super.drawText(text, x, y, paint) }
        override fun drawPaint(paint: android.graphics.Paint) { calls++; super.drawPaint(paint) }
    }

    private class Cost(val ms: Double, val calls: Int, val kb: Double)

    // The JVM's own count of what this thread has allocated; reached by reflection because the
    // test compiles against Android's classes, which don't have it.
    private val threadBean: Any = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
    private val allocated = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private fun allocatedBytes(id: Long): Long = allocated.invoke(threadBean, id) as Long

    private fun msPerFrame(theme: AppTheme, dark: Boolean, w: Int, h: Int, frames: Int = 60): Cost {
        val style = lanyinStyle(theme, dark)
        val counting = CountingCanvas(android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888))
        val canvas = Canvas(counting)
        val scope = CanvasDrawScope()
        val feed = FakeFeed()
        val audio = BackdropAudio()
        val density = Density(2.75f)
        fun frame(k: Int) {
            val t = k / 60f
            feed.t = t
            audio.read(feed, t, 1f)
            scope.draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                drawBackdropFrame(style, t, 1f, page = false, heard = audio, progress = 0.4f, trackNumber = 1, measurer = null)
            }
        }
        repeat(10) { frame(it) } // warm up
        counting.calls = 0
        val id = Thread.currentThread().id
        val bytes = allocatedBytes(id)
        val start = System.nanoTime()
        for (k in 10 until 10 + frames) frame(k)
        val ms = (System.nanoTime() - start) / 1e6 / frames
        return Cost(ms, counting.calls / frames, (allocatedBytes(id) - bytes) / 1024.0 / frames)
    }

    @Test
    fun `every theme's player backdrop draws in bounded time`() {
        val report = StringBuilder("backdrop per frame, portrait 1080x2340 | landscape 2340x1080: ms (CPU raster), draw calls, KB allocated\n")
        for (theme in AppTheme.entries) {
            val dark = theme.darkOnly || !theme.lightOnly && theme == AppTheme.DUSK
            val port = msPerFrame(theme, dark, 1080, 2340)
            val land = msPerFrame(theme, dark, 2340, 1080)
            report.append("%-8s %7.1f %5d %7.1f | %7.1f %5d %7.1f\n".format(theme.name, port.ms, port.calls, port.kb, land.ms, land.calls, land.kb))
            // Generous: a CPU renderer on a shared machine; the point is to catch runaway work.
            assert(port.ms < 400 && land.ms < 400) { "${theme.name} took ${port.ms}/${land.ms} ms a frame" }
            assert(port.calls < 3000 && land.calls < 3000) { "${theme.name} draws ${port.calls}/${land.calls} times a frame" }
        }
        println(report)
    }
}
