package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Polished chrome, top to bottom: bright, a dark band at the horizon line, bright again. */
val ChromeStops = arrayOf(
    0f to Color(0xFFFFFFFF), 0.3f to Color(0xFFD9D9D9), 0.48f to Color(0xFF6B6B6B),
    0.52f to Color(0xFFF4F4F4), 0.75f to Color(0xFF9A9A9A), 1f to Color(0xFFE8E8E8),
)

/** [ChromeStops] as a brush across whatever it fills. */
val ChromeBrush: Brush = Brush.verticalGradient(*ChromeStops)

/** A star with [points] spikes between [inner] and [outer] radii round [center], turned by [turn] radians. */
internal fun starPath(center: Offset, outer: Float, inner: Float, points: Int, turn: Float = 0f): Path = Path().apply {
    for (k in 0 until points * 2) {
        val r = if (k % 2 == 0) outer else inner
        val a = turn + k * PI.toFloat() / points - PI.toFloat() / 2f
        val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/**
 * 酸: Y2K at night. Black, a lime grid running to the horizon at the foot of the screen, two
 * starbursts turning slowly at the edges and sparkles that twinkle. On the player the grid rolls
 * toward you, faster with the bass, the bursts swell on the beat and the sparkles flash with the
 * treble.
 */
internal fun DrawScope.drawAcidNight(style: LanyinStyle, t: Float, e: Float, page: Boolean, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val flare = audio?.let { (1f - it.sinceBeat(t) / 0.5f).coerceIn(0f, 1f) } ?: 0f
    // A holographic haze in one corner.
    drawCircle(Brush.radialGradient(listOf(c.accentAlt.copy(alpha = 0.16f), Color(0x00FF3EA5)), Offset(w * 1.05f, h * 0.08f), w * 0.7f), w * 0.7f, Offset(w * 1.05f, h * 0.08f))
    // The grid.
    val horizon = h * (if (page) 0.74f else 0.68f)
    val vanish = Offset(w / 2f, horizon)
    val line = c.accent.copy(alpha = if (page) 0.09f else 0.16f)
    val stroke = density
    for (k in -12..12) {
        val x = w / 2f + k * w * 0.16f
        drawLine(line, vanish, Offset(w / 2f + (x - w / 2f) * 4f, h * 1.6f), stroke)
    }
    val roll = if (page) 0f else (t * (0.25f + 0.35f * e + 0.6f * (audio?.bass ?: 0f))) % 1f
    for (k in 0 until 14) {
        val z = (k + 1f - roll) / 14f
        val y = horizon + (h - horizon) * z * z
        drawLine(line.copy(alpha = line.alpha * (0.3f + 0.7f * z)), Offset(0f, y), Offset(w, y), stroke)
    }
    drawRect(Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0f)), startY = horizon - 2f, endY = horizon + (h - horizon) * 0.25f), Offset(0f, horizon - 2f), Size(w, (h - horizon) * 0.25f + 2f))
    // Starbursts at the edges.
    val swell = 1f + 0.12f * flare
    drawPath(starPath(Offset(w * 0.06f, h * 0.2f), w * 0.2f * swell, w * 0.13f * swell, 14, t * 0.1f), c.accent.copy(alpha = if (page) 0.14f else 0.26f), style = Stroke(1.5f * density))
    drawPath(starPath(Offset(w * 0.98f, h * 0.52f), w * 0.16f * swell, w * 0.1f * swell, 12, -t * 0.08f), c.accentAlt.copy(alpha = if (page) 0.12f else 0.24f), style = Stroke(1.5f * density))
    // Sparkles.
    val treble = audio?.treble ?: 0f
    for (k in 0 until 7) {
        val p = Offset(w * hash01(k, 71), h * (0.05f + 0.6f * hash01(k, 72)))
        val twinkle = 0.5f + 0.5f * sin(t * (1.6f + hash01(k, 73)) + k * 2f)
        val r = density * (5f + 7f * hash01(k, 74)) * (0.6f + 0.4f * twinkle + 0.5f * treble)
        drawPath(starPath(p, r, r * 0.22f, 4), (if (k % 3 == 0) c.accent else Color.White).copy(alpha = (if (page) 0.25f else 0.45f) * (0.4f + 0.6f * twinkle)))
    }
}

/**
 * 酸's progress: a lime pill, the part played filled with diagonal stripes that roll along as
 * the music plays, and a chrome bead where it is.
 */
internal fun DrawScope.drawStripeTrack(f: Float, phase: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val h = (13f + 3f * dragging) * density
    val top = (size.height - h) / 2f
    val x = size.width * f
    val radius = CornerRadius(h / 2f)
    drawRoundRect(c.accent, Offset(0f, top), Size(size.width, h), radius, style = Stroke(1.5f * density))
    val inset = 3f * density
    val fill = Path().apply { addRoundRect(RoundRect(inset, top + inset, (x - inset).coerceAtLeast(inset + 1f), top + h - inset, CornerRadius((h - inset * 2) / 2f))) }
    clipPath(fill) {
        val pitch = 9f * density
        var sx = -h + (phase * density * 12f) % pitch - pitch
        while (sx < x + h) {
            drawLine(c.accent, Offset(sx, top + h), Offset(sx + h, top), 4f * density)
            sx += pitch
        }
    }
    val bead = 8f * density
    drawCircle(c.accent.copy(alpha = 0.3f), bead * 1.7f, Offset(x, size.height / 2f))
    drawCircle(Brush.verticalGradient(*ChromeStops, startY = size.height / 2f - bead, endY = size.height / 2f + bead), bead, Offset(x, size.height / 2f))
}

/** 酸's play key: a lime disc in a black gap and a lime ring, glowing. */
internal fun DrawScope.drawLimeDisc(style: LanyinStyle, glow: Float) {
    val c = style.colors
    val r = size.minDimension / 2f
    val halo = r * (1.45f + 0.2f * glow)
    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.3f + 0.25f * glow), Color.Transparent), center, halo), halo)
    drawCircle(c.accent, r + 4f * density, style = Stroke(2f * density))
    drawCircle(c.accent, r - 2f * density)
}

/** A starburst flashing out round 酸's disc on the beat. */
internal fun DrawScope.drawBeatBurst(style: LanyinStyle, beat: Float, turn: Float) {
    if (beat <= 0f) return
    val r = size.minDimension / 2f
    val out = r * (1.35f + 0.45f * (1f - beat))
    drawPath(starPath(center, out, out * 0.78f, 16, turn), style.colors.accent.copy(alpha = beat * 0.8f), style = Stroke(2f * density))
}

/**
 * 酸's generated sleeves: a holographic disc, a chrome blob in sparkles, a lime starburst with a
 * smile, and a chrome sun over a lime checkerboard.
 */
internal fun DrawScope.acidCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bgDeep)
    val turn = hash01(seed, 1) * 360f
    when (seed % 4) {
        0 -> {
            val r = w * 0.4f
            drawCircle(
                Brush.sweepGradient(listOf(c.accentAlt, Color(0xFF7B2CFF), Color(0xFF00E5FF), c.accent, c.accentAlt), center),
                r,
            )
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), center - Offset(r * 0.3f, r * 0.35f), r * 0.6f), r)
            drawCircle(c.bgDeep, r * 0.16f)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.16f, style = Stroke(w * 0.01f))
        }
        1 -> {
            for (k in 0 until 4) {
                val p = Offset(w * (0.35f + 0.3f * hash01(seed * 5 + k, 2)), h * (0.35f + 0.3f * hash01(seed * 5 + k, 3)))
                val r = w * (0.14f + 0.08f * hash01(seed * 5 + k, 4))
                drawCircle(Brush.verticalGradient(*ChromeStops, startY = p.y - r, endY = p.y + r), r, p)
            }
            for (k in 0 until 5) {
                val p = Offset(w * hash01(seed * 9 + k, 5), h * hash01(seed * 9 + k, 6))
                drawPath(starPath(p, w * 0.05f, w * 0.012f, 4), if (k % 2 == 0) c.accent else Color.White)
            }
        }
        2 -> {
            drawPath(starPath(center, w * 0.42f, w * 0.3f, 18, turn / 57f), c.accent)
            val face = c.bgDeep
            drawCircle(face, w * 0.035f, center + Offset(-w * 0.08f, -h * 0.06f))
            drawCircle(face, w * 0.035f, center + Offset(w * 0.08f, -h * 0.06f))
            drawArc(face, 20f, 140f, false, Offset(w * 0.36f, h * 0.36f), Size(w * 0.28f, h * 0.28f), style = Stroke(w * 0.03f))
        }
        else -> {
            val horizon = h * 0.55f
            val sunR = w * 0.26f
            val sun = Offset(w / 2f, horizon)
            clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, horizon)) }) {
                drawCircle(Brush.verticalGradient(*ChromeStops, startY = horizon - sunR, endY = horizon), sunR, sun)
                for (k in 0 until 4) drawRect(c.bgDeep, Offset(0f, horizon - sunR * (0.12f + k * 0.16f)), Size(w, sunR * 0.05f * (k + 1)))
            }
            val rows = 6
            for (r in 0 until rows) {
                val y0 = horizon + (h - horizon) * (r / rows.toFloat()).let { it * it }
                val y1 = horizon + (h - horizon) * ((r + 1) / rows.toFloat()).let { it * it }
                for (k in -6..6) {
                    if ((k + r) % 2 != 0) continue
                    val x0 = w / 2f + k * w * 0.12f * (0.3f + (y0 - horizon) / (h - horizon) * 2f)
                    val x1 = w / 2f + (k + 1) * w * 0.12f * (0.3f + (y0 - horizon) / (h - horizon) * 2f)
                    drawRect(c.accent, Offset(minOf(x0, x1), y0), Size(abs(x1 - x0), y1 - y0))
                }
            }
        }
    }
}

/** 酸's motif: a starburst with a sparkle beside it. */
internal fun DrawScope.acidMotif(color: Color, accent: Color) {
    val r = size.minDimension * 0.3f
    drawPath(starPath(center, r, r * 0.72f, 14), color, style = Stroke(2f * density))
    drawPath(starPath(center + Offset(r * 1.05f, -r * 0.85f), r * 0.32f, r * 0.08f, 4), accent)
}
