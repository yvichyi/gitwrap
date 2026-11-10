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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
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

/** 机's walnut, rendered with the concept's material renderer; set when the theme first draws. */
internal object HifiTextures {
    var walnut: ImageBitmap? = null
}

/**
 * A strip of brushed aluminium, made once: rows of fine streaks, each a little lighter or darker,
 * long along the brushing. Tiled across a panel it reads as the hairline finish.
 */
internal val brushedTile: ImageBitmap by lazy {
    val w = 512
    val h = 128
    val rnd = java.util.Random(11)
    val rowShade = FloatArray(h) { rnd.nextGaussian().toFloat() }
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        var v = 0f
        for (x in 0 until w) {
            // Streaks run along the row: noise smoothed heavily in x, fresh per row.
            v = v * 0.96f + rnd.nextGaussian().toFloat() * 0.2f
            val l = (128 + (rowShade[y] * 0.55f + v) * 26f).toInt().coerceIn(0, 255)
            pixels[y * w + x] = (0xff shl 24) or (l shl 16) or (l shl 8) or l
        }
    }
    android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

internal val brushedBrush: Brush by lazy {
    androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(brushedTile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))
}

/**
 * Brushed aluminium over [size]: the finish's colour lighter at the top, the hairline streaks, and
 * the soft vertical band a studio light leaves across a brushed panel.
 */
internal fun DrawScope.drawBrushedMetal(base: Color, dark: Boolean, band: Float = 0.3f) {
    drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, if (dark) 0.05f else 0.18f), base, lerp(base, Color.Black, if (dark) 0.2f else 0.08f))))
    drawRect(brushedBrush, alpha = if (dark) 0.12f else 0.2f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
    drawRect(Brush.horizontalGradient(
        0f to Color.Transparent, (band - 0.14f).coerceAtLeast(0f) to Color.Transparent,
        band to Color.White.copy(alpha = if (dark) 0.06f else 0.2f),
        (band + 0.16f).coerceAtMost(1f) to Color.Transparent, 1f to Color.Transparent,
    ))
}

/**
 * 机: the faceplate the pages are on, in its walnut cabinet. Brushed aluminium (champagne, or
 * black anodised at night) between two walnut cheeks, a dark gap and a bright chamfer where the
 * plate meets the wood.
 */
internal fun DrawScope.drawDeviceCase(style: LanyinStyle, page: Boolean) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawBrushedMetal(c.bg, style.dark)
    val cheek = 9.dp.toPx()
    val wood = HifiTextures.walnut
    for (x0 in floatArrayOf(0f, w - cheek)) {
        if (wood != null) {
            var y = 0f
            val tile = wood.height * (cheek / wood.width.toFloat()).coerceAtLeast(0.01f) * 3f
            while (y < h) {
                drawImage(wood, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = androidx.compose.ui.unit.IntSize(wood.width / 3, wood.height), dstOffset = androidx.compose.ui.unit.IntOffset(x0.toInt(), y.toInt()), dstSize = androidx.compose.ui.unit.IntSize(cheek.toInt() + 1, tile.toInt() + 1))
                y += tile
            }
        } else {
            drawRect(Color(0xFF3A2416), Offset(x0, 0f), Size(cheek, h))
        }
    }
    // Where plate meets wood: a dark gap, then the plate's bright chamfer.
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(cheek, 0f), Size(1.dp.toPx(), h))
    drawRect(Color.White.copy(alpha = if (style.dark) 0.12f else 0.55f), Offset(cheek + 1.dp.toPx(), 0f), Size(1.dp.toPx(), h))
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(w - cheek - 1.dp.toPx(), 0f), Size(1.dp.toPx(), h))
    drawRect(Color.Black.copy(alpha = 0.18f), Offset(w - cheek - 2.dp.toPx(), 0f), Size(1.dp.toPx(), h))
    if (page) return
    val inset = 11.dp.toPx() + cheek
    drawScrew(style, Offset(inset, h - inset), 20f)
    drawScrew(style, Offset(w - inset, h - inset), -35f)
}

/** A hex-socket screw: a domed head catching the light from the top left, the socket dark. */
internal fun DrawScope.drawScrew(style: LanyinStyle, at: Offset, slot: Float) {
    val r = 3.5.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.5f), r + 0.8.dp.toPx(), at)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE9EAEC), Color(0xFF8B8F94), Color(0xFF55585C)), at - Offset(r * 0.35f, r * 0.4f), r * 1.4f), r, at)
    val hex = Path().apply {
        for (k in 0 until 6) {
            val a = (slot + k * 60f) * PI.toFloat() / 180f
            val p = at + Offset(cos(a) * r * 0.42f, sin(a) * r * 0.42f)
            if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(hex, Color(0xFF1C1D1F))
}

/** A key's face from top to bottom, the lip it sits on and its rim. */
internal class KeyColors(val top: Color, val bottom: Color, val lip: Color, val rim: Color)

internal fun LanyinStyle.keyColors(accent: Boolean = false): KeyColors {
    val c = colors
    return when {
        accent -> KeyColors(lerp(c.accent, Color.White, 0.16f), c.accent, lerp(c.accent, Color.Black, 0.45f), lerp(c.accent, Color.Black, 0.3f))
        dark -> KeyColors(Color(0xFF3C3D41), Color(0xFF26272A), Color(0xFF0B0B0C), Color(0xFF55565B))
        else -> KeyColors(Color(0xFFE6E1D5), Color(0xFFC3BCAD), Color(0xFF6E685D), Color(0xFF8F887B))
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
        // A tight contact shadow where the key meets the case, not a haze round it: real keys sit
        // in the panel, they don't float over it.
        for ((dy, a) in listOf(3.5f to 0.05f, 1.8f to 0.1f)) {
            translate(0f, keyTravel.toPx() + dy * density * (1f - 0.5f * press)) { drawOutline(outline, Color.Black.copy(alpha = a)) }
        }
    }
    translate(0f, keyTravel.toPx()) { drawOutline(outline, keys.lip) }
    translate(0f, travel) {
        drawOutline(outline, Brush.verticalGradient(listOf(keys.top, keys.bottom)))
        // The aluminium's hairline brushing, and its diamond-cut edge.
        drawOutline(outline, brushedBrush, alpha = 0.16f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
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
 * 机's progress: the tape scale of the cassette deck. A strip of black glass lit from below by
 * two warm lamps, minute and quarter ticks along it, the part played lit amber, and the red
 * pointer where the tape is; it brightens while dragged.
 */
internal fun DrawScope.drawFaderTrack(f: Float, dragging: Float, style: LanyinStyle) {
    val h = 18.dp.toPx()
    val top = (size.height - h) / 2f
    val w = size.width
    val x = w * f
    val round = CornerRadius(2.dp.toPx())
    // The bezel: dark above, bright below.
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(-1.dp.toPx(), top - 1.dp.toPx()), Size(w + 2.dp.toPx(), h + 1.dp.toPx()), round)
    drawRoundRect(Color.White.copy(alpha = if (style.dark) 0.12f else 0.6f), Offset(-1.dp.toPx(), top + h - 0.5.dp.toPx()), Size(w + 2.dp.toPx(), 1.dp.toPx()), round)
    drawRoundRect(Color(0xFF120E0A), Offset(0f, top), Size(w, h), round)
    for (lx in floatArrayOf(w * 0.22f, w * 0.78f)) {
        drawRect(Brush.radialGradient(listOf(Color(0xFFFFB45A).copy(alpha = 0.22f + 0.1f * dragging), Color.Transparent), Offset(lx, top + h * 1.1f), w * 0.35f), Offset(0f, top), Size(w, h))
    }
    val lamp = Color(0xFFFFDDA8)
    for (k in 0..40) {
        val tx = w * k / 40f
        val big = k % 8 == 0
        drawLine(lamp.copy(alpha = if (big) 0.9f else 0.55f), Offset(tx, top + h - 2.dp.toPx()), Offset(tx, top + h - (if (big) 9 else if (k % 2 == 0) 6 else 4).dp.toPx()), (if (big) 1.2f else 0.7f) * density)
    }
    if (x > 0f) drawRect(Color(0xFFFF8A3D).copy(alpha = 0.9f), Offset(0f, top + h - 2.5.dp.toPx()), Size(x, 1.5.dp.toPx()))
    drawRect(Color(0xFFFF3B22), Offset(x - 1.dp.toPx(), top + 1.5.dp.toPx()), Size(2.dp.toPx(), h - 3.dp.toPx()))
    drawRect(Brush.radialGradient(listOf(Color(0xFFFF3B22).copy(alpha = 0.45f), Color.Transparent), Offset(x, top + h / 2f), 10.dp.toPx()), Offset(x - 10.dp.toPx(), top), Size(20.dp.toPx(), h))
    // The glass's reflection along its top.
    drawRect(Color.White.copy(alpha = 0.05f), Offset(0f, top), Size(w, h * 0.35f))
}

/**
 * The colours inside 机's screen: a vacuum fluorescent display behind black glass, its segments
 * cyan, unlit ones a faint ghost; amber for what's marked.
 */
fun LanyinStyle.screenStyle(): LanyinStyle = copy(
    dark = true,
    display = io.lanyin.player.ui.theme.PixelFamily,
    displayWeight = FontWeight.Normal,
    body = io.lanyin.player.ui.theme.PixelFamily,
    colors = colors.copy(
        bg = Color(0xFF050807), bgDeep = Color(0xFF030504), surface = Color(0xFF0A100E), surfaceAlt = Color(0xFF0E1613),
        content = Color(0xFF6DF5D6), muted = Color(0xFF2E8C79), accent = Color(0xFFFFB03A), accentAlt = Color(0xFF6DF5D6), onAccent = Color(0xFF1A1006),
        outline = Color(0x336DF5D6), vinyl = Color(0xFF030504), like = Color(0xFFFF5A45),
    ),
    lyric = lyric.copy(glow = true),
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
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .drawBehind {
                // The polished bezel: dark above where the plate shades it, bright below.
                drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(-1.5.dp.toPx(), -1.5.dp.toPx()), Size(size.width + 3.dp.toPx(), size.height + 1.5.dp.toPx()), CornerRadius(5.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = if (style.dark) 0.14f else 0.8f), Offset(-1.dp.toPx(), 1.5.dp.toPx()), Size(size.width + 2.dp.toPx(), size.height), CornerRadius(5.dp.toPx()))
            }
            .clip(shape)
            .background(screen.colors.bg)
            .drawWithCache {
                val shade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = 12.dp.toPx())
                // A faint reflection across the glass.
                val sheen = Path().apply {
                    moveTo(size.width * 0.55f, 0f); lineTo(size.width * 0.78f, 0f); lineTo(size.width * 0.48f, size.height); lineTo(size.width * 0.25f, size.height); close()
                }
                val mesh = 2.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    // The fine grid in front of the phosphor, then the glass.
                    var gx = 0f
                    while (gx < size.width) {
                        drawRect(Color.Black.copy(alpha = 0.18f), Offset(gx, 0f), Size(0.6f * density, size.height))
                        gx += mesh
                    }
                    drawRect(shade)
                    drawPath(sheen, Color.White.copy(alpha = 0.04f))
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
fun VuMeter(
    feed: io.lanyin.player.core.audio.AudioFeed,
    playing: Boolean,
    modifier: Modifier = Modifier,
    /** The bands it reads: all but the top by default, or the low or high end for a pair of meters. */
    bands: IntRange = 0 until 24,
    label: String = "VU",
) {
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
    val labelStyle = remember(style) { TextStyle(fontFamily = SpaceMonoFamily, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = style.colors.muted) }
    val vu = remember(measurer, labelStyle, label) { measurer.measure(label, labelStyle) }
    // Its own layer: the needle redraws every frame, the screen around it needn't.
    Canvas(modifier.graphicsLayer()) {
        val c = style.colors
        val t = clock.floatValue
        var target = 0f
        if (feed.levels(levels)) {
            var sum = 0f
            for (i in bands) sum += levels[i] * (if (i < 8) 1.3f else 0.8f)
            target = (sum / bands.count() * 1.35f).coerceIn(0f, 1.06f) * energy
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
    drawRect(grainBrush, alpha = 0.08f)
}
