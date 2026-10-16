package io.lanyin.player.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.LocalLanyinStyle
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 机: the case. Warm grey plastic, lit a little from above, with the fine texture moulded
 * plastic has; on the player two screws hold the bottom corners down.
 */
internal fun DrawScope.drawDeviceCase(style: LanyinStyle, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(lerp(c.bg, Color.White, if (style.dark) 0.03f else 0.16f), c.bg, c.bgDeep)))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (style.dark) 0.06f else 0.05f)
    if (page) return
    val inset = 11.dp.toPx()
    drawScrew(style, Offset(inset, h - inset), 20f)
    drawScrew(style, Offset(w - inset, h - inset), -35f)
}

private fun DrawScope.drawScrew(style: LanyinStyle, at: Offset, slot: Float) {
    val c = style.colors
    val r = 3.5.dp.toPx()
    drawCircle(Color.White.copy(alpha = if (style.dark) 0.05f else 0.5f), r, at + Offset(0f, 0.8f * density))
    drawCircle(lerp(c.bgDeep, Color.Black, 0.08f), r, at)
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), at, r), r, at)
    rotate(slot, at) { drawLine(Color.Black.copy(alpha = 0.35f), at - Offset(r * 0.7f, 0f), at + Offset(r * 0.7f, 0f), 1.dp.toPx()) }
}

/** A key's face from top to bottom, the lip it sits on and its rim. */
internal class KeyColors(val top: Color, val bottom: Color, val lip: Color, val rim: Color)

internal fun LanyinStyle.keyColors(accent: Boolean = false): KeyColors {
    val c = colors
    return when {
        accent -> KeyColors(lerp(c.accent, Color.White, 0.16f), c.accent, lerp(c.accent, Color.Black, 0.28f), lerp(c.accent, Color.Black, 0.12f))
        dark -> KeyColors(Color(0xFF46474C), Color(0xFF38393E), Color(0xFF1B1C1F), Color(0xFF4F5055))
        else -> KeyColors(Color(0xFFF9F7F2), Color(0xFFE8E5DD), Color(0xFFC6C2B8), Color(0xFFD3CFC6))
    }
}

/**
 * A key cap in [shape]: a soft shadow, the lip it rests on and its face lit from above with a
 * bright top edge. [press] (0..1) sinks the face onto the lip; content should move with it by
 * [keyTravel].
 */
internal fun DrawScope.drawKeycap(shape: Shape, keys: KeyColors, press: Float = 0f, shadow: Boolean = true) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val travel = keyTravel.toPx() * press
    if (shadow) {
        for ((dy, a) in listOf(6f to 0.04f, 4f to 0.06f, 2.5f to 0.08f)) {
            translate(0f, dy * density) { drawOutline(outline, Color.Black.copy(alpha = a * (1f - 0.6f * press))) }
        }
    }
    translate(0f, keyTravel.toPx()) { drawOutline(outline, keys.lip) }
    translate(0f, travel) {
        drawOutline(outline, Brush.verticalGradient(listOf(keys.top, keys.bottom)))
        drawOutline(outline, keys.rim, style = Stroke(1.dp.toPx()))
        // Light catching the top edge, fading down the sides.
        drawOutline(
            outline,
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.6f), 0.3f to Color.White.copy(alpha = 0f)),
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

/** How far a key travels when pressed. */
internal val keyTravel: Dp = 2.5.dp

/** A key cap behind this element, sinking while [press] reads near 1. */
fun Modifier.keycap(shape: Shape, style: LanyinStyle, accent: Boolean = false, press: () -> Float = { 0f }): Modifier = drawBehind {
    drawKeycap(shape, style.keyColors(accent), press())
}

/**
 * 机's progress: a fader. A recessed groove, the part played filled in ink, and a ribbed cap
 * that slides along it, its grip line orange while held.
 */
internal fun DrawScope.drawFaderTrack(f: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val cy = size.height / 2f
    val x = size.width * f
    val groove = 6.dp.toPx()
    val top = cy - groove / 2f
    val round = CornerRadius(groove / 2f)
    drawRoundRect(if (style.dark) Color.Black.copy(alpha = 0.5f) else Color(0xFFCAC6BD), Offset(0f, top), Size(size.width, groove), round)
    drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, groove * 0.45f), round)
    if (x > 0f) drawRoundRect(c.content, Offset(0f, top + 1.dp.toPx()), Size(x, groove - 2.dp.toPx()), round)
    // Scale marks under the groove at each quarter.
    for (k in 0..4) {
        val tx = size.width * k / 4f
        drawLine(c.muted.copy(alpha = 0.6f), Offset(tx, cy + groove), Offset(tx, cy + groove + 3.dp.toPx()), 1.dp.toPx())
    }
    val knob = Size(22.dp.toPx(), 28.dp.toPx())
    val keys = style.keyColors()
    translate(x - knob.width / 2f, cy - knob.height / 2f - 1.dp.toPx()) {
        val corner = CornerRadius(5.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(0f, 2.5.dp.toPx()), knob, corner)
        drawRoundRect(Brush.verticalGradient(listOf(keys.top, keys.bottom), 0f, knob.height), Offset.Zero, knob, corner)
        drawRoundRect(keys.rim, Offset.Zero, knob, corner, style = Stroke(1.dp.toPx()))
        for (k in -1..1) {
            val y = knob.height / 2f + k * 4.dp.toPx()
            drawLine(
                if (k == 0) lerp(c.content, c.accent, dragging) else c.content.copy(alpha = 0.25f),
                Offset(knob.width * 0.25f, y), Offset(knob.width * 0.75f, y),
                (if (k == 0) 2f else 1f) * density,
            )
        }
    }
}

/** The colours inside 机's screen: black glass lit in warm white, the case's accents kept. */
fun LanyinStyle.screenStyle(): LanyinStyle = copy(
    dark = true,
    colors = colors.copy(
        bg = Color(0xFF121311), bgDeep = Color(0xFF0B0C0B), surface = Color(0xFF1B1D1A), surfaceAlt = Color(0xFF232521),
        content = Color(0xFFF1EFE6), muted = Color(0xFF80847A), onAccent = Color.White,
        outline = Color(0x26F1EFE6), vinyl = Color(0xFF0B0C0B),
    ),
)

/**
 * 机's screen, set into the case: black glass with a shadow along its top edge and a bright lip
 * below where the case catches the light. Inside it the theme turns to [screenStyle], so the
 * artwork and lyrics within read as lit pixels.
 */
@Composable
fun DeviceScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val style = Lanyin.style
    val screen = remember(style) { style.screenStyle() }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .drawBehind {
                drawRoundRect(Color.White.copy(alpha = if (style.dark) 0.06f else 0.75f), Offset(0f, 1.5.dp.toPx()), size, CornerRadius(22.dp.toPx()))
            }
            .clip(shape)
            .background(screen.colors.bg)
            .drawWithCache {
                val shade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = 12.dp.toPx())
                // A faint reflection across the glass.
                val sheen = Path().apply {
                    moveTo(size.width * 0.55f, 0f); lineTo(size.width * 0.78f, 0f); lineTo(size.width * 0.48f, size.height); lineTo(size.width * 0.25f, size.height); close()
                }
                onDrawWithContent {
                    drawContent()
                    drawRect(shade)
                    drawPath(sheen, Color.White.copy(alpha = 0.025f))
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalLanyinStyle provides screen, LocalContentColor provides screen.colors.content) {
            content()
        }
    }
}

/**
 * A VU meter drawn on 机's screen: a scale from −20 to +3 with its last stretch in orange and a
 * needle that swings with the level, overshooting a hair as a real one does and falling back to
 * rest when the music stops.
 */
@Composable
fun VuMeter(feed: io.lanyin.player.core.audio.AudioFeed, playing: Boolean, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (!playing) {
            settling = true
            kotlinx.coroutines.delay(900)
            settling = false
        }
    }
    val clock = rememberFrameClock(playing || settling)
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "vuEnergy")
    val levels = remember { FloatArray(32) }
    // Position, velocity and the clock at the last frame.
    val needle = remember { floatArrayOf(0f, 0f, -1f) }
    val measurer = rememberTextMeasurer()
    val label = remember(style) { TextStyle(fontFamily = SpaceMonoFamily, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = style.colors.muted) }
    val vu = remember(measurer, label) { measurer.measure("VU", label) }
    // Its own layer: the needle redraws every frame, the screen around it needn't.
    Canvas(modifier.graphicsLayer()) {
        val c = style.colors
        val t = clock.floatValue
        var target = 0f
        if (feed.levels(levels)) {
            var sum = 0f
            for (i in 0 until 24) sum += levels[i] * (if (i < 8) 1.3f else 0.8f)
            target = (sum / 24f * 1.35f).coerceIn(0f, 1.06f) * energy
        }
        val dt = if (needle[2] < 0f) 0f else (t - needle[2]).coerceIn(0f, 0.05f)
        needle[2] = t
        // A stiff, slightly underdamped spring: VU ballistics, about 300 ms to settle.
        needle[1] += ((target - needle[0]) * 160f - needle[1] * 19f) * dt
        needle[0] = (needle[0] + needle[1] * dt).coerceIn(-0.03f, 1.1f)
        val pivot = Offset(size.width / 2f, size.height * 1.02f)
        val r = minOf(size.width * 0.5f, size.height * 0.92f)
        val from = 215f
        val sweep = 110f
        fun at(u: Float, radius: Float): Offset {
            val a = (from + sweep * u) / 180f * PI.toFloat()
            return Offset(pivot.x + cos(a) * radius, pivot.y + sin(a) * radius)
        }
        val arcTopLeft = Offset(pivot.x - r * 0.86f, pivot.y - r * 0.86f)
        val arcSize = Size(r * 1.72f, r * 1.72f)
        drawArc(c.content.copy(alpha = 0.55f), from, sweep * 0.72f, false, arcTopLeft, arcSize, style = Stroke(1.dp.toPx()))
        drawArc(c.accent, from + sweep * 0.72f, sweep * 0.28f, false, arcTopLeft, arcSize, style = Stroke(2.5.dp.toPx()))
        for (k in 0..8) {
            val u = k / 8f
            drawLine(if (u > 0.72f) c.accent else c.content.copy(alpha = 0.55f), at(u, r * 0.86f), at(u, r * (if (k % 2 == 0) 0.7f else 0.76f)), 1.dp.toPx())
        }
        drawText(vu, topLeft = Offset(pivot.x - vu.size.width / 2f, pivot.y - r * 0.5f))
        drawLine(c.content, pivot, at(needle[0], r * 0.98f), 1.4.dp.toPx(), StrokeCap.Round)
        drawCircle(c.content, 3.dp.toPx(), pivot)
    }
}

/**
 * 机's generated sleeves: cut paper shapes on cream, after the graphic covers that come with
 * synthesisers: a sun and a block, a row of level bars, quarter circles from a corner, a grid of
 * buttons.
 */
internal fun DrawScope.deviceCover(style: LanyinStyle, seed: Int) {
    val w = size.width
    val h = size.height
    val cream = Color(0xFFF1ECE1)
    val ink = Color(0xFF1A1A1A)
    val orange = Color(0xFFFF5A1F)
    val blue = Color(0xFF2B6CB0)
    drawRect(cream)
    when (seed % 4) {
        0 -> {
            drawCircle(orange, w * 0.26f, Offset(w * (0.38f + 0.1f * hash01(seed, 1)), h * 0.4f))
            drawRect(ink, Offset(w * 0.5f, h * 0.52f), Size(w * 0.36f, h * 0.26f))
            drawRect(ink, Offset(0f, h * 0.86f), Size(w, h * 0.025f))
        }
        1 -> {
            for (k in 0 until 7) {
                val bar = h * (0.2f + 0.55f * hash01(seed * 9 + k, 2))
                val color = when (k % 3) { 0 -> ink; 1 -> orange; else -> blue }
                drawRect(color, Offset(w * (0.1f + k * 0.115f), h * 0.82f - bar), Size(w * 0.07f, bar))
            }
        }
        2 -> {
            val corner = Offset(0f, h)
            for ((k, color) in listOf(ink, orange, cream, blue).withIndex()) {
                val r = w * (0.95f - k * 0.2f)
                drawCircle(color, r, corner)
            }
            drawCircle(orange, w * 0.06f, Offset(w * 0.8f, h * 0.2f))
        }
        else -> {
            for (row in 0 until 3) for (col in 0 until 3) {
                val lit = (row * 3 + col) == (seed / 4) % 9
                val p = Offset(w * (0.25f + col * 0.25f), h * (0.25f + row * 0.25f))
                drawCircle(if (lit) orange else ink, w * 0.085f, p)
                if (!lit) drawCircle(cream, w * 0.03f, p)
            }
        }
    }
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.08f)
}

/** 机's motif: a knob with its pointer and a scale of dots round it. */
internal fun DrawScope.deviceMotif(color: Color, accent: Color) {
    val r = size.minDimension * 0.28f
    val c = center
    drawCircle(color, r, c, style = Stroke(r * 0.12f))
    val a = (-50f) / 180f * PI.toFloat()
    drawLine(accent, c, Offset(c.x + cos(a) * r * 0.8f, c.y + sin(a) * r * 0.8f), r * 0.14f, StrokeCap.Round)
    for (k in 0..10) {
        val u = (135f + k * 27f) / 180f * PI.toFloat()
        drawCircle(color.copy(alpha = if (k < 6) 1f else 0.35f), r * 0.05f, Offset(c.x + cos(u) * r * 1.35f, c.y + sin(u) * r * 1.35f))
    }
}
