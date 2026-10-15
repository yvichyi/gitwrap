package io.lanyin.player.ui.design

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The theme's backdrop. [live] animates it (the player); pages use a still, quieter version.
 * [playing] raises its energy: a faster grid, a livelier sun.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, live: Boolean = false, playing: Boolean = false, trackNumber: Int? = null) {
    val style = Lanyin.style
    val clock = rememberFrameClock(live)
    val measurer = if (style.backdrop == BackdropKind.Grid) androidx.compose.ui.text.rememberTextMeasurer() else null
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "energy")
    Canvas(modifier) {
        val t = clock.floatValue
        val e = energy
        val page = !live
        when (style.backdrop) {
            BackdropKind.Ink -> drawInk(style, t, page)
            BackdropKind.Neon -> drawNeon(style, t, e, page)
            BackdropKind.Dusk -> drawDusk(style, t, e, page)
            BackdropKind.Grid -> drawGridPaper(style, page, measurer, trackNumber)
        }
    }
}

private fun DrawScope.glowBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

private fun DrawScope.drawInk(style: LanyinStyle, t: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    glowBlob(c.content.copy(alpha = if (style.dark) 0.05f else 0.045f), Offset(w * 0.85f, h * 0.12f), w * 0.8f * (1f + 0.03f * sin(t * 0.4f)))
    if (page || w <= h) return

    // 远山: three ridges fading into mist across the right of the stage, nearest darkest.
    val ink = c.content
    for (layer in 0..2) {
        val base = h * (0.62f + layer * 0.1f)
        val amp = h * (0.2f - layer * 0.045f)
        val from = w * (0.4f + 0.06f * layer)
        val ridge = Path().apply {
            moveTo(from, h)
            var x = from
            while (x <= w + 8f) {
                val u = (x - from) / (w - from)
                // Peaks from a few slow sines, eased in from the left so the range starts low.
                val peaks = 0.55f + 0.3f * sin(u * 7.1f + layer * 2.1f) + 0.15f * sin(u * 17.3f + layer)
                // Rise out of the ground at the left rather than starting on a cliff.
                val rise = (u / 0.3f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                lineTo(x, h - (h - (base - amp * peaks)) * rise)
                x += 6f
            }
            lineTo(w, h)
            close()
        }
        val alpha = (if (style.dark) 0.07f else 0.06f) * (1f + layer * 0.5f)
        drawPath(ridge, Brush.verticalGradient(listOf(ink.copy(alpha = alpha), ink.copy(alpha = 0f)), startY = base - amp, endY = h))
    }
    // A band of mist drifting slowly across the foot of the hills.
    val drift = (t * 6f * density) % w
    for (k in 0..1) {
        val x = -w * 0.3f + drift + k * w
        drawOval(
            Brush.radialGradient(listOf(c.bg.copy(alpha = 0.7f), c.bg.copy(alpha = 0f)), Offset(x + w * 0.3f, h * 0.8f), w * 0.3f),
            Offset(x, h * 0.72f), Size(w * 0.6f, h * 0.16f),
        )
    }
}

/**
 * One brush stroke round a circle, as a single filled outline so overlapping pieces don't
 * leave seams: it swells in the middle, tapers at both ends and leaves a gap.
 */
internal fun ensoPath(center: Offset, radius: Float, thickness: Float, sweepDegrees: Float = 310f): Path {
    val steps = 72
    val outer = ArrayList<Offset>(steps + 1)
    val inner = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val angle = Math.toRadians((f * sweepDegrees).toDouble())
        // Heavier where the brush lands, thinning as it lifts.
        // sin(π) is a hair below zero in floats, and a fractional power of that is NaN.
        val swell = sin(PI.toFloat() * f.pow(0.8f)).coerceAtLeast(0f).pow(0.7f)
        val half = thickness * (0.12f + 0.88f * swell) / 2f
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        outer += Offset(center.x + (radius + half) * dx, center.y + (radius + half) * dy)
        inner += Offset(center.x + (radius - half) * dx, center.y + (radius - half) * dy)
    }
    return Path().apply {
        moveTo(outer[0].x, outer[0].y)
        outer.drop(1).forEach { lineTo(it.x, it.y) }
        inner.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
}

/** Cheap deterministic noise in [0, 1). */
internal fun hash01(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawNeon(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val horizon = h * when {
        page -> 0.8f
        w > h -> 0.82f
        else -> 0.5f
    }
    // Sky glow.
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.75f to c.bgDeep, 1f to c.accentAlt.copy(alpha = 0.35f), startY = 0f, endY = horizon))
    if (!page && w > h) {
        val sun = Offset(w * 0.8f, horizon)
        val r = h * 0.26f
        glowBlob(c.accentAlt.copy(alpha = 0.25f), sun, r * 1.8f)
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(sun, r)) }
        val fill = Brush.verticalGradient(
            listOf(lerp(c.accentAlt, Color(0xFFFFD36E), 0.5f).copy(alpha = 0.55f), c.accentAlt.copy(alpha = 0.4f)),
            startY = sun.y - r, endY = sun.y,
        )
        clipPath(disc) {
            // Solid crown, then slices that thin toward the horizon.
            var top = sun.y - r
            var gap = r * 0.035f
            var slice = r * 0.5f
            while (top < horizon) {
                drawRect(fill, Offset(sun.x - r, top), Size(r * 2, minOf(slice, horizon - top)))
                top += slice + gap
                slice *= 0.62f
                gap *= 1.15f
            }
        }
    }
    drawLine(c.accentAlt.copy(alpha = if (page) 0.45f else 0.8f), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.5f * density)
    // Perspective grid floor, scrolling toward the viewer.
    val vanishing = Offset(w / 2f, horizon)
    for (i in -14..14) {
        val bottomX = w / 2f + i * w * 0.14f
        drawLine(c.accent.copy(alpha = 0.22f), vanishing.copy(x = w / 2f + i * w * 0.012f), Offset(bottomX, h), strokeWidth = 1f * density)
    }
    val scroll = (t * (0.12f + 0.5f * e)) % 1f
    for (j in 0 until 14) {
        val z = (j + scroll) / 14f
        val y = horizon + (h - horizon) * z.pow(2.2f)
        drawLine(c.accent.copy(alpha = 0.06f + 0.34f * z), Offset(0f, y), Offset(w, y), strokeWidth = (0.6f + 1.4f * z) * density)
    }
    // Floor fade so content stays legible.
    drawRect(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.2f), c.bg.copy(alpha = 0.85f)), startY = horizon, endY = h), topLeft = Offset(0f, horizon))
    // A few stars and the CRT scanlines.
    for (i in 0 until 30) {
        val twinkle = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (1f + 2f * hash01(i, 11)) + i))
        drawCircle(c.content.copy(alpha = 0.5f * twinkle * hash01(i, 12)), radius = density * (0.6f + hash01(i, 13)), center = Offset(hash01(i, 14) * w, hash01(i, 15) * horizon * 0.85f))
    }
    val gap = 3f * density
    var y = 0f
    while (y < h) {
        drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += gap
    }
}

/**
 * 暮: an evening sky over warm paper. A wash of peach and rose (plum at night) from the top, a
 * low sun with a soft glow, and fine film grain. On pages the sun is a glow in the corner; on the
 * player it sits low, beside the artwork rather than behind the lyrics, and breathes with the music.
 */
private fun DrawScope.drawDusk(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val dark = style.dark
    drawRect(c.bg)
    // The sky: rose high up, peach lower, fading into the paper.
    val skyEnd = h * if (page) 0.42f else 0.7f
    val rose = lerp(c.accentAlt, c.bg, if (dark) 0.55f else 0.72f)
    drawRect(Brush.verticalGradient(0f to rose, 0.45f to c.bgDeep, 1f to c.bg, startY = 0f, endY = skyEnd), size = Size(w, skyEnd))
    val landscape = w > h
    val radius = when {
        page -> w * 0.2f
        landscape -> h * 0.2f
        else -> w * 0.2f
    }
    val breathe = 1f + 0.03f * e * sin(t * 1.3f)
    val center = when {
        page -> Offset(w * 0.86f, h * 0.06f)
        landscape -> Offset(w * 0.9f, h * 0.62f)
        else -> Offset(w * 0.82f, h * 0.2f)
    }
    val sunCore = if (dark) lerp(c.accent, Color(0xFFFFC08A), 0.25f) else lerp(c.accent, Color(0xFFFFD2A6), 0.45f)
    // Glow, then the disc with a soft edge; at night an ember sunk into the sky.
    glowBlob(sunCore.copy(alpha = if (dark) 0.22f else 0.3f), center, radius * 3.2f * breathe)
    drawCircle(
        Brush.radialGradient(
            0f to sunCore.copy(alpha = if (dark) 0.75f else 0.9f),
            0.82f to sunCore.copy(alpha = if (dark) 0.6f else 0.8f),
            1f to sunCore.copy(alpha = 0f),
            center = center, radius = radius * breathe,
        ),
        radius * breathe, center,
    )
    if (!page) {
        // A calm sea under it: a few thin glints, drifting.
        val horizon = center.y + radius * 1.25f
        for (k in 0 until 5) {
            val y = horizon + k * radius * 0.16f
            val half = radius * (0.9f - k * 0.14f)
            val drift = sin(t * 0.6f + k) * radius * 0.06f
            drawLine(sunCore.copy(alpha = (0.35f - k * 0.06f) * (0.6f + 0.4f * e)), Offset(center.x - half + drift, y), Offset(center.x + half + drift, y), radius * 0.035f, StrokeCap.Round)
        }
    }
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (dark) 0.08f else 0.06f)
}

/** A small tile of film grain, generated once. */
private val grain: ImageBitmap by lazy {
    val side = 96
    val pixels = IntArray(side * side) { i ->
        val v = (hash01(i, 77) * 255).toInt()
        val a = (hash01(i, 78) * 160).toInt()
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}
