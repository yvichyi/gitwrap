package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.sin

/*
 * The landscape player's scenery for the themes that keep the shared stage: life in 墨's ink
 * painting, the evening going on in 暮's, 霓's palms and falling stars, 格's poster moving.
 */

/** Wings once a beat with the music (flapping freely without it), [phase] in beats. */
private fun wingBeat(audio: BackdropAudio?, t: Float, salt: Int): Float =
    sin(((audio?.beatClock ?: (t * 1.4f)) + hash01(salt, 9)) * PI.toFloat())

/**
 * 墨: a few birds crossing the sky above the far hills, wings beating with the music, and a lone
 * boat on the water at the hills' foot, rocking, its ripples spreading on each beat.
 */
internal fun DrawScope.drawInkWildlife(style: LanyinStyle, t: Float, audio: BackdropAudio?) {
    val ink = style.colors.content
    val w = size.width
    val h = size.height
    // The flock: slow, a loose line, wrapping round from the right.
    val travel = audio?.travel(3, 16f) ?: (t * 16f)
    val span = w * 1.5f
    for (k in 0 until 4) {
        val x = w * 1.15f - ((travel * density + k * 46f * density + hash01(k, 41) * 30f * density) % span)
        val y = h * (0.16f + 0.05f * k + 0.03f * hash01(k, 42)) + 4f * density * sin(t * 0.9f + k)
        val s = (5.5f + 2f * hash01(k, 43)) * density
        val flap = wingBeat(audio, t, k)
        val bird = Path().apply {
            moveTo(x - s, y - s * 0.45f * flap)
            quadraticTo(x - s * 0.45f, y - s * 0.1f, x, y)
            quadraticTo(x + s * 0.45f, y - s * 0.1f, x + s, y - s * 0.45f * flap)
        }
        drawPath(bird, ink.copy(alpha = 0.5f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
    }
    // The boat, on the water at the foot of the far hills, clear of the lyrics and the bars.
    val bob = 2.5f * density * sin(t * 1.1f)
    val at = Offset(w * 0.88f, h * 0.66f + bob)
    val hull = Path().apply {
        moveTo(at.x - 22f * density, at.y - 3f * density)
        quadraticTo(at.x, at.y + 6f * density, at.x + 24f * density, at.y - 4f * density)
        quadraticTo(at.x, at.y + 1f * density, at.x - 22f * density, at.y - 3f * density)
        close()
    }
    drawPath(hull, ink.copy(alpha = 0.55f))
    // The boatman and his pole.
    drawLine(ink.copy(alpha = 0.55f), at + Offset(4f * density, -1f * density), at + Offset(5f * density, -12f * density), 2.2f * density, StrokeCap.Round)
    drawCircle(ink.copy(alpha = 0.55f), 2.4f * density, at + Offset(5.2f * density, -14.5f * density))
    drawLine(ink.copy(alpha = 0.45f), at + Offset(-6f * density, -20f * density), at + Offset(14f * density, 8f * density), 1f * density)
    // Ripples from each recent beat.
    if (audio != null) {
        for (k in 0 until 3) {
            val n = audio.beatCount - 1 - k
            if (n < 0) break
            val age = t - audio.beats[n % BackdropAudio.BEATS]
            if (age < 0f || age > 1.8f) continue
            val p = age / 1.8f
            val rx = (26f + 40f * p) * density
            drawOval(ink.copy(alpha = 0.22f * (1f - p)), Offset(at.x - rx, at.y + 2f * density - rx * 0.12f), Size(rx * 2, rx * 0.24f), style = Stroke(1f * density))
        }
    }
}

/** 暮, over the song: gulls gliding across the sky, and the swell rolling in below the horizon. */
internal fun DrawScope.drawDuskEvening(style: LanyinStyle, t: Float, audio: BackdropAudio?, horizon: Float) {
    val c = style.colors
    val w = size.width
    // Gulls, gliding with an occasional beat of the wings.
    val travel = audio?.travel(3, 20f) ?: (t * 20f)
    for (k in 0 until 3) {
        val x = ((travel * density * (0.8f + 0.25f * k) + k * w * 0.4f) % (w * 1.3f)) - w * 0.15f
        val y = horizon * (0.22f + 0.12f * k) + 6f * density * sin(t * 0.7f + k * 2f)
        val s = (7f - k) * density
        val flap = 0.35f + 0.65f * wingBeat(audio, t, k + 7).coerceAtLeast(0f)
        val gull = Path().apply {
            moveTo(x - s, y - s * 0.3f * flap)
            quadraticTo(x - s * 0.5f, y - s * 0.75f * flap, x, y)
            quadraticTo(x + s * 0.5f, y - s * 0.75f * flap, x + s, y - s * 0.3f * flap)
        }
        drawPath(gull, (if (style.dark) Color(0xFF0B0E20) else c.content).copy(alpha = if (style.dark) 0.7f else 0.42f), style = Stroke(1.5f * density, cap = StrokeCap.Round))
    }
    // The swell: long low waves rolling in below the horizon.
    for (k in 0 until 4) {
        val y0 = horizon + (8f + k * 9f) * density
        val wave = Path()
        var x = 0f
        val shift = t * (18f + 6f * k) * density
        wave.moveTo(0f, y0)
        while (x <= w) {
            wave.lineTo(x, y0 + 2f * density * sin((x + shift) / (34f * density) + k))
            x += 8f * density
        }
        drawPath(wave, c.content.copy(alpha = 0.07f - k * 0.012f), style = Stroke(1.2f * density))
    }
}

/**
 * 格's poster in landscape, moving: the red disc swelling on the beat, the blue bar sliding along
 * the grid with the bar, a yellow square turning a quarter every four beats.
 */
internal fun DrawScope.drawGridPoster(style: LanyinStyle, t: Float, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val pulse = audio?.pulse(t, 0.35f) ?: 0f
    val beats = audio?.beatClock ?: 0f
    val disc = Offset(w * 0.9f, h * 0.24f)
    val r = h * 0.07f * (1f + 0.22f * pulse)
    drawCircle(c.outline, r, disc + Offset(4f * density, 4f * density))
    drawCircle(c.accent, r, disc)
    drawCircle(c.outline, r, disc, style = Stroke(2.5f * density))
    val bar = Size(w * 0.12f, h * 0.035f)
    val slide = w * 0.025f * sin(beats * PI.toFloat() / 2f)
    val barAt = Offset(w * 0.78f + slide, h * 0.36f)
    drawRect(c.outline, barAt + Offset(4f * density, 4f * density), bar)
    drawRect(c.accentAlt, barAt, bar)
    drawRect(c.outline, barAt, bar, style = Stroke(2.5f * density))
    // A quarter turn every four beats, eased so it snaps round and settles.
    val turns = kotlin.math.floor(beats / 4f)
    val f = ((beats / 4f) - turns).coerceIn(0f, 1f)
    val eased = if (f > 0.8f) ((f - 0.8f) / 0.2f).let { it * it * (3f - 2f * it) } else 0f
    val angle = (turns + eased) * 90f + 12f
    val sq = h * 0.09f
    // Half off the edge, as a poster crops its shapes.
    val at = Offset(w * 0.975f, h * 0.47f)
    rotate(angle, at) {
        translate(4f * density, 4f * density) { drawRect(c.outline, at - Offset(sq / 2f, sq / 2f), Size(sq, sq)) }
        drawRect(GridYellow, at - Offset(sq / 2f, sq / 2f), Size(sq, sq))
        drawRect(c.outline, at - Offset(sq / 2f, sq / 2f), Size(sq, sq), style = Stroke(2.5f * density))
    }
}
