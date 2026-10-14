package io.lanyin.player.ui.design

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * [playing] raises its energy: bigger waves, faster grid, livelier petals.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, live: Boolean = false, playing: Boolean = false) {
    val style = Lanyin.style
    val clock = rememberFrameClock(live)
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "energy")
    Canvas(modifier) {
        val t = clock.floatValue
        val e = energy
        val page = !live
        when (style.backdrop) {
            BackdropKind.Tide -> drawTide(style, t, e, page)
            BackdropKind.Ink -> drawInk(style, t, page)
            BackdropKind.Sakura -> drawSakura(style, t, e, page)
            BackdropKind.Neon -> drawNeon(style, t, e, page)
            BackdropKind.Dusk -> drawDusk(style, t, e, page)
        }
    }
}

private fun DrawScope.glowBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

private fun DrawScope.drawTide(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val a = if (page) 0.6f else 1f
    val spread = if (page) 0.45f else 1f
    glowBlob(c.bgDeep.copy(alpha = 0.95f * a), Offset(w * (0.15f + 0.08f * sin(t * 0.23f)), h * spread * (0.12f + 0.05f * cos(t * 0.19f))), w * 0.95f)
    glowBlob(c.accent.copy(alpha = (if (style.dark) 0.24f else 0.20f) * a), Offset(w * (0.88f + 0.06f * cos(t * 0.17f)), h * spread * (0.26f + 0.07f * sin(t * 0.21f))), w * 0.8f)
    glowBlob(c.accentAlt.copy(alpha = (if (style.dark) 0.18f else 0.14f) * a), Offset(w * (0.3f + 0.12f * sin(t * 0.13f + 1f)), h * spread * (0.6f + 0.06f * cos(t * 0.11f))), w * 0.85f)
    if (page) return

    // Three slow swells along the bottom, the "澜" itself. They rise while music plays.
    val step = 6f
    for (layer in 0..2) {
        val baseY = h * (0.80f + 0.05f * layer)
        val amp = (8f + 18f * e) * density * (1f - 0.22f * layer)
        val k = (2 * PI / (w * (1.1f + 0.35f * layer))).toFloat()
        val phase = t * (0.45f + 0.2f * layer) + layer * 1.7f
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w + step) {
                lineTo(x, baseY + amp * sin(k * x + phase))
                x += step
            }
            lineTo(w, h)
            close()
        }
        drawPath(path, (if (layer == 1) c.accentAlt else c.accent).copy(alpha = if (style.dark) 0.09f else 0.08f))
    }
}

private fun DrawScope.drawInk(style: LanyinStyle, t: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    glowBlob(c.content.copy(alpha = if (style.dark) 0.05f else 0.045f), Offset(w * 0.85f, h * 0.12f), w * 0.8f * (1f + 0.03f * sin(t * 0.4f)))
    if (page) return

    // An ensō around where the artwork sits (left half in landscape, upper middle in portrait).
    val landscape = w > h
    val center = when {
        page -> Offset(w * 0.88f, h * 0.08f)
        landscape -> Offset(w * 0.22f, h * 0.46f)
        else -> Offset(w * 0.5f, h * 0.36f)
    }
    val radius = when {
        page -> w * 0.3f
        landscape -> h * 0.3f
        else -> w * 0.47f
    }
    rotate(degrees = -70f + t * 4f, pivot = center) {
        drawPath(ensoPath(center, radius, radius * 0.13f), c.content.copy(alpha = if (style.dark) 0.09f else 0.07f))
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

internal val petalPath: Path = Path().apply {
    moveTo(0f, 1f)
    cubicTo(0.75f, 0.45f, 0.8f, -0.55f, 0.32f, -0.95f)
    lineTo(0f, -0.68f)
    lineTo(-0.32f, -0.95f)
    cubicTo(-0.8f, -0.55f, -0.75f, 0.45f, 0f, 1f)
    close()
}

internal fun DrawScope.drawPetal(center: Offset, sizePx: Float, rotation: Float, color: Color) {
    withTransform({
        translate(center.x, center.y)
        rotate(rotation, Offset.Zero)
        scale(sizePx, sizePx, Offset.Zero)
    }) { drawPath(petalPath, color) }
}

private fun DrawScope.drawSakura(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(c.bg, c.bg, c.bgDeep)))
    glowBlob(c.accent.copy(alpha = 0.12f), Offset(w * 0.9f, h * 0.05f), w * 0.7f)
    val count = if (page) 9 else 24
    val margin = 24f * density
    for (i in 0 until count) {
        val speed = (28f + 34f * hash01(i, 2)) * density * (0.6f + 0.6f * e)
        val y = ((hash01(i, 3) * (h + margin) + t * speed) % (h + 2 * margin)) - margin
        val sway = (14f + 26f * hash01(i, 4)) * density
        val x = hash01(i, 1) * w + sway * sin(t * (0.6f + hash01(i, 5)) + i)
        val sizePx = (6f + 8f * hash01(i, 6)) * density
        val tint = lerp(c.accent, Color.White, if (style.dark) 0.15f else 0.35f * hash01(i, 7))
        drawPetal(Offset(x, y), sizePx, rotation = hash01(i, 8) * 360f + t * (40f + 60f * hash01(i, 9)), color = tint.copy(alpha = 0.28f + 0.35f * hash01(i, 10)))
    }
}

private fun DrawScope.drawNeon(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val horizon = h * (if (page) 0.3f else 0.6f)
    // Sky glow.
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.75f to c.bgDeep, 1f to c.accentAlt.copy(alpha = 0.35f), startY = 0f, endY = horizon))
    drawLine(c.accentAlt.copy(alpha = 0.9f), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.5f * density)
    // Perspective grid floor, scrolling toward the viewer.
    val vanishing = Offset(w / 2f, horizon)
    for (i in -14..14) {
        val bottomX = w / 2f + i * w * 0.14f
        drawLine(c.accentAlt.copy(alpha = 0.32f), vanishing.copy(x = w / 2f + i * w * 0.012f), Offset(bottomX, h), strokeWidth = 1f * density)
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

private fun DrawScope.drawDusk(style: LanyinStyle, t: Float, e: Float, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(0f to c.bgDeep, (if (page) 0.35f else 0.62f) to c.bg, 1f to c.bg))
    // A striped seventies sun, bobbing gently while music plays. In landscape it sets low in the
    // corner so it never sits behind the lyrics.
    val bob = sin(t * 0.8f) * 6f * density * e
    val landscape = w > h
    val radius = when {
        page -> w * 0.17f
        landscape -> h * 0.42f
        else -> w * 0.3f
    }
    val center = when {
        page -> Offset(w * 0.9f, h * 0.035f)
        landscape -> Offset(w * 0.07f, h * 0.84f + bob)
        else -> Offset(w * 0.8f, h * 0.2f + bob)
    }
    val sun = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    clipPath(sun) {
        drawRect(Brush.verticalGradient(listOf(c.accentAlt, c.accent), startY = center.y - radius, endY = center.y + radius))
        for (k in 0 until 6) {
            val bandY = center.y + radius * (0.12f + k * 0.15f)
            val thickness = radius * (0.025f + k * 0.022f)
            drawRect(lerp(c.bgDeep, c.bg, ((bandY / h) / 0.62f).coerceIn(0f, 1f)), topLeft = Offset(center.x - radius, bandY), size = Size(radius * 2, thickness))
        }
    }
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (style.dark) 0.10f else 0.08f)
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
