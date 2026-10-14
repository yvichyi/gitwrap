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
        AppTheme.DUSK -> duskCover(style, seed)
    }
}

private fun DrawScope.tideCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val deep = if (style.dark) lerp(c.bgDeep, c.accent, 0.35f) else lerp(c.accent, Color.Black, 0.25f)
    val (from, to) = if (seed % 2 == 0) deep to c.accentAlt else c.accentAlt to deep
    drawRect(Brush.linearGradient(listOf(from, lerp(from, to, 0.5f), to), Offset.Zero, Offset(w, h)))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.09f, center = Offset(w * (0.62f + 0.2f * hash01(seed, 1)), h * (0.22f + 0.12f * hash01(seed, 2))))
    for (i in 0 until 4) {
        val baseY = h * (0.52f + i * 0.1f)
        val amp = h * 0.035f
        val k = (2 * PI / (w * (0.7f + 0.15f * i))).toFloat()
        val phase = hash01(seed, 3 + i) * 6f
        val path = Path().apply {
            moveTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                lineTo(x, baseY + amp * sin(k * x + phase))
                x += w / 48f
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.18f + 0.1f * i), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
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

private fun DrawScope.sakuraCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val a = lerp(c.accent, Color.White, 0.45f)
    val b = lerp(c.accentAlt, Color.White, 0.4f)
    drawRect(Brush.linearGradient(if (seed % 2 == 0) listOf(a, b) else listOf(b, a), Offset.Zero, Offset(w, h)))
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

private fun DrawScope.neonCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.surface)
    val center = Offset(w * (0.4f + 0.2f * hash01(seed, 1)), h * (0.4f + 0.2f * hash01(seed, 2)))
    for (ring in 0 until 3) {
        val color = if ((ring + seed) % 2 == 0) c.accent else c.accentAlt
        val r = w * (0.14f + 0.1f * ring)
        for ((width, alpha) in listOf(0.05f to 0.12f, 0.025f to 0.3f, 0.008f to 1f)) {
            drawCircle(color.copy(alpha = alpha), radius = r, center = center, style = Stroke(w * width))
        }
    }
    var y = h * 0.78f
    while (y < h) {
        drawLine(c.accentAlt.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), strokeWidth = h * 0.008f)
        y += h * 0.045f
    }
}

private fun DrawScope.duskCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.surface)
    // Seventies rainbow: concentric arcs rising from the bottom edge.
    val bands = listOf(c.content, c.accent, c.accentAlt, lerp(c.accentAlt, c.surface, 0.5f))
    val thickness = w * 0.085f
    val center = Offset(w * (0.5f + 0.18f * (hash01(seed, 1) - 0.5f)), h * 1.02f)
    bands.forEachIndexed { i, color ->
        val r = w * 0.62f - i * thickness
        drawArc(
            color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            style = Stroke(thickness),
        )
    }
    drawCircle(c.accent, radius = w * 0.07f, center = Offset(w * 0.78f, h * 0.2f))
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
            AppTheme.DUSK -> for (i in 0 until 4) {
                val rr = r * (1f - i * 0.22f)
                drawArc(color.copy(alpha = 1f - i * 0.2f), 180f, 180f, false, Offset(center.x - rr, h * 0.75f - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.12f))
            }
        }
    }
}
