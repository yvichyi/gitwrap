package io.lanyin.player.ui.design

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 律's shapes, Material 3 Expressive's family: a round outline with [lobes] soft scallops
 * [depth] deep (9 shallow ones make the "cookie", 4 deep ones a clover), or with [lobes] 0 a
 * squircle. Every shape is sampled by angle, so any two can be blended point for point.
 */
internal data class Lobed(val lobes: Int, val depth: Float)

internal val Cookie = Lobed(9, 0.07f)
internal val Clover = Lobed(4, 0.2f)
internal val Flower = Lobed(6, 0.13f)
internal val Burst = Lobed(12, 0.06f)
internal val Squircle = Lobed(0, 0f)

/** Radius of [shape] at angle [a], as a share of the full radius (the outline stays within it). */
internal fun Lobed.radiusAt(a: Float): Float = if (lobes == 0) {
    // |x|⁴ + |y|⁴ = 1
    val c = abs(cos(a)).pow(4)
    val s = abs(sin(a)).pow(4)
    0.94f / (c + s).pow(0.25f)
} else {
    (1f - depth) + depth * cos(lobes * a)
}

/** The outline between [from] (0) and [to] (1), centred in a box of [size], turned by [degrees]. */
internal fun morphPath(size: Size, from: Lobed, to: Lobed, t: Float, degrees: Float = 0f, scale: Float = 1f): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f * scale
    val turn = degrees / 180f * PI.toFloat()
    val steps = 144
    return Path().apply {
        for (i in 0..steps) {
            val a = i / steps.toFloat() * 2f * PI.toFloat()
            val k = from.radiusAt(a) * (1f - t) + to.radiusAt(a) * t
            val x = cx + cos(a + turn) * r * k
            val y = cy + sin(a + turn) * r * k
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** A clip shape morphing from [from] to [to] by [t]. */
internal fun morphShape(from: Lobed, to: Lobed, t: Float): Shape = GenericShape { size, _ -> addPath(morphPath(size, from, to, t)) }

/** A spring that rings out after a beat: 1 at rest, overshooting and settling. */
internal fun beatBounce(since: Float, strength: Float = 0.1f): Float =
    1f + strength * exp(-since * 5f) * cos(since * 16f)

/**
 * 律's backdrop: big tonal shapes (cookie, clover, flower, burst) half off the edges, turning
 * slowly; on the player they bounce on every beat. Pages keep two, still.
 */
internal fun DrawScope.drawPulse(style: LanyinStyle, t: Float, page: Boolean, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val tone = if (style.dark) 0.16f else 0.11f
    val soft = if (style.dark) 0.1f else 0.07f
    val since = audio?.sinceBeat(t) ?: 99f
    data class Placed(val shape: Lobed, val x: Float, val y: Float, val r: Float, val color: Color, val spin: Float)
    val shapes = if (page) {
        listOf(
            Placed(Cookie, 0.95f, 0.02f, 0.36f, c.accent.copy(alpha = tone), 0f),
            Placed(Clover, 0.02f, 0.62f, 0.26f, c.accentAlt.copy(alpha = soft), 0f),
        )
    } else {
        listOf(
            Placed(Cookie, 0.92f, 0.06f, 0.42f, c.accent.copy(alpha = tone), 9f),
            Placed(Clover, 0.04f, 0.42f, 0.3f, c.accentAlt.copy(alpha = tone), -12f),
            Placed(Flower, 0.96f, 0.7f, 0.28f, style.pulseHues()[2].copy(alpha = soft), 7f),
            Placed(Burst, 0.12f, 0.95f, 0.34f, style.pulseHues()[3].copy(alpha = soft), -5f),
        )
    }
    val span = minOf(w, h)
    shapes.forEachIndexed { i, p ->
        // Each shape answers the beat a moment after the last, so the bounce travels round the page.
        val bounce = if (audio == null) 1f else beatBounce((since - i * 0.06f).coerceAtLeast(0f), 0.07f)
        val d = span * p.r * 2f * bounce
        val path = morphPath(Size(d, d), p.shape, p.shape, 0f, degrees = t * p.spin)
        val left = w * p.x - d / 2f
        val top = h * p.y - d / 2f
        path.translate(Offset(left, top))
        drawPath(path, p.color)
    }
}

/** 律's playful set: the two accents plus an orange and a green, softened at night. */
internal fun LanyinStyle.pulseHues(): List<Color> {
    val c = colors
    val orange = if (dark) Color(0xFFFFB68A) else Color(0xFFE8793A)
    val green = if (dark) Color(0xFF9CD69A) else Color(0xFF3F8F4E)
    return listOf(c.accent, c.accentAlt, orange, green)
}

/** 律's generated sleeves: a big shape and a small one on a pastel ground. */
internal fun DrawScope.pulseCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val hues = style.pulseHues()
    val shapes = listOf(Cookie, Clover, Flower, Burst)
    val main = hues[seed % 4]
    val other = hues[(seed + 2) % 4]
    drawRect(lerp(c.bg, other, if (style.dark) 0.22f else 0.2f))
    val shape = shapes[(seed / 4 + seed) % 4]
    val d = size.minDimension * (0.7f + 0.15f * hash01(seed, 61))
    val path = morphPath(Size(d, d), shape, shape, 0f, degrees = hash01(seed, 62) * 90f)
    path.translate(Offset(size.width * (0.5f + 0.2f * (hash01(seed, 63) - 0.5f)) - d / 2f, size.height * (0.46f + 0.2f * (hash01(seed, 64) - 0.5f)) - d / 2f))
    drawPath(path, main)
    val small = size.minDimension * 0.26f
    val dot = morphPath(Size(small, small), shapes[(seed + 1) % 4], shapes[(seed + 1) % 4], 0f)
    dot.translate(Offset(size.width * 0.72f - small / 2f, size.height * 0.74f - small / 2f))
    drawPath(dot, other)
}

/**
 * Android's media squiggle: the part played is a wave that rolls along while playing and lies
 * flat when paused ([amplitude] 0), the rest a plain line, and a tall pill marks the place.
 */
internal fun DrawScope.drawWaveTrack(f: Float, phase: Float, amplitude: Float, lit: Color, rest: Color) {
    val cy = size.height / 2f
    val x = size.width * f
    val stroke = 4f * density
    val wavelength = 28f * density
    val amp = 3.5f * density * amplitude
    val gap = 6f * density
    // Remaining part.
    if (x + gap < size.width) drawLine(rest, Offset(x + gap, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
    // Played part.
    val end = (x - gap).coerceAtLeast(0f)
    if (end > 0f) {
        val path = Path().apply {
            moveTo(0f, cy + amp * sin(-phase))
            var px = 0f
            while (px < end) {
                px = (px + 2f * density).coerceAtMost(end)
                lineTo(px, cy + amp * sin(2f * PI.toFloat() * px / wavelength - phase))
            }
        }
        drawPath(path, lit, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    val thumb = Size(4f * density, 22f * density)
    drawRoundRect(lit, Offset(x - thumb.width / 2f, cy - thumb.height / 2f), thumb, CornerRadius(thumb.width / 2f))
}

/** A tonal fill for 律's surfaces: the background leaning toward the accent. */
internal fun LanyinStyle.tonal(amount: Float): Color = lerp(colors.bg, colors.accent, amount)
