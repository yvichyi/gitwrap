package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generated album art in the active theme's visual language. Stands in for real covers until
 * the NetEase client lands, and keeps placeholder grids from looking empty.
 */
@Composable
fun CoverArt(seed: Int, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    Canvas(modifier) { drawCoverArt(style, seed) }
}

fun DrawScope.drawCoverArt(style: LanyinStyle, seed: Int) {
    when (style.theme) {
        AppTheme.TIDE -> tideCover(style, seed)
        AppTheme.INK -> inkCover(style, seed)
        AppTheme.SAKURA -> sakuraCover(style, seed)
        AppTheme.NEON -> neonCover(style, seed)
        AppTheme.GRID -> gridCover(style, seed)
        AppTheme.DUSK -> {
            duskCover(style, seed)
            // Sleeves printed in mustard and orange glare on a dark page; dim them at night.
            if (style.dark) drawRect(Color.Black.copy(alpha = 0.18f))
        }
    }
}

private fun DrawScope.tideCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val deep = if (style.dark) lerp(c.bgDeep, c.accent, 0.35f) else lerp(c.accent, Color.Black, 0.25f)
    val (from, to) = if (seed % 2 == 0) deep to c.accentAlt else c.accentAlt to deep
    // Four compositions so a row of covers doesn't read as one image repeated.
    when (seed % 4) {
        0 -> {
            // Moon over swells.
            drawRect(Brush.linearGradient(listOf(from, lerp(from, to, 0.5f), to), Offset.Zero, Offset(w, h)))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.09f, center = Offset(w * (0.62f + 0.2f * hash01(seed, 1)), h * (0.22f + 0.12f * hash01(seed, 2))))
            for (i in 0 until 4) drawSwell(h * (0.52f + i * 0.1f), h * 0.035f, w * (0.7f + 0.15f * i), hash01(seed, 3 + i) * 6f, Color.White.copy(alpha = 0.18f + 0.1f * i), w * 0.012f)
        }
        1 -> {
            // Light falling into deep water, bubbles rising.
            drawRect(Brush.verticalGradient(listOf(lerp(c.accentAlt, Color.White, 0.2f), deep, lerp(deep, Color.Black, 0.35f))))
            for (i in 0 until 4) {
                val x0 = w * (0.1f + 0.22f * i + 0.05f * hash01(seed, 10 + i))
                val ray = Path().apply {
                    moveTo(x0, 0f)
                    lineTo(x0 + w * 0.08f, 0f)
                    lineTo(x0 + w * 0.3f, h)
                    lineTo(x0 + w * 0.12f, h)
                    close()
                }
                drawPath(ray, Color.White.copy(alpha = 0.08f + 0.05f * hash01(seed, 20 + i)))
            }
            for (i in 0 until 9) {
                val r = w * (0.012f + 0.022f * hash01(seed * 3 + i, 31))
                drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(w * (0.55f + 0.35f * hash01(seed * 3 + i, 32)), h * (0.3f + 0.65f * hash01(seed * 3 + i, 33))), style = Stroke(w * 0.006f))
            }
        }
        2 -> {
            // A moon and its broken reflection.
            val horizon = h * 0.56f
            drawRect(Brush.verticalGradient(listOf(lerp(deep, Color.Black, 0.2f), deep), endY = horizon))
            drawRect(Brush.verticalGradient(listOf(deep, lerp(deep, Color.Black, 0.45f)), startY = horizon), topLeft = Offset(0f, horizon))
            val mx = w * (0.38f + 0.24f * hash01(seed, 40))
            drawCircle(lerp(c.accentAlt, Color.White, 0.55f), w * 0.15f, Offset(mx, horizon - h * 0.2f))
            for (k in 0 until 8) {
                val y = horizon + h * (0.04f + k * 0.052f)
                val half = w * (0.16f - k * 0.012f) * (0.7f + 0.3f * hash01(seed * 5 + k, 41))
                drawLine(Color.White.copy(alpha = 0.6f - k * 0.06f), Offset(mx - half, y), Offset(mx + half, y), h * 0.012f, StrokeCap.Round)
            }
        }
        else -> {
            // Ribbons of current crossing the frame.
            drawRect(Brush.linearGradient(listOf(lerp(deep, Color.Black, 0.3f), deep), Offset.Zero, Offset(w, h)))
            for (i in 0 until 3) {
                val color = if (i == 1) c.accentAlt else lerp(c.accent, Color.White, 0.3f)
                val base = h * (0.3f + 0.2f * i)
                val k = (2 * PI / (w * (0.9f + 0.3f * i))).toFloat()
                val phase = hash01(seed, 50 + i) * 6f
                val ribbon = Path().apply {
                    moveTo(0f, base)
                    var x = 0f
                    while (x <= w) { lineTo(x, base + h * 0.08f * sin(k * x + phase)); x += w / 48f }
                    while (x >= 0f) { lineTo(x, base + h * 0.06f + h * 0.08f * sin(k * x + phase + 0.6f)); x -= w / 48f }
                    close()
                }
                drawPath(ribbon, color.copy(alpha = 0.35f + 0.15f * i))
            }
        }
    }
}

/** One swell line across the cover. */
private fun DrawScope.drawSwell(baseY: Float, amp: Float, wavelength: Float, phase: Float, color: Color, stroke: Float) {
    val k = (2 * PI / wavelength).toFloat()
    val path = Path().apply {
        moveTo(0f, baseY)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, baseY + amp * sin(k * x + phase))
            x += size.width / 48f
        }
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun DrawScope.inkCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.surfaceAlt)
    val ink = c.content
    // Four compositions so a row of covers doesn't read as one image repeated.
    when (seed % 4) {
        0 -> {
            val center = Offset(w * (0.46f + 0.08f * hash01(seed, 1)), h * (0.46f + 0.06f * hash01(seed, 2)))
            rotate(hash01(seed, 3) * 360f, center) {
                drawPath(ensoPath(center, w * 0.3f, w * 0.085f, sweepDegrees = 300f), ink.copy(alpha = 0.92f))
            }
        }
        1 -> {
            // Ink-wash mountains, darkest at the peaks.
            fun mountain(peakX: Float, peakY: Float, spread: Float, alpha: Float) {
                val path = Path().apply {
                    moveTo(peakX - spread, h)
                    quadraticTo(peakX - spread * 0.35f, peakY + h * 0.05f, peakX, peakY)
                    quadraticTo(peakX + spread * 0.4f, peakY + h * 0.08f, peakX + spread, h)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(ink.copy(alpha = alpha), ink.copy(alpha = alpha * 0.08f)), startY = peakY, endY = h))
            }
            mountain(w * 0.66f, h * (0.38f + 0.06f * hash01(seed, 4)), w * 0.55f, 0.45f)
            mountain(w * 0.34f, h * (0.5f + 0.05f * hash01(seed, 5)), w * 0.5f, 0.9f)
            drawCircle(c.accentAlt, w * 0.055f, Offset(w * 0.78f, h * 0.24f))
            return
        }
        2 -> drawPath(brushStrokePath(Offset(w * 0.16f, h * (0.56f + 0.06f * hash01(seed, 6))), w * 0.68f, w * 0.11f), ink.copy(alpha = 0.92f))
        else -> {
            // Ripples round a dropped stone.
            val center = Offset(w * (0.38f + 0.12f * hash01(seed, 7)), h * (0.42f + 0.1f * hash01(seed, 8)))
            for (i in 1..3) drawCircle(ink.copy(alpha = 0.75f - i * 0.15f), w * 0.1f * i, center, style = Stroke(w * 0.012f))
            drawCircle(ink, w * 0.035f, center)
        }
    }
    // The red seal.
    drawRect(c.accentAlt, topLeft = Offset(w * 0.74f, h * 0.76f), size = Size(w * 0.1f, w * 0.1f))
}

/** One horizontal stroke of a loaded brush: pressed down at the start, lifting into a dry tail. */
internal fun brushStrokePath(start: Offset, length: Float, thickness: Float): Path {
    val steps = 48
    val top = ArrayList<Offset>(steps + 1)
    val bottom = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val half = thickness / 2f * (0.8f + 0.2f * sin(PI.toFloat() * f)) * (1f - 0.88f * f.pow(4f))
        val x = start.x + length * f
        val y = start.y - length * 0.035f * sin(PI.toFloat() * f)
        top += Offset(x, y - half)
        bottom += Offset(x, y + half * 0.9f)
    }
    return Path().apply {
        moveTo(top[0].x, top[0].y)
        top.drop(1).forEach { lineTo(it.x, it.y) }
        bottom.asReversed().forEach { lineTo(it.x, it.y) }
        // Round heel where the brush first touched the paper, as part of the same outline.
        val heelRadius = (bottom[0].y - top[0].y) / 2f
        val heelCenter = Offset(start.x, (top[0].y + bottom[0].y) / 2f)
        arcTo(androidx.compose.ui.geometry.Rect(heelCenter, heelRadius), 90f, 180f, false)
        close()
    }
}

private fun DrawScope.sakuraCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val a = lerp(c.accent, Color.White, 0.45f)
    val b = lerp(c.accentAlt, Color.White, 0.4f)
    drawRect(Brush.linearGradient(if (seed % 2 == 0) listOf(a, b) else listOf(b, a), Offset.Zero, Offset(w, h)))
    when (seed % 4) {
        0 -> {
            // A soft moon in a flurry of petals.
            drawCircle(Color.White.copy(alpha = 0.35f), radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.48f))
            for (i in 0 until 6) {
                drawPetal(
                    center = Offset(w * (0.15f + 0.7f * hash01(seed * 7 + i, 1)), h * (0.15f + 0.7f * hash01(seed * 7 + i, 2))),
                    sizePx = w * (0.05f + 0.05f * hash01(seed * 7 + i, 3)),
                    rotation = hash01(seed * 7 + i, 4) * 360f,
                    color = (if (i % 2 == 0) Color.White else c.accent).copy(alpha = 0.85f),
                )
            }
        }
        1 -> {
            // One big blossom.
            val center = Offset(w * (0.44f + 0.12f * hash01(seed, 5)), h * (0.46f + 0.1f * hash01(seed, 6)))
            drawBlossom(center, w * 0.2f, hash01(seed, 7) * 72f, Color.White.copy(alpha = 0.92f), lerp(c.accent, Color.White, 0.1f))
        }
        2 -> {
            // A branch in bloom.
            val branch = Path().apply {
                moveTo(-w * 0.05f, h * 0.82f)
                cubicTo(w * 0.3f, h * 0.7f, w * 0.45f, h * 0.35f, w * 1.05f, h * 0.2f)
            }
            drawPath(branch, lerp(c.content, c.accentAlt, 0.3f).copy(alpha = 0.75f), style = Stroke(w * 0.03f, cap = StrokeCap.Round))
            for (i in 0 until 5) {
                val f = 0.18f + i * 0.17f
                val p = Offset(w * (-0.05f + 1.1f * f), h * (0.82f - 0.62f * f.pow(0.8f)) + h * 0.05f * (hash01(seed + i, 8) - 0.5f))
                drawBlossom(p, w * (0.06f + 0.03f * hash01(seed + i, 9)), hash01(seed + i, 10) * 72f, Color.White.copy(alpha = 0.95f), c.accent)
            }
        }
        else -> {
            // Bokeh: soft overlapping lights.
            for (i in 0 until 8) {
                val color = if (i % 2 == 0) Color.White else lerp(c.accentAlt, Color.White, 0.3f)
                drawCircle(
                    color.copy(alpha = 0.18f + 0.22f * hash01(seed * 11 + i, 12)),
                    radius = w * (0.08f + 0.14f * hash01(seed * 11 + i, 13)),
                    center = Offset(w * hash01(seed * 11 + i, 14), h * hash01(seed * 11 + i, 15)),
                )
            }
        }
    }
}

/** Five petals round a centre. */
internal fun DrawScope.drawBlossom(center: Offset, radius: Float, rotation: Float, petal: Color, heart: Color) {
    for (k in 0 until 5) {
        val angle = Math.toRadians((rotation + k * 72f - 90f).toDouble())
        val p = Offset(center.x + radius * 0.55f * kotlin.math.cos(angle).toFloat(), center.y + radius * 0.55f * kotlin.math.sin(angle).toFloat())
        drawPetal(p, radius * 0.55f, rotation + k * 72f + 180f, petal)
    }
    drawCircle(heart, radius * 0.2f, center)
}

private fun DrawScope.neonCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.surface)
    when (seed % 4) {
        0 -> {
            // Rings over a glowing floor.
            val center = Offset(w * (0.4f + 0.2f * hash01(seed, 1)), h * (0.4f + 0.2f * hash01(seed, 2)))
            for (ring in 0 until 3) {
                val color = if ((ring + seed) % 2 == 0) c.accent else c.accentAlt
                glowCircle(color, w * (0.14f + 0.1f * ring), center, w)
            }
            var y = h * 0.78f
            while (y < h) {
                drawLine(c.accentAlt.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), strokeWidth = h * 0.008f)
                y += h * 0.045f
            }
        }
        3 -> {
            // Synthwave sun on a grid.
            val horizon = h * 0.62f
            drawRect(Brush.verticalGradient(listOf(c.surface, lerp(c.surface, c.accentAlt, 0.35f)), endY = horizon))
            val sun = Offset(w * 0.5f, horizon)
            val r = w * 0.27f
            clipRect(bottom = horizon) {
                drawCircle(Brush.verticalGradient(listOf(lerp(c.accentAlt, Color.Yellow, 0.45f), c.accentAlt), startY = sun.y - r, endY = sun.y), r, sun)
                for (k in 0 until 4) drawRect(c.surface, Offset(0f, sun.y - r * (0.12f + k * 0.2f)), Size(w, r * (0.03f + k * 0.015f)))
            }
            for (i in -6..6) drawLine(c.accent.copy(alpha = 0.6f), Offset(w / 2 + i * w * 0.03f, horizon), Offset(w / 2 + i * w * 0.2f, h), w * 0.006f)
            for (j in 1..4) {
                val y = horizon + (h - horizon) * (j / 4f).pow(1.8f)
                drawLine(c.accent.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), w * 0.006f)
            }
        }
        2 -> {
            // A neon prism.
            val cx = w * (0.45f + 0.1f * hash01(seed, 3))
            val tri = Path().apply {
                moveTo(cx, h * 0.2f)
                lineTo(cx + w * 0.3f, h * 0.72f)
                lineTo(cx - w * 0.3f, h * 0.72f)
                close()
            }
            glowPath(tri, c.accent, w)
            val inner = Path().apply {
                moveTo(cx, h * 0.62f)
                lineTo(cx + w * 0.13f, h * 0.4f)
                lineTo(cx - w * 0.13f, h * 0.4f)
                close()
            }
            glowPath(inner, c.accentAlt, w)
        }
        1 -> {
            // Equaliser bars.
            val bars = 7
            val gap = w * 0.035f
            val bw = (w * 0.8f - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val level = 0.25f + 0.7f * hash01(seed * 13 + i, 16)
                val x = w * 0.1f + i * (bw + gap)
                var y = h * 0.82f
                val top = h * 0.82f - h * 0.62f * level
                while (y > top) {
                    val f = ((h * 0.82f - y) / (h * 0.62f)).coerceIn(0f, 1f)
                    drawRect(lerp(c.accent, c.accentAlt, f), Offset(x, y - h * 0.035f), Size(bw, h * 0.028f))
                    y -= h * 0.045f
                }
            }
        }
    }
}

/** A neon tube circle: wide faint glow, then the bright core. */
private fun DrawScope.glowCircle(color: Color, radius: Float, center: Offset, w: Float) {
    for ((width, alpha) in listOf(0.05f to 0.12f, 0.025f to 0.3f, 0.008f to 1f)) {
        drawCircle(color.copy(alpha = alpha), radius = radius, center = center, style = Stroke(w * width))
    }
}

private fun DrawScope.glowPath(path: Path, color: Color, w: Float) {
    for ((width, alpha) in listOf(0.05f to 0.12f, 0.025f to 0.3f, 0.008f to 1f)) {
        drawPath(path, color.copy(alpha = alpha), style = Stroke(w * width, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private fun DrawScope.duskCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    // Seventies sleeves in chocolate, burnt orange, dusty rose and teal, with mustard only as a
    // highlight, so a shelf of them reads warm without turning yellow.
    val teal = if (style.dark) Color(0xFF3D7773) else Color(0xFF2F6B67)
    val rose = if (style.dark) Color(0xFFC77D6C) else Color(0xFFC9775F)
    val brown = if (style.dark) Color(0xFF3A2416) else c.content
    val cream = if (style.dark) Color(0xFFF2E3CC) else c.surface
    when (seed % 4) {
        0 -> {
            // Rainbow arcs rising from the bottom edge.
            drawRect(cream)
            val bands = listOf(brown, c.accent, rose, teal)
            val thickness = w * 0.085f
            val center = Offset(w * (0.5f + 0.18f * (hash01(seed, 1) - 0.5f)), h * 1.02f)
            bands.forEachIndexed { i, color ->
                val r = w * 0.62f - i * thickness
                drawArc(color, 180f, 180f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(thickness))
            }
            drawCircle(c.accentAlt, radius = w * 0.06f, center = Offset(w * 0.78f, h * 0.2f))
        }
        1 -> {
            // Striped sun setting over teal water.
            drawRect(Brush.verticalGradient(listOf(rose, c.accent), endY = h * 0.68f))
            val sun = Offset(w * 0.5f, h * 0.6f)
            val r = w * 0.28f
            drawCircle(cream, r, sun)
            for (k in 0 until 4) {
                val y = sun.y - r * 0.05f + k * r * 0.2f
                drawRect(lerp(rose, c.accent, (y / (h * 0.68f)).coerceIn(0f, 1f)), Offset(sun.x - r, y), Size(r * 2, r * (0.04f + k * 0.03f)))
            }
            drawRect(teal, Offset(0f, h * 0.68f), Size(w, h * 0.32f))
            for (k in 0 until 3) {
                val y = h * (0.74f + k * 0.07f)
                val half = w * (0.18f - k * 0.04f)
                drawLine(cream.copy(alpha = 0.7f), Offset(sun.x - half, y), Offset(sun.x + half, y), h * 0.014f, StrokeCap.Round)
            }
        }
        2 -> {
            // Groovy waves.
            drawRect(cream)
            val colors = listOf(brown, c.accent, rose, teal)
            val band = h * 0.1f
            colors.forEachIndexed { i, color ->
                val baseY = h * (0.3f + i * 0.12f)
                val path = Path().apply {
                    moveTo(0f, baseY)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, baseY + h * 0.06f * sin((x / w) * 2f * PI.toFloat() + hash01(seed, 2) * 3f))
                        x += w / 40f
                    }
                    var back = w
                    while (back >= 0f) {
                        lineTo(back, baseY + band + h * 0.06f * sin((back / w) * 2f * PI.toFloat() + hash01(seed, 2) * 3f))
                        back -= w / 40f
                    }
                    close()
                }
                drawPath(path, color)
            }
        }
        else -> {
            // A daisy on deep teal.
            drawRect(teal)
            val center = Offset(w * (0.42f + 0.16f * hash01(seed, 3)), h * (0.44f + 0.12f * hash01(seed, 4)))
            val petalLength = w * 0.2f
            for (k in 0 until 12) {
                rotate(k * 30f, center) {
                    drawOval(cream, Offset(center.x - w * 0.045f, center.y - petalLength - w * 0.04f), Size(w * 0.09f, petalLength))
                }
            }
            drawCircle(c.accent, w * 0.075f, center)
            drawCircle(brown, w * 0.075f, center, style = Stroke(w * 0.012f))
        }
    }
}

/** The theme's signature shape as a one-colour line drawing, for empty states. */
@Composable
fun ThemeMotif(color: Color, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h / 2)
        val r = minOf(w, h) * 0.38f
        val line = Stroke(style.iconStroke * density * 1.2f, cap = StrokeCap.Round)
        when (style.theme) {
            AppTheme.INK -> {
                // A single brush stroke and a seal: calligraphy, not a loading spinner.
                drawPath(brushStrokePath(Offset(w * 0.12f, h * 0.55f), w * 0.7f, h * 0.13f), color)
                drawRect(color, Offset(w * 0.78f, h * 0.24f), Size(w * 0.09f, w * 0.09f))
            }
            AppTheme.TIDE -> for (i in 0 until 3) {
                val y = h * (0.38f + i * 0.14f)
                val path = Path().apply {
                    moveTo(w * 0.1f, y)
                    var x = w * 0.1f
                    while (x <= w * 0.9f) {
                        lineTo(x, y + h * 0.05f * sin((x / w) * 4f * PI.toFloat() + i))
                        x += 2f
                    }
                }
                drawPath(path, color.copy(alpha = 1f - i * 0.25f), style = line)
            }
            AppTheme.SAKURA -> for (k in 0 until 5) {
                rotate(k * 72f, center) { drawPetal(center + Offset(0f, -r * 0.55f), r * 0.42f, 0f, color.copy(alpha = 0.85f)) }
            }
            AppTheme.NEON -> for (i in 0 until 3) drawCircle(color.copy(alpha = 1f - i * 0.28f), r * (0.45f + i * 0.28f), center, style = line)
            AppTheme.GRID -> gridMotif(style)
            AppTheme.DUSK -> for (i in 0 until 4) {
                val rr = r * (1f - i * 0.22f)
                drawArc(color.copy(alpha = 1f - i * 0.2f), 180f, 180f, false, Offset(center.x - rr, h * 0.75f - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.12f))
            }
        }
    }
}
