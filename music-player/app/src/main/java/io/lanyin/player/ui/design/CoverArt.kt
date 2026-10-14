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
    val center = Offset(w * (0.46f + 0.08f * hash01(seed, 1)), h * (0.46f + 0.06f * hash01(seed, 2)))
    rotate(hash01(seed, 3) * 360f, center) {
        drawPath(ensoPath(center, w * 0.3f, w * 0.085f, sweepDegrees = 300f), c.content.copy(alpha = 0.92f))
    }
    // The red seal.
    drawRect(c.accentAlt, topLeft = Offset(w * 0.74f, h * 0.76f), size = Size(w * 0.1f, w * 0.1f))
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
