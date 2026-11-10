package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
        AppTheme.INK -> inkCover(style, seed)
        AppTheme.NEON -> neonCover(style, seed)
        AppTheme.GRID -> gridCover(style, seed)
        AppTheme.DUSK -> duskCover(style, seed)
        AppTheme.DOT -> dotCover(style, seed)
        AppTheme.DEVICE -> deviceCover(style, seed)
        AppTheme.PAPER -> paperCover(style, seed)
        AppTheme.GAME -> gameCover(style, seed)
        AppTheme.ACID -> acidCover(style, seed)
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

/**
 * 圆相 (ensō): a circle in one stroke of the brush, round [center] at radius [r]. It starts
 * pressed down at the upper left, runs clockwise, and lifts into a thin dry tail before it
 * closes; the brush is never quite even, so the edge breathes a little.
 */
internal fun ensoPath(center: Offset, r: Float, thickness: Float): Path {
    val steps = 72
    val start = -150f
    val sweep = 318f
    val outer = ArrayList<Offset>(steps + 1)
    val inner = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val a = Math.toRadians((start + sweep * f).toDouble())
        // Full almost at once, then lifting off towards the end.
        val press = sin(PI.toFloat() / 2f * (f * 5f).coerceAtMost(1f))
        val half = thickness / 2f * (0.35f + 0.65f * press) * (1f - 0.82f * f.pow(2.4f))
        val rr = r * (1f + 0.022f * sin(f * 9.4f) - 0.03f * f)
        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
        outer += center + dir * (rr + half)
        inner += center + dir * (rr - half * 0.85f)
    }
    return Path().apply {
        moveTo(outer[0].x, outer[0].y)
        outer.drop(1).forEach { lineTo(it.x, it.y) }
        inner.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
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

/**
 * 霓's sleeves are pixel art in the night's palette, on a 24-cell grid: a moon over the sea, the
 * city in the rain with its windows lit, the expressway's lamps running away to a point, a teal
 * sign on a wet wall.
 */
private fun DrawScope.neonCover(style: LanyinStyle, seed: Int) {
    val n = 24
    val cell = size.minDimension / n
    fun px(x: Int, y: Int, c: Int) {
        if (x in 0 until n && y in 0 until n) drawRect(Color(c), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
    }
    fun row(y0: Int, y1: Int, c: Int) {
        for (y in y0..y1) for (x in 0 until n) px(x, y, c)
    }
    val j = seed * 7919
    row(0, 23, Drive.K1)
    when (seed.mod(4)) {
        0 -> {
            // A moon over the sea, its road of light on the water.
            row(0, 4, Drive.K2); row(5, 9, Drive.K3); row(10, 13, Drive.K4)
            val mx = 14 + (j % 5)
            for (y in 3..9) for (x in mx - 3..mx + 3) if ((x - mx) * (x - mx) + (y - 6) * (y - 6) <= 10) px(x, y, if (x > mx + 1 || y > 8) Drive.K9 else Drive.WH)
            px(mx - 1, 5, Drive.K8); px(mx + 1, 7, Drive.K8)
            row(14, 23, Drive.K1)
            for (y in 15..23 step 2) for (x in mx - 2 + (y - 15) / 4..mx + 2 - (y - 15) / 4) px(x, y, if (y < 19) Drive.K9 else Drive.K7)
            for (x in 0 until n) if (x % 3 == 0) px(x, 14, Drive.K5)
        }
        1 -> {
            // The city in the rain, windows lit, a red light on the tallest tower.
            row(0, 6, Drive.K2); row(7, 11, Drive.K3); row(12, 15, Drive.A0)
            var x = 0
            var k = 0
            while (x < n) {
                val w = 3 + (j + k * 13) % 3
                val top = 6 + (j + k * 7) % 8
                for (y in top..23) for (xx in x until x + w) px(xx, y, if (xx == x) Drive.K3 else Drive.K1)
                for (y in top + 2..21 step 2) for (xx in x + 1 until x + w - 1 step 2) if ((xx * 7 + y * 3 + k) % 5 < 2) px(xx, y, if ((xx + y) % 3 == 0) Drive.A4 else Drive.A3)
                if (k == 1) { px(x + w / 2, top - 1, Drive.K1); px(x + w / 2, top - 2, Drive.R3) }
                x += w + 1
                k++
            }
            for (r in 0 until 14) px((j / 3 + r * 7) % n, (r * 5) % 14, Drive.K6)
        }
        2 -> {
            // The expressway at night: lamps running away to a point, tail lights ahead.
            row(0, 9, Drive.K2); row(10, 11, Drive.A1)
            for (y in 12..23) {
                val half = (y - 11) * 1.0f
                for (x in (12 - half).toInt()..(12 + half).toInt()) px(x, y, Drive.K1)
            }
            for (y in 13..23 step 3) px(12, y, Drive.K6)
            for ((i, d) in listOf(2, 4, 7, 11).withIndex()) {
                px(12 - d - 1, 11 - d / 2, Drive.A5); px(12 - d - 2, 11 - d / 2, Drive.A3)
                px(12 + d + 1, 11 - d / 2, Drive.A5); px(12 + d + 2, 11 - d / 2, Drive.A3)
                if (i < 2) px(12 + (if (i == 0) -1 else 1), 13 + i * 2, Drive.R3)
            }
        }
        else -> {
            // A teal sign on a wet wall: a note in stepped light, its glow checkered at the edge.
            for (y in 0 until n) for (x in 0 until n) px(x, y, if ((x + y) % 2 == 0) Drive.K2 else Drive.K1)
            val note = listOf("....######", "....#....#", "....#....#", "....#....#", "..###..###", ".####.####", "..##...##.")
            val ox = 7
            val oy = 8
            for (y in oy - 2..oy + note.size + 1) for (x in ox - 2..ox + 11) if ((x + y) % 2 == 0) px(x, y, Drive.T0)
            for ((dy, line) in note.withIndex()) for ((dx, ch) in line.withIndex()) if (ch == '#') px(ox + dx, oy + dy, if (dy == 0) Drive.T3 else Drive.T2)
            for (y in 19..23) for (x in 0 until n) if ((x * 3 + y) % 7 == 0) px(x, y, Drive.T1)
        }
    }
}

/**
 * 暮's sleeves are riso prints: a few flat shapes in the three inks on cream, overprinting where
 * they cross (a sun over the sea, hills under a moon, three circles, arches), with the ink's
 * grain. Under the prints proper they're separated and screened like any cover.
 */
private fun DrawScope.duskCover(style: LanyinStyle, seed: Int) {
    val w = size.width
    val h = size.height
    val m = androidx.compose.ui.graphics.BlendMode.Multiply
    val j = (seed * 37 % 100) / 100f
    drawRect(Riso.Paper)
    when (seed.mod(4)) {
        0 -> {
            // A sun going into the sea, its road in yellow stripes.
            drawRect(Riso.Pink.copy(alpha = 0.72f), size = Size(w, h * 0.62f), blendMode = m)
            drawCircle(Riso.Yellow, w * (0.2f + 0.06f * j), Offset(w * (0.42f + 0.2f * j), h * 0.56f), blendMode = m)
            drawRect(Riso.Blue, Offset(0f, h * 0.62f), Size(w, h * 0.38f), blendMode = m)
            for (k in 0 until 5) {
                val y = h * (0.66f + k * 0.065f)
                val half = w * (0.16f - k * 0.02f)
                drawRect(Riso.Yellow, Offset(w * (0.42f + 0.2f * j) - half, y), Size(half * 2, h * 0.022f), blendMode = m)
            }
        }
        1 -> {
            // Hills under a moon.
            drawRect(Riso.Blue.copy(alpha = 0.6f), size = Size(w, h * 0.7f), blendMode = m)
            drawCircle(Riso.Yellow, w * 0.11f, Offset(w * (0.62f + 0.15f * j), h * 0.26f), blendMode = m)
            val far = Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.3f, h * (0.4f + 0.1f * j), w * 0.55f, h * 0.7f, w, h * 0.5f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(far, Riso.Pink.copy(alpha = 0.85f), blendMode = m)
            val near = Path().apply {
                moveTo(0f, h * 0.8f)
                cubicTo(w * 0.35f, h * 0.66f, w * 0.7f, h * 0.9f, w, h * 0.74f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(near, Riso.Blue, blendMode = m)
        }
        2 -> {
            // Three circles, one per drum.
            val r = w * 0.27f
            drawCircle(Riso.Pink, r, Offset(w * 0.38f, h * (0.4f + 0.06f * j)), blendMode = m)
            drawCircle(Riso.Yellow, r, Offset(w * 0.62f, h * 0.42f), blendMode = m)
            drawCircle(Riso.Blue.copy(alpha = 0.9f), r, Offset(w * 0.5f, h * 0.62f), blendMode = m)
        }
        else -> {
            // Arches, nested, offset a little drum to drum.
            for (k in 0 until 4) {
                val ink = Riso.Inks[(k + seed).mod(3)]
                val inset = w * (0.08f + k * 0.1f)
                val top = h * (0.18f + k * 0.1f)
                drawPath(Path().apply {
                    moveTo(inset, h)
                    lineTo(inset, top + (w / 2f - inset))
                    arcTo(androidx.compose.ui.geometry.Rect(inset, top, w - inset, top + (w - 2 * inset)), 180f, 180f, false)
                    lineTo(w - inset, h)
                    close()
                }, ink.copy(alpha = 0.85f), blendMode = m)
            }
        }
    }
    drawRect(grainBrush, alpha = 0.14f, blendMode = m)
    if (style.dark) drawRect(Color.Black.copy(alpha = 0.08f))
}

/** The theme's signature shape as a one-colour line drawing, for empty states. */
@Composable
fun ThemeMotif(color: Color, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    // 机: a real knob, turned up a little, with its scale.
    if (style.theme == AppTheme.DEVICE) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            Canvas(Modifier.matchParentSize()) { drawKnobScale(color, minOf(size.width, size.height) * 0.46f, count = 11, lit = 4) }
            HifiKnob(d * 0.72f, -60f)
        }
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h / 2)
        val r = minOf(w, h) * 0.38f
        val line = Stroke(style.iconStroke * density * 1.2f, cap = StrokeCap.Round)
        when (style.theme) {
            // 圆相: the circle of one breath of the brush, left open. Emptiness, said the ink way.
            AppTheme.INK -> drawPath(ensoPath(center, r, r * 0.3f), color)
            AppTheme.NEON -> for (i in 0 until 3) drawCircle(color.copy(alpha = 1f - i * 0.28f), r * (0.45f + i * 0.28f), center, style = line)
            AppTheme.GRID -> gridMotif(style)
            AppTheme.DUSK -> for (i in 0 until 4) {
                val rr = r * (1f - i * 0.22f)
                drawArc(color.copy(alpha = 1f - i * 0.2f), 180f, 180f, false, Offset(center.x - rr, h * 0.75f - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.12f))
            }
            AppTheme.DOT -> dotMotif(color, style.colors.accentAlt)
            // 机's knob is a component of its own, above.
            AppTheme.DEVICE -> Unit
            AppTheme.PAPER -> paperMotif(color, style.colors.accent)
            AppTheme.GAME -> gameMotif(color)
            AppTheme.ACID -> acidMotif(color, style.colors.accent)
            // A frame of film with its sprocket holes.
        }
    }
}
