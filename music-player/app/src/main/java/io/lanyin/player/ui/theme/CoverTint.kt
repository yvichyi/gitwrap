package io.lanyin.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.lanyin.player.ui.design.sized
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The colours a cover is mostly made of: its most vivid dominant hue, and a second one if it has one. */
data class CoverSeeds(val primary: Int, val secondary: Int?)

/**
 * Picks accent colours from cover art and makes them safe to use: the hue is the cover's, the
 * lightness is moved until it reads against the theme's background.
 */
object CoverColors {
    private const val BINS = 24

    /** Seeds from ARGB [pixels], or null for a cover with no colour to speak of (greys, black and white). */
    fun seeds(pixels: IntArray): CoverSeeds? {
        val weight = FloatArray(BINS)
        val rs = FloatArray(BINS)
        val gs = FloatArray(BINS)
        val bs = FloatArray(BINS)
        var counted = 0
        for (p in pixels) {
            if (p ushr 24 < 128) continue
            counted++
            val r = (p shr 16 and 0xff) / 255f
            val g = (p shr 8 and 0xff) / 255f
            val b = (p and 0xff) / 255f
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val chroma = hi - lo
            if (chroma < 0.14f || hi < 0.18f) continue
            // Vivid pixels count for much more than muddy ones, so a small bright subject beats a dull field.
            val w = chroma * chroma
            val bin = (hue(r, g, b, hi, chroma) / 360f * BINS).toInt().coerceIn(0, BINS - 1)
            weight[bin] += w
            rs[bin] += r * w
            gs[bin] += g * w
            bs[bin] += b * w
        }
        if (counted == 0 || weight.sum() / counted < 0.006f) return null
        val smooth = FloatArray(BINS) { i -> weight[i] + 0.5f * (weight[(i + BINS - 1) % BINS] + weight[(i + 1) % BINS]) }
        val best = smooth.indices.maxBy { smooth[it] }
        val second = smooth.indices
            .filter { circular(it, best) >= 3 && smooth[it] >= smooth[best] * 0.22f }
            .maxByOrNull { smooth[it] }
        fun mean(center: Int): Int {
            var w = 0f; var r = 0f; var g = 0f; var b = 0f
            for (d in -1..1) {
                val i = (center + d + BINS) % BINS
                w += weight[i]; r += rs[i]; g += gs[i]; b += bs[i]
            }
            return argb(r / w, g / w, b / w)
        }
        return CoverSeeds(mean(best), second?.let(::mean))
    }

    /**
     * [seed]'s hue at a saturation and lightness that read against [bg]: [minContrast] 4.5 for
     * text and thin lines, as WCAG asks of body text.
     */
    fun fit(seed: Int, bg: Int, dark: Boolean, minContrast: Float = 4.5f): Int {
        val (h, s0, l0) = hsl(seed)
        val s = s0.coerceIn(0.42f, 0.88f)
        var l = if (dark) l0.coerceIn(0.55f, 0.78f) else l0.coerceIn(0.26f, 0.5f)
        var out = fromHsl(h, s, l)
        repeat(40) {
            if (contrast(out, bg) >= minContrast) return out
            l = (l + if (dark) 0.015f else -0.015f).coerceIn(0f, 1f)
            out = fromHsl(h, s, l)
        }
        return out
    }

    /** White or near-black, whichever reads better on [fill]. */
    fun onColor(fill: Int): Int {
        val light = 0xFFFFFFFF.toInt()
        val darkInk = 0xFF101114.toInt()
        return if (contrast(light, fill) >= contrast(darkInk, fill)) light else darkInk
    }

    /** The WCAG contrast ratio of two opaque colours, 1..21. */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun luminance(c: Int): Float {
        fun lin(v: Int): Float {
            val x = v / 255f
            return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(c shr 16 and 0xff) + 0.7152f * lin(c shr 8 and 0xff) + 0.0722f * lin(c and 0xff)
    }

    private fun circular(a: Int, b: Int): Int = min(abs(a - b), BINS - abs(a - b))

    private fun hue(r: Float, g: Float, b: Float, hi: Float, chroma: Float): Float {
        val h = when (hi) {
            r -> ((g - b) / chroma).mod(6f)
            g -> (b - r) / chroma + 2f
            else -> (r - g) / chroma + 4f
        }
        return (h * 60f).mod(360f)
    }

    private fun hsl(c: Int): Triple<Float, Float, Float> {
        val r = (c shr 16 and 0xff) / 255f
        val g = (c shr 8 and 0xff) / 255f
        val b = (c and 0xff) / 255f
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val chroma = hi - lo
        if (chroma == 0f) return Triple(0f, 0f, l)
        val s = chroma / (1f - abs(2f * l - 1f))
        return Triple(hue(r, g, b, hi, chroma), s.coerceIn(0f, 1f), l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
        val m = l - c / 2f
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(r + m, g + m, b + m)
    }

    private fun argb(r: Float, g: Float, b: Float): Int =
        (0xff shl 24) or ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or (b * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/** The theme's colours with the accents taken from [seeds]; the backdrop, text and like colour stay the theme's. */
fun LanyinStyle.tintedBy(seeds: CoverSeeds): LanyinColors {
    val c = colors
    val bg = c.bg.toArgb()
    val accent = CoverColors.fit(seeds.primary, bg, dark)
    val alt = CoverColors.fit(seeds.secondary ?: seeds.primary, bg, dark, minContrast = 3f)
    // 点 stays black and white and 机 keeps its orange key: only their one spot of colour, the
    // red dot or the blue, comes from the cover.
    if (backdrop == BackdropKind.Dot || backdrop == BackdropKind.Device) return c.copy(accentAlt = Color(accent))
    return c.copy(
        accent = Color(accent),
        accentAlt = Color(alt),
        onAccent = Color(CoverColors.onColor(accent)),
        // 霓 draws its outlines in the accent; the others keep their neutral rules.
        outline = if (backdrop == BackdropKind.Neon) Color(accent).copy(alpha = c.outline.alpha) else c.outline,
    )
}

/** [content] with the accents eased over to [seeds]' (back to the theme's when null). */
@Composable
fun TintedBy(seeds: CoverSeeds?, content: @Composable () -> Unit) {
    val style = Lanyin.style
    val target = remember(style, seeds) { seeds?.let { style.tintedBy(it) } ?: style.colors }
    val spec = tween<Color>(700)
    val accent by animateColorAsState(target.accent, spec, label = "tintAccent")
    val accentAlt by animateColorAsState(target.accentAlt, spec, label = "tintAlt")
    val onAccent by animateColorAsState(target.onAccent, spec, label = "tintOn")
    val outline by animateColorAsState(target.outline, spec, label = "tintOutline")
    val tinted = style.copy(colors = style.colors.copy(accent = accent, accentAlt = accentAlt, onAccent = onAccent, outline = outline))
    CompositionLocalProvider(LocalLanyinStyle provides tinted, content = content)
}

/** The seeds of the cover at [url], once it has loaded; null meanwhile and for colourless covers. */
@Composable
fun rememberCoverSeeds(url: String?): CoverSeeds? {
    val context = LocalPlatformContext.current
    var seeds by remember { mutableStateOf<Pair<String, CoverSeeds?>?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrEmpty()) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(sized(url, SAMPLE_PX)).size(SAMPLE_PX).allowHardware(false).build()
        val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return@LaunchedEffect
        seeds = url to withContext(Dispatchers.Default) {
            val bitmap = image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            CoverColors.seeds(pixels)
        }
    }
    // Until the new cover is read, the last one's colours stay rather than flashing back to the theme's.
    return seeds?.second?.takeIf { url != null }
}

private const val SAMPLE_PX = 96
