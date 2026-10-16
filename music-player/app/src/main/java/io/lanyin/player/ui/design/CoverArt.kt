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
        AppTheme.FILM -> filmCover(style, seed)
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
 * 霓's covers: a neon sign on a dark brick wall, its light spilling over the bricks round it.
 * Four signs, so a row of covers isn't one picture repeated: a heart, a note over a wave, the
 * moon and stars, a record.
 */
private fun DrawScope.neonCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(lerp(c.surface, Color.Black, 0.3f))
    val course = h / 9f
    val brick = w / 3.5f
    val mortar = Color.White.copy(alpha = 0.035f)
    for (row in 0..9) {
        val y = row * course
        drawLine(mortar, Offset(0f, y), Offset(w, y), w * 0.004f)
        var x = if (row % 2 == 0) 0f else brick / 2f
        while (x < w) {
            drawLine(mortar, Offset(x, y), Offset(x, y + course), w * 0.004f)
            x += brick
        }
    }
    val pink = c.accent
    val cyan = c.accentAlt
    val amber = Color(0xFFFFC46B)
    val tube = w * 0.026f
    val cx = w * (0.47f + 0.06f * hash01(seed, 1))
    val cy = h * (0.47f + 0.05f * hash01(seed, 2))
    val s = w * 0.62f
    fun spill(color: Color) = drawRect(Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), Offset(cx, cy), w * 0.62f))
    when (seed % 4) {
        0 -> {
            spill(pink)
            drawTube(
                Path().apply {
                    moveTo(cx, cy + 0.36f * s)
                    cubicTo(cx - 0.1f * s, cy + 0.26f * s, cx - 0.46f * s, cy + 0.06f * s, cx - 0.44f * s, cy - 0.14f * s)
                    cubicTo(cx - 0.42f * s, cy - 0.38f * s, cx - 0.12f * s, cy - 0.42f * s, cx, cy - 0.2f * s)
                    cubicTo(cx + 0.12f * s, cy - 0.42f * s, cx + 0.42f * s, cy - 0.38f * s, cx + 0.44f * s, cy - 0.14f * s)
                    cubicTo(cx + 0.46f * s, cy + 0.06f * s, cx + 0.1f * s, cy + 0.26f * s, cx, cy + 0.36f * s)
                },
                pink, tube,
            )
            for (k in 0..2) {
                val at = Offset(w * (0.18f + 0.64f * hash01(seed + k, 5)), h * (0.14f + 0.72f * hash01(seed + k, 6)))
                if ((at - Offset(cx, cy)).getDistance() > s * 0.42f) drawTube(starPath(at, w * 0.05f, w * 0.012f, 4), cyan, tube * 0.6f)
            }
        }
        1 -> {
            spill(cyan)
            // An eighth note, and a pink wave under it.
            val head = Offset(cx - 0.1f * s, cy + 0.2f * s)
            rotate(-22f, head) { drawTube(Path().apply { addOval(Rect(head.x - 0.15f * s, head.y - 0.1f * s, head.x + 0.15f * s, head.y + 0.1f * s)) }, cyan, tube) }
            val stemX = head.x + 0.14f * s
            drawTubeLine(Offset(stemX, head.y - 0.03f * s), Offset(stemX, cy - 0.38f * s), cyan, tube)
            drawTube(Path().apply { moveTo(stemX, cy - 0.38f * s); cubicTo(stemX + 0.06f * s, cy - 0.2f * s, stemX + 0.3f * s, cy - 0.22f * s, stemX + 0.24f * s, cy - 0.02f * s) }, cyan, tube)
            drawTube(
                Path().apply {
                    moveTo(w * 0.16f, h * 0.84f)
                    var x = w * 0.16f
                    while (x < w * 0.84f) {
                        x += w * 0.01f
                        lineTo(x, h * 0.84f + h * 0.025f * sin((x / w) * 6f * PI.toFloat()))
                    }
                },
                pink, tube * 0.7f,
            )
        }
        2 -> {
            spill(amber)
            // A crescent moon, and stars in cyan.
            val moon = Path().apply { addOval(Rect(Offset(cx, cy), s * 0.32f)) }
            val bite = Path().apply { addOval(Rect(Offset(cx + s * 0.16f, cy - s * 0.1f), s * 0.28f)) }
            drawTube(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), amber, tube)
            for (k in 0..2) {
                val at = Offset(cx + s * (0.28f + 0.12f * k), cy - s * (0.3f - 0.22f * k))
                drawTube(starPath(at, w * (0.05f - 0.01f * k), w * 0.012f, 4), cyan, tube * 0.6f)
            }
        }
        else -> {
            spill(pink)
            // A record: the disc in cyan, its label in pink, the arm across it.
            drawTube(Path().apply { addOval(Rect(Offset(cx, cy), s * 0.34f)) }, cyan, tube)
            drawTube(Path().apply { addOval(Rect(Offset(cx, cy), s * 0.11f)) }, pink, tube)
            drawCircle(neonCore(pink), tube * 0.7f, Offset(cx, cy))
            drawTube(Path().apply { moveTo(cx + s * 0.44f, cy - s * 0.42f); lineTo(cx + s * 0.4f, cy + s * 0.02f); lineTo(cx + s * 0.22f, cy + s * 0.16f) }, cyan, tube * 0.8f)
        }
    }
}

/** 暮's sleeves are travel photos; see [duskPhoto]. Dimmed a little at night so they don't glare on the dark page. */
private fun DrawScope.duskCover(style: LanyinStyle, seed: Int) {
    duskPhoto(seed)
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
            AppTheme.DOT -> dotMotif(color, style.colors.accentAlt)
            AppTheme.DEVICE -> deviceMotif(color, style.colors.accent)
            AppTheme.PAPER -> paperMotif(color, style.colors.accent)
            AppTheme.GAME -> gameMotif(color)
            AppTheme.ACID -> acidMotif(color, style.colors.accent)
            // A frame of film with its sprocket holes.
            AppTheme.FILM -> {
                drawRect(color, Offset(center.x - r, center.y - r * 0.75f), Size(r * 2, r * 1.5f), style = line)
                for (k in 0 until 5) {
                    val x = center.x - r + r * 0.2f + k * r * 0.4f
                    drawRect(color, Offset(x - r * 0.06f, center.y - r * 0.62f), Size(r * 0.12f, r * 0.12f))
                    drawRect(color, Offset(x - r * 0.06f, center.y + r * 0.5f), Size(r * 0.12f, r * 0.12f))
                }
            }
        }
    }
}
