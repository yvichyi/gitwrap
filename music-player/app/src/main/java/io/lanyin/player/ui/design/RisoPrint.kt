package io.lanyin.player.ui.design

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coverage of the three inks (pink, yellow, blue), each 0..1, at ([x], [y]) in a print's pixels. */
internal fun interface InkField {
    fun sample(x: Float, y: Float, out: FloatArray)
}

/**
 * 暮's prints. A picture is separated into the three riso inks (fluorescent pink, sunflower,
 * medium blue) and each separation is screened into dots at its own angle, each drum landing a
 * hair out of register; then the three are printed over each other on cream paper, multiplying,
 * so pink over blue gives violet and pink over yellow orange. The ink lies a little unevenly.
 *
 * Making a print is a few million small sums, so it happens once per picture and size, off the
 * main thread (see [rememberPrint]).
 */
internal object RisoScreen {
    /** The inks and the paper in sRGB, 0..1. */
    val INKS = arrayOf(
        floatArrayOf(1f, 0.282f, 0.690f),
        floatArrayOf(1f, 0.710f, 0.067f),
        floatArrayOf(0.196f, 0.333f, 0.643f),
    )
    val PAPER = floatArrayOf(0.949f, 0.922f, 0.867f)

    /** Screen angles, as for cyan, magenta and yellow: pink at 75°, yellow at 0°, blue at 15°. */
    private val ANGLES = floatArrayOf(75f, 0f, 15f)

    /** Which way each drum lands off register: pink right and down, blue up and left. */
    private val SHIFT = arrayOf(floatArrayOf(0.9f, 0.55f), floatArrayOf(0f, 0f), floatArrayOf(-0.5f, -0.75f))

    // ── separation ──

    private const val N = 13
    private val lut: FloatArray by lazy { buildLut() }

    /**
     * For a grid of target colours, the mix of the three inks on paper that comes closest
     * (lightness counting most), slightly preferring less ink, as a riso print does.
     */
    private fun buildLut(): FloatArray {
        val levels = 11
        val count = levels * levels * levels
        val cr = FloatArray(count)
        val cg = FloatArray(count)
        val cb = FloatArray(count)
        val cy = FloatArray(count)
        val cov = FloatArray(count * 3)
        var i = 0
        for (p in 0 until levels) for (y in 0 until levels) for (b in 0 until levels) {
            val a = floatArrayOf(p / (levels - 1f), y / (levels - 1f), b / (levels - 1f))
            var r = PAPER[0]
            var g = PAPER[1]
            var bl = PAPER[2]
            for (k in 0 until 3) {
                r *= 1f - a[k] * (1f - INKS[k][0])
                g *= 1f - a[k] * (1f - INKS[k][1])
                bl *= 1f - a[k] * (1f - INKS[k][2])
            }
            cr[i] = r; cg[i] = g; cb[i] = bl
            cy[i] = 0.3f * r + 0.59f * g + 0.11f * bl
            cov[i * 3] = a[0]; cov[i * 3 + 1] = a[1]; cov[i * 3 + 2] = a[2]
            i++
        }
        val out = FloatArray(N * N * N * 3)
        for (ri in 0 until N) for (gi in 0 until N) for (bi in 0 until N) {
            val tr = ri / (N - 1f)
            val tg = gi / (N - 1f)
            val tb = bi / (N - 1f)
            val ty = 0.3f * tr + 0.59f * tg + 0.11f * tb
            var best = 0
            var bestD = Float.MAX_VALUE
            for (j in 0 until count) {
                val dy = cy[j] - ty
                val dr = cr[j] - tr
                val dg = cg[j] - tg
                val db = cb[j] - tb
                val d = 3f * dy * dy + dr * dr + dg * dg + db * db + 0.004f * (cov[j * 3] + cov[j * 3 + 1] + cov[j * 3 + 2])
                if (d < bestD) {
                    bestD = d
                    best = j
                }
            }
            val o = ((ri * N + gi) * N + bi) * 3
            out[o] = cov[best * 3]; out[o + 1] = cov[best * 3 + 1]; out[o + 2] = cov[best * 3 + 2]
        }
        return out
    }

    /** The ink coverages for an sRGB colour (0..1), interpolated over the table, into [out] at [at]. */
    fun separate(r: Float, g: Float, b: Float, out: FloatArray, index: Int = 0) {
        val t = lut
        val fr = r.coerceIn(0f, 1f) * (N - 1)
        val fg = g.coerceIn(0f, 1f) * (N - 1)
        val fb = b.coerceIn(0f, 1f) * (N - 1)
        val r0 = min(fr.toInt(), N - 2)
        val g0 = min(fg.toInt(), N - 2)
        val b0 = min(fb.toInt(), N - 2)
        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0
        for (k in 0 until 3) {
            fun at(ri: Int, gi: Int, bi: Int) = t[((ri * N + gi) * N + bi) * 3 + k]
            val c00 = at(r0, g0, b0) * (1 - db) + at(r0, g0, b0 + 1) * db
            val c01 = at(r0, g0 + 1, b0) * (1 - db) + at(r0, g0 + 1, b0 + 1) * db
            val c10 = at(r0 + 1, g0, b0) * (1 - db) + at(r0 + 1, g0, b0 + 1) * db
            val c11 = at(r0 + 1, g0 + 1, b0) * (1 - db) + at(r0 + 1, g0 + 1, b0 + 1) * db
            out[index + k] = (c00 * (1 - dg) + c01 * dg) * (1 - dr) + (c10 * (1 - dg) + c11 * dg) * dr
        }
    }

    /** A picture's three separations at its own resolution. */
    class Plates(val w: Int, val h: Int, val cov: Array<FloatArray>) {
        /** The plates laid over a [outW]×[outH] print, cropped to fill it, plus [warm] extra yellow. */
        fun field(outW: Int, outH: Int, warm: Float = 0f): InkField {
            val scale = max(outW / w.toFloat(), outH / h.toFloat())
            val ox = (outW - w * scale) / 2f
            val oy = (outH - h * scale) / 2f
            return InkField { x, y, out ->
                val sx = ((x - ox) / scale - 0.5f).coerceIn(0f, w - 1.001f)
                val sy = ((y - oy) / scale - 0.5f).coerceIn(0f, h - 1.001f)
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val fx = sx - x0
                val fy = sy - y0
                val i = y0 * w + x0
                for (k in 0 until 3) {
                    val p = cov[k]
                    out[k] = (p[i] * (1 - fx) + p[i + 1] * fx) * (1 - fy) + (p[i + w] * (1 - fx) + p[i + w + 1] * fx) * fy
                }
                if (warm > 0f) out[1] = out[1] + (1f - out[1]) * warm
            }
        }
    }

    /**
     * Separates ARGB [pixels]: levels stretched to the picture's own darkest and lightest, colour
     * pushed a little, as a print wants.
     */
    fun plates(pixels: IntArray, w: Int, h: Int, punch: Float = 1.18f): Plates {
        // Levels: the 2nd and 98th percentile of lightness become black and white.
        val hist = IntArray(256)
        for (p in pixels) hist[(((p shr 16 and 0xff) * 77 + (p shr 8 and 0xff) * 150 + (p and 0xff) * 29) shr 8).coerceIn(0, 255)]++
        fun pct(q: Float): Int {
            var acc = 0
            val target = (pixels.size * q).toInt()
            for (i in 0 until 256) {
                acc += hist[i]
                if (acc >= target) return i
            }
            return 255
        }
        val lo = pct(0.02f) / 255f
        val hi = max(pct(0.98f) / 255f, lo + 0.2f)
        val cov = Array(3) { FloatArray(w * h) }
        val tmp = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = ((p shr 16 and 0xff) / 255f - lo) / (hi - lo)
            var g = ((p shr 8 and 0xff) / 255f - lo) / (hi - lo)
            var b = ((p and 0xff) / 255f - lo) / (hi - lo)
            val y = 0.3f * r + 0.59f * g + 0.11f * b
            r = y + (r - y) * punch
            g = y + (g - y) * punch
            b = y + (b - y) * punch
            separate(r, g, b, tmp)
            cov[0][i] = tmp[0]; cov[1][i] = tmp[1]; cov[2][i] = tmp[2]
        }
        return Plates(w, h, cov)
    }

    // ── screening ──

    /**
     * Screens [field] into a [w]×[h] print, dots [cell] pixels apart. With [paper] false the result
     * is the ink alone, white where there is none, to be printed over another layer with a
     * multiply. [only] prints one drum (0 pink, 1 yellow, 2 blue): a separation proof.
     * [inside] keeps the ink to a shape (1 inside, 0 out), soft at its edge.
     */
    fun print(
        w: Int,
        h: Int,
        cell: Float,
        field: InkField,
        paper: Boolean = true,
        only: Int = -1,
        seed: Int = 0,
        misregister: Float = cell * 0.32f,
        inside: ((Float, Float) -> Float)? = null,
    ): IntArray {
        // Coverage on a grid twice as coarse as the pixels, sampled from there.
        val gs = 2
        val gw = w / gs + 2
        val gh = h / gs + 2
        val cov = Array(3) { FloatArray(gw * gh) }
        val tmp = FloatArray(3)
        for (j in 0 until gh) for (i in 0 until gw) {
            field.sample(i * gs.toFloat(), j * gs.toFloat(), tmp)
            val o = j * gw + i
            cov[0][o] = tmp[0]; cov[1][o] = tmp[1]; cov[2][o] = tmp[2]
        }
        fun coverAt(k: Int, x: Float, y: Float): Float {
            val gx = (x / gs).coerceIn(0f, gw - 1.001f)
            val gy = (y / gs).coerceIn(0f, gh - 1.001f)
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val fx = gx - x0
            val fy = gy - y0
            val p = cov[k]
            val i = y0 * gw + x0
            return (p[i] * (1 - fx) + p[i + 1] * fx) * (1 - fy) + (p[i + gw] * (1 - fx) + p[i + gw + 1] * fx) * fy
        }
        val cs = FloatArray(3) { cos(ANGLES[it] * PI.toFloat() / 180f) }
        val sn = FloatArray(3) { sin(ANGLES[it] * PI.toFloat() / 180f) }
        val out = IntArray(w * h)
        val invPi = 1f / PI.toFloat()
        for (y in 0 until h) {
            for (x in 0 until w) {
                var fr = 1f
                var fg = 1f
                var fb = 1f
                val keep = inside?.invoke(x + 0.5f, y + 0.5f) ?: 1f
                if (keep > 0f) for (k in 0 until 3) {
                    if (only >= 0 && k != only) continue
                    val sx = SHIFT[k][0] * misregister
                    val sy = SHIFT[k][1] * misregister
                    val px = x + 0.5f - sx
                    val py = y + 0.5f - sy
                    val c = cs[k]
                    val s = sn[k]
                    val u = (px * c + py * s) / cell
                    val v = (-px * s + py * c) / cell
                    val iu = floor(u)
                    val iv = floor(v)
                    val fu = u - iu - 0.5f
                    val fv = v - iv - 0.5f
                    val cu = (iu + 0.5f) * cell
                    val cv = (iv + 0.5f) * cell
                    val a = coverAt(k, cu * c - cv * s + sx, cu * s + cv * c + sy)
                    if (a <= 0.004f) continue
                    val alpha = if (a <= 0.5f) {
                        val r = sqrt(a * invPi)
                        ((r - sqrt(fu * fu + fv * fv)) * cell + 0.5f).coerceIn(0f, 1f)
                    } else {
                        // Past half, the paper shows as holes between the dots instead.
                        val rh = sqrt((1f - a) * invPi)
                        val du = 0.5f - abs(fu)
                        val dv = 0.5f - abs(fv)
                        ((sqrt(du * du + dv * dv) - rh) * cell + 0.5f).coerceIn(0f, 1f)
                    }
                    // The ink lies unevenly, as it does off a drum.
                    val ink = alpha * keep * (0.82f + 0.18f * noise(x, y, k + seed * 7))
                    fr *= 1f - ink * (1f - INKS[k][0])
                    fg *= 1f - ink * (1f - INKS[k][1])
                    fb *= 1f - ink * (1f - INKS[k][2])
                }
                val r: Float
                val g: Float
                val b: Float
                if (paper) {
                    val grain = 1f - 0.03f * noise(x, y, 31)
                    r = PAPER[0] * fr * grain
                    g = PAPER[1] * fg * grain
                    b = PAPER[2] * fb * grain
                } else {
                    r = fr; g = fg; b = fb
                }
                out[y * w + x] = (0xff shl 24) or ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                    ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
        }
        return out
    }

    /** A cheap hash of a pixel to 0..1. */
    fun noise(x: Int, y: Int, salt: Int): Float {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    /** Smooth value noise, about one bump per [scale] pixels, 0..1. */
    fun smooth(x: Float, y: Float, scale: Float, salt: Int): Float {
        val gx = x / scale
        val gy = y / scale
        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val fx = gx - x0
        val fy = gy - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        fun n(i: Int, j: Int) = noise(i, j, salt)
        val a = n(x0, y0) * (1 - sx) + n(x0 + 1, y0) * sx
        val b = n(x0, y0 + 1) * (1 - sx) + n(x0 + 1, y0 + 1) * sx
        return a * (1 - sy) + b * sy
    }

    fun image(pixels: IntArray, w: Int, h: Int): ImageBitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Prints already made, by what they are and their size. */
internal object PrintCache {
    private val cache = object : LruCache<Any, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: Any, value: ImageBitmap) = value.width * value.height * 4
    }
    private val plates = LruCache<Any, RisoScreen.Plates>(24)

    /** The screenshot tests can't wait for a background thread; they set `lanyin.syncPrints`. */
    val sync: Boolean = System.getProperty("lanyin.syncPrints") == "true"

    operator fun get(key: Any): ImageBitmap? = cache.get(key)
    operator fun set(key: Any, value: ImageBitmap) {
        cache.put(key, value)
    }

    fun plates(key: Any): RisoScreen.Plates? = plates.get(key)
    fun putPlates(key: Any, value: RisoScreen.Plates) {
        plates.put(key, value)
    }
}

/**
 * A print [render]ed at [w]×[h] pixels, kept by [key] and size; null while it's being made off
 * the main thread (the screenshot tests make it in place).
 */
@Composable
internal fun rememberPrint(key: Any?, w: Int, h: Int, render: (Int, Int) -> IntArray?): ImageBitmap? {
    if (key == null || w <= 0 || h <= 0) return null
    val full = listOf(key, w, h)
    PrintCache[full]?.let { return it }
    if (PrintCache.sync) {
        return remember(full) { render(w, h)?.let { RisoScreen.image(it, w, h) }?.also { PrintCache[full] = it } }
    }
    val made by produceState<ImageBitmap?>(null, full) {
        value = withContext(Dispatchers.Default) { render(w, h)?.let { RisoScreen.image(it, w, h) } }?.also { PrintCache[full] = it }
    }
    return made
}

/**
 * A cover's separations: the real cover once it has loaded, the generated sleeve for [seed]
 * before that or without one.
 */
@Composable
internal fun rememberCoverPlates(url: String?, seed: Int): RisoScreen.Plates? {
    val style = Lanyin.style
    val context = LocalPlatformContext.current
    val sleeveKey = listOf("sleeve", style.theme, style.dark, seed)
    val sleeve = PrintCache.plates(sleeveKey) ?: remember(sleeveKey) {
        sleevePlates(style, seed).also { PrintCache.putPlates(sleeveKey, it) }
    }
    if (url == null) return sleeve
    PrintCache.plates(url)?.let { return it }
    val loaded by produceState<RisoScreen.Plates?>(null, url) {
        value = withContext(Dispatchers.IO) {
            val px = 200
            val request = ImageRequest.Builder(context).data(sized(url, px)).size(px).allowHardware(false).build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withContext null
            val bitmap = result.image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            RisoScreen.plates(pixels, bitmap.width, bitmap.height)
        }?.also { PrintCache.putPlates(url, it) }
    }
    return loaded ?: sleeve
}

/** The generated sleeve for [seed], drawn small and separated. */
private fun sleevePlates(style: LanyinStyle, seed: Int): RisoScreen.Plates {
    val side = 96
    val bitmap = ImageBitmap(side, side)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(side.toFloat(), side.toFloat())) { drawCoverArt(style, seed) }
    val pixels = IntArray(side * side)
    bitmap.readPixels(pixels)
    return RisoScreen.plates(pixels, side, side, punch = 1f)
}

/**
 * The pictures 暮 prints that aren't covers. Coverages are designed rather than separated, so the
 * inks overprint where they should: pink and blue for violet, pink and yellow for orange.
 */
internal object RisoScenes {
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Sunset by the month, for a city around 30°N: a printed almanac rather than the sky's real one. */
    fun sunset(month: Int): Float = floatArrayOf(17.6f, 18.0f, 18.4f, 18.7f, 19.0f, 19.3f, 19.3f, 19.0f, 18.5f, 17.9f, 17.4f, 17.3f)[(month - 1).coerceIn(0, 11)]

    /**
     * The sky over a print, top to horizon: pink over a little blue up high, warming to yellow
     * at the horizon; deep violet once it's dark ([night] 0..1).
     */
    private fun sky(t: Float, night: Float, out: FloatArray) {
        val pink = lerp(0.72f, 0.5f, t)
        val yellow = smoothstep(0.15f, 1f, t) * 0.9f
        val blue = (1f - t) * (1f - t) * 0.45f
        out[0] = lerp(pink, 0.42f, night)
        out[1] = lerp(yellow, 0.06f * t, night)
        out[2] = lerp(blue, 0.82f - 0.2f * t, night)
    }

    /**
     * Today's print: the sea from a headland with its lighthouse, the sun where it stands at
     * [hour] (half sunk at [sunsetHour], gone and a moon up an hour after), its light in stripes
     * on the water.
     */
    fun evening(w: Int, h: Int, hour: Float, sunsetHour: Float): InkField {
        val horizon = h * 0.6f
        val night = smoothstep(sunsetHour + 0.3f, sunsetHour + 1.2f, hour) + smoothstep(sunsetHour - 13f, sunsetHour - 14f, hour)
        val n = night.coerceIn(0f, 1f)
        val alt = ((sunsetHour - hour) / 3f).coerceIn(-0.35f, 1f)
        val sunR = w * 0.16f
        val sunX = w * 0.64f
        val sunY = horizon - alt * h * 0.42f
        val moonX = w * 0.74f
        val moonY = h * 0.2f
        val moonR = w * 0.07f
        return InkField { x, y, out ->
            val tex = RisoScreen.smooth(x, y, w * 0.06f, 5) - 0.5f
            if (y < horizon) {
                sky(y / horizon, n, out)
                // Stars once it's dark: a few yellow points scattered over the upper sky.
                if (n > 0f && y < horizon * 0.75f) {
                    val cs = w * 0.075f
                    val gx = floor(x / cs)
                    val gy = floor(y / cs)
                    if (RisoScreen.noise(gx.toInt(), gy.toInt(), 41) > 0.72f) {
                        val sx = (gx + 0.2f + 0.6f * RisoScreen.noise(gx.toInt(), gy.toInt(), 42)) * cs
                        val sy = (gy + 0.2f + 0.6f * RisoScreen.noise(gx.toInt(), gy.toInt(), 43)) * cs
                        if ((x - sx) * (x - sx) + (y - sy) * (y - sy) < (w * 0.009f) * (w * 0.009f)) {
                            out[1] = lerp(out[1], 1f, n); out[0] = lerp(out[0], 0.1f, n); out[2] = lerp(out[2], 0.1f, n)
                        }
                    }
                }
                val ds = sqrt((x - sunX) * (x - sunX) + (y - sunY) * (y - sunY))
                if (n < 1f && ds < sunR) {
                    val e = (1f - n)
                    out[1] = lerp(out[1], 1f, e)
                    out[0] = lerp(out[0], 0.22f + 0.25f * (ds / sunR), e)
                    out[2] = lerp(out[2], 0f, e)
                }
                val dm = sqrt((x - moonX) * (x - moonX) + (y - moonY) * (y - moonY))
                if (n > 0f && dm < moonR) {
                    // A crescent: the moon less a disc a little to the left.
                    val dc = sqrt((x - moonX + moonR * 0.45f) * (x - moonX + moonR * 0.45f) + (y - moonY + moonR * 0.1f) * (y - moonY + moonR * 0.1f))
                    if (dc > moonR * 0.85f) {
                        out[1] = lerp(out[1], 0.95f, n); out[0] = lerp(out[0], 0.05f, n); out[2] = lerp(out[2], 0f, n)
                    }
                }
            } else {
                // The sea: blue, a little pink at the far edge, the sun's road of stripes on it.
                val d = (y - horizon) / (h - horizon)
                out[2] = 0.68f + 0.16f * d
                out[0] = 0.22f * (1f - d) + 0.06f
                out[1] = 0f
                val roadW = sunR * (0.9f + 1.4f * d) * (1f - n * 0.6f)
                val period = 5f + 22f * d * d
                val stripe = ((y - horizon) % period) / period
                if (abs(x - sunX) < roadW && stripe < 0.45f && alt > -0.25f) {
                    val k = (1f - abs(x - sunX) / roadW).coerceIn(0f, 1f)
                    val light = if (n > 0.5f) 0.6f else 1f
                    out[1] = 0.95f * k * light
                    out[0] = 0.45f * k + out[0] * (1 - k)
                    out[2] = out[2] * (1f - 0.75f * k)
                }
            }
            // The headland from the left, the lighthouse on it.
            val landTop = horizon - h * (0.16f * (1f - smoothstep(0f, w * 0.42f, x)) + 0.03f * sin(x / w * 9f)) * (if (x < w * 0.44f) 1f else 0f)
            if (x < w * 0.44f && y > landTop && y < horizon + h * 0.04f * (1f - x / (w * 0.44f))) {
                out[0] = 0.86f; out[2] = 0.88f; out[1] = 0.08f
            }
            val lx = w * 0.14f
            val towerTop = horizon - h * 0.26f
            if (abs(x - lx) < w * 0.018f && y > towerTop && y < horizon - h * 0.1f) {
                out[0] = 1f; out[2] = 1f; out[1] = 0.25f
            }
            if (abs(x - lx) < w * 0.012f && y > towerTop - h * 0.025f && y < towerTop) {
                out[1] = 1f; out[0] = 0.15f; out[2] = 0f
            }
            // At night the lamp's beam sweeps out over the sea.
            if (n > 0.3f && x > lx) {
                val bx = x - lx
                val by = y - (towerTop - h * 0.012f)
                val spread = bx * 0.09f + h * 0.006f
                if (abs(by + bx * 0.04f) < spread && bx < w * 0.62f) {
                    val k = (1f - bx / (w * 0.62f)) * (1f - abs(by + bx * 0.04f) / spread) * n
                    out[1] = out[1] + (1f - out[1]) * 0.75f * k
                    out[2] = out[2] * (1f - 0.6f * k)
                    out[0] = out[0] * (1f - 0.5f * k)
                }
            }
            for (k in 0 until 3) out[k] = (out[k] + tex * 0.08f).coerceIn(0f, 1f)
        }
    }

    /** The ridge of the player print's hills at [x]: the highest of the three lines of hills, as y. */
    fun ridge(x: Float, w: Int, h: Int, seed: Int): Float = min(min(hill(x, w, h, seed, 0), hill(x, w, h, seed, 1)), hill(x, w, h, seed, 2))

    private fun hill(x: Float, w: Int, h: Int, seed: Int, layer: Int): Float {
        val u = x / w
        val ph = (seed % 97) * 0.37f + layer * 1.7f
        val base = h * floatArrayOf(0.66f, 0.74f, 0.84f)[layer]
        val amp = h * floatArrayOf(0.09f, 0.07f, 0.05f)[layer]
        return base - amp * (0.6f * sin(u * (3.1f + layer) + ph) + 0.4f * sin(u * (7.3f + layer * 2) + ph * 1.9f))
    }

    /**
     * The player's print without its sun: the evening sky and three lines of hills, far pink, then
     * violet, then the near ones in blue. The cover is printed over it as the sun.
     */
    fun hills(w: Int, h: Int, seed: Int): InkField = InkField { x, y, out ->
        val tex = RisoScreen.smooth(x, y, w * 0.05f, 9) - 0.5f
        sky((y / (h * 0.72f)).coerceAtMost(1f), 0f, out)
        out[1] = (out[1] + 0.06f).coerceAtMost(1f)
        val far = hill(x, w, h, seed, 0)
        val mid = hill(x, w, h, seed, 1)
        val near = hill(x, w, h, seed, 2)
        when {
            y > near -> { out[0] = 0.5f; out[1] = 0.05f; out[2] = 0.95f }
            y > mid -> { out[0] = 0.78f; out[1] = 0.05f; out[2] = 0.62f }
            y > far -> {
                val d = ((y - far) / (h * 0.08f)).coerceIn(0f, 1f)
                out[0] = 0.72f; out[1] = 0.18f * (1 - d); out[2] = 0.22f + 0.12f * d
            }
        }
        for (k in 0 until 3) out[k] = (out[k] + tex * 0.07f).coerceIn(0f, 1f)
    }
}
