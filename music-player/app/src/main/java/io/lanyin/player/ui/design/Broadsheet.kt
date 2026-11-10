package io.lanyin.player.ui.design

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 报's press: the newsprint itself, type printed by letterpress, and the marks a press leaves on
 * the sheet. Everything here is drawn in code; the textures are made once and tiled.
 */

// ─── Newsprint ───────────────────────────────────────────────────────────────

private var fibreCache: Pair<Float, ImageBitmap>? = null

/**
 * A tile of newsprint as it looks close to: groundwood fibres, short and grey, some catching the
 * light; flecks of bark and of ink; a soft mottling where the pulp lay thicker. Grey on clear, to
 * be laid over the paper's colour with an overlay so it works on the night edition's stock too.
 * Everything is drawn wrapped, so the tile repeats without a seam.
 */
private fun newsprintFibres(density: Float): ImageBitmap {
    fibreCache?.let { (d, tile) -> if (d == density) return tile }
    val side = (150f * density).toInt().coerceIn(256, 600)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val rnd = java.util.Random(53)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun wrapped(x: Float, y: Float, reach: Float, draw: (Float, Float) -> Unit) {
        for (dx in -1..1) for (dy in -1..1) {
            val px = x + dx * side
            val py = y + dy * side
            if (px + reach < 0 || py + reach < 0 || px - reach > side || py - reach > side) continue
            draw(px, py)
        }
    }
    // Mottling: big soft clouds, darker and lighter.
    for (k in 0 until 26) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val r = side * (0.08f + 0.14f * rnd.nextFloat())
        val dark = rnd.nextBoolean()
        val base = if (dark) 0x000000 else 0xFFFFFF
        wrapped(x, y, r) { px, py ->
            paint.shader = android.graphics.RadialGradient(
                px, py, r, intArrayOf((0x16 shl 24) or base, base), null, android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(px, py, r, paint)
        }
    }
    paint.shader = null
    // Fibres: short curved strokes, most a little darker than the paper, some lighter.
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    val path = android.graphics.Path()
    for (k in 0 until (side * side / 220)) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val len = (2f + rnd.nextFloat() * 7f) * density
        val a = rnd.nextFloat() * PI.toFloat()
        val bend = (rnd.nextFloat() - 0.5f) * len * 0.6f
        val light = rnd.nextFloat() < 0.3f
        paint.color = if (light) android.graphics.Color.argb(40 + rnd.nextInt(40), 255, 255, 255) else android.graphics.Color.argb(18 + rnd.nextInt(34), 40, 34, 26)
        paint.strokeWidth = (0.35f + rnd.nextFloat() * 0.45f) * density
        wrapped(x, y, len) { px, py ->
            path.reset()
            path.moveTo(px, py)
            path.quadTo(px + cos(a) * len / 2f - sin(a) * bend, py + sin(a) * len / 2f + cos(a) * bend, px + cos(a) * len, py + sin(a) * len)
            canvas.drawPath(path, paint)
        }
    }
    // Flecks: bark (warm), ink (black), and the odd shive (a little splinter of wood).
    paint.style = Paint.Style.FILL
    for (k in 0 until (side * side / 1400)) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val r = (0.3f + rnd.nextFloat() * 0.7f) * density
        paint.color = when (rnd.nextInt(5)) {
            0 -> android.graphics.Color.argb(120, 20, 18, 16)
            1, 2 -> android.graphics.Color.argb(70, 110, 78, 40)
            else -> android.graphics.Color.argb(50, 60, 50, 40)
        }
        wrapped(x, y, r) { px, py -> canvas.drawCircle(px, py, r, paint) }
    }
    paint.style = Paint.Style.STROKE
    for (k in 0 until 6) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val len = (5f + rnd.nextFloat() * 6f) * density
        val a = rnd.nextFloat() * PI.toFloat()
        paint.color = android.graphics.Color.argb(80, 120, 86, 44)
        paint.strokeWidth = 0.8f * density
        wrapped(x, y, len) { px, py -> canvas.drawLine(px, py, px + cos(a) * len, py + sin(a) * len, paint) }
    }
    return bitmap.asImageBitmap().also { fibreCache = density to it }
}

/** The newsprint's fibres as a brush, to lay over the paper's colour. */
internal fun DrawScope.newsprintBrush(): ShaderBrush = newsprintBrushAt(density)

internal fun newsprintBrushAt(density: Float): ShaderBrush =
    ShaderBrush(ImageShader(newsprintFibres(density), TileMode.Repeated, TileMode.Repeated))

/**
 * The paper's age: its edges a shade warmer and darker than its middle, as newsprint goes once
 * the light has been at it. The night edition's dark stock doesn't show it.
 */
internal fun DrawScope.drawAgedEdges(style: LanyinStyle) {
    if (style.dark) return
    val tan = Color(0xFF8A6A3A)
    val r = maxOf(size.width, size.height) * 0.78f
    drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to tan.copy(alpha = 0.10f), center = center, radius = r))
}

/**
 * The other side of the sheet showing through, faint and backwards: its columns of type, a
 * headline across two of them and a photo. A sheet that doesn't scroll (the player) shows it; a
 * page that scrolls doesn't, as a ghost that stayed put while the page moved would look wrong.
 */
internal fun DrawScope.drawShowThrough(style: LanyinStyle) {
    val ink = style.colors.content.copy(alpha = if (style.dark) 0.012f else 0.016f)
    val w = size.width
    val h = size.height
    val d = density
    val margin = 18f * d
    val cols = if (w > h) 5 else 3
    val gutter = 12f * d
    val colW = (w - margin * 2 - gutter * (cols - 1)) / cols
    val line = 3.2f * d
    val pitch = 7f * d
    // Mirrored: the reverse page reads right to left through the paper.
    for (col in 0 until cols) {
        val x = w - margin - (col + 1) * colW - col * gutter
        var y = margin + 40f * d
        if (col == 0) {
            // A headline over the first two columns, and a photo.
            drawRect(ink, Offset(x - colW - gutter, y), Size(colW * 2 + gutter, 14f * d))
            drawRect(ink, Offset(x - colW - gutter + colW * 0.3f, y + 20f * d), Size(colW * 1.4f, 9f * d))
        }
        if (col <= 1) y += 40f * d
        var n = 0
        while (y < h - margin) {
            if (col == 2 && n == 6) {
                drawRect(ink.copy(alpha = ink.alpha * 1.6f), Offset(x, y), Size(colW, colW * 0.75f))
                y += colW * 0.75f + pitch
            }
            val short = hash01(col * 131 + n, 7) < 0.12f
            drawRect(ink, Offset(x, y), Size(if (short) colW * (0.3f + 0.4f * hash01(n, col)) else colW, line))
            y += pitch + if (short) pitch * 0.8f else 0f
            n++
        }
    }
}

// ─── Letterpress ─────────────────────────────────────────────────────────────

private var voidCache: Pair<Float, ImageBitmap>? = null

/**
 * Where the ink didn't take: small specks and pits, and faint streaks along the roller's travel.
 * Used to cut into type printed on the press.
 */
private fun inkVoids(density: Float): ImageBitmap {
    voidCache?.let { (d, tile) -> if (d == density) return tile }
    val side = (96f * density).toInt().coerceIn(160, 400)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val rnd = java.util.Random(7)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (k in 0 until (side * side / 70)) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val r = (0.25f + rnd.nextFloat() * rnd.nextFloat() * 1.2f) * density
        paint.color = android.graphics.Color.argb(70 + rnd.nextInt(185), 0, 0, 0)
        for (dx in -1..1) for (dy in -1..1) canvas.drawCircle(x + dx * side, y + dy * side, r, paint)
    }
    paint.strokeWidth = 0.6f * density
    for (k in 0 until 18) {
        val x = rnd.nextFloat() * side
        val y = rnd.nextFloat() * side
        val len = (8f + rnd.nextFloat() * 30f) * density
        paint.color = android.graphics.Color.argb(40 + rnd.nextInt(40), 0, 0, 0)
        for (dx in -1..1) for (dy in -1..1) canvas.drawLine(x + dx * side, y + dy * side, x + dx * side + len, y + dy * side, paint)
    }
    return bitmap.asImageBitmap().also { voidCache = density to it }
}

/**
 * Printed by letterpress: the content's ink pitted where it didn't take, so heavy type looks
 * pressed into the sheet rather than lit on a screen. [strength] from 0 (clean) to 1.
 */
fun Modifier.letterpress(strength: Float = 1f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val brush = ShaderBrush(ImageShader(inkVoids(density), TileMode.Repeated, TileMode.Repeated))
        onDrawWithContent {
            drawContent()
            drawRect(brush, alpha = 0.75f * strength, blendMode = BlendMode.DstOut)
        }
    }

/** Ink squeezed a hair past the type's edges, as it is when the forme meets the paper. */
fun TextStyle.inked(color: Color): TextStyle = copy(shadow = Shadow(color.copy(alpha = 0.4f), Offset.Zero, 1.6f))

// ─── Marks of the press ──────────────────────────────────────────────────────

/** A registration target: a ring and its cross, to line the plates up by. */
internal fun DrawScope.drawRegistration(at: Offset, r: Float, color: Color) {
    val w = 0.7f * density
    drawCircle(color, r, at, style = Stroke(w))
    drawCircle(color, r * 0.45f, at)
    drawLine(color, at - Offset(r * 1.5f, 0f), at + Offset(r * 1.5f, 0f), w)
    drawLine(color, at - Offset(0f, r * 1.5f), at + Offset(0f, r * 1.5f), w)
}

/**
 * The press's control strip: patches of the black at full, three quarters, half and a quarter,
 * then the red at full and half; printed in the margin to check the ink.
 */
internal fun DrawScope.drawColorBar(black: Color, red: Color) {
    val n = 6
    val cell = size.width / n
    val tints = listOf(black to 1f, black to 0.75f, black to 0.5f, black to 0.25f, red to 1f, red to 0.5f)
    tints.forEachIndexed { i, (col, a) ->
        drawRect(col.copy(alpha = col.alpha * a), Offset(i * cell, 0f), Size(cell - 0.8f * density, size.height))
    }
}

/** Registration targets at both ends of a row, and the control strip, as at the top of a sheet. */
@Composable
fun PressMarks(modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.size(10.dp).align(Alignment.CenterStart)) { drawRegistration(center, size.minDimension * 0.32f, c.content.copy(alpha = 0.7f)) }
        Canvas(Modifier.size(54.dp, 5.dp).align(Alignment.Center)) { drawColorBar(c.content, c.accent) }
        Canvas(Modifier.size(10.dp).align(Alignment.CenterEnd)) { drawRegistration(center, size.minDimension * 0.32f, c.content.copy(alpha = 0.7f)) }
    }
}

/**
 * The weather in the masthead's ear, as the paper draws it: a sun of strokes by day, a crescent
 * at night, a cloud with the sun behind it in the morning and the evening.
 */
internal fun DrawScope.drawWeather(hour: Int, ink: Color, red: Color, paper: Color) {
    val w = size.width
    val h = size.height
    val d = density
    val stroke = 1.2f * d
    val c = Offset(w * 0.5f, h * 0.5f)
    val r = minOf(w, h) * 0.22f
    when (hour) {
        in 8..16 -> {
            drawCircle(red, r, c)
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                drawLine(ink, c + Offset(cos(a), sin(a)) * (r * 1.4f), c + Offset(cos(a), sin(a)) * (r * 1.95f), stroke, StrokeCap.Round)
            }
        }
        in 5..7, in 17..18 -> {
            val sun = c + Offset(r * 0.6f, -r * 0.5f)
            drawCircle(red, r * 0.8f, sun)
            val cloud = Path().apply {
                val y = c.y + r * 0.7f
                moveTo(c.x - r * 1.6f, y)
                cubicTo(c.x - r * 1.8f, y - r * 0.9f, c.x - r * 0.6f, y - r * 1.1f, c.x - r * 0.4f, y - r * 0.6f)
                cubicTo(c.x - r * 0.1f, y - r * 1.5f, c.x + r * 1.2f, y - r * 1.3f, c.x + r * 1.1f, y - r * 0.5f)
                cubicTo(c.x + r * 1.9f, y - r * 0.5f, c.x + r * 1.9f, y, c.x + r * 1.5f, y)
                close()
            }
            drawPath(cloud, paper)
            drawPath(cloud, ink, style = Stroke(stroke))
        }
        else -> {
            val moon = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, r * 1.2f))
            }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.55f, -r * 0.35f), r * 1.05f)) }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), ink)
            for ((x, y) in listOf(0.78f to 0.22f, 0.86f to 0.62f, 0.2f to 0.2f)) drawCircle(ink, 0.9f * d, Offset(w * x, h * y))
        }
    }
}

// ─── Clippings ───────────────────────────────────────────────────────────────

/**
 * A piece cut out of the paper: newsprint with its fibres, edges cut by scissors (never quite
 * straight, the bottom in short snips), a soft shadow under it, laid down at a slight [tilt],
 * and a paper clip over its top edge.
 */
@Composable
fun NewsClipping(modifier: Modifier = Modifier, tilt: Float = -0.8f, seed: Int = 3, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Box(modifier) {
        Box(
            Modifier
                .graphicsLayer { rotationZ = tilt }
                .drawWithCache {
                    val edge = clippingEdge(size, seed, density)
                    val fibres = newsprintBrushAt(density)
                    onDrawBehind {
                        translate(1.5f * density, 3f * density) { drawPath(edge, Color.Black.copy(alpha = if (style.dark) 0.4f else 0.16f)) }
                        drawPath(edge, c.surface)
                        drawPath(edge, fibres, alpha = if (style.dark) 0.4f else 0.7f, blendMode = BlendMode.Overlay)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
        // The paper clip, over the top edge near the right, clear of the kicker.
        Canvas(Modifier.align(Alignment.TopEnd).padding(end = 64.dp).size(14.dp, 34.dp).graphicsLayer { rotationZ = tilt + 8f; translationY = -14f * density }) {
            drawPaperClip(if (style.dark) Color(0xFFB8B4AA) else Color(0xFF8E8A82))
        }
    }
}

/** The outline of a clipping: four cuts, each a little off true, the foot in short snips. */
private fun clippingEdge(size: Size, seed: Int, density: Float): Path {
    val w = size.width
    val h = size.height
    fun j(k: Int) = (hash01(seed * 17 + k, 5) - 0.5f) * 2.4f * density
    return Path().apply {
        moveTo(j(1), j(2))
        lineTo(w * 0.5f, j(3) * 0.6f)
        lineTo(w + j(4), j(5))
        lineTo(w + j(6), h * 0.5f)
        lineTo(w + j(7), h + j(8))
        // The bottom cut in snips: each a short straight cut, angled a touch differently.
        var x = w
        var k = 0
        while (x > 0f) {
            val step = (14f + 10f * hash01(seed + k, 9)) * density
            x = (x - step).coerceAtLeast(0f)
            lineTo(x, h + (hash01(seed + k, 10) - 0.5f) * 3f * density)
            k++
        }
        lineTo(j(9), h * 0.5f)
        close()
    }
}

/** A paper clip, its wire bent round twice, drawn in [size] (narrow and tall). */
internal fun DrawScope.drawPaperClip(metal: Color) {
    val w = size.width
    val h = size.height
    val s = 1.4f * density
    val clip = Path().apply {
        moveTo(w * 0.3f, h * 0.72f)
        lineTo(w * 0.3f, h * 0.18f)
        cubicTo(w * 0.3f, h * 0.02f, w * 0.85f, h * 0.02f, w * 0.85f, h * 0.18f)
        lineTo(w * 0.85f, h * 0.86f)
        cubicTo(w * 0.85f, h * 1.0f, w * 0.1f, h * 1.0f, w * 0.1f, h * 0.86f)
        lineTo(w * 0.1f, h * 0.3f)
    }
    drawPath(clip, Color.Black.copy(alpha = 0.18f), style = Stroke(s * 1.6f, cap = StrokeCap.Round))
    drawPath(clip, metal, style = Stroke(s, cap = StrokeCap.Round))
    drawPath(clip, Color.White.copy(alpha = 0.45f), style = Stroke(s * 0.35f, cap = StrokeCap.Round))
}
