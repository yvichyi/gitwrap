package io.lanyin.player.ui.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
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

/** [ChromeStops] dimmed, for a rim that shouldn't shout: on cards. */
val ChromeSoftBrush: Brush = Brush.verticalGradient(*ChromeStops.map { (at, color) -> at to color.copy(alpha = 0.42f) }.toTypedArray())

/** The colours iMacs came in, for 酸's see-through plastic: Bondi blue, tangerine, grape, strawberry, blueberry. */
object Flavor {
    val Bondi = Color(0xFF00A9C9)
    val Tangerine = Color(0xFFFF8A1F)
    val Grape = Color(0xFF8B5CF6)
    val Strawberry = Color(0xFFFF4F7B)
    val Blueberry = Color(0xFF3B82F6)
    val all = listOf(Bondi, Tangerine, Grape, Strawberry, Blueberry)
}

/** The sheen across the top of glossy plastic, as on an Aqua button: white fading down over its upper half. */
fun DrawScope.drawGloss(strength: Float = 0.38f) {
    val inset = minOf(size.width, size.height) * 0.07f
    val h = size.height * 0.46f - inset
    if (h <= 0f) return
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = strength), Color.White.copy(alpha = strength * 0.08f)), startY = inset, endY = inset + h),
        Offset(inset, inset), Size(size.width - inset * 2, h), CornerRadius(minOf(h, size.width * 0.5f)),
    )
}

/**
 * iMac-era see-through plastic: [tint] over the dark, the light coming through brighter at its
 * foot, a gloss across its top (over what it holds when [glossOver], as on a gel button, or
 * behind it) and, with [rim], a chrome edge.
 */
@Composable
fun Modifier.candyPlastic(tint: Color, shape: Shape, rim: Boolean = true, glossOver: Boolean = false): Modifier = this
    .clip(shape)
    .drawBehind {
        drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.66f), tint.copy(alpha = 0.36f))))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(size.width / 2f, size.height * 1.05f), size.maxDimension * 0.6f))
        if (!glossOver) drawGloss(0.3f)
    }
    .then(if (glossOver) Modifier.drawWithContent { drawContent(); drawGloss() } else Modifier)
    .then(if (rim) Modifier.border(1.5.dp, ChromeBrush, shape) else Modifier)

/** A small bead of [tint] gel filling the canvas: a darker foot, a white catchlight near its top. */
fun DrawScope.drawGelBead(tint: Color) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(tint, Color.White, 0.35f), tint, androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.3f)), c.y - r, c.y + r), r, c)
    drawOval(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.55f, c.y - r * 0.82f), Size(r * 1.1f, r * 0.7f))
}

/** A round gel button in [tint] plastic, its icon white under the gloss. */
@Composable
fun GelButton(icon: LineIcon, tint: Color, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier
            .size(size)
            .shadow(8.dp, CircleShape, ambientColor = tint, spotColor = tint)
            .candyPlastic(tint, CircleShape, glossOver = true)
            .pressable(label = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ThemedIcon(icon, null, tint = Color.White, size = size * 0.42f)
    }
}

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
 * 酸: Y2K after dark. A deep grape glass like a CRT's, a holographic light leak across the top,
 * glossy bubbles of coloured plastic adrift (rising slowly behind the player, faster with the
 * music, a little fuller on the beat), a faint grid to the horizon on the player, and a few white
 * sparkles that flash in turn on the beats.
 */
internal fun DrawScope.drawAcidNight(style: LanyinStyle, t: Float, e: Float, page: Boolean, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(c.bg, c.bgDeep, c.bg)))
    // The holographic leak: a soft band of rainbow light across the top.
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Flavor.Bondi.copy(alpha = 0.16f), Flavor.Grape.copy(alpha = 0.18f), Flavor.Strawberry.copy(alpha = 0.14f), Color.Transparent),
            Offset(0f, 0f), Offset(w, h * 0.35f),
        ),
        size = Size(w, h * 0.45f),
    )
    if (!page) {
        // A faint grid to the horizon, rolling half a line a beat.
        val horizon = h * 0.7f
        val vanish = Offset(w / 2f, horizon)
        val line = Color.White.copy(alpha = 0.07f)
        for (k in -10..10) drawLine(line, vanish, Offset(w / 2f + k * w * 0.64f, h * 1.6f), density)
        val roll = (audio?.let { it.beatClock * 0.5f } ?: (t * (0.25f + 0.35f * e))) % 1f
        for (k in 0 until 12) {
            val z = (k + 1f - roll) / 12f
            val y = horizon + (h - horizon) * z * z
            drawLine(line.copy(alpha = line.alpha * (0.3f + 0.7f * z)), Offset(0f, y), Offset(w, y), density)
        }
        drawRect(Brush.verticalGradient(listOf(c.bg, Color.Transparent), startY = horizon - 2f, endY = horizon + (h - horizon) * 0.3f), Offset(0f, horizon - 2f), Size(w, (h - horizon) * 0.3f + 2f))
    }
    // Bubbles of plastic, rising.
    val swell = 1f + 0.08f * (audio?.pulse(t, 0.45f) ?: 0f)
    val rise = audio?.travel(1, 0.02f + 0.06f * audio.level) ?: 0f
    for (k in 0 until (if (page) 5 else 7)) {
        val r = minOf(w, h) * (0.05f + 0.09f * hash01(k, 91)) * swell
        val y = ((hash01(k, 92) - rise * (0.6f + 0.8f * hash01(k, 93))) % 1f + 1f) % 1f
        val p = Offset(w * hash01(k, 94), -r + (h + 2 * r) * y)
        drawBubble(p, r, Flavor.all[k % Flavor.all.size], if (page) 0.5f else 0.75f)
    }
    // Sparkles.
    val treble = audio?.treble ?: 0f
    for (k in 0 until 6) {
        val p = Offset(w * hash01(k, 71), h * (0.05f + 0.6f * hash01(k, 72)))
        val twinkle = if (audio != null) {
            val beat = kotlin.math.floor(audio.beatClock)
            val since = audio.beatClock - beat
            if ((beat.toInt() + k) % 3 == 0) (1f - since) * (1f - since) else 0.12f
        } else {
            0.5f + 0.5f * sin(t * (1.6f + hash01(k, 73)) + k * 2f)
        }
        val r = density * (5f + 7f * hash01(k, 74)) * (0.6f + 0.4f * twinkle + 0.5f * treble)
        drawPath(starPath(p, r, r * 0.22f, 4), Color.White.copy(alpha = (if (page) 0.22f else 0.4f) * (0.4f + 0.6f * twinkle)))
    }
}

/** A glossy bubble of [tint] plastic: see-through, a rim of colour, a highlight up on its left. */
internal fun DrawScope.drawBubble(center: Offset, r: Float, tint: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.05f * alpha), tint.copy(alpha = 0.35f * alpha)), center, r), r, center)
    drawCircle(tint.copy(alpha = 0.55f * alpha), r, center, style = Stroke(1.2f * density))
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f * alpha), Color.Transparent), startY = center.y - r * 0.8f, endY = center.y - r * 0.1f),
        Offset(center.x - r * 0.6f, center.y - r * 0.78f), Size(r * 0.9f, r * 0.55f),
    )
}

/**
 * 酸's progress, as a progress bar of the turn of the century: a dark capsule set into the
 * surface with a chrome rim, the part played a lime gel with white stripes rolling along it as the
 * music plays and a gloss over it, and a chrome bead where it is.
 */
internal fun DrawScope.drawStripeTrack(f: Float, phase: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val h = (12f + 3f * dragging) * density
    val top = (size.height - h) / 2f
    val x = size.width * f
    val radius = CornerRadius(h / 2f)
    drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(0f, top), Size(size.width, h), radius)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), startY = top, endY = top + h * 0.5f), Offset(0f, top), Size(size.width, h), radius)
    drawRoundRect(ChromeBrush, Offset(0f, top), Size(size.width, h), radius, style = Stroke(1f * density), alpha = 0.6f)
    val inset = 2f * density
    val fill = Path().apply { addRoundRect(RoundRect(inset, top + inset, (x - inset).coerceAtLeast(inset + 1f), top + h - inset, CornerRadius((h - inset * 2) / 2f))) }
    clipPath(fill) {
        drawRect(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(c.accent, Color.White, 0.35f), c.accent, androidx.compose.ui.graphics.lerp(c.accent, Color.Black, 0.15f)), startY = top, endY = top + h))
        val pitch = 10f * density
        var sx = -h + (phase * density * 12f) % pitch - pitch
        while (sx < x + h) {
            drawLine(Color.White.copy(alpha = 0.28f), Offset(sx, top + h), Offset(sx + h, top), 3.5f * density)
            sx += pitch
        }
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), startY = top, endY = top + h * 0.5f), Offset(0f, top), Size(x, h * 0.5f))
    }
    val bead = 8f * density
    drawCircle(Color.Black.copy(alpha = 0.35f), bead, Offset(x, size.height / 2f + 1.5f * density))
    drawCircle(Brush.verticalGradient(*ChromeStops, startY = size.height / 2f - bead, endY = size.height / 2f + bead), bead, Offset(x, size.height / 2f))
}

/** 酸's play key: a disc of lime gel in a chrome bezel, glossed, glowing a little. */
internal fun DrawScope.drawLimeDisc(style: LanyinStyle, glow: Float) {
    val c = style.colors
    val r = size.minDimension / 2f
    val halo = r * (1.35f + 0.2f * glow)
    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f + 0.2f * glow), Color.Transparent), center, halo), halo)
    // Lime gel in a chrome bezel, a gloss across its upper half.
    drawCircle(ChromeBrush, r + 1f * density)
    val face = r - 2.5f * density
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFE6FF7A), c.accent, Color(0xFF86B800)), center.y - face, center.y + face), face)
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.04f)), center.y - face * 0.92f, center.y - face * 0.05f),
        Offset(center.x - face * 0.68f, center.y - face * 0.92f), Size(face * 1.36f, face * 0.87f),
    )
}

/** A white starburst flashing out round 酸's disc on the beat. */
internal fun DrawScope.drawBeatBurst(style: LanyinStyle, beat: Float, turn: Float) {
    if (beat <= 0f) return
    val r = size.minDimension / 2f
    val out = r * (1.35f + 0.45f * (1f - beat))
    drawPath(starPath(center, out, out * 0.78f, 16, turn), Color.White.copy(alpha = beat * 0.7f), style = Stroke(1.5f * density))
}

/**
 * 酸's generated sleeves: a holographic disc, chrome blobs in sparkles, a glossy heart of
 * strawberry plastic, and a chrome sun over a checkerboard of grape and Bondi blue.
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
            // A heart of strawberry plastic, glossy, with sparkles.
            val heart = Path().apply {
                val s0 = w * 0.62f
                val cx = w / 2f
                val cy = h * 0.5f
                moveTo(cx, cy + 0.36f * s0)
                cubicTo(cx - 0.1f * s0, cy + 0.26f * s0, cx - 0.46f * s0, cy + 0.06f * s0, cx - 0.44f * s0, cy - 0.14f * s0)
                cubicTo(cx - 0.42f * s0, cy - 0.38f * s0, cx - 0.12f * s0, cy - 0.42f * s0, cx, cy - 0.2f * s0)
                cubicTo(cx + 0.12f * s0, cy - 0.42f * s0, cx + 0.42f * s0, cy - 0.38f * s0, cx + 0.44f * s0, cy - 0.14f * s0)
                cubicTo(cx + 0.46f * s0, cy + 0.06f * s0, cx + 0.1f * s0, cy + 0.26f * s0, cx, cy + 0.36f * s0)
            }
            drawPath(heart, Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(Flavor.Strawberry, Color.White, 0.25f), Flavor.Strawberry, androidx.compose.ui.graphics.lerp(Flavor.Strawberry, Color.Black, 0.3f))))
            clipPath(heart) { drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), startY = h * 0.3f, endY = h * 0.48f), Offset(w * 0.28f, h * 0.3f), Size(w * 0.44f, h * 0.18f)) }
            drawPath(heart, ChromeBrush, style = Stroke(w * 0.012f))
            for (k in 0 until 3) {
                val p = Offset(w * (0.18f + 0.64f * hash01(seed * 3 + k, 7)), h * (0.12f + 0.2f * k))
                drawPath(starPath(p, w * 0.045f, w * 0.011f, 4), Color.White)
            }
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
                    drawRect(if (r % 2 == 0) Flavor.Grape else Flavor.Bondi, Offset(minOf(x0, x1), y0), Size(abs(x1 - x0), y1 - y0))
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
