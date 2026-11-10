package io.lanyin.player.ui.design

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.R
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.LongCangFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 机's hardware: the faceplates of the stack, knobs, the seven-segment and fluorescent displays,
 * the lamp-lit VU meters and the cassette. The colours that aren't the theme's own (the display's
 * phosphor, the lamp, the tape label) are the parts', the same by day and night.
 */
object Hifi {
    val Vfd = Color(0xFF6DF5D6)
    val VfdDim = Color(0xFF2E8C79)
    val Amber = Color(0xFFFFB03A)
    val Lamp = Color(0xFFFFDDA8)
    val Red = Color(0xFFFF3B22)
    val Glass = Color(0xFF050807)
    val Shell = Color(0xFF2B2522)
    val Label = Color(0xFFF2EBDD)
    val LabelInk = Color(0xFF2A2420)
    val LabelBand = Color(0xFFE0532C)
    val Tape = Color(0xFF3E2618)
}

/** Lettering at a fixed size: the words printed on the panels don't grow with the system font. */
@Composable
internal fun hifiSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/** The small capitals engraved on the panels. */
@Composable
internal fun engraved(size: Float = 8f) = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = hifiSp(size), letterSpacing = 0.16.em)

/** The maker's name, as on every unit of the stack. */
@Composable
internal fun Wordmark(color: Color, size: Float = 15f) {
    Text("LANYIN", style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = hifiSp(size), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 0.02.em), color = color, maxLines = 1)
}

/**
 * One unit of the stack: a brushed plate with a chamfered edge, a hex screw in each corner, the
 * wordmark and model across the top, and on the right [trailing] (a note, a power light, a key).
 */
@Composable
fun HifiUnit(
    model: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.drawBehind { drawUnitPlate(style) }.padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(c.content)
            Text(model, style = engraved(8f), color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 10.dp).weight(1f))
            trailing()
        }
        content()
    }
}

/** A unit's plate: its shadow on the shelf, the brushed face, the chamfer and four screws. */
internal fun DrawScope.drawUnitPlate(style: LanyinStyle, screws: Boolean = true) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val one = 1.dp.toPx()
    drawRect(Color.Black.copy(alpha = if (style.dark) 0.7f else 0.45f), Offset(0f, 2.dp.toPx()), size)
    drawBrushedMetal(c.surfaceAlt, style.dark, band = 0.64f)
    // The diamond-cut chamfer: bright where it faces the light, dark beneath.
    drawRect(Color.White.copy(alpha = if (style.dark) 0.2f else 0.75f), Offset.Zero, Size(w, one))
    drawRect(Color.White.copy(alpha = if (style.dark) 0.08f else 0.4f), Offset(0f, one), Size(one, h - one))
    drawRect(Color.Black.copy(alpha = 0.4f), Offset(0f, h - one), Size(w, one))
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(w - one, 0f), Size(one, h))
    if (!screws) return
    val inset = 7.dp.toPx()
    drawScrew(style, Offset(inset, inset), 12f)
    drawScrew(style, Offset(w - inset, inset), 41f)
    drawScrew(style, Offset(inset, h - inset), -20f)
    drawScrew(style, Offset(w - inset, h - inset), 5f)
}

/**
 * The walnut cabinet the stack stands in: the rendered grain tiled down, darkened at night, with
 * the shadow its top throws inside.
 */
internal fun DrawScope.drawWalnut(wood: ImageBitmap, dark: Boolean) {
    val scale = size.width / wood.width
    val tile = (wood.height * scale).coerceAtLeast(1f)
    var y = 0f
    while (y < size.height) {
        drawImage(wood, IntOffset.Zero, IntSize(wood.width, wood.height), IntOffset(0, y.toInt()), IntSize(size.width.toInt() + 1, tile.toInt() + 1))
        y += tile
    }
    if (dark) drawRect(Color.Black.copy(alpha = 0.35f))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 14.dp.toPx()))
}

/** The walnut tile, loaded once. */
@Composable
fun rememberWalnut(): ImageBitmap {
    val wood = ImageBitmap.imageResource(R.drawable.hifi_walnut)
    HifiTextures.walnut = wood
    return wood
}

/**
 * A turned aluminium knob (black anodised at night), rendered with its knurled skirt; the pointer
 * is drawn here so it can turn while the light on the metal stays put. [angle] is in degrees from
 * straight up.
 */
@Composable
fun HifiKnob(diameter: Dp, angle: Float, modifier: Modifier = Modifier, black: Boolean = Lanyin.style.dark, pointer: Color? = null) {
    val big = diameter > 44.dp
    val img = ImageBitmap.imageResource(
        when {
            big && black -> R.drawable.hifi_knob_big_black
            big -> R.drawable.hifi_knob_big
            black -> R.drawable.hifi_knob_small_black
            else -> R.drawable.hifi_knob_small
        },
    )
    // The renders keep a margin of 2 units round a knob of radius 38 or 14.
    val inner = if (big) 38f / 40f else 14f / 16f
    Canvas(modifier.size(diameter)) { drawKnob(img, inner, angle, black, pointer, big) }
}

internal fun DrawScope.drawKnob(img: ImageBitmap, inner: Float, angle: Float, black: Boolean, pointer: Color?, big: Boolean) {
    val r = size.minDimension / 2f
    val c = center
    // A soft shadow under it, offset away from the light above.
    val drop = c + Offset(0f, r * 0.12f)
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.2f), Color.Transparent), drop, r * 1.18f), r * 1.18f, drop)
    val full = r / inner
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset((c.x - full).toInt(), (c.y - full).toInt()), IntSize((full * 2).toInt(), (full * 2).toInt()))
    val mark = pointer ?: if (black) Color(0xFFE8E4DA) else Color(0xFF1A1A1A)
    rotate(angle, c) {
        if (big) {
            // An engraved line from the cap's edge in towards the centre.
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x + 0.6f * density, c.y - r * 0.74f), Offset(c.x + 0.6f * density, c.y - r * 0.36f), 2.4f * density, StrokeCap.Round)
            drawLine(mark, Offset(c.x, c.y - r * 0.74f), Offset(c.x, c.y - r * 0.36f), 2f * density, StrokeCap.Round)
        } else {
            drawLine(mark, Offset(c.x, c.y - r * 0.82f), Offset(c.x, c.y - r * 0.3f), 1.6f * density, StrokeCap.Round)
        }
    }
}

/** A knob's scale: [count] dots round it from −135° to +135°, the first [lit] of them brighter. */
internal fun DrawScope.drawKnobScale(color: Color, radius: Float, count: Int = 11, lit: Int = count) {
    for (k in 0 until count) {
        val a = ((-135f + 270f * k / (count - 1)) - 90f) / 180f * PI.toFloat()
        drawCircle(color.copy(alpha = if (k < lit) 0.9f else 0.35f), 1.1f * density, center + Offset(cos(a) * radius, sin(a) * radius))
    }
}

/** A small round light: its lens, lit [color] with a glow, or dark. */
internal fun DrawScope.drawLed(at: Offset, color: Color, on: Boolean, r: Float = 2.2f * density) {
    drawCircle(Color.Black.copy(alpha = 0.5f), r + 0.8f * density, at)
    if (on) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), at, r * 3.2f), r * 3.2f, at)
        drawCircle(color, r, at)
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.35f, at - Offset(r * 0.3f, r * 0.3f))
    } else {
        drawCircle(lerp(color, Color.Black, 0.72f), r, at)
    }
}

@Composable
fun Led(color: Color, on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(7.dp)) { drawLed(center, color, on) }
}

/**
 * A toggle switch: its knurled nut on the panel and the chrome bat pointing up for on, down for
 * off.
 */
internal fun DrawScope.drawToggle(on: Boolean) {
    val c = center
    val r = size.minDimension * 0.3f
    drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.1f, c + Offset(0f, 1.5f * density))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE6E7E9), Color(0xFF7E8187)), c - Offset(r * 0.3f, r * 0.4f), r * 1.4f), r, c)
    val dir = if (on) -1f else 1f
    val tip = c + Offset(0f, dir * size.height * 0.42f)
    drawLine(Color.Black.copy(alpha = 0.35f), c + Offset(1.5f * density, 1.5f * density), tip + Offset(1.5f * density, 2f * density), r * 0.55f, StrokeCap.Round)
    drawLine(Brush.horizontalGradient(listOf(Color(0xFF8A8D92), Color(0xFFF4F4F5), Color(0xFF9A9DA2)), c.x - r * 0.3f, c.x + r * 0.3f), c, tip, r * 0.55f, StrokeCap.Round)
    drawCircle(Color(0xFFF0F0F1), r * 0.36f, tip)
}

// ─── Seven segments ──────────────────────────────────────────────────────────

private val SegmentMap = mapOf(
    '0' to 0b0111111, '1' to 0b0000110, '2' to 0b1011011, '3' to 0b1001111, '4' to 0b1100110,
    '5' to 0b1101101, '6' to 0b1111101, '7' to 0b0000111, '8' to 0b1111111, '9' to 0b1101111,
    '-' to 0b1000000, ' ' to 0, 'A' to 0b1110111, 'E' to 0b1111001, 'F' to 0b1110001, 'P' to 0b1110011,
    'L' to 0b0111000, 'n' to 0b1010100, 'o' to 0b1011100, 'd' to 0b1011110, 'C' to 0b0111001,
    'r' to 0b1010000, 't' to 0b1111000, 'U' to 0b0111110, 'b' to 0b1111100, 'H' to 0b1110110, 'S' to 0b1101101,
)

/** How wide [text] sets in seven segments [height] tall. */
fun sevenSegWidth(text: String, height: Float): Float {
    var w = 0f
    for (ch in text) w += if (ch == '.' || ch == ':') height * 0.22f else height * 0.66f
    return w
}

/**
 * [text] in seven segments [height] tall from [topLeft], italic a few degrees as a display's are:
 * lit segments in [color] with their glow, the rest a ghost of it, as unlit phosphor shows
 * through the glass.
 */
fun DrawScope.drawSevenSeg(text: String, topLeft: Offset, height: Float, color: Color, ghost: Float = 0.06f, glow: Boolean = true) {
    val t = height * 0.095f
    val w = height * 0.46f
    val skew = 0.1f
    var x = topLeft.x
    for (ch in text) {
        if (ch == '.' || ch == ':') {
            val dots = if (ch == '.') listOf(height - t / 2) else listOf(height * 0.3f, height * 0.72f)
            for (dy in dots) drawCircle(color, t * 0.62f, Offset(x + height * 0.08f + (height - dy) * skew, topLeft.y + dy))
            x += height * 0.22f
            continue
        }
        val bits = SegmentMap[ch] ?: 0
        for (s in 0 until 7) {
            val on = bits and (1 shl s) != 0
            if (!on && ghost <= 0f) continue
            val path = segmentPath(s, x, topLeft.y, w, height, t, skew)
            if (on) {
                if (glow) drawPath(path, color.copy(alpha = 0.12f), style = Stroke(t * 1.1f))
                drawPath(path, color)
            } else {
                drawPath(path, color.copy(alpha = ghost))
            }
        }
        x += height * 0.66f
    }
}

/** Segment [s] (a b c d e f g) of a digit at [x0], [y0], [w] by [h], [t] thick. */
private fun segmentPath(s: Int, x0: Float, y0: Float, w: Float, h: Float, t: Float, skew: Float): Path {
    val gap = t * 0.18f
    fun p(x: Float, y: Float) = Offset(x0 + x + (h - y) * skew, y0 + y)
    val half = h / 2f
    val pts: List<Offset> = when (s) {
        0, 6, 3 -> {
            val y = when (s) { 0 -> t / 2; 6 -> half; else -> h - t / 2 }
            val a = t / 2 + gap
            val b = w - t / 2 - gap
            listOf(p(a, y), p(a + t / 2, y - t / 2), p(b - t / 2, y - t / 2), p(b, y), p(b - t / 2, y + t / 2), p(a + t / 2, y + t / 2))
        }
        else -> {
            val x = if (s == 1 || s == 2) w - t / 2 else t / 2
            val (ya, yb) = if (s == 1 || s == 5) (t / 2 + gap) to (half - gap) else (half + gap) to (h - t / 2 - gap)
            listOf(p(x, ya), p(x + t / 2, ya + t / 2), p(x + t / 2, yb - t / 2), p(x, yb), p(x - t / 2, yb - t / 2), p(x - t / 2, ya + t / 2))
        }
    }
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
}

/** [text] in seven segments, sized to fit [height]. */
@Composable
fun SevenSeg(text: String, height: Dp, color: Color = Hifi.Vfd, modifier: Modifier = Modifier, ghost: Float = 0.06f) {
    val px = with(LocalDensity.current) { height.toPx() }
    val width = with(LocalDensity.current) { (sevenSegWidth(text, px) + px * 0.12f).toDp() }
    Canvas(modifier.size(width, height)) { drawSevenSeg(text, Offset.Zero, size.height, color, ghost) }
}

// ─── Meters ──────────────────────────────────────────────────────────────────

/**
 * A meter needle's ballistics: a stiff, slightly underdamped spring, about 300 ms to settle, so
 * it overshoots a hair and falls back as a real one does.
 */
internal class Needle {
    var pos = 0f
    private var vel = 0f
    private var last = -1f
    fun step(target: Float, t: Float): Float {
        val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.05f)
        last = t
        vel += ((target - pos) * 160f - vel * 19f) * dt
        pos = (pos + vel * dt).coerceIn(-0.03f, 1.1f)
        return pos
    }
}

/** Where the meter's scale puts [db]: the VU scale's own spacing, 0 dB about two thirds along. */
internal fun vuAt(db: Float): Float {
    val lo = 10f.pow(-20f / 20f)
    val hi = 10f.pow(3f / 20f)
    return ((10f.pow(db / 20f) - lo) / (hi - lo)).coerceIn(0f, 1f)
}

/** The level of [bands] this frame for a meter, 0..~1.05, or 0 when the feed is quiet. */
internal fun meterLevel(feed: io.lanyin.player.core.audio.AudioFeed, levels: FloatArray, bands: IntRange): Float {
    if (!feed.levels(levels)) return 0f
    var sum = 0f
    for (i in bands) sum += levels[i] * (if (i < 8) 1.3f else 0.8f)
    return (sum / bands.count() * 1.35f).coerceIn(0f, 1.06f)
}

/**
 * The amplifier's analogue VU meter: a cream face lit warm from below, the scale from 20 to +3
 * with its red stretch, the maker's name, the black shroud over the pivot with [channel] printed
 * on it, and a needle that swings with the music.
 */
@Composable
fun AnalogVu(
    feed: io.lanyin.player.core.audio.AudioFeed,
    playing: Boolean,
    channel: String,
    modifier: Modifier = Modifier,
    bands: IntRange = 0 until 24,
) {
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (!playing) {
            settling = true
            kotlinx.coroutines.delay(900)
            settling = false
        }
    }
    val clock = rememberFrameClock(playing || settling)
    val levels = remember { FloatArray(32) }
    val needle = remember { Needle() }
    val measurer = rememberTextMeasurer()
    val unit = hifiSp(1f)
    val face = remember(measurer, unit) { FaceText(measurer, unit) }
    val channelStyle = engraved(6.5f).copy(color = Color(0xFFE8E2D4))
    val label = remember(measurer, channel) { measurer.measure(channel, channelStyle) }
    Canvas(modifier.graphicsLayer()) {
        val t = clock.floatValue
        val target = if (playing) meterLevel(feed, levels, bands) else 0f
        drawAnalogVu(face, label, needle.step(target, t))
    }
}

/** The face's printing, measured once. */
internal class FaceText(measurer: TextMeasurer, unit: TextUnit) {
    private val ink = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = unit * 7f, fontWeight = FontWeight.Bold, color = Color(0xFF2A241C))
    val numbers = listOf(-20f to "20", -10f to "10", -7f to "7", -5f to "5", -3f to "3", 0f to "0", 3f to "+3").map { (db, s) ->
        db to measurer.measure(s, if (db >= 0f) ink.copy(color = Color(0xFFC0301E)) else ink)
    }
    val vu = measurer.measure("VU", ink.copy(fontSize = unit * 9f, fontWeight = FontWeight.Black))
    val maker = measurer.measure("LANYIN", ink.copy(fontSize = unit * 5.5f, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Black))
    val minus = measurer.measure("−", ink)
    val plus = measurer.measure("+", ink.copy(color = Color(0xFFC0301E)))
}

internal fun DrawScope.drawAnalogVu(face: FaceText, channel: androidx.compose.ui.text.TextLayoutResult, value: Float) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(3.dp.toPx())
    // The bezel and the face, lit warm from below and dimmer in the top corners.
    drawRoundRect(Color(0xFF0E0D0C), cornerRadius = r)
    val inset = 3.dp.toPx()
    val faceSize = Size(w - 2 * inset, h - 2 * inset)
    val faceAt = Offset(inset, inset)
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFF4C2), Color(0xFFF6DC8E), Color(0xFFD9AE5A)), Offset(w / 2f, h * 0.95f), w * 0.75f), faceAt, faceSize)
    val pivot = Offset(w / 2f, h * 1.08f)
    val radius = h * 0.86f
    val from = -142f
    val sweep = 104f
    fun at(u: Float, rr: Float): Offset {
        val a = (from + sweep * u - 90f + 90f) / 180f * PI.toFloat()
        // Angles measured from straight right; the arc runs from upper left to upper right.
        return Offset(pivot.x + cos(a) * rr, pivot.y + sin(a) * rr)
    }
    val ink = Color(0xFF2A241C)
    val red = Color(0xFFC0301E)
    val arcTL = Offset(pivot.x - radius * 0.78f, pivot.y - radius * 0.78f)
    val arcSize = Size(radius * 1.56f, radius * 1.56f)
    drawArc(ink, from, sweep * vuAt(0f), false, arcTL, arcSize, style = Stroke(0.9f * density))
    drawArc(red, from + sweep * vuAt(0f), sweep * (1f - vuAt(0f)), false, Offset(pivot.x - radius * 0.8f, pivot.y - radius * 0.8f), Size(radius * 1.6f, radius * 1.6f), style = Stroke(3.2f * density))
    for (db in listOf(-20f, -15f, -10f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f)) {
        val u = vuAt(db)
        val major = db in listOf(-20f, -10f, -7f, -5f, -3f, 0f, 3f)
        drawLine(if (db >= 0f) red else ink, at(u, radius * 0.78f), at(u, radius * (if (major) 0.9f else 0.85f)), (if (major) 1.1f else 0.8f) * density)
    }
    for ((db, text) in face.numbers) {
        val p = at(vuAt(db), radius * 1.0f)
        drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
    }
    drawText(face.minus, topLeft = Offset(inset + 3.dp.toPx(), inset + 1.dp.toPx()))
    drawText(face.plus, topLeft = Offset(w - inset - 3.dp.toPx() - face.plus.size.width, inset + 1.dp.toPx()))
    drawText(face.vu, topLeft = Offset(w / 2f - face.vu.size.width / 2f, h * 0.48f))
    drawText(face.maker, topLeft = Offset(inset + 4.dp.toPx(), h - inset - face.maker.size.height - 1.dp.toPx()))
    // The needle, its shadow on the face, then the shroud over the pivot.
    val tip = at(value.coerceIn(-0.03f, 1.08f), radius * 0.94f)
    clipRect(inset, inset, w - inset, h - inset) {
        drawLine(Color.Black.copy(alpha = 0.18f), pivot + Offset(2.dp.toPx(), 0f), tip + Offset(2.dp.toPx(), 1.5.dp.toPx()), 1.2f * density)
        drawLine(Color(0xFF16120E), pivot, tip, 1.1f * density, StrokeCap.Round)
    }
    val shroudW = w * 0.48f
    val shroudH = h * 0.2f
    val shroudAt = Offset(w / 2f - shroudW / 2f, h - inset - shroudH)
    drawRoundRect(Color(0xFF141311), shroudAt, Size(shroudW, shroudH + inset), CornerRadius(2.dp.toPx()))
    drawText(channel, topLeft = Offset(w / 2f - channel.size.width / 2f, shroudAt.y + (shroudH - channel.size.height) / 2f))
    // Glass: a soft sheen across the top, the face darker at its edges.
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = inset, endY = h * 0.45f), faceAt, Size(faceSize.width, h * 0.4f))
    drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.12f to Color.Transparent, 0.88f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f)), faceAt, faceSize)
}

/**
 * The fluorescent level meter: two rows of short bars, L and R, cyan to 0 dB and amber beyond,
 * with a peak that holds a moment before falling.
 */
@Composable
fun VfdLevels(feed: io.lanyin.player.core.audio.AudioFeed, playing: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberFrameClock(playing)
    val levels = remember { FloatArray(32) }
    val state = remember { FloatArray(6) } // level L, R; peak L, R; peak time L, R
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontFamily = SpaceMonoFamily, fontSize = hifiSp(6.5f), color = Hifi.VfdDim)
    val marks = remember(measurer) { listOf("-30", "-20", "-10", "-5", "0", "+3", "+6").map { measurer.measure(it, small) } }
    val lr = remember(measurer) { listOf("L", "R").map { measurer.measure(it, small.copy(color = Hifi.Vfd, fontWeight = FontWeight.Bold)) } }
    Canvas(modifier) {
        val t = clock.floatValue
        val live = playing && feed.levels(levels)
        val lo = if (live) (0 until 12).sumOf { levels[it].toDouble() }.toFloat() / 12f * 1.5f else 0f
        val hi = if (live) (10 until 30).sumOf { levels[it].toDouble() }.toFloat() / 20f * 1.9f else 0f
        val targets = floatArrayOf((lo * 0.7f + hi * 0.3f).coerceIn(0f, 1f), (lo * 0.45f + hi * 0.55f).coerceIn(0f, 1f))
        val labelW = 10.dp.toPx()
        val rowH = (size.height - 10.dp.toPx()) / 2f
        val bars = 32
        val barW = (size.width - labelW) / bars
        for (ch in 0..1) {
            val v = state[ch] * 0.6f + targets[ch] * 0.4f
            state[ch] = v
            if (v >= state[2 + ch] || t - state[4 + ch] > 0.8f) { state[2 + ch] = v; state[4 + ch] = t }
            val y = ch * rowH + rowH * 0.18f
            drawText(lr[ch], topLeft = Offset(0f, y + (rowH * 0.64f - lr[ch].size.height) / 2f))
            val lit = (v * bars).toInt()
            val peak = (state[2 + ch] * bars).toInt().coerceAtMost(bars - 1)
            for (k in 0 until bars) {
                val hot = k >= bars * 0.78f
                val color = if (hot) Hifi.Amber else Hifi.Vfd
                val on = k < lit || (k == peak && peak > 0)
                drawRect(color.copy(alpha = if (on) 1f else 0.08f), Offset(labelW + k * barW, y), Size(barW * 0.7f, rowH * 0.64f))
            }
        }
        val scaleY = size.height - 9.dp.toPx()
        marks.forEachIndexed { i, m ->
            val x = labelW + (size.width - labelW) * (i / (marks.size - 1f)) - m.size.width / 2f
            drawText(m, topLeft = Offset(x.coerceIn(labelW, size.width - m.size.width), scaleY))
        }
    }
}

// ─── Dials ───────────────────────────────────────────────────────────────────

/**
 * The tuner's dial: a strip of glass lit amber from behind, the band printed 88 to 108 MHz with
 * its ticks, and the red pointer at [mhz].
 */
@Composable
fun TuningDial(mhz: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val num = TextStyle(fontFamily = SpaceMonoFamily, fontSize = hifiSp(7f), fontWeight = FontWeight.Bold, color = Hifi.Lamp)
    val labels = remember(measurer) { (88..108 step 2).map { it to measurer.measure("$it", num) } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawGlassStrip()
        val pad = 10.dp.toPx()
        fun x(f: Float) = pad + (w - 2 * pad) * (f - 88f) / 20f
        for ((f, text) in labels) drawText(text, topLeft = Offset(x(f.toFloat()) - text.size.width / 2f, 2.dp.toPx()))
        for (k in 0..80) {
            val f = 88f + k * 0.25f
            val big = k % 8 == 0
            val mid = k % 4 == 0
            drawLine(Hifi.Lamp.copy(alpha = if (big) 0.9f else 0.55f), Offset(x(f), h - 2.dp.toPx()), Offset(x(f), h - (if (big) 8 else if (mid) 6 else 4).dp.toPx()), (if (big) 1.1f else 0.7f) * density)
        }
        val px = x(mhz.coerceIn(88f, 108f))
        drawRect(Brush.radialGradient(listOf(Hifi.Red.copy(alpha = 0.5f), Color.Transparent), Offset(px, h / 2f), 12.dp.toPx()), Offset(px - 12.dp.toPx(), 0f), Size(24.dp.toPx(), h))
        drawRect(Hifi.Red, Offset(px - 1.dp.toPx(), 1.dp.toPx()), Size(2.dp.toPx(), h - 2.dp.toPx()))
        drawRect(Color.White.copy(alpha = 0.05f), Offset.Zero, Size(w, h * 0.35f))
    }
}

/** A strip of dark glass set into the plate, lit from behind by two warm lamps. */
internal fun DrawScope.drawGlassStrip(lamps: Float = 0.24f) {
    val w = size.width
    val h = size.height
    val one = 1.dp.toPx()
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(-one, -one), Size(w + 2 * one, h + one))
    drawRect(Color.White.copy(alpha = 0.45f), Offset(-one, h), Size(w + 2 * one, one))
    drawRect(Color(0xFF120E0A))
    for (lx in floatArrayOf(w * 0.22f, w * 0.78f)) {
        drawRect(Brush.radialGradient(listOf(Color(0xFFFFB45A).copy(alpha = lamps), Color.Transparent), Offset(lx, h * 1.1f), w * 0.35f))
    }
}

// ─── Cassette ────────────────────────────────────────────────────────────────

/**
 * A cassette: the smoked shell with its screws and the head opening at its foot; the label with
 * its side and type on a red band and the song written on it by hand; the window with the two
 * reels, the tape passing from one to the other as the song plays ([progress] 0..1), the hubs
 * turning while [playing].
 */
@Composable
fun Cassette(title: String, artist: String, progress: () -> Float, playing: Boolean, modifier: Modifier = Modifier, side: String = "A") {
    val clock = rememberFrameClock(playing)
    val measurer = rememberTextMeasurer()
    val unit = hifiSp(1f)
    val shortTitle = if (title.length > 14) title.take(13) + "…" else title
    val hand = remember(measurer, shortTitle, unit) { measurer.measure(shortTitle.ifEmpty { "空白磁带" }, TextStyle(fontFamily = LongCangFamily, fontSize = unit * 22f, color = Hifi.LabelInk), maxLines = 1, softWrap = false) }
    val shortArtist = if (artist.length > 10) artist.take(9) + "…" else artist
    val by = remember(measurer, shortArtist, unit) { measurer.measure(shortArtist, TextStyle(fontFamily = LongCangFamily, fontSize = unit * 12f, color = Hifi.LabelInk.copy(alpha = 0.8f)), maxLines = 1, softWrap = false) }
    val bandStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = unit * 9f, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em, color = Color.White)
    val sideText = remember(measurer, side, unit) { measurer.measure(side, bandStyle.copy(fontSize = unit * 13f, fontWeight = FontWeight.Black)) }
    val typeText = remember(measurer, unit) { measurer.measure("CrO2 · 60 MIN", bandStyle) }
    // The hubs turn by the tape's own clock: faster when the reel is small.
    val spin = remember { floatArrayOf(0f, 0f, -1f) }
    Canvas(modifier) {
        val p = progress().coerceIn(0f, 1f)
        val t = clock.floatValue
        val dt = if (spin[2] < 0f) 0f else (t - spin[2]).coerceIn(0f, 0.1f)
        spin[2] = t
        drawCassette(p, t, dt, spin, hand, by, sideText, typeText)
    }
}

internal fun DrawScope.drawCassette(
    p: Float,
    t: Float,
    dt: Float,
    spin: FloatArray,
    hand: androidx.compose.ui.text.TextLayoutResult,
    by: androidx.compose.ui.text.TextLayoutResult,
    sideText: androidx.compose.ui.text.TextLayoutResult,
    typeText: androidx.compose.ui.text.TextLayoutResult,
) {
    val w = size.width
    val h = size.height
    val u = w / 100f // the shell is 100 units wide, 64 tall
    val corner = CornerRadius(3.5f * u)
    // The shell.
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.6f * u), size, corner)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A332E), Hifi.Shell, Color(0xFF1E1A17))), cornerRadius = corner)
    drawRoundRect(Color.White.copy(alpha = 0.1f), Offset(0.5f * u, 0.5f * u), Size(w - u, h - u), corner, style = Stroke(0.5f * u))
    for (x in floatArrayOf(4f * u, w - 4f * u)) for (y in floatArrayOf(4f * u, h - 4f * u)) {
        drawCircle(Color(0xFF14110F), 1.5f * u, Offset(x, y))
        drawCircle(Color(0xFF8C8580), 1.1f * u, Offset(x, y))
        drawLine(Color(0xFF2A2522), Offset(x - 0.7f * u, y), Offset(x + 0.7f * u, y), 0.35f * u)
    }
    // The label.
    val lx = 7f * u
    val ly = 6f * u
    val lw = w - 14f * u
    val lh = h * 0.6f
    drawRoundRect(Hifi.Label, Offset(lx, ly), Size(lw, lh), CornerRadius(1.5f * u))
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.06f)), ly, ly + lh), Offset(lx, ly), Size(lw, lh))
    val bandH = 9f * u
    drawRect(Hifi.LabelBand, Offset(lx, ly + 1.5f * u), Size(lw, bandH))
    // The band's printing, sized to the band.
    val sideScale = 7f * u / sideText.size.height
    scaledAt(sideScale, Offset(lx + 3f * u, ly + 1.5f * u + (bandH - sideText.size.height * sideScale) / 2f)) { drawText(sideText) }
    val typeScale = 5.4f * u / typeText.size.height
    scaledAt(typeScale, Offset(lx + lw - 3f * u - typeText.size.width * typeScale, ly + 1.5f * u + (bandH - typeText.size.height * typeScale) / 2f)) { drawText(typeText) }
    // Ruled lines and the song, written on them.
    val lineY = ly + 1.5f * u + bandH + 9f * u
    drawLine(Hifi.LabelInk.copy(alpha = 0.18f), Offset(lx + 3f * u, lineY), Offset(lx + lw - 3f * u, lineY), 0.25f * u)
    // The song about 8 units tall, the artist 4.5, each narrowed to its share of the line.
    val handScale = minOf(8.5f * u / hand.size.height, lw * 0.6f / hand.size.width)
    rotate(-1.2f, Offset(lx + 4f * u, lineY)) {
        scaledAt(handScale, Offset(lx + 4f * u, lineY - hand.size.height * handScale * 0.86f)) { drawText(hand) }
    }
    if (by.size.width > 0) {
        val byScale = minOf(5f * u / by.size.height, lw * 0.28f / by.size.width)
        scaledAt(byScale, Offset(lx + lw - 4f * u - by.size.width * byScale, lineY - by.size.height * byScale * 0.9f)) { drawText(by) }
    }
    // The window, cut through the label: smoked plastic over the reels.
    val wx = lx + lw * 0.2f
    val wy = lineY + 3f * u
    val ww = lw * 0.6f
    val wh = ly + lh - 3f * u - wy
    val winR = CornerRadius(wh * 0.5f)
    // Smoked clear plastic: the cassette's insides show grey through it.
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6E655E), Color(0xFF4E4640), Color(0xFF3A332E)), wy, wy + wh), Offset(wx, wy), Size(ww, wh), winR)
    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(wx, wy), Size(ww, wh), winR, style = Stroke(0.5f * u))
    val hubR = wh * 0.26f
    val maxR = wh * 1.05f
    val leftC = Offset(wx + wh * 0.62f, wy + wh / 2f)
    val rightC = Offset(wx + ww - wh * 0.62f, wy + wh / 2f)
    val rl = sqrt(hubR * hubR * 1.4f + (maxR * maxR - hubR * hubR * 1.4f) * (1f - p))
    val rr = sqrt(hubR * hubR * 1.4f + (maxR * maxR - hubR * hubR * 1.4f) * p)
    // The tape packs, seen only through the window, wound round each hub.
    val window = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(wx, wy, wx + ww, wy + wh, winR)) }
    clipPath(window) {
        for ((center, radius) in listOf(leftC to rl, rightC to rr)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF3A2216), Color(0xFF2A170D), Color(0xFF1C0F08)), center, radius), radius, center)
            // The wound tape's sheen: fine rings, brighter where the lamp catches them.
            for (k in 1..4) drawCircle(Color(0xFFB07A4E).copy(alpha = 0.12f), radius * (0.4f + k * 0.14f), center, style = Stroke(0.25f * u))
            drawArc(Color(0xFFE0A070).copy(alpha = 0.18f), 200f, 60f, false, center - Offset(radius * 0.85f, radius * 0.85f), Size(radius * 1.7f, radius * 1.7f), style = Stroke(0.6f * u))
        }
    }
    // Hubs: white sprockets, turning by the tape's speed at each reel.
    val speed = 40f * u
    spin[0] += speed * dt / rl.coerceAtLeast(1f) * 57.3f
    spin[1] += speed * dt / rr.coerceAtLeast(1f) * 57.3f
    for ((k, center) in listOf(leftC, rightC).withIndex()) {
        drawCircle(Color(0xFFEFEAE0), hubR, center)
        rotate(spin[k], center) {
            for (j in 0 until 6) rotate(j * 60f, center) {
                drawRect(Color(0xFF2A241F), Offset(center.x - hubR * 0.13f, center.y - hubR * 0.92f), Size(hubR * 0.26f, hubR * 0.36f))
            }
        }
        drawCircle(Color(0xFF2A241F), hubR * 0.42f, center)
    }
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), wy, wy + wh * 0.6f), Offset(wx, wy), Size(ww, wh), winR)
    // The head opening at the foot: a trapezoid, its pressure-pad holes and guide pins.
    val headTop = ly + lh + 3.5f * u
    val head = Path().apply {
        moveTo(w * 0.17f, h); lineTo(w * 0.22f, headTop); lineTo(w * 0.78f, headTop); lineTo(w * 0.83f, h); close()
    }
    drawPath(head, Color(0xFF221D1A))
    drawPath(head, Color.White.copy(alpha = 0.06f), style = Stroke(0.4f * u))
    for (fx in floatArrayOf(0.3f, 0.42f, 0.5f, 0.58f, 0.7f)) {
        drawCircle(Color(0xFF0C0A09), (if (fx == 0.5f) 1.6f else 1.2f) * u, Offset(w * fx, (headTop + h) / 2f + 0.5f * u))
    }
}

/** Draw [block] scaled by [scale] from [at]. */
private inline fun DrawScope.scaledAt(scale: Float, at: Offset, crossinline block: DrawScope.() -> Unit) {
    withTransform({ translate(at.x, at.y); scale(scale, scale, Offset.Zero) }) { block() }
}

// ─── Discs ──────────────────────────────────────────────────────────────────

/** Under a compact disc's print: its shadow and the silver of the disc. */
internal fun DrawScope.drawDiscUnder() {
    val r = size.minDimension / 2f
    drawCircle(Color.Black.copy(alpha = 0.5f), r, center + Offset(0f, 2.dp.toPx()))
    drawCircle(Color(0xFFBFC4C9), r)
}

/**
 * Over a compact disc's print: the clear rim, a rainbow where the light catches the data side,
 * and the hub with its stacking ring and hole.
 */
internal fun DrawScope.drawDiscOver() {
    val r = size.minDimension / 2f
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.97f, style = Stroke(r * 0.05f))
    drawArc(
        Brush.sweepGradient(listOf(Color.Transparent, Color(0x55FF6A88), Color(0x55FFD36A), Color(0x556AF0C8), Color(0x556A9BFF), Color.Transparent), center),
        200f, 80f, false, Offset(r * 0.18f, r * 0.18f), Size(r * 1.64f, r * 1.64f), style = Stroke(r * 0.5f),
    )
    drawCircle(Color(0xFFDCDFE2).copy(alpha = 0.9f), r * 0.3f)
    drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.3f, style = Stroke(1f * density))
    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.22f, style = Stroke(0.8f * density))
    drawCircle(Color(0xFF1A1716), r * 0.11f)
}

/** A compact disc with [print] on it (a cover), fitted to a square. */
@Composable
fun CompactDisc(modifier: Modifier = Modifier, print: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawDiscUnder() }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(0.94f).clip(androidx.compose.foundation.shape.CircleShape)) { print() }
        Canvas(Modifier.fillMaxSize()) { drawDiscOver() }
    }
}

// ─── Counter ─────────────────────────────────────────────────────────────────

/** The tape counter: four white figures on black drums in a little window, the drums rounded by light. */
@Composable
fun MechCounter(value: Int, modifier: Modifier = Modifier, digits: Int = 4) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = hifiSp(13f), fontWeight = FontWeight.Bold, color = Color(0xFFF2EEE6))
    val text = "%0${digits}d".format(value % 10.0.pow(digits).toInt())
    val glyphs = remember(measurer) { (0..9).map { measurer.measure("$it", style) } }
    Canvas(modifier.size((digits * 11 + 4).dp, 20.dp)) {
        val one = 1.dp.toPx()
        drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, one), size)
        drawRect(Color(0xFF0B0A09))
        val cell = (size.width - 4.dp.toPx()) / digits
        for ((i, ch) in text.withIndex()) {
            val x = 2.dp.toPx() + i * cell
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF2A2826), Color(0xFF0A0A0A))), Offset(x + 0.5f * one, one), Size(cell - one, size.height - 2 * one))
            val g = glyphs[ch - '0']
            drawText(g, topLeft = Offset(x + (cell - g.size.width) / 2f, (size.height - g.size.height) / 2f))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.6f))))
    }
}
