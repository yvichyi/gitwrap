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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.BackdropKind
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
 * A believable stand-in for the analyzer, for previews and the demo player: smooth levels in
 * 0..1, louder in the bass, with a kick on every beat (96 bpm). Band 0 is the lowest.
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
    BackdropKind.Neon -> 72
    BackdropKind.Dusk -> 44
    BackdropKind.Grid -> 56
    BackdropKind.Film -> 90
    BackdropKind.Dot -> 72
    BackdropKind.Device -> 60
}

/**
 * A spectrum that grows out of the artwork's edge, drawn in the theme's own hand: hairline dial
 * ticks for 墨, glowing bars for 霓, soft rays for 暮, square bars for 格, a light meter's scale
 * for 影, a ring of LEDs for 点 and a level meter for 机. Mirrored left and right with the bass at
 * the top. Redraws every frame without
 * recomposing, from [feed]: what is being heard; while paused it settles into a quiet resting ring.
 */
@Composable
fun Modifier.spectrumHalo(skin: PlayerSkin, playing: Boolean, feed: io.lanyin.player.core.audio.AudioFeed): Modifier {
    val style = Lanyin.style
    val shape = haloShape(skin, style) ?: return this
    // Ticks every frame so the draw reads the feed again.
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
            clock.floatValue
            feed.levels(levels)
            feed.levels(echo, lagMs = 350)
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
        // Glowing bars that run from blue into violet as they rise.
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
        // 点: a ring of LEDs, each lit as far as its band reaches, the red one at the top.
        BackdropKind.Dot -> {
            for (i in 0 until n) {
                val v = level(i)
                val out = 4.dp.toPx() + v * reach * 0.55f
                val top = i == 0
                drawCircle(
                    (if (top) c.accentAlt else c.content).copy(alpha = alpha * (if (top) 1f else 0.16f + 0.84f * v)),
                    (1.5f + 1.2f * v).dp.toPx(), Offset(px(i, out), py(i, out)),
                )
            }
        }
        // 机: a level meter's bars, turning orange where they peak.
        BackdropKind.Device -> {
            val width = 2.5.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val len = 2.dp.toPx() + v * reach
                val color = if (v > 0.68f) c.accent else c.content.copy(alpha = 0.45f + 0.45f * v)
                drawLine(color.copy(alpha = color.alpha * alpha), Offset(px(i, 0f), py(i, 0f)), Offset(px(i, len), py(i, len)), width)
            }
        }
        // 影: a light meter's scale, ticks in the film's orange, every fifth longer.
        BackdropKind.Film -> {
            val stroke = 1.2.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val major = i % 5 == 0
                val len = (if (major) 5.dp.toPx() else 2.5.dp.toPx()) + v * reach
                drawLine(lerp(c.content, c.accent, 0.4f + 0.6f * v).copy(alpha = alpha * ((if (major) 0.5f else 0.28f) + 0.5f * v)), Offset(px(i, 0f), py(i, 0f)), Offset(px(i, len), py(i, len)), stroke)
            }
        }
        // Soft rounded rays, rose at the root warming to terracotta as they reach.
        BackdropKind.Dusk -> {
            val width = 3.dp.toPx()
            for (i in 0 until n) {
                val v = level(i)
                val len = 2.dp.toPx() + v * reach
                drawLine(
                    lerp(c.accentAlt, c.accent, v).copy(alpha = alpha * (0.35f + 0.6f * v)),
                    Offset(px(i, 0f), py(i, 0f)), Offset(px(i, len), py(i, len)), width, StrokeCap.Round,
                )
            }
        }
    }
}
