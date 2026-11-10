package io.lanyin.player.ui.design

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 霓's palette: twenty-seven colours for a rainy night. The night's blues and greys up to the
 * white of a headlight, the sodium lamps' amber, the brake lights' red and the teal of a sign.
 */
internal object Drive {
    const val K0 = 0xFF05060A.toInt()
    const val K1 = 0xFF0A0D14.toInt()
    const val K2 = 0xFF10141E.toInt()
    const val K3 = 0xFF171D2B.toInt()
    const val K4 = 0xFF20283A.toInt()
    const val K5 = 0xFF2B354B.toInt()
    const val K6 = 0xFF3A4660.toInt()
    const val K7 = 0xFF4F5D78.toInt()
    const val K8 = 0xFF6D7E9A.toInt()
    const val K9 = 0xFF9FB2C8.toInt()
    const val WH = 0xFFD8E4EE.toInt()
    const val A0 = 0xFF2A1A14.toInt()
    const val A1 = 0xFF4E2C1A.toInt()
    const val A2 = 0xFF80461F.toInt()
    const val A3 = 0xFFB8682A.toInt()
    const val A4 = 0xFFE89A3E.toInt()
    const val A5 = 0xFFFFC874.toInt()
    const val A6 = 0xFFFFF0C8.toInt()
    const val R0 = 0xFF3A0E12.toInt()
    const val R1 = 0xFF7A1A1E.toInt()
    const val R2 = 0xFFD0302C.toInt()
    const val R3 = 0xFFFF6A4A.toInt()
    const val T0 = 0xFF0C2E33.toInt()
    const val T1 = 0xFF16595A.toInt()
    const val T2 = 0xFF2A9A8E.toInt()
    const val T3 = 0xFF6FE0C8.toInt()

    val PALETTE = intArrayOf(K0, K1, K2, K3, K4, K5, K6, K7, K8, K9, WH, 0xFF1A1210.toInt(), A0, A1, A2, A3, A4, A5, A6, R0, R1, R2, R3, T0, T1, T2, T3)
    val AMBER = intArrayOf(A6, A5, A4, A3, A2, A1, A0)
    val RED = intArrayOf(R3, R2, R1, R0)
    val TEAL = intArrayOf(T3, T2, T1, T0)

    fun lum(c: Int): Float = (0.3f * (c shr 16 and 0xff) + 0.59f * (c shr 8 and 0xff) + 0.11f * (c and 0xff)) / 255f

    private val B4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /**
     * [pixels] (ARGB) locked to the palette with 4×4 ordered dithering, as the concept's pixel art
     * is: soft gradients become stepped bands of real palette colours.
     */
    fun dither(pixels: IntArray, w: Int, h: Int, spread: Float = 0.06f): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until h) for (x in 0 until w) {
            val p = pixels[y * w + x]
            val th = (B4[(y and 3) * 4 + (x and 3)] / 16f - 0.5f) * spread * 255f
            val r = (p shr 16 and 0xff) + th
            val g = (p shr 8 and 0xff) + th
            val b = (p and 0xff) + th
            var best = 0
            var bestD = Float.MAX_VALUE
            for (c in PALETTE) {
                val dr = r - (c shr 16 and 0xff)
                val dg = g - (c shr 8 and 0xff)
                val db = b - (c and 0xff)
                val d = 0.3f * dr * dr + 0.59f * dg * dg + 0.11f * db * db
                if (d < bestD) {
                    bestD = d
                    best = c
                }
            }
            out[y * w + x] = best
        }
        return out
    }
}

/**
 * The view through the windscreen on a rainy night on the expressway, as pixel art [w]×[h]
 * pixels: the city's skyline in the haze with its tower lights and a teal sign, sodium lamps on
 * the barrier coming towards you and sliding past, a car ahead braking now and then, the lights
 * dragged out in streaks on the wet deck; inside, the roof lining with a photo tucked in the
 * visor, the rear-view mirror with headlights behind and a little pine tree hanging from it, the
 * pillars, and the cat on the dashboard watching the road. Rain falls, drops gather on the glass
 * and the wipers clear them every so often.
 *
 * What never moves is drawn once ([bg] behind, [fg] in front); [frame] lays the moving parts
 * between them. Positions are worked out on the concept's 130-pixel-wide frame and stretched to
 * the width.
 */
internal class Windshield(val w: Int, val h: Int, seed: Int) {
    val gt = max(5, (h * 0.09f).toInt())
    val gb = h - max(6, (h * 0.11f).toInt())
    val hz = gt + ((gb - gt) * 0.6f).toInt()
    private val vx = w / 2f
    private val sx = w / 130f
    private val sy = (gb - gt) / 66f
    private val bg = IntArray(w * h)
    private val fg = IntArray(w * h)
    private val rnd = java.util.Random(seed.toLong())
    private val staticLights = ArrayList<FloatArray>()
    private val signX: Int
    private val signY: Int

    private class Drop(val x: Int, val y: Int, val again: Float, val angle: Float, val right: Boolean)
    private val drops: List<Drop>
    private val streaks: List<IntArray>
    private val leftPivot get() = floatArrayOf(18f * sx, gb.toFloat() - 1f)
    private val rightPivot get() = floatArrayOf(vx + 2f * sx, gb.toFloat() - 1f)

    init {
        // ── the sky, in bands, a checkered seam between each ──
        val bands = arrayOf(intArrayOf(Drive.K1, Drive.K1), intArrayOf(Drive.K1, Drive.K2), intArrayOf(Drive.K2, Drive.K3), intArrayOf(Drive.K3, Drive.K4), intArrayOf(Drive.K4, Drive.K5), intArrayOf(Drive.K5, Drive.A1))
        rect(bg, 0, 0, w - 1, gt, Drive.K1)
        for (i in bands.indices) {
            val y0 = gt + (hz - gt) * i / bands.size
            val y1 = gt + (hz - gt) * (i + 1) / bands.size - 1
            rect(bg, 0, y0, w - 1, y1, bands[i][1])
            for (x in 0 until w) if ((x + y0) % 2 == 0) set(bg, x, y0, bands[i][0])
        }
        for (x in 0 until w step 3) set(bg, x, hz - 3, Drive.K5)
        // Clouds: long wobbling lines lit faintly from below by the city.
        for ((fy, f0, f1) in listOf(Triple(0.12f, 0.14f, 0.45f), Triple(0.28f, 0.5f, 0.9f), Triple(0.4f, 0.17f, 0.38f), Triple(0.55f, 0.6f, 0.85f), Triple(0.66f, 0.23f, 0.46f))) {
            val cy = gt + ((hz - gt) * fy).toInt()
            for (x in (w * f0).toInt() until (w * f1).toInt()) {
                val wob = (sin(x * 0.45f + cy) * 1.2f).toInt()
                set(bg, x, cy + wob, if (fy < 0.3f) Drive.K3 else Drive.K4)
                if ((x + cy) % 3 != 0) set(bg, x, cy + wob + 1, if (fy > 0.3f) Drive.A0 else Drive.K2)
            }
        }
        // Below the horizon: black, then the deck.
        rect(bg, 0, hz + 1, w - 1, gb + 2, Drive.K0)
        // Far skyline: low, grey-blue, glittering.
        var x = (8 * sx).toInt()
        while (x < w - 8 * sx) {
            val tw = 3 + rnd.nextInt(5)
            val th = ((3 + rnd.nextInt(9)) * sy).toInt()
            tower(x, x + tw, hz - th, Drive.K4, Drive.A2, 0.3f, null, false, null)
            x += tw + rnd.nextInt(2)
        }
        // Mid towers: darker, taller, crowned, a red light on some.
        x = (10 * sx).toInt() + rnd.nextInt(4)
        var k = 0
        var signTower = -1
        val signAt = (w * 0.76f).toInt()
        while (x < w - 10 * sx) {
            val tw = 5 + rnd.nextInt(6)
            val top = gt + ((6 + rnd.nextInt(18)) * sy).toInt()
            val crown = when (k % 4) { 0 -> Drive.A3; 2 -> Drive.T2; else -> null }
            tower(x, x + tw, top, Drive.K1, Drive.A3, 0.24f, crown, k % 3 == 0, if (k % 2 == 0) Drive.K3 else Drive.K2)
            for (j in 0 until 3) staticLights.add(floatArrayOf(x + 1f + rnd.nextFloat() * (tw - 2), hz + 1f, 0f, 3f, 0.45f))
            if (signTower < 0 && x + tw >= signAt) signTower = x
            x += tw + 2 + rnd.nextInt(5)
            k++
        }
        // The vertical sign: two characters in teal, glowing.
        signX = (if (signTower > 0) signTower else signAt) + 1
        signY = gt + (14 * sy).toInt()
        rect(bg, signX - 1, signY - 1, signX + 3, signY + 14, Drive.K0)
        glow(bg, signX + 1f, signY + 6f, 7f, Drive.TEAL, 2)
        drawSign(bg, true)
        staticLights.add(floatArrayOf(signX + 1f, hz + 1f, 2f, 1f, 0.6f))
        // The deck: the road between the barriers, the rails, the reflectors.
        for (y in hz + 1..gb + 2) {
            val l = road(-40f, y)
            val r = road(168f, y)
            for (xx in max(0, l.toInt())..min(w - 1, r.toInt())) set(bg, xx, y, Drive.K1)
        }
        for (xn in floatArrayOf(-6f, 134f)) {
            var z = 1f
            while (z < 60f) {
                val p0 = pp(xn, 74f, z)
                val p1 = pp(xn, 64f, z)
                for (yy in p1[1].roundToInt()..p0[1].roundToInt()) if (yy in gt..gb + 2) set(bg, p0[0].roundToInt(), yy, if (yy == p1[1].roundToInt()) Drive.K5 else Drive.K3)
                z *= 1.012f
            }
            for (zz in floatArrayOf(1.3f, 1.8f, 2.5f, 3.5f, 5f, 7f, 10f, 14f)) {
                val r = pp(xn, 68f, zz)
                set(bg, r[0].roundToInt(), r[1].roundToInt(), if (zz < 4f) Drive.A4 else Drive.A3)
            }
        }
        for (xn in floatArrayOf(8f, 120f)) {
            val a = pp(xn, 74f, 1f)
            val b = pp(xn, 74f, 40f)
            line(bg, a[0], a[1], b[0], b[1], Drive.K4)
        }
        // A gantry far ahead with the city's name on it, straight on.
        val gz = 4.2f
        val g0 = pp(-6f, -6f, gz)
        val g1 = pp(134f, -6f, gz)
        val b0 = pp(-6f, 64f, gz)
        val b1 = pp(134f, 64f, gz)
        line(bg, b0[0], b0[1], g0[0], g0[1], Drive.K3)
        line(bg, b1[0], b1[1], g1[0], g1[1], Drive.K3)
        line(bg, g0[0], g0[1], g1[0], g1[1], Drive.K3)
        val gx0 = (vx + 6 * sx).toInt()
        val gy0 = g0[1].toInt() + 1
        rect(bg, gx0, gy0, gx0 + 17, gy0 + 7, Drive.T1)
        rect(bg, gx0, gy0, gx0 + 17, gy0, Drive.T2)
        for ((dx, dy) in listOf(2 to 2, 2 to 3, 2 to 4, 2 to 5, 1 to 3, 3 to 3)) set(bg, gx0 + dx, gy0 + dy, Drive.WH)
        for ((dx, dy) in listOf(6 to 2, 7 to 2, 8 to 2, 6 to 4, 8 to 4, 7 to 5, 10 to 2, 12 to 2, 11 to 3, 11 to 4, 10 to 5, 12 to 5, 14 to 2, 15 to 3, 14 to 4, 15 to 5)) set(bg, gx0 + dx, gy0 + dy, Drive.K9)

        // ── inside the car (in front of everything) ──
        // Roof lining, the overhead console and the sun visors, a photo tucked into the driver's.
        for (xx in 0 until w) {
            val edge = gt - 1 + ((xx - vx) / vx).pow(2) * 4f
            for (yy in 0..edge.toInt()) set(fg, xx, yy, if ((xx + yy * 3) % 7 != 0) Drive.K2 else Drive.K3)
        }
        val cx = vx.toInt()
        rect(fg, cx - 13, 0, cx + 13, 3, Drive.K1)
        rect(fg, cx - 12, 1, cx + 12, 2, Drive.K3)
        set(fg, cx - 8, 1, Drive.A4); set(fg, cx + 8, 1, Drive.K6); set(fg, cx, 1, Drive.R1)
        val vL = (14 * sx).toInt()
        rect(fg, vL, gt - 2, vL + 30, gt + 3, Drive.K3); line(fg, vL.toFloat(), gt + 3f, vL + 30f, gt + 3f, Drive.K1); line(fg, vL + 1f, gt - 2f, vL + 29f, gt - 2f, Drive.K5)
        rect(fg, vL + 6, gt - 1, vL + 13, gt + 3, Drive.WH); rect(fg, vL + 7, gt, vL + 12, gt + 2, Drive.K6); set(fg, vL + 9, gt + 2, Drive.A4); set(fg, vL + 10, gt + 1, Drive.A3)
        val vR = (w - 14 * sx - 30).toInt()
        rect(fg, vR, gt - 2, vR + 30, gt + 3, Drive.K3); line(fg, vR.toFloat(), gt + 3f, vR + 30f, gt + 3f, Drive.K1); line(fg, vR + 1f, gt - 2f, vR + 29f, gt - 2f, Drive.K5)
        // Pillars, their edges catching the street light.
        for (yy in gt - 2..gb + 2) {
            val f = (yy - gt + 2f) / (gb - gt + 4f)
            val reach = (10 + 8 * f).toInt()
            for (xx in 0..reach) set(fg, xx, yy, Drive.K1)
            for (xx in w - 1 - reach until w) set(fg, xx, yy, Drive.K1)
            set(fg, reach, yy, Drive.K5)
            set(fg, w - 1 - reach, yy, Drive.A1)
        }
        // The rear-view mirror: a strip of the road behind, two headlights following.
        rect(fg, cx - 1, 2, cx + 1, gt, Drive.K1)
        rect(fg, cx - 17, gt, cx + 17, gt + 9, Drive.K0)
        rect(fg, cx - 16, gt + 1, cx + 16, gt + 8, Drive.K3)
        rect(fg, cx - 15, gt + 2, cx + 15, gt + 7, Drive.K2)
        rect(fg, cx - 15, gt + 5, cx + 15, gt + 7, Drive.K1)
        for (hx in intArrayOf(cx - 6, cx + 4)) {
            set(fg, hx, gt + 5, Drive.WH); set(fg, hx + 1, gt + 5, Drive.K9)
        }
        set(fg, cx - 13, gt + 3, Drive.A2); set(fg, cx + 12, gt + 3, Drive.A3)
        line(fg, cx - 16f, gt + 9f, cx + 16f, gt + 9f, Drive.K5)
        // The little pine tree hung from the mirror.
        val tx = cx + 10
        line(fg, tx - 1f, gt + 10f, tx.toFloat(), gt + 15f, Drive.K6)
        for ((r, row) in listOf(16 to 1, 17 to 2, 18 to 3, 19 to 2, 20 to 3, 21 to 4, 22 to 2).withIndex()) {
            val (dy, half) = row
            rect(fg, tx - half, gt + dy, tx + half, gt + dy, if (r % 2 == 1) Drive.T1 else Drive.T2)
        }
        set(fg, tx - 1, gt + 17, Drive.T3); set(fg, tx + 1, gt + 20, Drive.T3); rect(fg, tx, gt + 23, tx, gt + 24, Drive.A1)
        // The top of the dashboard: an amber rim, the defroster slots, and the cat.
        for (xx in 0 until w) {
            val top = gb - 1 + (((xx - vx) / vx).pow(2) * 3f).toInt()
            for (yy in top until h) set(fg, xx, yy, Drive.K2)
            set(fg, xx, top, if (xx % 9 != 0) Drive.K5 else Drive.A1)
        }
        for (xx in (24 * sx).toInt() until (50 * sx).toInt() step 2) { set(fg, xx, gb + 3, Drive.K0); set(fg, xx, gb + 4, Drive.K0) }
        for (xx in (w - 50 * sx).toInt() until (w - 24 * sx).toInt() step 2) { set(fg, xx, gb + 3, Drive.K0); set(fg, xx, gb + 4, Drive.K0) }
        val cat = arrayOf("..#....#..", ".###..###.", ".########.", "##########", "##########", ".########.", ".########.", "##########", "###########", "############")
        val catX = w - 30
        val catY = gb - 9
        for ((j, row) in cat.withIndex()) for ((i, ch) in row.withIndex()) if (ch == '#') set(fg, catX + i, catY + j, Drive.K0)
        set(fg, catX + 2, catY + 2, Drive.K3); set(fg, catX + 7, catY + 2, Drive.K3)
        for (t in 0 until 5) set(fg, catX + 11 + (if (t > 2) 1 else 0), catY + 9 - t, Drive.K0)
        line(fg, catX + 1f, catY + 1f, catX + 3f, catY.toFloat(), Drive.A1)
        // Drops' shadows on the dash.
        for (i in 0 until w / 2) {
            val sx0 = 4 + rnd.nextInt(max(1, w - 8))
            val sy0 = gb + 6 + rnd.nextInt(max(1, h - gb - 6))
            if (fg[sy0.coerceIn(0, h - 1) * w + sx0] != 0) set(fg, sx0, sy0, if (rnd.nextFloat() < 0.7f) Drive.K3 else Drive.K4)
        }

        // Drops on the glass, and where the rain falls.
        val list = ArrayList<Drop>()
        repeat((w * 0.36f).toInt()) {
            val dx = (18 + rnd.nextInt(max(1, w - 36)))
            val dy = (gt + 3 + rnd.nextInt(max(1, gb - gt - 7)))
            val right = dx > vx
            val pv = if (right) rightPivot else leftPivot
            list.add(Drop(dx, dy, 1.5f + rnd.nextFloat() * 6f, atan2(pv[1] - dy, dx - pv[0]), right))
        }
        drops = list
        streaks = List((w * 0.55f).toInt()) { intArrayOf(rnd.nextInt(w), rnd.nextInt(max(1, gb - gt)), 18 + rnd.nextInt(18)) }
    }

    // ── drawing helpers ──
    private fun set(a: IntArray, x: Int, y: Int, c: Int) {
        if (x in 0 until w && y in 0 until h) a[y * w + x] = c
    }

    private fun rect(a: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in max(0, y0)..min(h - 1, y1)) for (x in max(0, x0)..min(w - 1, x1)) a[y * w + x] = c
    }

    private fun line(a: IntArray, x0: Float, y0: Float, x1: Float, y1: Float, c: Int) {
        val n = max(abs(x1 - x0), abs(y1 - y0)).toInt() + 1
        for (i in 0..n) {
            val t = i / n.toFloat()
            set(a, (x0 + (x1 - x0) * t).roundToInt(), (y0 + (y1 - y0) * t).roundToInt(), c)
        }
    }

    /** Brightens only: a ring of each colour of the ramp, the outermost checkered. */
    private fun glow(a: IntArray, x: Float, y: Float, r: Float, ramp: IntArray, start: Int) {
        val n = ramp.size - start
        if (n <= 0) return
        for (yy in (y - r - 1).toInt()..(y + r + 1).toInt()) {
            if (yy !in gt..gb) continue
            for (xx in (x - r - 1).toInt()..(x + r + 1).toInt()) {
                if (xx !in 0 until w) continue
                val d = hypot(xx - x, (yy - y) * 1.15f)
                if (d > r) continue
                val lvl = (d / r * n).toInt()
                if (lvl >= n) continue
                if (lvl == n - 1 && (xx + yy) % 2 != 0) continue
                val c = ramp[start + lvl]
                val i = yy * w + xx
                if (Drive.lum(c) > Drive.lum(a[i])) a[i] = c
            }
        }
    }

    private fun brighten(a: IntArray, x: Int, y: Int, c: Int) {
        if (x in 0 until w && y in gt..gb) {
            val i = y * w + x
            if (Drive.lum(c) > Drive.lum(a[i])) a[i] = c
        }
    }

    /** Where a point of the road at [xn] (the concept's frame) and depth [z] lands on the glass. */
    private fun pp(xn: Float, yn: Float, z: Float) = floatArrayOf(vx + (xn - 64f) * sx / z, hz + (yn - 47f) * sy / z)

    /** The x of road coordinate [xn] on row [y] of the deck. */
    private fun road(xn: Float, y: Int): Float = vx + (xn - 64f) * sx * (y - hz) / (27f * sy)

    private fun tower(x0: Int, x1: Int, top: Int, body: Int, win: Int, lit: Float, crown: Int?, red: Boolean, edge: Int?) {
        rect(bg, x0, top, x1, hz, body)
        if (edge != null) for (y in top..hz) set(bg, x0, y, edge)
        var y = top + 3
        while (y < hz - 1) {
            var x = x0 + 1
            while (x < x1) {
                if (rnd.nextFloat() < lit) set(bg, x, y, if (rnd.nextFloat() < 0.8f) win else Drive.K6)
                x += 2
            }
            y += 2
        }
        if (crown != null) {
            for (x in x0..x1) if (x % 2 == 0) set(bg, x, top, crown)
            set(bg, x0, top + 1, crown); set(bg, x1, top + 1, crown)
        }
        if (red) {
            val cx = (x0 + x1) / 2
            for (yy in top - 4 until top) set(bg, cx, yy, body)
            set(bg, cx, top - 5, Drive.R3)
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) set(bg, cx + dx, top - 5 + dy, Drive.R1)
        }
    }

    private fun drawSign(a: IntArray, lit: Boolean) {
        val glyph = arrayOf("#.#", "###", ".#.", "#.#", "...", "###", "#.#", "###", "#.#", "#.#")
        for ((j, row) in glyph.withIndex()) for ((i, ch) in row.withIndex()) {
            if (ch == '#') set(a, signX + i, signY + j + (if (j > 4) 1 else 0), if (lit) Drive.T3 else Drive.T0)
        }
    }

    private val car = arrayOf("..##########..", ".#3333333333#.", "#344444444443#", "##############", "#rR########Rr#", "#22222pp22222#", "##############", ".00........00.")

    /**
     * One frame at [t] seconds into [out], [travel] seconds of driving along (it stops when the
     * car does); the wipers run every [wipeEvery] seconds.
     */
    fun frame(t: Float, out: IntArray, travel: Float = t, wipeEvery: Float = 9f) {
        System.arraycopy(bg, 0, out, 0, bg.size)
        val lights = ArrayList<FloatArray>(staticLights)
        // Lane markings coming at you.
        var zz = 1.1f * 1.38f.pow(1f - (travel * 0.9f) % 1f)
        while (zz < 30f) {
            val a = pp(64f, 74f, zz)
            val b = pp(64f, 74f, zz * 1.14f)
            line(out, a[0], a[1], b[0], b[1], if (zz < 4f) Drive.K6 else Drive.K4)
            zz *= 1.38f
        }
        // The sign stutters off now and then.
        if (RisoScreen.noise(floor(t * 6f).toInt(), 3, 17) > 0.94f) drawSign(out, false)
        // Lamp posts on the left barrier, sodium heads leaning out over the road, coming past.
        val n = 7
        for (k in 0 until n) {
            val ph = ((travel * 0.22f + k / n.toFloat()) % 1f)
            val z = 1.15f * 13f.pow(1f - ph)
            val base = pp(-6f, 64f, z)
            val top = pp(-6f, -50f, z)
            val head = pp(20f, -48f, z)
            top[1] = max(top[1], gt.toFloat())
            if (z < 7f) {
                val c = if (z < 2f) Drive.K2 else Drive.K3
                line(out, base[0], base[1], top[0], top[1], c)
                line(out, top[0], top[1], head[0], head[1], c)
            }
            glow(out, head[0], head[1], 1.5f + 6f / z, Drive.AMBER, 1)
            set(out, head[0].roundToInt(), head[1].roundToInt(), Drive.A6)
            lights.add(floatArrayOf(head[0], pp(20f, 74f, z)[1], 0f, 1f, 0.8f * min(1f, 2.4f / z)))
        }
        // The car ahead, braking now and then, and one further off.
        val braking = (t % 7f) < 2.2f
        val bx = (vx + 14 * sx - car[0].length / 2f).toInt()
        val by = gb - (12 * sy).toInt() - car.size
        for ((j, row) in car.withIndex()) for ((i, ch) in row.withIndex()) {
            val c = when (ch) {
                '#', '0' -> Drive.K0; '3' -> Drive.K3; '4' -> Drive.K4; '2' -> Drive.K2; 'p' -> Drive.K7; 'r' -> Drive.R2
                'R' -> if (braking) Drive.R3 else Drive.R2
                else -> 0
            }
            if (c != 0) set(out, bx + i, by + j, c)
        }
        for (lx in floatArrayOf(bx + 1.5f, bx + car[0].length - 2.5f)) {
            glow(out, lx, by + 4f, if (braking) 4.5f else 3.5f, Drive.RED, 1)
            lights.add(floatArrayOf(lx, by + car.size + 1f, 1f, 1f, 0.95f))
        }
        if (braking) {
            set(out, bx + 6, by + 1, Drive.R3); set(out, bx + 7, by + 1, Drive.R3)
        }
        val fx = (vx - 8 * sx).toInt()
        val fy = hz + (6 * sy).toInt()
        rect(out, fx, fy - 3, fx + 5, fy - 1, Drive.K0)
        rect(out, fx + 1, fy - 4, fx + 4, fy - 4, Drive.K0)
        set(out, fx, fy - 2, Drive.R2); set(out, fx + 5, fy - 2, Drive.R2)
        glow(out, fx.toFloat(), fy - 2f, 2.2f, Drive.RED, 1)
        glow(out, fx + 5f, fy - 2f, 2.2f, Drive.RED, 1)
        lights.add(floatArrayOf(fx + 2.5f, fy.toFloat(), 1f, 1f, 0.6f))
        // The wet deck drags every light out towards you in a stepped streak.
        for (l in lights) {
            val (lx, ly) = l[0] to l[1]
            if (ly < hz || ly > gb) continue
            val ramp = when (l[2].toInt()) { 0 -> Drive.AMBER; 1 -> Drive.RED; else -> Drive.TEAL }
            val start = l[3].toInt() + 1
            val cols = ramp.size - start
            if (cols <= 0) continue
            val len = ((5 + (ly - hz) * 1.1f / sy) * l[4]).toInt() + 2
            for (i in 0 until len) {
                val yy = (ly + 1 + i).toInt()
                if (yy > gb) break
                if (yy % 3 == 1 && i > 2) continue
                val lvl = min(cols - 1, (i / len.toFloat() * cols).toInt())
                brighten(out, lx.roundToInt(), yy, ramp[start + lvl])
            }
        }
        // Rain: short slanted streaks falling past.
        val span = gb - gt
        for (s in streaks) {
            val y0 = gt + ((s[1] + t * s[2] * 3f) % span).toInt()
            for (k in 0 until 3) {
                val xx = s[0] - k / 2 - ((t * s[2] * 0.4f) % 1f).toInt()
                val yy = y0 + k
                if (yy > gb || xx !in 0 until w) continue
                brighten(out, xx, yy, if (Drive.lum(out[yy * w + xx]) < 0.1f) Drive.K5 else Drive.K7)
            }
        }
        // Drops on the glass, gathering again after the wipers have been across.
        val cycle = t % wipeEvery
        val sweep = 1.2f
        val armAngle = when {
            cycle < sweep / 2 -> cycle / (sweep / 2)
            cycle < sweep -> 1f - (cycle - sweep / 2) / (sweep / 2)
            else -> 0f
        }
        val reach = (PI.toFloat() * 0.62f) * armAngle
        for (d in drops) {
            val wiped = when {
                cycle < sweep / 2 -> d.angle < reach
                else -> cycle - sweep < d.again
            }
            if (wiped && t > sweep) continue
            val under = Drive.lum(out[d.y * w + d.x])
            val hi = if (under > 0.2f) Drive.K9 else if (under > 0.06f) Drive.K7 else Drive.K5
            set(out, d.x, d.y, hi)
            set(out, d.x + 1, d.y, if (under < 0.1f) Drive.K4 else Drive.K6)
            set(out, d.x, d.y + 1, Drive.K0)
            set(out, d.x + 1, d.y + 1, Drive.K0)
        }
        // The wipers: at rest along the bottom of the glass, or sweeping.
        for ((pivot, len) in listOf(leftPivot to 46f * sx, rightPivot to 44f * sx)) {
            val a = -0.12f - reach
            val ex = pivot[0] + cos(a) * len
            val ey = pivot[1] + sin(a) * len
            line(out, pivot[0], pivot[1] + 1, ex, ey + 1, Drive.K0)
            line(out, pivot[0], pivot[1], ex, ey, Drive.K3)
        }
        // And the car itself, in front of it all.
        for (i in fg.indices) if (fg[i] != 0) out[i] = fg[i]
    }
}

/**
 * The windscreen, drawn [pixel] to a pixel and animated while [running]: the rain, the lamps
 * coming past, the car ahead braking, the wipers every few seconds. [content] lays out over it
 * (the head-up display).
 */
@Composable
fun NightWindshield(
    modifier: Modifier = Modifier,
    seed: Int = 7,
    pixel: Dp = 3.dp,
    running: Boolean = true,
    speed: Float = 1f,
    content: @Composable BoxWithConstraintsScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { pixel.toPx() }
        val w = (constraints.maxWidth / px).toInt().coerceAtLeast(40)
        val h = (constraints.maxHeight / px).toInt().coerceAtLeast(30)
        val scene = remember(w, h, seed) { Windshield(w, h, seed) }
        val buffer = remember(w, h) { IntArray(w * h) }
        val bitmap = remember(w, h) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val clock = rememberFrameClock(running)
        // Twelve frames a second is the pixel art's own rate; the canvas redraws only on a new one.
        val tick by remember { derivedStateOf { (clock.floatValue * 12f).toInt() } }
        // How far we've driven: it only grows while the car moves, so stopping doesn't jump.
        val road = remember { floatArrayOf(0f, 0f) }
        Canvas(Modifier.fillMaxSize()) {
            val t = tick / 12f
            road[0] += (t - road[1]).coerceIn(0f, 0.5f) * speed
            road[1] = t
            scene.frame(3.5f + t, buffer, 3.5f + road[0])
            bitmap.setPixels(buffer, 0, w, 0, 0, w, h)
            drawImage(image, dstSize = IntSize((w * px).roundToInt(), (h * px).roundToInt()), filterQuality = FilterQuality.None)
        }
        content()
    }
}

/**
 * A cover as 霓 shows it: shrunk to [cells] pixels a side and locked to the night's palette with
 * ordered dithering, drawn with hard pixel edges.
 */
@Composable
fun PixelCover(url: String?, seed: Int, modifier: Modifier = Modifier, cells: Int = 36) {
    val pixels = rememberCoverPixels(url, seed, cells)
    val image = remember(pixels) {
        val out = Drive.dither(pixels, cells, cells, 0.1f)
        Bitmap.createBitmap(out, cells, cells, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Canvas(modifier) {
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/**
 * 霓's pages: the console's screen set into the dashboard. Near black with the faint lines of the
 * screen's pixel rows, and the air vents showing at its edges.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDashboard() {
    val w = size.width
    val h = size.height
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0E121B), androidx.compose.ui.graphics.Color(0xFF0A0D14), androidx.compose.ui.graphics.Color(0xFF07090E))))
    val px = 3.dp.toPx()
    var y = px * 0.67f
    while (y < h) {
        drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(w, px * 0.33f))
        y += px
    }
    for (side in 0..1) {
        val x0 = if (side == 0) 0f else w - 5 * px
        drawRect(androidx.compose.ui.graphics.Color(0xFF05060A), androidx.compose.ui.geometry.Offset(x0, h * 0.28f - px), androidx.compose.ui.geometry.Size(5 * px, 32 * px))
        for (k in 0 until 10) {
            val yy = h * 0.28f + k * 3 * px
            drawRect(androidx.compose.ui.graphics.Color(Drive.K4), androidx.compose.ui.geometry.Offset(x0 + (if (side == 0) 0f else px), yy), androidx.compose.ui.geometry.Size(4 * px, px))
            drawRect(androidx.compose.ui.graphics.Color(Drive.K2), androidx.compose.ui.geometry.Offset(x0 + (if (side == 0) 0f else px), yy + px), androidx.compose.ui.geometry.Size(4 * px, px))
        }
    }
}
