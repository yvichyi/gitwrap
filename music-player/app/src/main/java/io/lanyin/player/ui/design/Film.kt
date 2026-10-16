package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 影: a darkroom. Near-black, light leaking in red and orange at two edges and falling off fast,
 * a vignette, and grain that is redrawn every frame on the player so it seethes like projected
 * film; now and then a speck of dust or a scratch crosses the frame. Light leaks flare on the
 * beat, and on the player the film's edge printing runs down the right-hand side.
 */
internal fun DrawScope.drawFilm(
    style: LanyinStyle,
    t: Float,
    e: Float,
    page: Boolean,
    audio: BackdropAudio?,
    measurer: TextMeasurer? = null,
    trackNumber: Int? = null,
) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    drawRect(Brush.verticalGradient(listOf(c.bgDeep, c.bg), endY = h * 0.45f))
    val flare = audio?.pulse(t, 0.6f) ?: 0f
    val breathe = 0.75f + 0.25f * sin(t * 0.45f) + 0.3f * flare + 0.25f * (audio?.bass ?: 0f)
    val strength = if (page) 0.55f else 1f
    // Light leaks: red from the left, orange from the top right, wandering slowly. Bright at the
    // edge of the frame and gone within a hand's width, as light fogging film is.
    fun leak(color: Color, at: Offset, radius: Float, alpha: Float) {
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = alpha), 0.3f to color.copy(alpha = alpha * 0.45f), 0.65f to color.copy(alpha = alpha * 0.08f), 1f to Color.Transparent, center = at, radius = radius),
            radius, at,
        )
    }
    leak(c.accentAlt, Offset(-w * 0.2f, h * (0.32f + 0.12f * sin(t * 0.13f))), maxOf(w, h) * 0.5f, 0.42f * breathe * strength)
    leak(c.accent, Offset(w * (1.12f + 0.04f * cos(t * 0.17f)), h * 0.04f), maxOf(w, h) * 0.46f, 0.4f * breathe * strength)
    // Vignette.
    drawRect(Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = Offset(w / 2f, h * 0.45f), radius = maxOf(w, h) * 0.75f))
    // Grain: a new frame of it twelve times a second on the player.
    val frame = if (page) 0 else (t * 12f).toInt()
    translate(-96f * hash01(frame, 81), -96f * hash01(frame, 82)) {
        drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), Offset.Zero, Size(w + 96f, h + 96f), alpha = if (page) 0.07f else 0.1f)
    }
    if (page) return
    // Dust and the odd scratch.
    for (k in 0 until 3) {
        if (hash01(frame, 90 + k) > 0.72f) {
            val p = Offset(hash01(frame, 100 + k) * w, hash01(frame, 110 + k) * h)
            drawCircle(c.content.copy(alpha = 0.22f), density * (0.8f + 1.6f * hash01(frame, 120 + k)), p)
        }
    }
    if (hash01(frame, 130) > 0.9f) {
        val x = hash01(frame, 131) * w
        drawLine(c.content.copy(alpha = 0.07f), Offset(x, 0f), Offset(x + w * 0.01f, h), density)
    }
    // A projector's slight flicker.
    drawRect(Color.Black.copy(alpha = 0.035f * hash01(frame, 140)))
    if (measurer != null) drawEdgePrint(measurer, trackNumber ?: 1)
}

/**
 * The latent printing along a film's edge: the stock's name and frame numbers with their
 * arrows, in the orange the base shows through, running down the right of the frame.
 */
private fun DrawScope.drawEdgePrint(measurer: TextMeasurer, trackNumber: Int) {
    val frame = trackNumber * 2
    val text = buildString {
        append("LANYIN 400")
        for (k in 0 until 8) append("     ▸ ").append(frame + k / 2).append(if (k % 2 == 1) "A" else "")
    }
    val layout = measurer.measure(
        text,
        TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (8.5f / fontScale).sp, letterSpacing = 0.18.em),
    )
    // Turned a quarter clockwise its glyphs face outward, the line reading downwards.
    val pivot = Offset(size.width - 3.dp.toPx(), size.height * 0.1f)
    rotate(90f, pivot) {
        drawText(layout, color = FilmStamp.copy(alpha = 0.3f), topLeft = pivot)
    }
}

/** 影's generated sleeves: four snapshots in warm film colour, grained and vignetted. */
internal fun DrawScope.filmCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val cream = Color(0xFFFFE9C9)
    when (seed % 4) {
        0 -> {
            // Sunset over the sea.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3B2A44), c.accentAlt, c.accent, Color(0xFFF5B36A)), endY = h * 0.64f), size = Size(w, h * 0.64f))
            drawCircle(cream.copy(alpha = 0.95f), w * 0.13f, Offset(w * (0.4f + 0.2f * hash01(seed, 1)), h * 0.6f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2C35), Color(0xFF15100F)), startY = h * 0.64f, endY = h), Offset(0f, h * 0.64f), Size(w, h * 0.36f))
            for (k in 0 until 4) drawLine(cream.copy(alpha = 0.4f - k * 0.08f), Offset(w * (0.3f + k * 0.03f), h * (0.7f + k * 0.06f)), Offset(w * (0.6f - k * 0.03f), h * (0.7f + k * 0.06f)), h * 0.012f, StrokeCap.Round)
        }
        1 -> {
            // City lights out of focus.
            drawRect(Color(0xFF17110F))
            for (i in 0 until 14) {
                val color = listOf(c.accent, c.accentAlt, cream, Color(0xFF7FB2C9))[i % 4]
                val p = Offset(w * hash01(seed * 31 + i, 2), h * (0.2f + 0.7f * hash01(seed * 31 + i, 3)))
                val r = w * (0.05f + 0.09f * hash01(seed * 31 + i, 4))
                drawCircle(color.copy(alpha = 0.45f), r, p)
                drawCircle(color.copy(alpha = 0.25f), r, p, style = Stroke(w * 0.008f))
            }
        }
        2 -> {
            // A window of evening light on a wall.
            drawRect(Brush.linearGradient(listOf(Color(0xFF4A3528), Color(0xFF221812)), Offset.Zero, Offset(w, h)))
            val pane = Path().apply {
                moveTo(w * 0.25f, h * 0.2f); lineTo(w * 0.8f, h * 0.12f); lineTo(w * 0.86f, h * 0.7f); lineTo(w * 0.3f, h * 0.82f); close()
            }
            drawPath(pane, Brush.linearGradient(listOf(Color(0xFFF7B567), c.accent), Offset(w * 0.25f, h * 0.2f), Offset(w * 0.86f, h * 0.7f)))
            drawLine(Color(0xFF2A1E17), Offset(w * 0.52f, h * 0.16f), Offset(w * 0.58f, h * 0.76f), w * 0.03f)
            drawLine(Color(0xFF2A1E17), Offset(w * 0.28f, h * 0.5f), Offset(w * 0.83f, h * 0.41f), w * 0.03f)
        }
        else -> {
            // Hills against a hazy sky with the sun's flare.
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE9C79C), Color(0xFFD98C5F))))
            val sun = Offset(w * (0.62f + 0.2f * hash01(seed, 5)), h * 0.32f)
            drawCircle(Brush.radialGradient(listOf(cream, cream.copy(alpha = 0f)), sun, w * 0.4f), w * 0.4f, sun)
            for (layer in 0..1) {
                val path = Path().apply {
                    moveTo(0f, h)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, h * (0.62f + layer * 0.12f) - h * 0.08f * sin(x / w * 5f + seed + layer * 2f))
                        x += w / 24f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, if (layer == 0) Color(0xFF8C4B3A) else Color(0xFF3B231C))
            }
        }
    }
    drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.12f)
}

/**
 * A camera's iris: six blades round a hole that opens to [open] (0 nearly shut, 1 wide), turning
 * as it opens, with warm light coming through. The play glyph sits on top.
 */
internal fun DrawScope.drawAperture(open: Float, glow: Float, style: LanyinStyle) {
    val c = style.colors
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    // The light behind the blades.
    drawCircle(Brush.radialGradient(listOf(lerp(c.accent, Color.White, 0.35f).copy(alpha = 0.55f + 0.35f * glow), c.accentAlt.copy(alpha = 0.25f)), center, r), r, center)
    val hole = r * (0.42f + 0.4f * open)
    val turn = (30f * open) / 180f * PI.toFloat()
    val corners = List(6) { k ->
        val a = turn + k * PI.toFloat() / 3f
        Offset(center.x + cos(a) * hole, center.y + sin(a) * hole)
    }
    val rim = Path().apply { addOval(Rect(center, r)) }
    val gap = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        for (k in 1 until 6) lineTo(corners[k].x, corners[k].y)
        close()
    }
    val blades = Path().apply { op(rim, gap, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(blades, c.surfaceAlt)
    clipPath(rim) {
        // Each blade's edge runs on from a corner of the hole to the rim.
        for (k in 0 until 6) {
            val a = corners[k]
            val b = corners[(k + 1) % 6]
            val dir = Offset(b.x - a.x, b.y - a.y)
            val len = kotlin.math.hypot(dir.x, dir.y)
            val end = Offset(a.x + dir.x / len * r * 2f, a.y + dir.y / len * r * 2f)
            drawLine(c.content.copy(alpha = 0.18f), a, end, 1.2f * density)
        }
    }
    drawCircle(c.content.copy(alpha = 0.55f), r - 0.75f * density, center, style = Stroke(1.5f * density))
}

/**
 * A strip of film for the progress: sprocket holes along both edges, frame lines, the part
 * played exposed in orange, and a bright gate line where the music is.
 */
internal fun DrawScope.drawFilmTrack(f: Float, style: LanyinStyle) {
    val c = style.colors
    val h = 22f * density
    val top = (size.height - h) / 2f
    val x = size.width * f
    drawRoundRect(Color(0xFF0A0908), Offset(0f, top), Size(size.width, h), CornerRadius(2f * density))
    val band = Rect(0f, top + h * 0.28f, size.width, top + h * 0.72f)
    drawRect(c.content.copy(alpha = 0.06f), band.topLeft, band.size)
    drawRect(Brush.horizontalGradient(listOf(c.accentAlt.copy(alpha = 0.55f), c.accent.copy(alpha = 0.75f)), endX = x.coerceAtLeast(1f)), band.topLeft, Size(x, band.height))
    val pitch = 9f * density
    val hole = Size(4f * density, 3f * density)
    var hx = pitch / 2f
    val holeTop = top + h * 0.07f
    val holeBottom = top + h * 0.93f - hole.height
    val corner = CornerRadius(1f * density)
    while (hx < size.width) {
        val color = c.content.copy(alpha = if (hx < x) 0.32f else 0.16f)
        drawRoundRect(color, Offset(hx - hole.width / 2f, holeTop), hole, corner)
        drawRoundRect(color, Offset(hx - hole.width / 2f, holeBottom), hole, corner)
        hx += pitch
    }
    var fx = 0f
    while (fx < size.width) {
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(fx, band.top), Offset(fx, band.bottom), 1.5f * density)
        fx += pitch * 4f
    }
    drawLine(c.content.copy(alpha = 0.25f), Offset(x, top - 3f * density), Offset(x, top + h + 3f * density), 6f * density, StrokeCap.Round)
    drawLine(c.content, Offset(x, top - 3f * density), Offset(x, top + h + 3f * density), 2f * density, StrokeCap.Round)
}

/**
 * The orange seven-segment date of a point-and-shoot, 「'26 10 15」. Digits, spaces and the
 * apostrophe; [height] is a digit's height.
 */
internal fun DrawScope.drawDateStamp(text: String, topLeft: Offset, height: Float, color: Color = FilmStamp) {
    val dw = height * 0.55f
    val t = height * 0.13f
    var x = topLeft.x
    val y = topLeft.y
    for (ch in text) {
        when (ch) {
            ' ' -> x += dw * 0.6f
            '\'' -> {
                drawLine(color, Offset(x + t, y), Offset(x + t * 0.4f, y + height * 0.28f), t, StrokeCap.Round)
                x += t * 2.6f
            }
            else -> {
                val segs = SevenSegments[ch] ?: ""
                val half = height / 2f
                fun seg(from: Offset, to: Offset) = drawLine(color, Offset(x + from.x, y + from.y), Offset(x + to.x, y + to.y), t, StrokeCap.Round)
                // A slight forward slant, as the stamps print.
                val s = height * 0.08f
                if ('a' in segs) seg(Offset(t + s, 0f), Offset(dw - t + s, 0f))
                if ('b' in segs) seg(Offset(dw + s, t * 0.6f), Offset(dw + s * 0.5f, half - t * 0.6f))
                if ('c' in segs) seg(Offset(dw + s * 0.5f, half + t * 0.6f), Offset(dw, height - t * 0.6f))
                if ('d' in segs) seg(Offset(t, height), Offset(dw - t, height))
                if ('e' in segs) seg(Offset(s * 0.5f, half + t * 0.6f), Offset(0f, height - t * 0.6f))
                if ('f' in segs) seg(Offset(s, t * 0.6f), Offset(s * 0.5f, half - t * 0.6f))
                if ('g' in segs) seg(Offset(t + s * 0.5f, half), Offset(dw - t + s * 0.5f, half))
                x += dw + height * 0.28f
            }
        }
    }
}

/** Each digit's lit segments, a b c d e f g as on a calculator. */
private val SevenSegments = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcfgd",
)

/** How wide [drawDateStamp] draws [text] at [height]. */
internal fun dateStampWidth(text: String, height: Float): Float = text.sumOf { ch ->
    when (ch) {
        ' ' -> (height * 0.55f * 0.6f).toDouble()
        '\'' -> (height * 0.13f * 2.6f).toDouble()
        else -> (height * 0.55f + height * 0.28f).toDouble()
    }
}.toFloat()

/**
 * A frame of 35mm film round a picture inset by [inset]: black stock, sprocket holes above and
 * below, an orange edge marking with the frame number.
 */
internal fun DrawScope.drawFilmFrame(inset: Float, frameNumber: Int) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF0B0908))
    val hole = Size(w * 0.045f, inset * 0.36f)
    val pitch = w / 11f
    var x = pitch / 2f
    while (x < w) {
        for (y in listOf(inset * 0.32f, h - inset * 0.32f - hole.height)) {
            drawRoundRect(Color(0xFF2A241E), Offset(x - hole.width / 2f, y), hole, CornerRadius(hole.height * 0.25f))
        }
        x += pitch
    }
    // Edge print: a frame number and an arrow, in the orange of the film's base.
    val mark = FilmStamp.copy(alpha = 0.75f)
    drawDateStamp("$frameNumber", Offset(w * 0.06f, h - inset * 0.82f), inset * 0.32f, mark)
    val ay = h - inset * 0.66f
    val ax = w * 0.86f
    drawPath(Path().apply { moveTo(ax, ay - inset * 0.12f); lineTo(ax + inset * 0.24f, ay); lineTo(ax, ay + inset * 0.12f); close() }, mark)
}
