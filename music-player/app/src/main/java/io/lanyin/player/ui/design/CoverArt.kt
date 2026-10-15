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
        AppTheme.INK -> inkCover(style, seed)
        AppTheme.NEON -> neonCover(style, seed)
        AppTheme.GRID -> gridCover(style, seed)
        AppTheme.DUSK -> duskCover(style, seed)
    }
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
                drawCircle(Brush.verticalGradient(listOf(lerp(c.accent, Color.White, 0.25f), c.accentAlt), startY = sun.y - r, endY = sun.y), r, sun)
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

/**
 * 暮's sleeves: evening skies in terracotta, rose, gold and plum. A sun over still water, hills
 * stepping back into the haze, rings of light, and light through blinds.
 */
private fun DrawScope.duskCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val gold = Color(0xFFF2B35B)
    val plum = Color(0xFF5A3550)
    val cream = Color(0xFFFFF4EA)
    val sky = Brush.verticalGradient(listOf(c.accentAlt, c.accent, gold))
    when (seed % 4) {
        0 -> {
            // A sun on the horizon over still water.
            drawRect(sky, size = Size(w, h * 0.62f))
            val sun = Offset(w * (0.5f + 0.16f * (hash01(seed, 1) - 0.5f)), h * 0.62f)
            drawCircle(cream.copy(alpha = 0.92f), w * 0.22f, sun)
            drawRect(Brush.verticalGradient(listOf(lerp(c.accentAlt, plum, 0.55f), plum), startY = h * 0.62f, endY = h), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
            for (k in 0 until 4) {
                val y = h * (0.67f + k * 0.075f)
                val half = w * (0.2f - k * 0.04f)
                drawLine(cream.copy(alpha = 0.55f - k * 0.1f), Offset(sun.x - half, y), Offset(sun.x + half, y), h * 0.016f, StrokeCap.Round)
            }
        }
        1 -> {
            // Hills stepping back into the evening haze.
            drawRect(Brush.verticalGradient(listOf(lerp(gold, cream, 0.3f), lerp(c.accent, gold, 0.4f))))
            drawCircle(cream.copy(alpha = 0.9f), w * 0.13f, Offset(w * (0.3f + 0.4f * hash01(seed, 2)), h * 0.34f))
            val layers = listOf(lerp(c.accent, cream, 0.35f), c.accentAlt, lerp(c.accentAlt, plum, 0.5f), plum)
            layers.forEachIndexed { i, color ->
                val base = h * (0.5f + i * 0.13f)
                val path = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, base)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, base - h * 0.07f * sin((x / w) * PI.toFloat() * (1.2f + i * 0.4f) + hash01(seed + i, 3) * 6f))
                        x += w / 32f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, color)
            }
        }
        2 -> {
            // Rings of light around a low sun.
            drawRect(plum)
            val center = Offset(w * 0.5f, h * (0.78f + 0.1f * hash01(seed, 4)))
            val colors = listOf(c.accentAlt, c.accent, gold, cream)
            for (i in colors.indices) {
                val r = w * (0.82f - i * 0.17f)
                drawCircle(colors[i].copy(alpha = 0.55f + i * 0.15f), r, center)
            }
        }
        else -> {
            // Evening light through blinds.
            drawRect(sky)
            val slat = h / 9f
            for (k in 0 until 9) {
                val y = k * slat + slat * 0.62f
                drawRect(plum.copy(alpha = 0.18f + 0.05f * k), Offset(0f, y), Size(w, slat * 0.38f))
            }
            drawCircle(cream.copy(alpha = 0.85f), w * 0.17f, Offset(w * (0.62f + 0.2f * (hash01(seed, 5) - 0.5f)), h * 0.42f))
        }
    }
    // Dimmed a little at night, so the sleeves don't glare on the dark page.
    if (style.dark) drawRect(Color.Black.copy(alpha = 0.12f))
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
            AppTheme.NEON -> for (i in 0 until 3) drawCircle(color.copy(alpha = 1f - i * 0.28f), r * (0.45f + i * 0.28f), center, style = line)
            AppTheme.GRID -> gridMotif(style)
            AppTheme.DUSK -> for (i in 0 until 4) {
                val rr = r * (1f - i * 0.22f)
                drawArc(color.copy(alpha = 1f - i * 0.2f), 180f, 180f, false, Offset(center.x - rr, h * 0.75f - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.12f))
            }
        }
    }
}
