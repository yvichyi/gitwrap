package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.pow
import kotlin.math.sin

/**
 * 霓's pages: a dark brick wall, lit only by signs out of sight, a pink one high on the left and a
 * cyan one off to the right, their light spilling over the bricks.
 */
internal fun DrawScope.drawNightWall(style: LanyinStyle) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    // Courses of brick: a faint mortar line under each, the joints staggered course by course.
    val course = 18.dp.toPx()
    val brick = 46.dp.toPx()
    val mortar = Color.White.copy(alpha = 0.022f)
    var y = 0f
    var row = 0
    while (y < h) {
        drawLine(mortar, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        var x = if (row % 2 == 0) 0f else brick / 2f
        while (x < w) {
            drawLine(mortar, Offset(x, y), Offset(x, y + course), 1.dp.toPx())
            x += brick
        }
        y += course
        row++
    }
    drawRect(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.15f), Color.Transparent), Offset(w * 0.12f, -h * 0.04f), w * 0.85f))
    drawRect(Brush.radialGradient(listOf(c.accentAlt.copy(alpha = 0.09f), Color.Transparent), Offset(w * 1.05f, h * 0.38f), w * 0.7f))
    // Darker toward the foot, where the bars sit.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = h * 0.6f, endY = h))
}

/**
 * Behind the player, the street itself at night after rain. Buildings close on either side; a far
 * skyline whose windows light with the music (the low notes to the left); in
 * landscape a striped sun going down at the end of the street. The asphalt is wet: the lane
 * markings run toward you, half a line a beat, and the signs and the sun lie in it broken up by
 * ripples. Rain falls through it all.
 */
internal fun DrawScope.drawNightStreet(style: LanyinStyle, t: Float, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val wide = w > h
    // Low in landscape, under the lyrics, so the skyline doesn't stand behind the words.
    val horizon = h * if (wide) 0.76f else 0.52f
    val vx = w * if (wide) 0.78f else 0.5f
    val pulse = audio?.pulse(t, 0.35f) ?: 0f
    drawRect(c.bg)
    // The sky over the city: dark, warming toward the horizon with the glow of the streets.
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 0.7f to c.bgDeep, 1f to lerp(c.accentAlt, c.accent, 0.6f).copy(alpha = 0.32f), startY = 0f, endY = horizon),
        size = Size(w, horizon),
    )
    if (wide) drawStreetSun(style, Offset(vx, horizon), h * 0.24f)
    drawSkylineWindows(style, horizon, vx, audio)
    // The street.
    drawRect(Brush.verticalGradient(listOf(lerp(c.bgDeep, Color.Black, 0.35f), c.bg), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
    drawLine(lerp(c.accentAlt, c.accent, 0.5f).copy(alpha = 0.35f + 0.5f * pulse), Offset(0f, horizon), Offset(w, horizon), (1f + 2f * pulse) * density)
    drawLanes(style, horizon, vx, audio, t)
    // The near buildings, their bases running down to the street's edges.
    val leftEdge = w * if (wide) 0.12f else 0.17f
    val rightEdge = w - w * if (wide) 0.07f else 0.17f
    fun baseLeft(x: Float) = h + (horizon - h) * (x / vx)
    fun baseRight(x: Float) = h + (horizon - h) * ((w - x) / (w - vx))
    val facade = lerp(c.bg, Color.Black, 0.45f)
    drawPath(Path().apply { moveTo(0f, 0f); lineTo(leftEdge, 0f); lineTo(leftEdge, baseLeft(leftEdge)); lineTo(0f, h); close() }, facade)
    drawPath(Path().apply { moveTo(w, 0f); lineTo(rightEdge, 0f); lineTo(rightEdge, baseRight(rightEdge)); lineTo(w, h); close() }, facade)
    // The street's light catching their corners, pink on one side and cyan on the other, and a
    // few windows still lit up their faces.
    drawLine(c.accent.copy(alpha = 0.3f), Offset(leftEdge, 0f), Offset(leftEdge, baseLeft(leftEdge)), 1.5.dp.toPx())
    drawLine(c.accentAlt.copy(alpha = 0.25f), Offset(rightEdge, 0f), Offset(rightEdge, baseRight(rightEdge)), 1.5.dp.toPx())
    for (k in 0 until 10) {
        val left = k % 2 == 0
        val span = if (left) leftEdge else w - rightEdge
        val wx = (if (left) 0f else rightEdge) + span * (0.2f + 0.55f * hash01(k, 81))
        val wy = h * (0.05f + 0.5f * hash01(k, 82))
        if (wy < (if (left) baseLeft(wx) else baseRight(wx)) - 20.dp.toPx()) {
            drawRect(Color(0xFFFFD9A8).copy(alpha = 0.18f + 0.2f * hash01(k, 83)), Offset(wx, wy), Size(5.dp.toPx(), 7.dp.toPx()))
        }
    }
    // The sun lies in the wet street, broken up by ripples.
    if (wide) drawStreetReflection(c.accent, vx, horizon, h, 0.8f, t, width = h * 0.24f)
    drawRain(style, t, audio)
    // Keep the foot dark enough for what sits on it.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.bg.copy(alpha = 0.8f)), startY = h * 0.72f, endY = h))
}

/** A light's reflection in the wet street: a column of its colour under it, broken into drifting ripples. */
private fun DrawScope.drawStreetReflection(color: Color, x: Float, horizon: Float, h: Float, lit: Float, t: Float, width: Float = 22.dp.toPx()) {
    val slices = 14
    val top = horizon + 6.dp.toPx()
    val span = h - top
    for (k in 0 until slices) {
        val f = k / slices.toFloat()
        val y = top + span * f
        val drift = sin(t * 1.8f + k * 1.7f) * (2f + 6f * f) * density
        val wide = width * (0.6f + 0.6f * f)
        drawRect(color.copy(alpha = 0.13f * lit * (1f - f)), Offset(x - wide / 2f + drift, y), Size(wide, span / slices * 0.55f))
    }
}

/** The far skyline, its windows lit by the spectrum: the bass to the left, the treble to the right. */
private fun DrawScope.drawSkylineWindows(style: LanyinStyle, horizon: Float, vx: Float, audio: BackdropAudio?) {
    val c = style.colors
    NeonBatch.windows.forEach { it.reset() }
    val w = size.width
    val block = lerp(c.bg, Color.Black, 0.3f)
    val window = 2.dp.toPx()
    val pitch = 5.dp.toPx()
    var x = 0f
    var i = 0
    while (x < w) {
        val bw = (18f + 26f * hash01(i, 61)) * density
        // Lower toward the end of the street, so the buildings step down to the vanishing point.
        val near = 1f - (kotlin.math.abs(x + bw / 2f - vx) / w).coerceIn(0f, 1f)
        val bh = (22f + 70f * hash01(i, 62)) * density * (1.15f - 0.55f * near) * (if (w > size.height) 0.7f else 1f)
        val top = horizon - bh
        drawRect(block, Offset(x, top), Size(bw - 1.dp.toPx(), bh))
        val band = ((x / w) * 31).toInt().coerceIn(0, 31)
        val level = audio?.levels?.get(band) ?: 0f
        // Brightness in four steps, so the windows of the whole skyline go down in a dozen paths.
        val step = (level * 4f).toInt().coerceIn(0, 3)
        var wy = top + pitch
        var row = 0
        while (wy < horizon - pitch) {
            var wx = x + pitch * 0.6f
            var col = 0
            while (wx < x + bw - pitch) {
                val seed = hash01(i * 37 + row * 7 + col, 63)
                // Some windows always lit; more come on as the band rises, from the bottom up.
                val rise = (horizon - wy) / bh
                val on = seed < 0.18f || (level > 0.05f && seed < 0.18f + level * 0.9f && rise < 0.25f + level)
                if (on) {
                    val tint = if (hash01(i * 13 + row + col, 64) < 0.75f) 0 else if (seed < 0.09f) 1 else 2
                    NeonBatch.windows[tint * 4 + step].apply {
                        moveTo(wx, wy)
                        lineTo(wx + window, wy)
                        lineTo(wx + window, wy + window * 1.3f)
                        lineTo(wx, wy + window * 1.3f)
                        close()
                    }
                }
                wx += pitch
                col++
            }
            wy += pitch * 1.3f
            row++
        }
        x += bw
        i++
    }
    // The lit windows, a path for each tint and brightness.
    for (k in NeonBatch.windows.indices) {
        val level = (k % 4 + 0.5f) / 4f
        val tint = when (k / 4) {
            0 -> Color(0xFFFFD9A8)
            1 -> c.accentAlt
            else -> c.accent
        }
        drawPath(NeonBatch.windows[k], tint.copy(alpha = 0.32f + 0.4f * level))
    }
}

/** The lane markings, running toward you half a line a beat, and a band of light down them on each beat. */
private fun DrawScope.drawLanes(style: LanyinStyle, horizon: Float, vx: Float, audio: BackdropAudio?, t: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val bottomX = w * 0.5f
    val scroll = (audio?.let { it.beatClock * 0.5f } ?: (t * 0.2f)) % 1f
    val n = 9
    for (j in 0 until n) {
        val z0 = (j + scroll) / n
        val z1 = (j + scroll + 0.45f) / n
        if (z0 > 1f) continue
        fun at(z: Float) = Offset(vx + (bottomX - vx) * z.pow(2f), horizon + (h - horizon) * z.pow(2f))
        drawLine(Color(0xFFE9E2D0).copy(alpha = 0.10f + 0.3f * z0), at(z0), at(z1.coerceAtMost(1f)), (1f + 5f * z0) * density, StrokeCap.Butt)
    }
    // The edges of the road, to the vanishing point.
    for (edgeX in listOf(-w * 0.2f, w * 1.2f)) {
        drawLine(c.content.copy(alpha = 0.06f), Offset(vx, horizon), Offset(edgeX, h), 1.dp.toPx())
    }
    if (audio != null) {
        for (k in 0 until 3) {
            val count = audio.beatCount - 1 - k
            if (count < 0) break
            val age = t - audio.beats[count % BackdropAudio.BEATS]
            if (age < 0f || age > 0.9f) continue
            val z = age / 0.9f
            val y = horizon + (h - horizon) * z.pow(2.2f)
            val band = (h - horizon) * 0.05f * (0.3f + z)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.22f * (1f - z)), Color.Transparent), startY = y - band, endY = y + band),
                Offset(0f, y - band), Size(w, band * 2),
            )
        }
    }
}

/** The striped sun of an eighties sunset, going down at the end of the street. */
private fun DrawScope.drawStreetSun(style: LanyinStyle, center: Offset, r: Float) {
    val c = style.colors
    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.22f), Color.Transparent), center, r * 1.9f), r * 1.9f, center)
    val disc = Path().apply { addOval(Rect(center, r)) }
    val fill = Brush.verticalGradient(listOf(Color(0xFFFFC46B).copy(alpha = 0.85f), c.accent.copy(alpha = 0.8f)), startY = center.y - r, endY = center.y)
    clipPath(disc) {
        var top = center.y - r
        var gap = r * 0.035f
        var slice = r * 0.5f
        while (top < center.y) {
            drawRect(fill, Offset(center.x - r, top), Size(r * 2, minOf(slice, center.y - top)))
            top += slice + gap
            slice *= 0.62f
            gap *= 1.15f
        }
    }
}

/** Rain: thin slanting streaks, a little heavier while the music is loud. */
private fun DrawScope.drawRain(style: LanyinStyle, t: Float, audio: BackdropAudio?) {
    val w = size.width
    val h = size.height
    val drops = 60
    val heavy = 0.6f + 0.6f * (audio?.level ?: 0f)
    val len = 14.dp.toPx()
    val slant = 3.dp.toPx()
    // Fainter and brighter drops, each lot one path.
    val (faint, bright) = NeonBatch.rain
    faint.reset()
    bright.reset()
    for (i in 0 until drops) {
        val speed = h * (1.0f + 0.7f * hash01(i, 71))
        val y = ((t * speed + hash01(i, 72) * h * 1.3f) % (h * 1.3f)) - h * 0.15f
        val x = hash01(i, 73) * (w + slant * 4f) - slant * 2f + y / h * slant * 4f
        val path = if (hash01(i, 74) < 0.5f) faint else bright
        path.moveTo(x, y)
        path.lineTo(x - slant, y + len)
    }
    val stroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
    drawPath(faint, style.colors.content.copy(alpha = 0.07f * heavy), style = stroke)
    drawPath(bright, style.colors.content.copy(alpha = 0.11f * heavy), style = stroke)
}

/**
 * Paths reused from frame to frame for what 霓 draws many of, so the rain and the lit windows go
 * down as a few draws rather than hundreds, and nothing new is made each frame. Backdrops draw on
 * the main thread, one at a time.
 */
private object NeonBatch {
    val rain = Pair(Path(), Path())
    /** Three tints (warm white, the two neons) by four brightness steps. */
    val windows = Array(12) { Path() }
}
