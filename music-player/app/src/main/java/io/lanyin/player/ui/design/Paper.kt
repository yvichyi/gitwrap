package io.lanyin.player.ui.design

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 报: newsprint. The paper's own faint dot screen and fibres; on the player, the fold across the
 * middle where the broadsheet was creased in half, a shadow above it and a lighter edge below.
 */
internal fun DrawScope.drawNewsprint(style: LanyinStyle, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    drawRect(ShaderBrush(ImageShader(newsprintTile(density), TileMode.Repeated, TileMode.Repeated)), alpha = if (style.dark) 0.05f else 0.07f)
    drawRect(grainBrush, alpha = if (style.dark) 0.05f else 0.06f)
    // A crease that stays put while a page scrolls under it would read as a glitch, and in
    // landscape it would run through the middle of the lyrics.
    if (page || w > h) return
    val fold = h * 0.5f
    val band = 22f * density
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = if (style.dark) 0.18f else 0.045f), startY = fold - band, endY = fold),
        Offset(0f, fold - band), Size(w, band),
    )
    drawRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = if (style.dark) 0.03f else 0.35f), 1f to Color.Transparent, startY = fold, endY = fold + band * 0.6f),
        Offset(0f, fold), Size(w, band * 0.6f),
    )
}

/** A tile of the newsprint's dot screen, made once per screen density. */
private var tileCache: Pair<Float, ImageBitmap>? = null

private fun newsprintTile(density: Float): ImageBitmap {
    tileCache?.let { (d, tile) -> if (d == density) return tile }
    val side = (4f * density).toInt().coerceAtLeast(3)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).drawCircle(side / 2f, side / 2f, side * 0.16f, Paint().apply { isAntiAlias = true; color = android.graphics.Color.BLACK })
    return bitmap.asImageBitmap().also { tileCache = density to it }
}

/**
 * 报's progress: a printer's rule with ticks along it, the part read underlined in red, and a red
 * pen mark where the music is.
 */
internal fun DrawScope.drawRulerTrack(f: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val cy = size.height / 2f
    val base = cy + 6f * density
    val x = size.width * f
    drawLine(c.content, Offset(0f, base), Offset(size.width, base), density)
    val pitch = 6f * density
    var i = 0
    var tx = 0f
    while (tx <= size.width + 0.5f) {
        val major = i % 5 == 0
        drawLine(c.content.copy(alpha = if (major) 0.75f else 0.35f), Offset(tx, base), Offset(tx, base - (if (major) 9f else 4.5f) * density), density)
        tx += pitch
        i++
    }
    drawRect(c.accent, Offset(0f, base - 1.5f * density), Size(x, 3f * density))
    val pen = (2f + dragging) * density
    drawLine(c.accent, Offset(x, cy - 10f * density), Offset(x, base + 3f * density), pen)
    val tip = Path().apply {
        moveTo(x - 5f * density, cy - 14f * density)
        lineTo(x + 5f * density, cy - 14f * density)
        lineTo(x, cy - 7f * density)
        close()
    }
    drawPath(tip, c.accent)
}

/**
 * 报's play key: a block of ink, printed over its red plate a hair out of register; on the beat
 * the plate slips a little further, as if the press jolted.
 */
internal fun DrawScope.drawStamp(style: LanyinStyle, beat: Float) {
    val c = style.colors
    val off = (2f + 2.5f * beat) * density
    drawRect(c.accent.copy(alpha = 0.9f), Offset(off, off), size)
    drawRect(c.content, size = size)
}

/**
 * A cover printed as a newspaper photo: dots on a 45° screen, each as large as the picture is
 * dark (or strongly coloured) there and inked in its colour there, as the colour pages are
 * printed. With 封面按主题重绘 off, the cover as it is.
 */
@Composable
fun HalftoneCover(url: String?, seed: Int, modifier: Modifier = Modifier) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    val style = Lanyin.style
    val n = 56
    val cells = rememberCoverPixels(url, seed, n)
    Canvas(modifier) {
        drawRect(style.colors.surface)
        drawColorHalftone(style, 44f, cells, n)
    }
}

/** The cover in halftone as a painter, for a record's label; the cover as it is with 封面按主题重绘 off. */
@Composable
fun rememberHalftonePainter(url: String?, seed: Int): androidx.compose.ui.graphics.painter.Painter? {
    if (!LocalCoverMedium.current) return rememberCoverPainter(url, 480)
    val style = Lanyin.style
    val n = 40
    val cells = rememberCoverPixels(url, seed, n)
    return androidx.compose.runtime.remember(cells, style) {
        object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize: Size get() = Size.Unspecified
            override fun DrawScope.onDraw() {
                drawRect(style.colors.surface)
                drawColorHalftone(style, 26f, cells, n)
            }
        }
    }
}

/**
 * [cells] (n×n ARGB) in colour halftone: a strongly coloured patch prints as large dots of its
 * colour even when it's light, so a cover keeps its colours; the ink is pulled a little towards
 * the paper's black, as cheap colour print is.
 */
private fun DrawScope.drawColorHalftone(style: LanyinStyle, lines: Float, cells: IntArray, n: Int) {
    val ink = style.colors.content
    drawHalftone(
        style, lines,
        inkAt = { u, v ->
            val c = Color(cells.nearestCell(n, u, v))
            val lum = luminance(c.toArgb())
            // Pale colours would vanish on the paper: darken them towards the ink.
            lerp(c, ink, (0.12f + (lum - 0.7f).coerceAtLeast(0f) * 1.2f).coerceIn(0f, 0.6f))
        },
    ) { u, v ->
        val c = Color(cells.nearestCell(n, u, v))
        val hi = maxOf(c.red, c.green, c.blue)
        val sat = if (hi <= 0f) 0f else (hi - minOf(c.red, c.green, c.blue)) / hi
        val l = cells.sampleLuminance(n, u, v)
        if (style.dark) l else l * (1f - 0.55f * sat)
    }
}

private fun IntArray.sampleLuminance(n: Int, u: Float, v: Float): Float = luminance(nearestCell(n, u, v))

/**
 * Halftone over the whole draw area from [lightness] (0 black … 1 white at a point in 0..1 on
 * each axis), [lines] dots across, in ink on paper.
 */
internal fun DrawScope.drawHalftone(
    style: LanyinStyle,
    lines: Float,
    ink: Color = style.colors.content,
    inkAt: ((Float, Float) -> Color)? = null,
    lightness: (Float, Float) -> Float,
) {
    val w = size.width
    val h = size.height
    val pitch = minOf(w, h) / lines
    val half = pitch / sqrt(2f)
    val reach = ((w + h) / pitch).toInt() + 2
    for (i in -reach..reach) {
        for (j in 0..reach * 2) {
            // A lattice turned 45°, the classic screen angle for black.
            val x = (i + j) * half
            val y = (j - i) * half
            if (x < -pitch || x > w + pitch || y < -pitch || y > h + pitch) continue
            val l = lightness((x / w).coerceIn(0f, 1f), (y / h).coerceIn(0f, 1f))
            // Darkness drives the dot's area, with a little contrast so the paper stays clean.
            val d = (((if (style.dark) l else 1f - l) - 0.06f) * 1.15f).coerceIn(0f, 1f)
            val r = pitch * 0.62f * sqrt(d)
            if (r > 0.35f) drawCircle(inkAt?.invoke((x / w).coerceIn(0f, 1f), (y / h).coerceIn(0f, 1f)) ?: ink, r, Offset(x, y))
        }
    }
}

/**
 * 报's generated photos, in halftone with a spot of red: the sun over the sea, a skyline,
 * a figure in a doorway, and a front-page graphic.
 */
internal fun DrawScope.paperCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    drawRect(c.surface)
    val shift = hash01(seed, 1) * 0.2f - 0.1f
    when (seed % 4) {
        0 -> drawHalftone(style, 34f) { u, v ->
            val sun = 1f - ((u - 0.6f - shift) * (u - 0.6f - shift) + (v - 0.42f) * (v - 0.42f)) / 0.03f
            if (v < 0.62f) maxOf(0.85f - v * 0.7f, sun.coerceIn(0f, 1f)) else {
                val glint = if (kotlin.math.abs(u - 0.6f - shift) < 0.08f * (1.2f - v)) 0.55f + 0.3f * sin(v * 90f) else 0f
                (0.25f + glint).coerceIn(0f, 1f)
            }
        }
        1 -> {
            val tops = FloatArray(9) { 0.35f + 0.4f * hash01(seed * 7 + it, 2) }
            drawHalftone(style, 34f) { u, v ->
                val b = (u * 9f).toInt().coerceIn(0, 8)
                if (v > tops[b]) 0.08f else 0.9f - v * 0.6f
            }
            drawCircle(c.accent, size.width * 0.07f, Offset(size.width * (0.75f + shift), size.height * 0.18f))
        }
        2 -> {
            drawHalftone(style, 34f) { u, v ->
                val head = (u - 0.5f) * (u - 0.5f) + (v - 0.4f) * (v - 0.4f) < 0.018f
                val body = v > 0.55f && kotlin.math.abs(u - 0.5f) < 0.22f + (v - 0.55f) * 0.4f
                val door = kotlin.math.abs(u - 0.5f) < 0.32f && v > 0.12f
                when {
                    head || body -> 0.05f
                    door -> 0.85f
                    else -> 0.35f
                }
            }
            drawRect(c.accent, Offset(size.width * 0.06f, size.height * 0.06f), Size(size.width * 0.16f, size.height * 0.05f))
        }
        else -> {
            drawHalftone(style, 34f) { u, v -> if (v + u * 0.4f in 0.45f..0.75f) 0.1f else 0.92f - 0.25f * v }
            drawCircle(c.accent, size.width * 0.18f, Offset(size.width * (0.72f + shift), size.height * 0.3f))
            for (k in 0 until 3) {
                drawRect(c.content, Offset(size.width * 0.08f, size.height * (0.8f + k * 0.055f)), Size(size.width * (0.7f - k * 0.18f), size.height * 0.025f))
            }
        }
    }
}

/** 报's motif: a folded newspaper, its masthead and columns. */
internal fun DrawScope.paperMotif(color: Color, red: Color) {
    val w = size.width
    val h = size.height
    val left = w * 0.2f
    val top = h * 0.2f
    val pw = w * 0.6f
    val ph = h * 0.62f
    val stroke = 2f * density
    drawRect(color, Offset(left, top), Size(pw, ph), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    drawRect(color, Offset(left + pw * 0.1f, top + ph * 0.1f), Size(pw * 0.8f, ph * 0.1f))
    drawRect(red, Offset(left + pw * 0.1f, top + ph * 0.3f), Size(pw * 0.36f, ph * 0.26f))
    for (k in 0 until 5) {
        val y = top + ph * (0.32f + k * 0.11f)
        drawLine(color, Offset(left + pw * 0.54f, y), Offset(left + pw * 0.9f, y), stroke * 0.7f)
    }
    drawLine(color, Offset(left + pw * 0.1f, top + ph * 0.72f), Offset(left + pw * 0.46f, top + ph * 0.72f), stroke * 0.7f)
}
