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
fun Backdrop(modifier: Modifier = Modifier, live: Boolean = false, playing: Boolean = false, trackNumber: Int? = null) {
    val style = Lanyin.style
    val clock = rememberFrameClock(live)
    val measurer = if (style.backdrop == BackdropKind.Grid) androidx.compose.ui.text.rememberTextMeasurer() else null
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
            BackdropKind.Grid -> drawGridPaper(style, page, measurer, trackNumber)
        }
    }
}

private fun DrawScope.glowBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

/**
 * 澜: watercolour on paper (navy by night) after a Fontaine-era illustration. A cobalt splash
 * thrown into a corner, fine streamlines sweeping across, soft water bubbles rising and wobbling,
 * long sparkles twinkling. Pages keep it to a corner and a couple of bubbles.
 */
private fun DrawScope.drawTide(style: LanyinStyle, t: Float, e: Float, page: Boolean, @Suppress("UNUSED_PARAMETER") moonPhase: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val dark = style.dark
    val unit = minOf(w, h)
    drawRect(c.bg)
    // Washes pooled on the paper.
    glowBlob(c.bgDeep.copy(alpha = if (dark) 0.9f else 0.85f), Offset(w * 0.15f, h * 0.1f), maxOf(w, h) * 0.7f)
    glowBlob(c.accentAlt.copy(alpha = if (dark) 0.08f else 0.12f), Offset(w * 0.9f, h * 0.6f), maxOf(w, h) * 0.55f)

    val splash = if (dark) Hydro.CobaltDeep else Hydro.Cobalt
    val splashEdge = if (dark) Hydro.Sparkle else Hydro.CobaltDeep
    val stream = if (dark) Hydro.Sparkle else Hydro.BubbleRim
    val sparkle = if (dark) Hydro.Sparkle else Color(0xFF9DBEF0)
    fun bubble(center: Offset, r: Float, wobble: Float, alpha: Float = 1f) = if (dark) {
        drawHydroBubble(center, r, wobble, body = Color(0xFF5E9BD6), rim = Hydro.Bubble, light = Color(0xFF8FC3EE), alpha = alpha * 0.85f)
    } else {
        drawHydroBubble(center, r, wobble, alpha = alpha)
    }
    fun twinkle(i: Int) = (0.35f + 0.65f * sin(t * (0.9f + 0.3f * i) + i * 2.1f).let { it * it })

    if (page) {
        // Tucked into the corner, behind the header's buttons rather than its text.
        drawSplash(Offset(w * 1.03f, -unit * 0.04f), unit * 0.165f, seed = 3, splash, splashEdge, direction = 2.5f, alpha = if (dark) 0.7f else 0.78f)
        drawStreamlines(Offset(-w * 0.05f, h * 0.66f), Offset(w * 1.05f, h * 0.44f), 3, unit * 0.14f, stream, t, alpha = 0.55f)
        // Half off the edges, where lists keep their hearts and row numbers clear of them.
        bubble(Offset(w + unit * 0.006f, h * 0.31f), unit * 0.04f, 0.7f)
        bubble(Offset(-unit * 0.004f, h * 0.86f), unit * 0.03f, 2.3f, alpha = 0.8f)
        drawSparkle(Offset(w * 0.7f, h * 0.12f), unit * 0.075f, sparkle, 0.85f)
        return
    }

    val landscape = w > h
    // The splash thrown in from the edge, kept clear of the bars and their buttons; a smaller one
    // answering from the other side.
    if (landscape) {
        // Long lyric lines can reach this far: keep it lighter so they still read over it.
        drawSplash(Offset(w * 1.04f, h * 0.4f), unit * 0.24f, seed = 3, splash, splashEdge, direction = 2.75f, alpha = if (dark) 0.55f else 0.7f)
        drawSplash(Offset(-unit * 0.04f, h * 1.02f), unit * 0.15f, seed = 7, splash, splashEdge, direction = -0.7f, alpha = if (dark) 0.55f else 0.65f)
    } else {
        drawSplash(Offset(w * 1.0f, -unit * 0.02f), unit * 0.26f, seed = 3, splash, splashEdge, direction = 2.5f, alpha = if (dark) 0.8f else 0.9f)
        drawSplash(Offset(-unit * 0.06f, h * 0.44f), unit * 0.13f, seed = 7, splash, splashEdge, direction = 0.3f, alpha = if (dark) 0.55f else 0.65f)
    }
    drawStreamlines(Offset(-w * 0.05f, h * 0.92f), Offset(w * 1.05f, h * 0.3f), 4, unit * 0.22f, stream, t, alpha = 0.6f + 0.3f * e)

    // Bubbles rising and wobbling, quicker while the music plays.
    val margin = unit * 0.1f
    for (i in 0 until 9) {
        val r = unit * (0.014f + 0.05f * hash01(i, 70).let { it * it })
        val speed = (10f + 18f * hash01(i, 71)) * density * (0.5f + 0.9f * e)
        val span = h + margin * 2
        val y = h + margin - ((hash01(i, 72) * span + t * speed) % span)
        val x = w * hash01(i, 73) + 10f * density * sin(t * (0.5f + 0.4f * hash01(i, 74)) + i * 1.7f)
        val fade = minOf(1f, (y / h).coerceIn(0f, 1f) * 4f) * minOf(1f, ((h - y) / h + 0.15f) * 3f).coerceIn(0f, 1f)
        bubble(Offset(x, y), r, t * (0.9f + 0.5f * hash01(i, 75)) + i, alpha = 0.85f * fade)
    }
    // Long sparkles, twinkling out of step.
    // Placed in the gaps the layout leaves: never on the lyrics.
    val spots = if (landscape) {
        listOf(Offset(w * 0.04f, h * 0.3f), Offset(w * 0.95f, h * 0.74f), Offset(w * 0.05f, h * 0.66f))
    } else {
        listOf(Offset(w * 0.18f, h * 0.2f), Offset(w * 0.86f, h * 0.5f), Offset(w * 0.1f, h * 0.62f))
    }
    spots.forEachIndexed { i, p -> drawSparkle(p, unit * (0.09f - 0.02f * i), sparkle, twinkle(i)) }
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
