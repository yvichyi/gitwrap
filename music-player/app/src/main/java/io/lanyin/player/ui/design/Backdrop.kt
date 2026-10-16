package io.lanyin.player.ui.design

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The theme's backdrop. [live] animates it (the player); pages use a still, quieter version.
 * [playing] raises its energy: a faster grid, a livelier sun. With a [feed] the player's backdrop
 * also listens: 墨 blooms ink on the beat, 暮's sun swells with the bass while clouds drift and
 * motes of light rise, 霓's horizon becomes a skyline of the spectrum, 影's light leaks flare and
 * 点's grid lights up in rings.
 */
@Composable
fun Backdrop(
    modifier: Modifier = Modifier,
    live: Boolean = false,
    playing: Boolean = false,
    trackNumber: Int? = null,
    feed: io.lanyin.player.core.audio.AudioFeed? = null,
) {
    val style = Lanyin.style
    // Paper, a case and a grid never move: no clock for them. 点's dots only move with the music.
    val animated = live && when (style.backdrop) {
        BackdropKind.Grid, BackdropKind.Device, BackdropKind.Paper, BackdropKind.Game -> false
        BackdropKind.Dot -> playing
        else -> true
    }
    val clock = rememberFrameClock(animated)
    val measurer = if (style.backdrop == BackdropKind.Grid || style.backdrop == BackdropKind.Film) androidx.compose.ui.text.rememberTextMeasurer() else null
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "energy")
    val audio = remember { BackdropAudio() }
    // A layer of its own, so redrawing the backdrop doesn't re-record what sits on it.
    Canvas(modifier.graphicsLayer()) {
        val t = if (animated) clock.floatValue else 0f
        val e = energy
        val page = !live
        val heard = if (feed != null && live) audio.also { it.read(feed, t, e) } else null
        when (style.backdrop) {
            BackdropKind.Ink -> drawInk(style, t, page, heard)
            BackdropKind.Neon -> drawNeon(style, t, e, page, heard)
            BackdropKind.Dusk -> drawDusk(style, t, e, page, heard)
            BackdropKind.Grid -> drawGridPaper(style, page, measurer, trackNumber)
            BackdropKind.Film -> drawFilm(style, t, e, page, heard, if (page) null else measurer, trackNumber)
            BackdropKind.Dot -> drawDotField(style, t, page, heard)
            BackdropKind.Device -> drawDeviceCase(style, page)
            BackdropKind.Paper -> drawNewsprint(style, page)
            BackdropKind.Game -> drawGameCase(style)
            BackdropKind.Acid -> drawAcidNight(style, t, e, page, heard)
        }
    }
}

/**
 * What the backdrop hears this frame: band levels (eased so shapes don't flicker), their bass,
 * middle and treble, and when the last few beats landed on the backdrop's clock.
 */
internal class BackdropAudio {
    val levels = FloatArray(32)
    private val raw = FloatArray(32)
    var bass = 0f
        private set
    var mid = 0f
        private set
    var treble = 0f
        private set
    var level = 0f
        private set

    /** Clock times of recent beats, newest at [beatCount] - 1 (mod the size). */
    val beats = FloatArray(BEATS) { -100f }
    var beatCount = 0
        private set
    private var lastSince = 99f

    fun read(feed: io.lanyin.player.core.audio.AudioFeed, t: Float, energy: Float) {
        if (!feed.levels(raw)) raw.fill(0f)
        for (i in levels.indices) {
            val target = raw[i] * energy
            // Up fast, down slowly.
            levels[i] += (target - levels[i]) * if (target > levels[i]) 0.55f else 0.12f
        }
        bass = levels.average(0, 6)
        mid = levels.average(6, 18)
        treble = levels.average(18, 32)
        level = levels.average(0, 32)
        val since = feed.sinceBeat()
        if (energy > 0.05f && since < lastSince - 0.02f && since < 0.3f) {
            beats[beatCount % BEATS] = t - since
            beatCount++
        }
        lastSince = since
    }

    /** Seconds since the most recent beat. */
    fun sinceBeat(t: Float): Float = if (beatCount == 0) 99f else t - beats[(beatCount - 1) % BEATS]

    private fun FloatArray.average(from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) sum += this[i]
        return sum / (to - from)
    }

    companion object {
        const val BEATS = 8
    }
}

private fun DrawScope.glowBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

private fun DrawScope.drawInk(style: LanyinStyle, t: Float, page: Boolean, audio: BackdropAudio? = null) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val swell = 1f + 0.03f * sin(t * 0.4f) + 0.08f * (audio?.bass ?: 0f)
    glowBlob(c.content.copy(alpha = if (style.dark) 0.05f else 0.045f), Offset(w * 0.85f, h * 0.12f), w * 0.8f * swell)
    if (audio != null) drawInkBlooms(style, t, audio)
    if (page) return
    if (w <= h) {
        // Portrait: two wisps of mist drift across behind the lyrics, quicker while the music plays.
        drawMist(style, t, h * 0.6f, w * 0.9f, h * 0.14f, speed = 5f + 9f * (audio?.level ?: 0f))
        return
    }

    // 远山: three ridges fading into mist across the right of the stage, nearest darkest.
    val ink = c.content
    for (layer in 0..2) {
        val base = h * (0.62f + layer * 0.1f)
        val amp = h * (0.2f - layer * 0.045f)
        val from = w * (0.4f + 0.06f * layer)
        val ridge = Path().apply {
            moveTo(from, h)
            var x = from
            while (x <= w + 8f) {
                val u = (x - from) / (w - from)
                // Peaks from a few slow sines, eased in from the left so the range starts low.
                val peaks = 0.55f + 0.3f * sin(u * 7.1f + layer * 2.1f) + 0.15f * sin(u * 17.3f + layer)
                // Rise out of the ground at the left rather than starting on a cliff.
                val rise = (u / 0.3f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                lineTo(x, h - (h - (base - amp * peaks)) * rise)
                x += 6f
            }
            lineTo(w, h)
            close()
        }
        val alpha = (if (style.dark) 0.07f else 0.06f) * (1f + layer * 0.5f)
        drawPath(ridge, Brush.verticalGradient(listOf(ink.copy(alpha = alpha), ink.copy(alpha = 0f)), startY = base - amp, endY = h))
    }
    // A band of mist drifting slowly across the foot of the hills.
    val drift = (t * 6f * density) % w
    for (k in 0..1) {
        val x = -w * 0.3f + drift + k * w
        drawOval(
            Brush.radialGradient(listOf(c.bg.copy(alpha = 0.7f), c.bg.copy(alpha = 0f)), Offset(x + w * 0.3f, h * 0.8f), w * 0.3f),
            Offset(x, h * 0.72f), Size(w * 0.6f, h * 0.16f),
        )
    }
}

/**
 * 墨晕: each beat drops ink somewhere on the paper. It spreads for a few seconds and fades,
 * pooling darker at its rim as watercolour does, from a few overlapping soft discs so the edge
 * isn't a perfect circle.
 */
private fun DrawScope.drawInkBlooms(style: LanyinStyle, t: Float, audio: BackdropAudio) {
    val ink = style.colors.content
    val side = size.minDimension
    val life = 2.6f
    for (k in 0 until BackdropAudio.BEATS) {
        val n = audio.beatCount - 1 - k
        if (n < 0) break
        val age = t - audio.beats[n % BackdropAudio.BEATS]
        if (age < 0f || age > life) continue
        val p = age / life
        val grow = 1f - (1f - p).pow(3)
        val fade = (1f - p).pow(1.4f)
        // Away from the words: around the artwork (the upper part in portrait, the left in landscape).
        val center = if (size.width > size.height) {
            Offset(size.width * (0.04f + 0.36f * hash01(n, 31)), size.height * (0.12f + 0.7f * hash01(n, 32)))
        } else {
            Offset(size.width * (0.08f + 0.84f * hash01(n, 31)), size.height * (0.07f + 0.4f * hash01(n, 32)))
        }
        val r = side * (0.06f + 0.22f * hash01(n, 33)) * (0.35f + 0.65f * grow)
        val a = (if (style.dark) 0.075f else 0.06f) * fade
        for (j in 0..2) {
            val wobble = Offset(cos(j * 2.1f + n) * r * 0.18f, sin(j * 2.1f + n) * r * 0.18f)
            drawCircle(
                Brush.radialGradient(0f to ink.copy(alpha = a * 0.55f), 0.8f to ink.copy(alpha = a), 1f to ink.copy(alpha = 0f), center = center + wobble, radius = r),
                r, center + wobble,
            )
        }
        drawCircle(ink.copy(alpha = a * 0.6f), r * 0.96f, center, style = Stroke(1.dp.toPx() * (1f + 2f * (1f - p))))
    }
}

/** Soft bands of mist crossing at height [y], [span] wide, wrapping round. */
private fun DrawScope.drawMist(style: LanyinStyle, t: Float, y: Float, span: Float, thickness: Float, speed: Float) {
    val c = style.colors
    val w = size.width
    for (k in 0..1) {
        val x = ((t * speed * density + k * (w + span) / 2f) % (w + span)) - span
        drawOval(
            Brush.radialGradient(
                listOf(c.content.copy(alpha = if (style.dark) 0.05f else 0.04f), c.content.copy(alpha = 0f)),
                Offset(x + span / 2f, y + k * thickness * 0.8f), span / 2f,
            ),
            Offset(x, y - thickness / 2f + k * thickness * 0.8f), Size(span, thickness),
        )
    }
}

/**
 * One brush stroke round a circle, as a single filled outline so overlapping pieces don't
 * leave seams: it swells in the middle, tapers at both ends and leaves a gap.
 */
internal fun ensoPath(center: Offset, radius: Float, thickness: Float, sweepDegrees: Float = 310f): Path {
    val steps = 72
    val outer = ArrayList<Offset>(steps + 1)
    val inner = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val angle = Math.toRadians((f * sweepDegrees).toDouble())
        // Heavier where the brush lands, thinning as it lifts.
        // sin(π) is a hair below zero in floats, and a fractional power of that is NaN.
        val swell = sin(PI.toFloat() * f.pow(0.8f)).coerceAtLeast(0f).pow(0.7f)
        val half = thickness * (0.12f + 0.88f * swell) / 2f
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        outer += Offset(center.x + (radius + half) * dx, center.y + (radius + half) * dy)
        inner += Offset(center.x + (radius - half) * dx, center.y + (radius - half) * dy)
    }
    return Path().apply {
        moveTo(outer[0].x, outer[0].y)
        outer.drop(1).forEach { lineTo(it.x, it.y) }
        inner.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
}

/** Cheap deterministic noise in [0, 1). */
internal fun hash01(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawNeon(style: LanyinStyle, t: Float, e: Float, page: Boolean, audio: BackdropAudio? = null) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val horizon = h * when {
        page -> 0.8f
        w > h -> 0.82f
        else -> 0.5f
    }
    // Sky glow.
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.75f to c.bgDeep, 1f to c.accentAlt.copy(alpha = 0.35f), startY = 0f, endY = horizon))
    if (!page && w > h) {
        val sun = Offset(w * 0.8f, horizon)
        val r = h * 0.26f
        glowBlob(c.accentAlt.copy(alpha = 0.25f), sun, r * 1.8f)
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(sun, r)) }
        val fill = Brush.verticalGradient(
            // Blue at the crown sinking into violet at the horizon: a cold sun, in the theme's own hues.
            listOf(c.accent.copy(alpha = 0.55f), c.accentAlt.copy(alpha = 0.5f)),
            startY = sun.y - r, endY = sun.y,
        )
        clipPath(disc) {
            // Solid crown, then slices that thin toward the horizon.
            var top = sun.y - r
            var gap = r * 0.035f
            var slice = r * 0.5f
            while (top < horizon) {
                drawRect(fill, Offset(sun.x - r, top), Size(r * 2, minOf(slice, horizon - top)))
                top += slice + gap
                slice *= 0.62f
                gap *= 1.15f
            }
        }
    }
    if (audio != null) drawSkyline(style, horizon, audio)
    // The horizon flares on each beat.
    val flare = audio?.let { (1f - it.sinceBeat(t) / 0.35f).coerceIn(0f, 1f) } ?: 0f
    if (flare > 0f) drawLine(c.accent.copy(alpha = 0.35f * flare), Offset(0f, horizon), Offset(w, horizon), strokeWidth = (3f + 6f * flare) * density)
    drawLine(lerp(c.accentAlt, c.accent, flare).copy(alpha = if (page) 0.45f else 0.8f + 0.2f * flare), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.5f * density)
    // Perspective grid floor, scrolling toward the viewer.
    val vanishing = Offset(w / 2f, horizon)
    for (i in -14..14) {
        val bottomX = w / 2f + i * w * 0.14f
        drawLine(c.accent.copy(alpha = 0.22f), vanishing.copy(x = w / 2f + i * w * 0.012f), Offset(bottomX, h), strokeWidth = 1f * density)
    }
    val scroll = (t * (0.12f + 0.5f * e + 0.6f * (audio?.bass ?: 0f))) % 1f
    for (j in 0 until 14) {
        val z = (j + scroll) / 14f
        val y = horizon + (h - horizon) * z.pow(2.2f)
        drawLine(c.accent.copy(alpha = 0.06f + 0.34f * z), Offset(0f, y), Offset(w, y), strokeWidth = (0.6f + 1.4f * z) * density)
    }
    // Each beat sends a pulse of light down the floor toward the viewer.
    if (audio != null) {
        for (k in 0 until 3) {
            val n = audio.beatCount - 1 - k
            if (n < 0) break
            val age = t - audio.beats[n % BackdropAudio.BEATS]
            if (age < 0f || age > 0.9f) continue
            val z = age / 0.9f
            val y = horizon + (h - horizon) * z.pow(2.2f)
            val band = (h - horizon) * 0.05f * (0.3f + z)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.28f * (1f - z)), Color.Transparent), startY = y - band, endY = y + band),
                Offset(0f, y - band), Size(w, band * 2),
            )
        }
    }
    // Floor fade so content stays legible.
    drawRect(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.2f), c.bg.copy(alpha = 0.85f)), startY = horizon, endY = h), topLeft = Offset(0f, horizon))
    // A few stars and the CRT scanlines.
    for (i in 0 until 30) {
        val twinkle = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (1f + 2f * hash01(i, 11)) + i))
        drawCircle(c.content.copy(alpha = 0.5f * twinkle * hash01(i, 12)), radius = density * (0.6f + hash01(i, 13)), center = Offset(hash01(i, 14) * w, hash01(i, 15) * horizon * 0.85f))
    }
    val gap = 3f * density
    var y = 0f
    while (y < h) {
        drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += gap
    }
}

/**
 * The spectrum as a city on the horizon: bars mirrored from the bass in the middle out to the
 * treble at both edges, glowing blue into violet, with a faint reflection on the floor.
 */
private fun DrawScope.drawSkyline(style: LanyinStyle, horizon: Float, audio: BackdropAudio) {
    val c = style.colors
    val w = size.width
    val n = 44
    val slot = w / n
    val bar = slot * 0.56f
    // Landscape keeps the city low, under the lyrics' footer.
    val reach = size.height * if (w > size.height) 0.042f else 0.13f
    for (i in 0 until n) {
        val u = kotlin.math.abs(i - (n - 1) / 2f) / (n / 2f)
        val band = (u * (audio.levels.size - 1)).toInt().coerceIn(0, audio.levels.size - 1)
        val v = audio.levels[band]
        val top = horizon - (1.5f * density + v * reach)
        val color = lerp(c.accent, c.accentAlt, v)
        val x = i * slot + (slot - bar) / 2f
        drawRect(color.copy(alpha = 0.16f), Offset(x - bar * 0.4f, top - 2f * density), Size(bar * 1.8f, horizon - top + 2f * density))
        drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f)), startY = top, endY = horizon), Offset(x, top), Size(bar, horizon - top))
        // Reflection.
        drawRect(color.copy(alpha = 0.12f), Offset(x, horizon), Size(bar, (horizon - top) * 0.45f))
    }
}

/**
 * 暮: an evening sky over warm paper. A wash of peach and rose (plum at night) from the top, a
 * low sun with a soft glow, and fine film grain. On pages the sun is a glow in the corner; on the
 * player it sits low, beside the artwork rather than behind the lyrics, and breathes with the music.
 */
private fun DrawScope.drawDusk(style: LanyinStyle, t: Float, e: Float, page: Boolean, audio: BackdropAudio? = null) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val dark = style.dark
    drawRect(c.bg)
    // The sky: rose high up, peach lower, fading into the paper.
    val skyEnd = h * if (page) 0.42f else 0.7f
    val rose = lerp(c.accentAlt, c.bg, if (dark) 0.55f else 0.72f)
    drawRect(Brush.verticalGradient(0f to rose, 0.45f to c.bgDeep, 1f to c.bg, startY = 0f, endY = skyEnd), size = Size(w, skyEnd))
    val landscape = w > h
    val radius = when {
        page -> w * 0.2f
        landscape -> h * 0.2f
        else -> w * 0.2f
    }
    val breathe = 1f + 0.03f * e * sin(t * 1.3f) + 0.09f * (audio?.bass ?: 0f)
    val center = when {
        page -> Offset(w * 0.86f, h * 0.06f)
        landscape -> Offset(w * 0.9f, h * 0.62f)
        else -> Offset(w * 0.82f, h * 0.2f)
    }
    val sunCore = if (dark) lerp(c.accent, Color(0xFFFFC08A), 0.25f) else lerp(c.accent, Color(0xFFFFD2A6), 0.45f)
    // Glow, then the disc with a soft edge; at night an ember sunk into the sky.
    glowBlob(sunCore.copy(alpha = if (dark) 0.22f else 0.3f), center, radius * 3.2f * breathe)
    drawCircle(
        Brush.radialGradient(
            0f to sunCore.copy(alpha = if (dark) 0.75f else 0.9f),
            0.82f to sunCore.copy(alpha = if (dark) 0.6f else 0.8f),
            1f to sunCore.copy(alpha = 0f),
            center = center, radius = radius * breathe,
        ),
        radius * breathe, center,
    )
    // Clouds pass in front of the sun.
    if (!page) drawClouds(style, t, skyEnd, e)
    if (!page) {
        // A calm sea under it: a few thin glints, drifting.
        val horizon = center.y + radius * 1.25f
        for (k in 0 until 5) {
            val y = horizon + k * radius * 0.16f
            val half = radius * (0.9f - k * 0.14f)
            val drift = sin(t * 0.6f + k) * radius * 0.06f
            val glint = 0.6f + 0.4f * e + 0.8f * (audio?.treble ?: 0f)
            drawLine(sunCore.copy(alpha = ((0.35f - k * 0.06f) * glint).coerceAtMost(1f)), Offset(center.x - half + drift, y), Offset(center.x + half + drift, y), radius * 0.035f, StrokeCap.Round)
        }
    }
    if (!page) drawMotes(style, t, sunCore, audio)
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (dark) 0.08f else 0.06f)
}

/** Three soft clouds crossing the sky, nearer ones larger and quicker; a little faster while playing. */
private fun DrawScope.drawClouds(style: LanyinStyle, t: Float, skyEnd: Float, e: Float) {
    val c = style.colors
    val w = size.width
    val tint = if (style.dark) lerp(c.accentAlt, c.bg, 0.35f) else lerp(Color.White, c.accent, 0.12f)
    for (k in 0 until 3) {
        val scale = 0.7f + 0.3f * k
        val span = w * 0.42f * scale
        val speed = (4f + 3f * k) * (0.6f + 0.6f * e) * density
        val x = w + span - ((t * speed + k * w * 0.55f) % (w + span * 2))
        val y = skyEnd * (0.18f + 0.22f * k)
        val alpha = (if (style.dark) 0.22f else 0.5f) * (0.6f + 0.2f * k)
        for (puff in 0 until 4) {
            val px = x + (puff - 1.5f) * span * 0.26f
            val r = span * (0.2f + 0.08f * ((puff + k) % 3))
            val py = y - r * 0.25f * (if (puff == 1 || puff == 2) 1f else 0f)
            drawOval(
                Brush.radialGradient(listOf(tint.copy(alpha = alpha), tint.copy(alpha = 0f)), Offset(px, py), r),
                Offset(px - r, py - r * 0.55f), Size(r * 2, r * 1.1f),
            )
        }
    }
}

/** Motes of warm light rising and swaying, brightening on each beat. */
private fun DrawScope.drawMotes(style: LanyinStyle, t: Float, light: Color, audio: BackdropAudio?) {
    val w = size.width
    val h = size.height
    val flash = audio?.let { (1f - it.sinceBeat(t) / 0.4f).coerceIn(0f, 1f) } ?: 0f
    val lift = 1f + 1.5f * (audio?.level ?: 0f)
    for (i in 0 until 26) {
        val speed = 0.018f + 0.03f * hash01(i, 41)
        val rise = ((t * speed * lift + hash01(i, 42)) % 1f)
        val y = h * (1.02f - rise * 0.95f)
        val x = w * hash01(i, 43) + sin(t * (0.4f + hash01(i, 44)) + i) * 14f * density
        val twinkle = 0.5f + 0.5f * sin(t * (1.5f + 2f * hash01(i, 45)) + i * 1.7f)
        // Fade in from the bottom, out near the top.
        val life = (rise / 0.15f).coerceAtMost(1f) * ((1f - rise) / 0.3f).coerceAtMost(1f)
        val a = (0.18f + 0.3f * twinkle + 0.4f * flash) * life * (if (style.dark) 1f else 0.8f)
        val r = density * (1.2f + 2.2f * hash01(i, 46))
        drawCircle(Brush.radialGradient(listOf(light.copy(alpha = a), light.copy(alpha = 0f)), Offset(x, y), r * 4f), r * 4f, Offset(x, y))
        drawCircle(light.copy(alpha = (a * 1.4f).coerceAtMost(1f)), r * 0.6f, Offset(x, y))
    }
}

/** A small tile of film grain, generated once. */
internal val grain: ImageBitmap by lazy {
    val side = 96
    val pixels = IntArray(side * side) { i ->
        val v = (hash01(i, 77) * 255).toInt()
        val a = (hash01(i, 78) * 160).toInt()
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}
