package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * 澜's watercolour hydro vocabulary, after the reference the listener sent (a Fontaine-era event
 * illustration): cobalt splashes with darker pooled edges and flung droplets, soft jelly-like
 * water bubbles with a dark rim below and a crescent of light above, long four-point sparkles,
 * and fine streamlines sweeping across the paper.
 */

/** The reference palette, sampled from the illustration. */
internal object Hydro {
    val Navy = Color(0xFF131A2C)
    val Cobalt = Color(0xFF4876CE)
    val CobaltDeep = Color(0xFF385F9A)
    val Bubble = Color(0xFF9ECCEE)
    val BubbleRim = Color(0xFF71A9D8)
    val BubbleLight = Color(0xFFD5EBF8)
    val Sparkle = Color(0xFFBFD9FA)
    val Paper = Color(0xFFF4F8FC)
}

private fun polar(center: Offset, r: Float, a: Float) = Offset(center.x + r * cos(a), center.y + r * sin(a))

/**
 * A watercolour splash: an irregular pool thrown in [direction] (radians, screen space), with
 * arms flung out that way, darker pigment pooled at its edge, a paler bloom inside and droplets
 * scattered past the arms.
 */
internal fun DrawScope.drawSplash(
    center: Offset,
    radius: Float,
    seed: Int,
    color: Color,
    edge: Color,
    direction: Float = -0.6f,
    alpha: Float = 1f,
) {
    val steps = 180
    val p1 = hash01(seed, 1) * 6.28f
    val p2 = hash01(seed, 2) * 6.28f
    val p3 = hash01(seed, 3) * 6.28f
    // Streaks: narrow, tapering spikes, most of them thrown the way the splash flew.
    val arms = List(12) { i ->
        val toward = i < 8
        val a = if (toward) direction + (hash01(seed, 10 + i) - 0.5f) * 1.5f else hash01(seed, 10 + i) * 6.28f
        val reach = if (toward) 0.3f + 0.9f * hash01(seed, 20 + i) else 0.08f + 0.2f * hash01(seed, 20 + i)
        val width = 0.03f + 0.05f * hash01(seed, 30 + i)
        Triple(a, reach, width)
    }
    fun radiusAt(a: Float): Float {
        var r = 0.66f + 0.06f * sin(3 * a + p1) + 0.04f * sin(7 * a + p2) + 0.025f * sin(13 * a + p3)
        for ((armAngle, reach, width) in arms) {
            var d = a - armAngle
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            // A sharper falloff than a bell, so streaks come to a point.
            r += reach * exp(-kotlin.math.abs(d) / width)
        }
        return r * radius
    }
    val blob = Path().apply {
        for (i in 0..steps) {
            val a = (i % steps) / steps.toFloat() * 2f * PI.toFloat()
            val p = polar(center, radiusAt(a), a)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(blob, Brush.radialGradient(listOf(color.copy(alpha = 0.5f * alpha), color.copy(alpha = 0.92f * alpha)), center, radius * 1.4f))
    // Pigment gathers at the edge as watercolour dries.
    drawPath(blob, edge.copy(alpha = 0.5f * alpha), style = Stroke(radius * 0.025f))
    // A paler bloom where the water pooled.
    val bloomCenter = center + Offset(-radius * 0.12f, -radius * 0.08f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * alpha), Color.Transparent), bloomCenter, radius * 0.5f), radius * 0.5f, bloomCenter)
    // Dry-brush trails: broken strokes thinning out along the throw.
    for (k in 0 until 4) {
        val a = direction + (hash01(seed, 200 + k) - 0.5f) * 0.7f
        var d = radius * (0.55f + 0.1f * k)
        val end = radius * (1.5f + 0.7f * hash01(seed, 210 + k))
        var width = radius * (0.09f - 0.015f * k)
        var j = 0
        while (d < end && width > 0.6f) {
            val step = radius * (0.12f + 0.1f * hash01(seed, 220 + k * 9 + j))
            drawLine(color.copy(alpha = 0.85f * alpha), polar(center, d, a), polar(center, d + step, a + 0.01f * j), width, StrokeCap.Round)
            d += step * (1.08f + 0.3f * hash01(seed, 240 + k * 9 + j))
            width *= 0.72f
            j++
        }
    }
    // Droplets flung past the streaks, smaller the further they flew.
    for ((i, arm) in arms.withIndex()) {
        val (a, reach) = arm
        if (reach < 0.25f) continue
        val tip = 0.66f + reach
        for (k in 0 until 3) {
            val d = (tip + 0.12f + 0.2f * k + 0.1f * hash01(seed, 40 + i * 3 + k)) * radius
            val jitter = (hash01(seed, 60 + i * 3 + k) - 0.5f) * 0.2f
            val r = radius * (0.04f - 0.011f * k) * (0.6f + 0.6f * hash01(seed, 80 + i * 3 + k))
            if (r > 0.5f) drawCircle(color.copy(alpha = 0.9f * alpha), r, polar(center, d, a + jitter))
        }
    }
    // Granulation: the pigment's fine grain.
    for (i in 0 until 22) {
        val a = hash01(seed, 100 + i) * 6.28f
        val d = radius * 0.62f * (0.15f + 0.8f * hash01(seed, 130 + i))
        drawCircle(edge.copy(alpha = 0.14f * alpha), radius * 0.007f * (1f + hash01(seed, 160 + i)), polar(center, d, a))
    }
}

/**
 * A soft hydro bubble that wobbles as it drifts: a pale body, a darker rim heavier underneath, a
 * crescent of light at the top left and a glint.
 */
internal fun DrawScope.drawHydroBubble(
    center: Offset,
    r: Float,
    wobble: Float,
    body: Color = Hydro.Bubble,
    rim: Color = Hydro.BubbleRim,
    light: Color = Hydro.BubbleLight,
    alpha: Float = 1f,
) {
    val steps = 64
    fun radiusAt(a: Float) = r * (1f + 0.05f * sin(3 * a + wobble) + 0.035f * sin(5 * a - 1.3f * wobble) + 0.02f * sin(2 * a + 0.7f * wobble))
    fun outline(scale: Float, from: Float, to: Float, closed: Boolean) = Path().apply {
        val n = (steps * (to - from) / (2 * PI.toFloat())).toInt().coerceAtLeast(2)
        for (i in 0..n) {
            val a = from + (to - from) * i / n
            val p = polar(center, radiusAt(a) * scale, a)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }
    val full = outline(1f, 0f, 2 * PI.toFloat(), closed = true)
    drawPath(
        full,
        Brush.radialGradient(
            listOf(light.copy(alpha = 0.7f * alpha), body.copy(alpha = 0.42f * alpha), body.copy(alpha = 0.6f * alpha)),
            center + Offset(-r * 0.28f, -r * 0.32f), r * 1.35f,
        ),
    )
    drawPath(full, rim.copy(alpha = 0.4f * alpha), style = Stroke(r * 0.05f))
    // The rim is heavier underneath, where the water gathers.
    drawPath(outline(1f, -0.35f, 2.6f, closed = false), rim.copy(alpha = 0.75f * alpha), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
    // A swirl of lighter water inside.
    drawPath(outline(0.62f, 3.4f, 5.6f, closed = false), Color.White.copy(alpha = 0.45f * alpha), style = Stroke(r * 0.045f, cap = StrokeCap.Round))
    // The crescent of light and a glint.
    drawPath(outline(0.8f, 3.55f, 4.45f, closed = false), Color.White.copy(alpha = 0.92f * alpha), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
    drawCircle(Color.White.copy(alpha = 0.9f * alpha), r * 0.07f, center + Offset(-r * 0.55f, -r * 0.05f))
}

/** A long four-point sparkle, tall rays and short arms, with a soft glow. */
internal fun DrawScope.drawSparkle(center: Offset, height: Float, color: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val v = height / 2f
    val h = height * 0.22f
    val pinch = height * 0.035f
    val star = Path().apply {
        moveTo(center.x, center.y - v)
        quadraticTo(center.x + pinch, center.y - pinch, center.x + h, center.y)
        quadraticTo(center.x + pinch, center.y + pinch, center.x, center.y + v)
        quadraticTo(center.x - pinch, center.y + pinch, center.x - h, center.y)
        quadraticTo(center.x - pinch, center.y - pinch, center.x, center.y - v)
        close()
    }
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * alpha), Color.Transparent), center, height * 0.32f), height * 0.32f, center)
    drawPath(star, color.copy(alpha = alpha))
}

/**
 * Fine streamlines sweeping from [from] to [to], fanned [spread] apart, each with a brighter
 * dash running along it at [t].
 */
internal fun DrawScope.drawStreamlines(from: Offset, to: Offset, count: Int, spread: Float, color: Color, t: Float, alpha: Float = 1f) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    val nx = -dy / len
    val ny = dx / len
    for (i in 0 until count) {
        val off = (i - (count - 1) / 2f) * spread / count.coerceAtLeast(1)
        val bow = len * (0.12f + 0.05f * i)
        val s = Offset(from.x + nx * off * 1.6f, from.y + ny * off * 1.6f)
        val e = Offset(to.x + nx * off * 0.6f, to.y + ny * off * 0.6f)
        val path = Path().apply {
            moveTo(s.x, s.y)
            cubicTo(
                s.x + dx * 0.33f + nx * bow, s.y + dy * 0.33f + ny * bow,
                s.x + dx * 0.66f + nx * bow * 0.4f, s.y + dy * 0.66f + ny * bow * 0.4f,
                e.x, e.y,
            )
        }
        val width = (1f + 1.2f * hash01(i, 300)) * density
        drawPath(path, color.copy(alpha = (0.18f + 0.14f * hash01(i, 301)) * alpha), style = Stroke(width, cap = StrokeCap.Round))
        val dash = len * 0.18f
        drawPath(
            path,
            color.copy(alpha = 0.55f * alpha),
            style = Stroke(
                width * 1.3f, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, len * 1.4f), -(t * len * (0.08f + 0.03f * i)) % (len * 1.4f + dash)),
            ),
        )
    }
}

/** 澜's play button: a solid water bubble in [color] that wobbles at [wobble]. */
internal fun DrawScope.drawBubbleButton(r: Float, wobble: Float, color: Color) {
    val c = center
    fun radiusAt(a: Float) = r * (1f + 0.022f * sin(3 * a + wobble) + 0.015f * sin(5 * a - 1.3f * wobble))
    fun arc(scale: Float, from: Float, to: Float, closed: Boolean) = Path().apply {
        val n = 48
        for (i in 0..n) {
            val a = from + (to - from) * i / n
            val p = Offset(c.x + radiusAt(a) * scale * cos(a), c.y + radiusAt(a) * scale * sin(a))
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }
    val body = arc(1f, 0f, 2 * PI.toFloat(), closed = true)
    drawPath(
        body,
        Brush.radialGradient(
            listOf(androidx.compose.ui.graphics.lerp(color, Color.White, 0.35f), color, androidx.compose.ui.graphics.lerp(color, Color.Black, 0.12f)),
            c + Offset(-r * 0.32f, -r * 0.36f), r * 1.5f,
        ),
    )
    drawPath(arc(0.97f, -0.3f, 2.5f, closed = false), androidx.compose.ui.graphics.lerp(color, Color.Black, 0.25f).copy(alpha = 0.55f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
    drawPath(arc(0.6f, 3.5f, 5.5f, closed = false), Color.White.copy(alpha = 0.22f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
    drawPath(arc(0.8f, 3.6f, 4.4f, closed = false), Color.White.copy(alpha = 0.75f), style = Stroke(r * 0.085f, cap = StrokeCap.Round))
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.06f, c + Offset(-r * 0.56f, -r * 0.02f))
}

/** The sparkle that rides a lyric's sweep in 澜. */
fun DrawScope.drawLyricSparkle(center: Offset, height: Float, color: Color, alpha: Float) = drawSparkle(center, height, color, alpha)
