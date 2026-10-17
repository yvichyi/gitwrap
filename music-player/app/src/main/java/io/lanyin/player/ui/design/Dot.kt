package io.lanyin.player.ui.design

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** Brightness steps the dot field is drawn in: one batch of points per step. */
private const val DOT_LEVELS = 6

/** Reused point buffers, one per brightness step; drawing happens on one thread. */
private val dotBuffers = Array(DOT_LEVELS) { FloatArray(0) }
private val dotPaint = Paint().apply {
    isAntiAlias = true
    strokeCap = Paint.Cap.ROUND
}

/**
 * 点: true black under a fine grid of dots, as on Nothing's wallpapers and glyph matrix. On the
 * player each beat sends a ring of light out through the grid from behind the artwork, and the
 * dots nearest it brighten with the bass. Pages keep the grid, dim and still.
 */
internal fun DrawScope.drawDotField(style: LanyinStyle, t: Float, page: Boolean, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val pitch = 15.dp.toPx()
    val cols = (w / pitch).toInt() + 1
    val rows = (h / pitch).toInt() + 1
    val left = (w - (cols - 1) * pitch) / 2f
    val top = (h - (rows - 1) * pitch) / 2f
    // Where the artwork sits: upper middle in portrait, the left of the stage in landscape.
    val focus = if (w > h) Offset(w * 0.27f, h * 0.5f) else Offset(w * 0.5f, h * 0.36f)
    val reach = hypot(maxOf(focus.x, w - focus.x), maxOf(focus.y, h - focus.y))
    val counts = IntArray(DOT_LEVELS)
    val needed = cols * rows * 2
    for (k in 0 until DOT_LEVELS) if (dotBuffers[k].size < needed) dotBuffers[k] = FloatArray(needed)
    val base = if (page) 0.07f else 0.1f
    for (row in 0 until rows) {
        val y = top + row * pitch
        for (col in 0 until cols) {
            val x = left + col * pitch
            var light = base
            if (audio != null) {
                val d = hypot(x - focus.x, y - focus.y)
                // Rings from the last few beats, spreading at a third of the screen a second.
                for (b in 0 until 3) {
                    val n = audio.beatCount - 1 - b
                    if (n < 0) break
                    val age = t - audio.beats[n % BackdropAudio.BEATS]
                    if (age < 0f || age > 1.6f) continue
                    val ring = age * reach * 0.75f
                    val band = (d - ring) / (pitch * 1.6f)
                    light += 0.55f * exp(-band * band) * (1f - age / 1.6f)
                }
                // A glow round the artwork that swells with the bass.
                val near = (1f - d / (reach * 0.55f)).coerceIn(0f, 1f)
                light += 0.22f * audio.bass * near * near
            }
            val level = ((light - base) / 0.6f * (DOT_LEVELS - 1) + 0.5f).toInt().coerceIn(0, DOT_LEVELS - 1)
            val buffer = dotBuffers[level]
            buffer[counts[level]++] = x
            buffer[counts[level]++] = y
        }
    }
    val radius = 1.1.dp.toPx()
    drawIntoCanvas { canvas ->
        dotPaint.strokeWidth = radius * 2f
        for (k in 0 until DOT_LEVELS) {
            if (counts[k] == 0) continue
            val alpha = base + (0.75f - base) * k / (DOT_LEVELS - 1f)
            dotPaint.color = c.content.copy(alpha = alpha).toArgb()
            canvas.nativeCanvas.drawPoints(dotBuffers[k], 0, counts[k], dotPaint)
        }
    }
}

/**
 * Shows what's drawn through an LED screen: in grey, and only inside a grid of round lights
 * [pitch] apart, so a cover reads as the glyph matrix would light it. Between the lights is
 * whatever lies behind (点's black). Off ([enabled] false), the cover shows as it is.
 */
fun Modifier.ledScreen(pitch: Dp = 4.dp, enabled: Boolean = true): Modifier = if (!enabled) this else this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val side = pitch.toPx().toInt().coerceAtLeast(2)
        val tile = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(tile).drawCircle(side / 2f, side / 2f, side * 0.42f, Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE })
        val mask = ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
        onDrawWithContent {
            drawContent()
            // Grey: take the saturation of black.
            drawRect(Color.Black, blendMode = BlendMode.Saturation)
            // A touch brighter, as a lit LED is.
            drawRect(Color.White.copy(alpha = 0.06f), blendMode = BlendMode.Screen)
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * 点's progress: a row of dots, those played lit white, the place marked by the red dot with
 * a soft glow. While dragging, the dots around the finger swell.
 */
internal fun DrawScope.drawDotsTrack(f: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val cy = size.height / 2f
    val pitch = 7.dp.toPx()
    val n = (size.width / pitch).toInt().coerceAtLeast(2)
    val step = size.width / (n - 1)
    val head = f * (n - 1)
    val r = 1.9.dp.toPx()
    for (i in 0 until n) {
        val x = i * step
        val near = (1f - kotlin.math.abs(i - head) / 4f).coerceIn(0f, 1f)
        val rr = r * (1f + 0.6f * near * dragging)
        drawCircle(if (i < head) c.content else c.content.copy(alpha = 0.18f), rr, Offset(x, cy))
    }
    val x = head * step
    val glow = 11.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(c.accentAlt.copy(alpha = 0.45f), Color.Transparent), Offset(x, cy), glow), glow, Offset(x, cy))
    drawCircle(c.accentAlt, (3.6f + 1.2f * dragging) * density, Offset(x, cy))
}

/**
 * The lights round 点's play disc while music plays: a ring of [count] dots lit clockwise from
 * the top as far as the level reaches, all flaring a little on the beat.
 */
internal fun DrawScope.drawDiscLights(style: LanyinStyle, level: Float, beat: Float, count: Int = 28) {
    val c = style.colors
    val r = size.minDimension / 2f + 8.dp.toPx()
    val lit = (level * 1.8f).coerceIn(0f, 1f) * count
    for (k in 0 until count) {
        val a = -PI.toFloat() / 2f + k * 2f * PI.toFloat() / count
        val on = k < lit
        val alpha = if (on) 0.9f else 0.12f + 0.35f * beat
        val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        drawCircle(if (k == 0) c.accentAlt else c.content.copy(alpha = alpha), (if (on) 1.8f else 1.4f) * density, p)
    }
}

/**
 * 点's generated sleeves: white dot-matrix pictures on black with the one red dot: a lit
 * sphere in halftone, a bar meter, rings round a point, a rolling wave.
 */
internal fun DrawScope.dotCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    // A dark grey tile, so a sleeve keeps its edge on the black page.
    drawRect(c.surface)
    val cells = 18
    val pitch = w / cells
    val maxR = pitch * 0.46f
    fun dot(col: Int, row: Int, amount: Float, color: Color = c.content) {
        if (amount <= 0.04f) return
        drawCircle(color, maxR * amount.coerceAtMost(1f), Offset((col + 0.5f) * pitch, (row + 0.5f) * pitch))
    }
    val rows = (h / pitch).toInt()
    when (seed % 4) {
        0 -> {
            // A sphere lit from the upper left, in halftone.
            val cx = cells * (0.45f + 0.1f * hash01(seed, 1))
            val cy = rows * (0.5f + 0.06f * hash01(seed, 2))
            val radius = cells * 0.36f
            for (row in 0 until rows) for (col in 0 until cells) {
                val dx = (col + 0.5f - cx) / radius
                val dy = (row + 0.5f - cy) / radius
                val d = dx * dx + dy * dy
                if (d > 1f) continue
                val z = kotlin.math.sqrt(1f - d)
                dot(col, row, (0.15f + 0.85f * (-dx * 0.55f - dy * 0.6f + z * 0.6f)).coerceIn(0f, 1f))
            }
            dot((cx + radius * 0.9f).toInt(), (cy - radius * 0.95f).toInt(), 1f, c.accentAlt)
        }
        1 -> {
            // A level meter: columns of dots, one peak in red.
            val red = 3 + (hash01(seed, 3) * 10).toInt()
            for (col in 1 until cells - 1) {
                val height = (rows * (0.2f + 0.55f * hash01(seed * 13 + col, 4))).toInt()
                for (k in 0 until height) dot(col, rows - 2 - k, 0.8f)
                if (col == red) dot(col, rows - 3 - height, 1f, c.accentAlt)
            }
        }
        2 -> {
            // Rings of dots round a red centre.
            val cx = cells / 2f
            val cy = rows / 2f
            for (row in 0 until rows) for (col in 0 until cells) {
                val d = hypot(col + 0.5f - cx, row + 0.5f - cy)
                val ring = kotlin.math.abs(sin(d * (0.9f + 0.3f * hash01(seed, 5))))
                if (d < 7.5f) dot(col, row, ring * (1f - d / 9f))
            }
            dot(cells / 2, rows / 2, 1f, c.accentAlt)
        }
        else -> {
            // A wave rolling through the grid.
            val phase = hash01(seed, 6) * 6f
            for (row in 0 until rows) for (col in 0 until cells) {
                val y = rows * (0.5f + 0.22f * sin(col * 0.45f + phase))
                val d = kotlin.math.abs(row + 0.5f - y)
                dot(col, row, (1f - d / 3.2f).coerceIn(0f, 1f) * 0.9f)
            }
            dot(1, 1, 1f, c.accentAlt)
        }
    }
}

/** 点's motif: a five by five matrix with the red dot at its heart. */
internal fun DrawScope.dotMotif(color: Color, red: Color) {
    val side = size.minDimension * 0.7f
    val pitch = side / 5f
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    for (row in 0 until 5) for (col in 0 until 5) {
        val p = Offset(left + (col + 0.5f) * pitch, top + (row + 0.5f) * pitch)
        val edge = row == 0 || row == 4 || col == 0 || col == 4
        if (row == 2 && col == 2) drawCircle(red, pitch * 0.32f, p)
        else drawCircle(color.copy(alpha = if (edge) 0.85f else 0.3f), pitch * 0.22f, p)
    }
}
