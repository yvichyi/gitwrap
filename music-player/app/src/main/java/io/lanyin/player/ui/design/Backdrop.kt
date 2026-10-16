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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
 * the glints on the sea shimmer, 霓's horizon becomes a skyline of the spectrum, 影's light leaks flare and
 * 点's grid lights up in rings.
 */
@Composable
fun Backdrop(
    modifier: Modifier = Modifier,
    live: Boolean = false,
    playing: Boolean = false,
    trackNumber: Int? = null,
    feed: io.lanyin.player.core.audio.AudioFeed? = null,
    /** How far through the song, 0..1; 暮's sun sets over it. */
    progress: () -> Float = { 0f },
) {
    val style = Lanyin.style
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Paper, a case and a grid never move: no clock for them (格's poster does, in landscape,
    // while the music plays). 点's dots only move with the music.
    val animated = live && when (style.backdrop) {
        BackdropKind.Device, BackdropKind.Paper, BackdropKind.Game -> false
        BackdropKind.Grid -> playing && landscape
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
        val wide = size.width > size.height
        when (style.backdrop) {
            BackdropKind.Ink -> {
                drawInk(style, t, page, heard)
                if (!page && wide) drawInkWildlife(style, t, heard)
            }
            BackdropKind.Neon -> if (page) drawNightWall(style) else drawNightStreet(style, t, heard)
            BackdropKind.Dusk -> if (page) {
                drawDuskPaper(style)
            } else {
                // Portrait: the sea under the photo, the sun behind its edge. Landscape: the sea
                // under the stage, the sun at the far end.
                val horizon = if (wide) 0.84f else 0.5f
                drawDuskSky(style, t, e, heard, progress(), horizon, sunAt = if (wide) 0.9f else 0.86f, sunSize = if (wide) 0.2f else 0.15f)
                drawDuskEvening(style, t, heard, size.height * horizon)
            }
            BackdropKind.Grid -> {
                drawGridPaper(style, page, measurer, trackNumber)
                if (!page && wide && trackNumber != null) drawGridPoster(style, t, heard)
            }
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
 * middle and treble, when the last few beats landed on the backdrop's clock, and a count of beats
 * that runs smoothly between them, for motion that keeps time with the music.
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

    /** Seconds from beat to beat, once the music has settled on a tempo; 0 before. */
    var period = 0f
        private set

    /**
     * Beats as they pass, counted smoothly: once there is a tempo it runs at it, and is drawn
     * toward whole numbers on the beats, so a grid that moves one line per beat crosses on it.
     * Before that it runs at an easy pace; it stops when the music does.
     */
    var beatClock = 0f
        private set
    private var pull = 0f
    private var lastT = Float.NaN
    private var dt = 0f
    private val travelled = FloatArray(TRAVELS)

    fun read(feed: io.lanyin.player.core.audio.AudioFeed, t: Float, energy: Float) {
        dt = if (lastT.isNaN() || t < lastT) 0f else minOf(t - lastT, 0.1f)
        lastT = t
        period = feed.beatPeriod()
        val rate = if (period > 0f) 1f / period else FREE_BEATS_PER_SECOND
        val advance = dt * rate * energy
        // Ease out the error found at the last beat, never so hard the motion runs backward, and
        // only while it moves: stopped, the clock stays stopped.
        val correction = if (advance > 0f) (pull * minOf(1f, dt * 5f)).coerceAtLeast(-0.6f * advance) else 0f
        pull -= correction
        beatClock += advance + correction
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
            if (period > 0f) {
                // Back to where the clock stood on the beat, at the pace it has been running.
                val at = beatClock - since * rate * energy
                pull = kotlin.math.round(at) - at
            }
        }
        lastSince = since
    }

    /** Seconds since the most recent beat. */
    fun sinceBeat(t: Float): Float = if (beatCount == 0) 99f else t - beats[(beatCount - 1) % BEATS]

    /** How hard the last beat still rings, 1 to 0 over [seconds] (sooner at a quick tempo). */
    fun pulse(t: Float, seconds: Float): Float = io.lanyin.player.core.audio.pulseAfter(sinceBeat(t), period, seconds)

    /**
     * How far a motion in [slot] running at [speed] a second has gone. The speed follows the music
     * from frame to frame, so the distance is added up as it goes: worked out as time × speed, it
     * would leap whenever the speed changed, and the further into the song the bigger the leap.
     */
    fun travel(slot: Int, speed: Float): Float {
        travelled[slot] += dt * speed
        return travelled[slot]
    }

    private fun FloatArray.average(from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) sum += this[i]
        return sum / (to - from)
    }

    companion object {
        const val BEATS = 8
        private const val TRAVELS = 4
        private const val FREE_BEATS_PER_SECOND = 1.6f
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
        drawMist(style, h * 0.6f, w * 0.9f, h * 0.14f, drift = audio?.travel(0, 5f + 9f * audio.level) ?: (t * 5f))
        return
    }

    // 远山: three ridges fading into mist across the right of the stage, nearest darkest.
    val ink = c.content
    for (layer in 0..2) {
        val base = h * (0.62f + layer * 0.1f)
        val amp = h * (0.2f - layer * 0.045f)
        val alpha = (if (style.dark) 0.07f else 0.06f) * (1f + layer * 0.5f)
        drawPath(InkRidges.at(size, layer), Brush.verticalGradient(listOf(ink.copy(alpha = alpha), ink.copy(alpha = 0f)), startY = base - amp, endY = h))
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

/** 墨's three ridges, nearest last. They stand still, so they're traced once for a size, not every frame. */
private object InkRidges {
    private var traced = Size.Zero
    private val ridges = arrayOfNulls<Path>(3)

    fun at(size: Size, layer: Int): Path {
        if (size != traced) {
            traced = size
            for (i in ridges.indices) ridges[i] = trace(size.width, size.height, i)
        }
        return ridges[layer]!!
    }

    private fun trace(w: Float, h: Float, layer: Int) = Path().apply {
        val base = h * (0.62f + layer * 0.1f)
        val amp = h * (0.2f - layer * 0.045f)
        val from = w * (0.4f + 0.06f * layer)
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

/** Soft bands of mist crossing at height [y], [span] wide, [drift] dp along, wrapping round. */
private fun DrawScope.drawMist(style: LanyinStyle, y: Float, span: Float, thickness: Float, drift: Float) {
    val c = style.colors
    val w = size.width
    for (k in 0..1) {
        val x = ((drift * density + k * (w + span) / 2f) % (w + span)) - span
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
