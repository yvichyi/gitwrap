package io.lanyin.player.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** One light of 流's backdrop: where it rests, how far and fast it wanders, how big it is. */
private class Light(val x: Float, val y: Float, val ax: Float, val ay: Float, val speed: Float, val phase: Float, val size: Float)

private val lights = listOf(
    Light(0.2f, 0.18f, 0.22f, 0.12f, 1.0f, 0.0f, 0.62f),
    Light(0.85f, 0.3f, 0.18f, 0.2f, 0.8f, 1.7f, 0.58f),
    Light(0.35f, 0.72f, 0.25f, 0.15f, 0.7f, 3.1f, 0.66f),
    Light(0.9f, 0.85f, 0.15f, 0.18f, 1.2f, 4.4f, 0.5f),
    Light(0.55f, 0.45f, 0.3f, 0.25f, 0.55f, 5.6f, 0.45f),
)

/** The five colours 流 flows with, from the two accents (the cover's, when it gives them). */
internal fun LanyinStyle.flowPalette(): List<Color> {
    val c = colors
    return listOf(
        c.accent,
        c.accentAlt,
        lerp(c.accent, c.accentAlt, 0.5f),
        lerp(c.accent, Color.Black, 0.5f),
        lerp(c.accentAlt, Color(0xFFFFC28A), 0.35f),
    )
}

/**
 * 流: large soft lights in the accent colours wandering slowly over a dark ground, the way Apple
 * Music and 椒盐音乐's 流光 tint the page with the cover. They swell with the bass and drift a
 * little faster while music plays; a dark veil and grain keep text readable and the gradients
 * from banding. Pages get the same lights, still and dimmer.
 */
internal fun DrawScope.drawFlow(style: LanyinStyle, t: Float, e: Float, page: Boolean, audio: BackdropAudio?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val span = max(w, h)
    drawRect(c.bg)
    val palette = style.flowPalette()
    val pace = t * (0.07f + 0.07f * e)
    val swell = 1f + 0.14f * (audio?.bass ?: 0f)
    val flare = audio?.let { (1f - it.sinceBeat(t) / 0.5f).coerceIn(0f, 1f) } ?: 0f
    lights.forEachIndexed { i, l ->
        val center = Offset(
            w * (l.x + l.ax * sin(pace * l.speed + l.phase)),
            h * (l.y + l.ay * cos(pace * l.speed * 0.8f + l.phase * 1.3f)),
        )
        val r = span * l.size * (if (i == 0) swell else 1f + (swell - 1f) * 0.5f)
        val a = (if (page) 0.55f else 0.78f) + 0.12f * flare
        drawCircle(
            Brush.radialGradient(0f to palette[i].copy(alpha = a), 0.55f to palette[i].copy(alpha = a * 0.45f), 1f to palette[i].copy(alpha = 0f), center = center, radius = r),
            r, center,
        )
    }
    // A veil for legibility, heavier toward the bottom where the controls are.
    drawRect(Color.Black.copy(alpha = if (page) 0.46f else 0.2f))
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = if (page) 0.3f else 0.42f)))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.05f)
}

/** 流's generated sleeves: the same flowing lights, composed four ways. */
internal fun DrawScope.flowCover(style: LanyinStyle, seed: Int) {
    val w = size.width
    val h = size.height
    val palette = style.flowPalette()
    drawRect(lerp(style.colors.bgDeep, palette[seed % palette.size], 0.25f))
    for (k in 0 until 4) {
        val color = palette[(seed + k * 2) % palette.size]
        val center = Offset(w * hash01(seed * 7 + k, 51), h * hash01(seed * 7 + k, 52))
        val r = w * (0.45f + 0.35f * hash01(seed * 7 + k, 53))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0f)), center, r), r, center)
    }
    when (seed % 4) {
        0 -> drawCircle(Color.White.copy(alpha = 0.85f), w * 0.07f, Offset(w * 0.5f, h * 0.5f))
        1 -> for (i in 0 until 5) drawLine(Color.White.copy(alpha = 0.7f), Offset(w * (0.3f + i * 0.1f), h * (0.62f - 0.18f * hash01(seed + i, 54))), Offset(w * (0.3f + i * 0.1f), h * 0.62f), w * 0.035f, androidx.compose.ui.graphics.StrokeCap.Round)
        2 -> drawCircle(Color.White.copy(alpha = 0.7f), w * 0.22f, Offset(w * 0.5f, h * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.02f))
        else -> Unit
    }
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.06f)
}

/** Frosted glass, as far as a page can fake it without a live blur: a pale veil and a bright rim. */
internal fun glassFill(tone: SurfaceTone, base: Color): Color = when (tone) {
    SurfaceTone.Card -> Color.White.copy(alpha = 0.09f)
    SurfaceTone.Flat -> Color.White.copy(alpha = 0.06f)
    // Bars and sheets sit over moving colour and must stay readable.
    SurfaceTone.Chrome -> lerp(base, Color.Black, 0.25f).copy(alpha = 0.9f)
    SurfaceTone.Accent -> Color.White.copy(alpha = 0.9f)
}

/** A thick rounded track, no handle, that thickens while dragged (after Apple Music and YouTube Music). */
internal fun DrawScope.drawBarTrack(f: Float, dragging: Float, ink: Color) {
    val h = (6f + 6f * dragging) * density
    val top = (size.height - h) / 2f
    val corner = androidx.compose.ui.geometry.CornerRadius(h / 2)
    drawRoundRect(ink.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), corner)
    drawRoundRect(ink.copy(alpha = 0.9f), Offset(0f, top), Size((size.width * f).coerceAtLeast(h), h), corner)
}
