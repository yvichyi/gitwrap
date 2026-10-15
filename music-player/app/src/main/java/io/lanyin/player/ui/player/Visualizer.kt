package io.lanyin.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.design.drawHydroBubble
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.PlayerSkin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Number of frequency bands; each sample around an outline reads the band its position maps to. */
internal const val SPECTRUM_BANDS = 32

/**
 * A believable stand-in for an FFT until playback lands in M2: smooth levels in 0..1, louder in
 * the bass, with a kick on every beat. Band 0 is the lowest.
 */
internal fun demoSpectrum(t: Float, out: FloatArray) {
    val n = out.size
    // 96 bpm: a pulse that decays across each beat.
    val beat = (1f - (t * 1.6f) % 1f).pow(3)
    for (i in 0 until n) {
        val f = if (n > 1) i / (n - 1f) else 0f
        val tilt = 1f - 0.62f * f
        val wobble = 0.5f +
            0.22f * sin(t * 2.3f + f * 9f) +
            0.16f * sin(t * 4.1f - f * 17f + 1.3f) +
            0.12f * sin(t * 7.7f + f * 31f + 0.4f)
        val kick = beat * (1f - f).pow(2) * 0.45f
        out[i] = (tilt * wobble + kick).coerceIn(0f, 1f)
    }
}

/** Level at [m] in 0..1 across the bands, interpolated so neighbouring samples move together. */
private fun FloatArray.levelAt(m: Float): Float {
    val x = m.coerceIn(0f, 1f) * (size - 1)
    val i = x.toInt().coerceAtMost(size - 2)
    val frac = x - i
    return this[i] + (this[i + 1] - this[i]) * frac
}

/** Points along an outline with their outward normals, as flat x,y pairs. */
internal class OutlineSamples(val points: FloatArray, val normals: FloatArray) {
    val count: Int get() = points.size / 2
}

/**
 * [count] points spaced evenly by arc length around the superellipse |x/a|^n + |y/b|^n = 1
 * centred on ([cx], [cy]): n = 2 is a circle, larger n tends to a rounded rectangle. The first
 * point is at the top and they run clockwise.
 */
internal fun superellipseSamples(cx: Float, cy: Float, a: Float, b: Float, n: Float, count: Int): OutlineSamples {
    val dense = 1024
    val xs = FloatArray(dense + 1)
    val ys = FloatArray(dense + 1)
    val lengths = FloatArray(dense + 1)
    // In doubles: the small exponent magnifies float error in sin(π) into a visible kink.
    val e = 2.0 / n
    for (k in 0..dense) {
        val phi = 2.0 * PI * k / dense
        val s = sin(phi)
        val c = cos(phi)
        xs[k] = (a * sign(s) * abs(s).pow(e)).toFloat()
        ys[k] = (-b * sign(c) * abs(c).pow(e)).toFloat()
        if (k > 0) lengths[k] = lengths[k - 1] + hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
    }
    val total = lengths[dense]
    val points = FloatArray(count * 2)
    val normals = FloatArray(count * 2)
    var seg = 1
    for (i in 0 until count) {
        val target = total * i / count
        while (seg < dense && lengths[seg] < target) seg++
        val span = (lengths[seg] - lengths[seg - 1]).coerceAtLeast(1e-6f)
        val frac = (target - lengths[seg - 1]) / span
        val x = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * frac
        val y = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * frac
        // Outward normal from the gradient of the implicit form.
        var nx = sign(x) * abs(x / a).pow(n - 1) / a
        var ny = sign(y) * abs(y / b).pow(n - 1) / b
        val len = hypot(nx, ny).coerceAtLeast(1e-6f)
        nx /= len
        ny /= len
        points[i * 2] = cx + x
        points[i * 2 + 1] = cy + y
        normals[i * 2] = nx
        normals[i * 2 + 1] = ny
    }
    return OutlineSamples(points, normals)
}

/** The shape a skin's artwork draws inside its bounds, so the halo can hug it. */
private class HaloShape(val inset: Dp, val exponent: Float)

private fun haloShape(skin: PlayerSkin, style: LanyinStyle): HaloShape? = when (skin) {
    PlayerSkin.VINYL -> HaloShape(14.dp, 2f)
    PlayerSkin.COVER -> HaloShape(18.dp, if (style.artShape == RectangleShape) 40f else 7f)
    PlayerSkin.CASSETTE -> HaloShape(16.dp, 12f)
    PlayerSkin.LYRICS -> null
}

/** How many marks each theme draws around a full outline. */
private fun LanyinStyle.markCount(): Int = when (backdrop) {
    BackdropKind.Ink -> 120
    BackdropKind.Tide -> 96
    BackdropKind.Sakura -> 56
    BackdropKind.Neon -> 72
    BackdropKind.Dusk -> 44
    BackdropKind.Grid -> 56
}

/**
 * A spectrum that grows out of the artwork's edge, drawn in the theme's own hand: hairline dial
 * ticks for 墨, a double wave for 澜, floating dots for 樱, glowing bars for 霓 and stacked VU
 * blocks for 暮. Mirrored left and right with the bass at the top. Redraws every frame without
 * recomposing; while paused it settles into a quiet resting ring.
 */
@Composable
fun Modifier.spectrumHalo(skin: PlayerSkin, playing: Boolean): Modifier {
    val style = Lanyin.style
    val shape = haloShape(skin, style) ?: return this
    val clock = rememberFrameClock(playing)
    val energy = animateFloatAsState(if (playing) 1f else 0f, tween(900), label = "spectrumEnergy")
    return drawWithCache {
        val inset = shape.inset.toPx()
        val gap = 6.dp.toPx()
        val reach = (size.minDimension * 0.075f).coerceIn(10.dp.toPx(), 22.dp.toPx())
        // A record is round whatever box it is given; other artwork fills its bounds.
        val halfW = (if (shape.exponent == 2f) size.minDimension else size.width) / 2f
        val halfH = (if (shape.exponent == 2f) size.minDimension else size.height) / 2f
        val samples = superellipseSamples(
            size.width / 2f, size.height / 2f,
            halfW - inset + gap, halfH - inset + gap,
            shape.exponent, style.markCount(),
        )
        val levels = FloatArray(SPECTRUM_BANDS)
        val echo = FloatArray(SPECTRUM_BANDS)
        onDrawBehind {
            demoSpectrum(clock.floatValue, levels)
            demoSpectrum(clock.floatValue - 0.35f, echo)
            drawSpectrum(style, samples, levels, echo, energy.value, reach, alpha = 1f)
        }
    }
}

/** Band position for sample [i]: mirrored so both halves of the ring share the treble, bass at the top. */
private fun OutlineSamples.bandAt(i: Int): Float {
    val u = i / count.toFloat()
    return if (u <= 0.5f) u * 2f else (1f - u) * 2f
}

private fun DrawScope.drawSpectrum(
    style: LanyinStyle,
    samples: OutlineSamples,
    levels: FloatArray,
    echo: FloatArray,
    energy: Float,
    reach: Float,
    alpha: Float,
) {
    val c = style.colors
    val p = samples.points
    val nrm = samples.normals
    val n = samples.count
    fun px(i: Int, out: Float) = p[i * 2] + nrm[i * 2] * out
    fun py(i: Int, out: Float) = p[i * 2 + 1] + nrm[i * 2 + 1] * out
    fun level(i: Int) = levels.levelAt(samples.bandAt(i)) * energy

    when (style.backdrop) {
        // Hairline dial: short ticks at rest, every tenth a little longer, growing with the music.
        BackdropKind.Ink -> {
            val stroke = 1.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val major = i % 10 == 0
                val len = (if (major) 6.dp.toPx() else 3.dp.toPx()) + v * reach
                drawLine(
                    c.content.copy(alpha = alpha * ((if (major) 0.42f else 0.2f) + 0.45f * v)),
                    Offset(px(i, 0f), py(i, 0f)), Offset(px(i, len), py(i, len)),
                    stroke,
                )
            }
        }
        // Two waves, the second one a beat behind, with a soft band filled between edge and wave.
        BackdropKind.Tide -> {
            val rest = 2.dp.toPx()
            val outer = Path()
            val inner = Path()
            val trail = Path()
            for (i in 0 until n) {
                val v = level(i)
                val e = echo.levelAt(samples.bandAt(i)) * energy
                val o = rest + v * reach
                val t = rest + 1.dp.toPx() + e * reach * 0.8f
                if (i == 0) {
                    outer.moveTo(px(i, o), py(i, o)); inner.moveTo(px(i, 0f), py(i, 0f)); trail.moveTo(px(i, t), py(i, t))
                } else {
                    outer.lineTo(px(i, o), py(i, o)); inner.lineTo(px(i, 0f), py(i, 0f)); trail.lineTo(px(i, t), py(i, t))
                }
            }
            outer.close(); inner.close(); trail.close()
            val band = Path().apply {
                addPath(outer)
                addPath(inner)
                fillType = PathFillType.EvenOdd
            }
            drawPath(band, c.accent.copy(alpha = alpha * 0.14f))
            drawPath(trail, c.accentAlt.copy(alpha = alpha * 0.45f), style = Stroke(1.dp.toPx()))
            drawPath(outer, c.accent.copy(alpha = alpha * 0.7f), style = Stroke(1.5.dp.toPx()))
            // Bubbles thrown off the crests.
            for (i in 0 until n step 8) {
                val v = level(i)
                if (v < 0.3f) continue
                val o = rest + v * reach + 6.dp.toPx()
                val r = (2.dp + 4.dp * v).toPx()
                if (style.dark) {
                    drawHydroBubble(Offset(px(i, o), py(i, o)), r, i * 0.7f, body = androidx.compose.ui.graphics.Color(0xFF5E9BD6), rim = c.accent, light = c.accentAlt, alpha = alpha * v)
                } else {
                    drawHydroBubble(Offset(px(i, o), py(i, o)), r, i * 0.7f, alpha = alpha * v)
                }
            }
        }
        // Dots that float outward, alternating the two accents.
        BackdropKind.Sakura -> {
            for (i in 0 until n) {
                val v = level(i)
                val color = if (i % 2 == 0) c.accent else c.accentAlt
                drawCircle(color.copy(alpha = alpha * 0.22f), 1.2.dp.toPx(), Offset(px(i, 0f), py(i, 0f)))
                val out = 3.dp.toPx() + v * reach
                drawCircle(color.copy(alpha = alpha * (0.4f + 0.55f * v)), (2.dp + 1.5.dp * v).toPx(), Offset(px(i, out), py(i, out)))
            }
        }
        // Glowing bars that shift from cyan to magenta as they rise.
        BackdropKind.Neon -> {
            val glow = 5.dp.toPx()
            val core = 1.5.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val len = 2.dp.toPx() + v * reach
                val color = lerp(c.accent, c.accentAlt, v)
                val from = Offset(px(i, 0f), py(i, 0f))
                val to = Offset(px(i, len), py(i, len))
                drawLine(color.copy(alpha = alpha * 0.18f), from, to, glow, StrokeCap.Round)
                drawLine(color.copy(alpha = alpha * 0.95f), from, to, core, StrokeCap.Round)
            }
        }
        // 格: heavy black bars, every sixth in red, like a bar chart set in a grid.
        BackdropKind.Grid -> {
            val width = 3.5.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val len = 3.dp.toPx() + v * reach
                val color = if (i % 6 == 0) c.accent else c.content
                drawLine(color.copy(alpha = alpha), Offset(px(i, 0f), py(i, 0f)), Offset(px(i, len), py(i, len)), width)
            }
        }
        // A seventies VU meter: stacked blocks, unlit ones faint, mustard low and orange high.
        BackdropKind.Dusk -> {
            // Near-square blocks of a fixed size so they read as lamps, not dashes.
            val blocks = 4
            val step = (reach / blocks).coerceAtLeast(4.5.dp.toPx())
            val blockLen = step - 1.5.dp.toPx()
            val width = 5.dp.toPx()
            for (i in 0 until n) {
                val lit = ceil(level(i) * blocks).toInt().coerceIn(1, blocks)
                for (k in 0 until blocks) {
                    val color = when {
                        k >= lit -> c.content.copy(alpha = alpha * 0.1f)
                        k < 2 -> c.accentAlt.copy(alpha = alpha)
                        else -> c.accent.copy(alpha = alpha)
                    }
                    val a = k * step
                    val b = a + blockLen
                    drawLine(color, Offset(px(i, a), py(i, a)), Offset(px(i, b), py(i, b)), width)
                }
            }
        }
    }
}
