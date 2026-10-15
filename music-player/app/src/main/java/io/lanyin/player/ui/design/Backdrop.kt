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
 * [playing] raises its energy: bigger waves, faster grid, livelier petals.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, live: Boolean = false, playing: Boolean = false) {
    val style = Lanyin.style
    val clock = rememberFrameClock(live)
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "energy")
    val phase = remember { moonPhase(java.time.LocalDate.now()) }
    Canvas(modifier) {
        val t = clock.floatValue
        val e = energy
        val page = !live
        when (style.backdrop) {
            BackdropKind.Tide -> drawTide(style, t, e, page, phase)
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

private fun DrawScope.drawTide(style: LanyinStyle, t: Float, e: Float, page: Boolean, moonPhase: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val a = if (page) 0.6f else 1f
    val spread = if (page) 0.45f else 1f
    glowBlob(c.bgDeep.copy(alpha = 0.95f * a), Offset(w * (0.15f + 0.08f * sin(t * 0.23f)), h * spread * (0.12f + 0.05f * cos(t * 0.19f))), w * 0.95f)
    glowBlob(c.accent.copy(alpha = (if (style.dark) 0.24f else 0.20f) * a), Offset(w * (0.88f + 0.06f * cos(t * 0.17f)), h * spread * (0.26f + 0.07f * sin(t * 0.21f))), w * 0.8f)
    glowBlob(c.accentAlt.copy(alpha = (if (style.dark) 0.18f else 0.14f) * a), Offset(w * (0.3f + 0.12f * sin(t * 0.13f + 1f)), h * spread * (0.6f + 0.06f * cos(t * 0.11f))), w * 0.85f)
    if (page) {
        drawSwells(style, t, e, top = 0.84f, strength = 0.7f)
        return
    }

    // Under the surface: light falling in from above, bubbles rising.
    drawLightRays(style, t)
    // Landscape: the sky over the right of the stage, where left-aligned lyrics leave room. At
    // night it is tonight's moon in its true phase; by day a hazy sun.
    if (w > h) {
        val center = Offset(w * 0.86f, h * 0.24f)
        val r = h * 0.075f
        if (style.dark) {
            val lit = moonIllumination(moonPhase)
            glowBlob(Color.White.copy(alpha = 0.05f + 0.12f * lit), center, r * 4f)
            drawMoon(
                center, r, moonPhase,
                Brush.radialGradient(listOf(Color(0xFFF1F7FA), Color(0xFFC9DDE6)), center, r),
                dark = Color.White.copy(alpha = 0.06f),
            )
        } else {
            glowBlob(Color.White.copy(alpha = 0.55f), center, r * 4.5f)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f)), center, r), r, center)
        }
    }
    drawBubbles(style, t, e)
    drawSwells(style, t, e, top = 0.8f, strength = 1f)
}

/** Shafts of light from the surface, swaying slowly; moonbeams at night. */
private fun DrawScope.drawLightRays(style: LanyinStyle, t: Float) {
    val w = size.width
    val h = size.height
    val tint = if (style.dark) style.colors.accent else Color.White
    val strength = if (style.dark) 0.06f else 0.14f
    for (i in 0 until 5) {
        val sway = w * 0.03f * sin(t * 0.17f + i * 1.3f)
        val top = w * (0.08f + 0.2f * i + 0.05f * hash01(i, 40)) + sway
        val topWidth = w * (0.04f + 0.04f * hash01(i, 41))
        val slant = w * 0.22f
        val ray = Path().apply {
            moveTo(top, 0f)
            lineTo(top + topWidth, 0f)
            lineTo(top + topWidth * 3f + slant, h * 0.75f)
            lineTo(top + slant, h * 0.75f)
            close()
        }
        val a = strength * (0.6f + 0.4f * hash01(i, 42)) * (0.75f + 0.25f * sin(t * 0.3f + i))
        drawPath(ray, Brush.verticalGradient(listOf(tint.copy(alpha = a), tint.copy(alpha = 0f)), startY = 0f, endY = h * 0.75f))
    }
}

/** Bubbles drifting up from the deep, quicker while music plays. */
private fun DrawScope.drawBubbles(style: LanyinStyle, t: Float, e: Float) {
    val w = size.width
    val h = size.height
    val ring = if (style.dark) style.colors.accentAlt else Color.White
    val margin = 20f * density
    for (i in 0 until 16) {
        val speed = (10f + 18f * hash01(i, 50)) * density * (0.7f + 0.8f * e)
        val y = h + margin - ((hash01(i, 51) * (h + 2 * margin) + t * speed) % (h + 2 * margin))
        val x = hash01(i, 52) * w + 6f * density * sin(t * (0.8f + hash01(i, 53)) + i * 2f)
        val r = (1.5f + 3f * hash01(i, 54)) * density
        // Fade in from the bottom and out near the surface.
        val depth = (y / h).coerceIn(0f, 1f)
        val alpha = (if (style.dark) 0.35f else 0.55f) * (0.4f + 0.6f * hash01(i, 55)) * minOf(1f, depth * 3f)
        drawCircle(ring.copy(alpha = alpha), r, Offset(x, y), style = Stroke(1f * density))
        drawCircle(ring.copy(alpha = alpha), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}

/** Three slow swells along the bottom, the "澜" itself. They rise while music plays. */
private fun DrawScope.drawSwells(style: LanyinStyle, t: Float, e: Float, top: Float, strength: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val step = 6f
    for (layer in 0..2) {
        val baseY = h * (top + 0.05f * layer)
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
        drawPath(path, (if (layer == 1) c.accentAlt else c.accent).copy(alpha = (if (style.dark) 0.09f else 0.08f) * strength))
    }
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
    // 樱's signature, as the sun is 暮's: a branch in bloom reaching in from the right. On pages it
    // sits in the top corner behind the header buttons; on the landscape stage, right of the lyrics
    // and below the top bar.
    when {
        page -> drawBlossomBranch(style, t, topRight = Offset(w * 1.03f, -h * 0.01f), width = w * 0.46f, height = h * 0.085f, strength = 0.6f)
        w > h -> drawBlossomBranch(style, t, topRight = Offset(w * 1.02f, h * 0.2f), width = w * 0.25f, height = h * 0.36f, strength = 1f)
    }
    val count = if (page) 6 else 16
    val margin = 24f * density
    for (i in 0 until count) {
        val speed = (28f + 34f * hash01(i, 2)) * density * (0.6f + 0.6f * e)
        val y = ((hash01(i, 3) * (h + margin) + t * speed) % (h + 2 * margin)) - margin
        val sway = (14f + 26f * hash01(i, 4)) * density
        val x = hash01(i, 1) * w + sway * sin(t * (0.6f + hash01(i, 5)) + i)
        val sizePx = (6f + 8f * hash01(i, 6)) * density
        val tint = lerp(c.accent, Color.White, if (style.dark) 0.15f else 0.35f * hash01(i, 7))
        val alpha = if (page) 0.16f + 0.2f * hash01(i, 10) else 0.14f + 0.22f * hash01(i, 10)
        drawPetal(Offset(x, y), sizePx, rotation = hash01(i, 8) * 360f + t * (40f + 60f * hash01(i, 9)), color = tint.copy(alpha = alpha))
    }
}

/** A blossoming branch drawn into the box hanging left and down from [topRight]. */
private fun DrawScope.drawBlossomBranch(style: LanyinStyle, t: Float, topRight: Offset, width: Float, height: Float, strength: Float) {
    val c = style.colors
    fun at(x: Float, y: Float) = Offset(topRight.x - width * (1f - x), topRight.y + height * y)
    val wood = lerp(c.content, c.accentAlt, 0.35f).copy(alpha = (if (style.dark) 0.45f else 0.35f) * strength)
    val stem = Path().apply {
        val a = at(1f, 0f); moveTo(a.x, a.y)
        val c1 = at(0.72f, 0.2f); val c2 = at(0.5f, 0.26f); val e = at(0.08f, 0.7f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
    }
    val twig = Path().apply {
        val a = at(0.62f, 0.2f); moveTo(a.x, a.y)
        val q = at(0.5f, 0.55f); val e = at(0.54f, 0.92f)
        quadraticTo(q.x, q.y, e.x, e.y)
    }
    drawPath(stem, wood, style = Stroke(4f * density, cap = StrokeCap.Round))
    drawPath(twig, wood, style = Stroke(2.5f * density, cap = StrokeCap.Round))
    val petal = lerp(c.accent, Color.White, if (style.dark) 0.25f else 0.45f).copy(alpha = (if (style.dark) 0.55f else 0.7f) * strength)
    val unit = minOf(width, height * 1.6f)
    val sway = 2f * density * sin(t * 0.7f)
    listOf(
        Offset(0.86f, 0.08f) to 0.1f, Offset(0.66f, 0.2f) to 0.085f, Offset(0.4f, 0.4f) to 0.095f,
        Offset(0.12f, 0.66f) to 0.075f, Offset(0.52f, 0.6f) to 0.07f, Offset(0.55f, 0.9f) to 0.06f,
    ).forEachIndexed { i, (p, r) ->
        drawBlossom(at(p.x, p.y) + Offset(sway, 0f), unit * r, i * 23f + t * 3f, petal, c.accent.copy(alpha = 0.8f * strength))
    }
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
        page -> w * 0.15f
        landscape -> h * 0.24f
        else -> w * 0.3f
    }
    val center = when {
        page -> Offset(w * 0.95f, h * 0.02f)
        landscape -> Offset(w * 0.9f, h * 0.5f + bob)
        else -> Offset(w * 0.8f, h * 0.2f + bob)
    }
    val sun = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    // At night the sun is an ember: sunk into the sky so it glows without glaring.
    val sink = if (style.dark) 0.5f else 0.3f
    clipPath(sun) {
        drawRect(
            // Orange shading to burnt orange; the mustard only tints the crown.
            Brush.verticalGradient(
                listOf(lerp(lerp(c.accentAlt, c.accent, 0.4f), c.bgDeep, sink), lerp(c.accent, c.bgDeep, sink)),
                startY = center.y - radius, endY = center.y + radius,
            ),
        )
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
