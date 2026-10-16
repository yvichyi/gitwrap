package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.PostcardSerifFamily
import kotlin.math.PI
import kotlin.math.sin

/*
 * 暮's vocabulary: a postcard from the coast at the end of the day. Cream paper with a grain to
 * it, a sky that runs from blue through lilac to peach over the sea, a sun with a clean edge,
 * stamps with perforated edges, round postmarks with their wavy cancellations, the red and blue
 * stripes round an airmail envelope. By night it is the blue hour: deep blue going violet, the
 * afterglow low on the sea, the first stars.
 */

/** The airmail envelope's red and blue. */
object Airmail {
    val Red = Color(0xFFD2483A)
    val Blue = Color(0xFF2F5D8A)
}

/** The stamps' colours, a set: vermilion, sea blue, olive, plum. */
val StampInks = listOf(Color(0xFFCF5634), Color(0xFF2F5D8A), Color(0xFF6E8B4E), Color(0xFF8A4A6E))

/** The sky from its top to the horizon: golden hour by day, the blue hour by night. */
private fun skyStops(dark: Boolean): Array<Pair<Float, Color>> = if (dark) {
    arrayOf(0f to Color(0xFF0D1227), 0.38f to Color(0xFF1C2046), 0.66f to Color(0xFF382B57), 0.86f to Color(0xFF6B3E62), 1f to Color(0xFFB4626A))
} else {
    arrayOf(0f to Color(0xFFD5DEEA), 0.34f to Color(0xFFE6D5DF), 0.62f to Color(0xFFF5CDB8), 0.86f to Color(0xFFF8C79C), 1f to Color(0xFFF6B486))
}

/** The sun: a warm white with a clean edge by day; by night an ember low on the sea. */
private fun sunColor(dark: Boolean): Color = if (dark) Color(0xFFE8735C) else Color(0xFFFFC48A)

/** The sky's colours from its top to the horizon, as a brush over [height] from [top]. */
internal fun duskSkyBrush(dark: Boolean, top: Float, height: Float): Brush = Brush.verticalGradient(*skyStops(dark), startY = top, endY = top + height)

/** Film grain over the whole of it, a little heavier at night. */
private fun DrawScope.drawGrain(dark: Boolean, strength: Float = 1f) {
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = (if (dark) 0.12f else 0.09f) * strength)
}

/**
 * 暮's pages: cream paper, warmed a little at the top as by the low sun, with grain and the odd
 * fibre; at night the blue hour over the page with a few stars.
 */
internal fun DrawScope.drawDuskPaper(style: LanyinStyle) {
    val c = style.colors
    val w = size.width
    val h = size.height
    if (style.dark) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1D3A), c.bg, c.bg), endY = h))
        drawRect(Brush.radialGradient(listOf(Color(0xFF6B3E62).copy(alpha = 0.28f), Color.Transparent), Offset(w * 0.9f, 0f), w * 0.9f), size = Size(w, h * 0.5f))
        for (i in 0 until 16) {
            val p = Offset(w * hash01(i, 61), h * 0.32f * hash01(i, 62))
            drawCircle(Color.White.copy(alpha = 0.18f + 0.3f * hash01(i, 63)), (0.6f + 0.7f * hash01(i, 64)) * density, p)
        }
    } else {
        drawRect(c.bg)
        drawRect(Brush.verticalGradient(listOf(c.bgDeep.copy(alpha = 0.55f), Color.Transparent), endY = h * 0.3f), size = Size(w, h * 0.3f))
        // Fibres in the paper.
        for (i in 0 until 26) {
            val p = Offset(w * hash01(i, 66), h * hash01(i, 67))
            val len = (6f + 14f * hash01(i, 68)) * density
            val a = hash01(i, 69) * PI.toFloat()
            drawLine(c.content.copy(alpha = 0.05f), p, p + Offset(kotlin.math.cos(a) * len, sin(a) * len), 0.6f * density, StrokeCap.Round)
        }
    }
    drawGrain(style.dark, 0.8f)
}

/**
 * 暮's player: the sky from blue to peach (the blue hour by night) down to the sea, a sun with a
 * clean edge sinking toward the horizon as the song plays out, long streaks of cloud lit from
 * below drifting across, the sun's path on the water in glints, and grain over all of it.
 * [horizon] is where the sea starts, as a fraction of the height; the sea fades into the paper
 * below it so what stands there stays readable.
 */
internal fun DrawScope.drawDuskSky(style: LanyinStyle, t: Float, e: Float, audio: BackdropAudio?, progress: Float, horizon: Float, sunAt: Float, sunSize: Float) {
    val c = style.colors
    val dark = style.dark
    val w = size.width
    val h = size.height
    val sea = h * horizon
    drawRect(c.bg)
    drawRect(Brush.verticalGradient(*skyStops(dark), endY = sea), size = Size(w, sea))
    // The sea: darker than the sky, holding a little of its warmth at the horizon, fading to paper.
    val water = if (dark) Color(0xFF1B2142) else Color(0xFFC3CAD6)
    val seaEnd = sea + (h - sea) * (if (dark) 0.55f else 0.4f)
    drawRect(Brush.verticalGradient(0f to lerp(water, skyStops(dark).last().second, 0.45f), 0.3f to water, 1f to c.bg, startY = sea, endY = seaEnd), Offset(0f, sea), Size(w, seaEnd - sea))
    // Night coming on from the top as the song goes.
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF141A3A).copy(alpha = (if (dark) 0.2f else 0.28f) * p), Color.Transparent), endY = sea), size = Size(w, sea))
    if (dark || p > 0.6f) {
        val shown = if (dark) 1f else (p - 0.6f) / 0.4f
        for (i in 0 until 22) {
            val tw = 0.55f + 0.45f * sin(t * (1.1f + hash01(i, 51)) + i)
            drawCircle(Color.White.copy(alpha = 0.6f * shown * tw * (0.4f + 0.6f * hash01(i, 55))), (0.6f + 0.8f * hash01(i, 52)) * density, Offset(w * hash01(i, 53), sea * 0.55f * hash01(i, 54)))
        }
    }
    // The sun, sinking from well up to under the sea over the song, swelling a touch with the bass.
    val r = w.coerceAtMost(h) * sunSize * (1f + 0.04f * (audio?.bass ?: 0f) + 0.015f * e * sin(t * 1.3f))
    val sunY = sea - r * (1.7f - 2.2f * p)
    val sun = Offset(w * sunAt, sunY)
    val core = sunColor(dark)
    // Its light on the sky round it, then the disc itself, edge and all.
    drawCircle(Brush.radialGradient(listOf(core.copy(alpha = if (dark) 0.3f else 0.45f), Color.Transparent), sun, r * 3.4f), r * 3.4f, sun)
    clipRect(bottom = sea) {
        drawCircle(Brush.verticalGradient(listOf(lerp(core, Color.White, if (dark) 0.25f else 0.7f), core, lerp(core, c.accent, if (dark) 0.35f else 0.55f)), sun.y - r, sun.y + r), r, sun)
    }
    // The afterglow along the horizon.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, core.copy(alpha = if (dark) 0.35f else 0.5f), Color.Transparent), startY = sea - h * 0.04f, endY = sea + h * 0.012f), Offset(0f, sea - h * 0.04f), Size(w, h * 0.052f))
    drawCloudStreaks(style, sea, sun, audio?.travel(1, 0.6f + 0.6f * e) ?: (t * (0.6f + 0.6f * e)))
    // The sun's path on the water: glints in a column under it, shimmering with the treble.
    val glint = 0.6f + 0.4f * e + 0.8f * (audio?.treble ?: 0f)
    for (k in 0 until 7) {
        val y = sea + (3f + k * 5.5f) * density * (1f + k * 0.15f)
        val half = r * (0.95f - k * 0.11f) * (0.8f + 0.2f * sin(t * 1.4f + k * 1.9f))
        val drift = sin(t * 0.7f + k * 1.3f) * r * 0.08f
        drawLine(core.copy(alpha = ((0.5f - k * 0.06f) * glint).coerceIn(0f, 1f)), Offset(sun.x - half + drift, y), Offset(sun.x + half + drift, y), 1.6f * density, StrokeCap.Round)
    }
    drawGrain(dark)
}

/** Long streaks of cloud across the sky, lit warm on their undersides, nearer ones lower and quicker. */
private fun DrawScope.drawCloudStreaks(style: LanyinStyle, sea: Float, sun: Offset, drift: Float) {
    val w = size.width
    val dark = style.dark
    for (k in 0 until 5) {
        val len = w * (0.32f + 0.3f * hash01(k, 31))
        val thick = (3f + 5f * hash01(k, 32)) * density
        val speed = (3f + 2.5f * k) * density
        val x = w + len - ((drift * speed + hash01(k, 33) * w * 1.6f) % (w + len * 2))
        val y = sea * (0.3f + 0.12f * k + 0.05f * hash01(k, 34))
        // Lit more the nearer it passes the sun.
        val near = (1f - kotlin.math.abs(y - sun.y) / sea).coerceIn(0f, 1f)
        val body = if (dark) Color(0xFF4A3A66) else Color(0xFFF7E6E2)
        val lit = if (dark) Color(0xFFD27A74) else Color(0xFFFFC9A0)
        val color = lerp(body, lit, 0.3f + 0.6f * near)
        val alpha = if (dark) 0.5f else 0.75f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha), color.copy(alpha = alpha), Color.Transparent), x, x + len),
            Offset(x, y), Size(len, thick), CornerRadius(thick),
        )
    }
}

/**
 * A stamp: perforated paper round a picture of a sun setting on the sea in [ink], drawn into
 * [rect]. The perforations are cut in [paper]'s colour behind it.
 */
internal fun DrawScope.drawStamp(rect: Rect, ink: Color, paper: Color, holeColor: Color) {
    val hole = rect.width * 0.07f
    drawRect(paper, rect.topLeft, rect.size)
    // The perforations: bites out of all four edges.
    val stepX = rect.width / (((rect.width / (hole * 3.2f)).toInt()).coerceAtLeast(2))
    var x = rect.left + stepX / 2f
    while (x < rect.right) {
        drawCircle(holeColor, hole, Offset(x, rect.top))
        drawCircle(holeColor, hole, Offset(x, rect.bottom))
        x += stepX
    }
    val stepY = rect.height / (((rect.height / (hole * 3.2f)).toInt()).coerceAtLeast(2))
    var y = rect.top + stepY / 2f
    while (y < rect.bottom) {
        drawCircle(holeColor, hole, Offset(rect.left, y))
        drawCircle(holeColor, hole, Offset(rect.right, y))
        y += stepY
    }
    val inset = rect.width * 0.17f
    val inner = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
    drawRect(ink, inner.topLeft, inner.size)
    // The picture: a sun half down on the sea.
    val sea = inner.top + inner.height * 0.64f
    clipRect(inner.left, inner.top, inner.right, sea) {
        drawCircle(paper.copy(alpha = 0.92f), inner.width * 0.24f, Offset(inner.center.x, sea))
    }
    drawLine(paper.copy(alpha = 0.7f), Offset(inner.left + inner.width * 0.16f, sea + inner.height * 0.12f), Offset(inner.right - inner.width * 0.16f, sea + inner.height * 0.12f), inner.height * 0.05f, StrokeCap.Round)
}

/** The stamp before a section's title, in the set's next colour. */
@Composable
internal fun StampMark(index: Int?) {
    val c = Lanyin.colors
    val ink = StampInks[((index ?: 1) - 1).mod(StampInks.size)]
    val paper = if (Lanyin.style.dark) Color(0xFFEDE4D6) else Color.White
    Canvas(Modifier.size(15.dp, 18.dp).graphicsLayer { rotationZ = -4f }) {
        drawStamp(Rect(Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())), ink, paper, c.bg)
    }
}

/**
 * A round postmark, struck at an angle: two rings with [top] and [bottom] set round inside them
 * and [middle] large across the centre, and, with [waves], the wavy cancellation lines running
 * off to the left.
 */
@Composable
fun Postmark(top: String, middle: String, bottom: String, ink: Color, modifier: Modifier = Modifier, diameter: Dp = 50.dp, waves: Boolean = true, angle: Float = -12f) {
    Box(modifier.graphicsLayer { rotationZ = angle }, contentAlignment = Alignment.CenterEnd) {
        Canvas(Modifier.size(if (waves) diameter * 2.4f else diameter, diameter)) {
            val r = size.height / 2f
            val center = Offset(size.width - r, r)
            drawCircle(ink, r - 1.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            drawCircle(ink, r - 5.dp.toPx(), center, style = Stroke(0.8.dp.toPx()))
            if (waves) {
                for (k in -1..1) {
                    val path = Path()
                    val y0 = center.y + k * r * 0.36f
                    val end = center.x - r - 3.dp.toPx()
                    var x = 0f
                    path.moveTo(x, y0)
                    while (x < end) {
                        x += 2.dp.toPx()
                        path.lineTo(x, y0 + r * 0.1f * sin(x / (r * 0.28f) * PI.toFloat()))
                    }
                    drawPath(path, ink.copy(alpha = ink.alpha * 0.8f), style = Stroke(1.1.dp.toPx()))
                }
            }
        }
        Column(Modifier.size(diameter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sp = { v: Float -> with(density) { (diameter.value * v).dp.toSp() } }
            val label = TextStyle(fontWeight = FontWeight.Bold, fontSize = sp(0.13f), lineHeight = sp(0.16f), letterSpacing = 0.1.em, textAlign = TextAlign.Center)
            Text(top, style = label, color = ink, maxLines = 1)
            Text(middle, style = TextStyle(fontFamily = PostcardSerifFamily, fontSize = sp(0.3f), lineHeight = sp(0.34f), textAlign = TextAlign.Center), color = ink, maxLines = 1)
            Text(bottom, style = label, color = ink, maxLines = 1)
        }
    }
}

/** The red and blue stripes round an airmail envelope, along the whole of the canvas. */
internal fun DrawScope.drawAirmailStripes(stripe: Float = 9f * density, alpha: Float = 1f) {
    val path = Path()
    var x = -size.height
    var k = 0
    clipRect {
        while (x < size.width + size.height) {
            path.reset()
            path.moveTo(x, size.height)
            path.lineTo(x + stripe, size.height)
            path.lineTo(x + stripe + size.height, 0f)
            path.lineTo(x + size.height, 0f)
            path.close()
            if (k % 3 != 2) drawPath(path, (if (k % 3 == 0) Airmail.Red else Airmail.Blue).copy(alpha = alpha))
            x += stripe
            k++
        }
    }
}

/** A rounded rectangle with a border of airmail stripes [band] wide round its edge. */
internal fun DrawScope.drawAirmailBorder(band: Float, corner: Float) {
    val outer = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), CornerRadius(corner))) }
    val inner = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(band, band), Size(size.width - band * 2, size.height - band * 2)), CornerRadius((corner - band).coerceAtLeast(0f)))) }
    val ring = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outer, inner)
    clipPath(ring) { drawAirmailStripes(band * 0.9f) }
}

/**
 * 暮's sleeves: photos from the trip, grainy, whatever the time of day the app is showing. The
 * sun half into the sea with a sail on it; a lighthouse on its headland against a lilac sky;
 * palms in silhouette before a big low sun; a harbour town in the blue hour, its lights coming on
 * and doubled in the water under a new moon.
 */
internal fun DrawScope.duskPhoto(seed: Int) {
    val w = size.width
    val h = size.height
    val ink = Color(0xFF2B2036)
    when (seed.mod(4)) {
        0 -> {
            val sea = h * (0.6f + 0.06f * hash01(seed, 1))
            drawRect(duskSkyBrush(false, 0f, sea), size = Size(w, sea))
            val sun = Offset(w * (0.38f + 0.24f * hash01(seed, 2)), sea)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD6A8).copy(alpha = 0.7f), Color.Transparent), sun, w * 0.5f), w * 0.5f, sun)
            clipRect(bottom = sea) { drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF0D8), Color(0xFFF79A62)), sun.y - w * 0.17f, sun.y), w * 0.17f, sun) }
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8C8FB0), Color(0xFF3F4770)), startY = sea, endY = h), Offset(0f, sea), Size(w, h - sea))
            for (k in 0 until 6) {
                val y = sea + h * (0.03f + k * 0.045f)
                val half = w * (0.16f - k * 0.022f)
                drawLine(Color(0xFFFFD9AE).copy(alpha = 0.75f - k * 0.1f), Offset(sun.x - half, y), Offset(sun.x + half, y), h * 0.012f, StrokeCap.Round)
            }
            // A sail far out.
            val boat = Offset(w * (0.18f + 0.1f * hash01(seed, 3)), sea - h * 0.005f)
            drawPath(Path().apply { moveTo(boat.x, boat.y); lineTo(boat.x, boat.y - h * 0.11f); lineTo(boat.x + w * 0.055f, boat.y); close() }, ink.copy(alpha = 0.85f))
            drawRect(ink.copy(alpha = 0.85f), Offset(boat.x - w * 0.03f, boat.y), Size(w * 0.1f, h * 0.012f))
        }
        1 -> {
            val sea = h * 0.7f
            drawRect(Brush.verticalGradient(listOf(Color(0xFFB9B4D6), Color(0xFFE9C3CF), Color(0xFFF7C49C)), endY = sea), size = Size(w, sea))
            drawCircle(Color(0xFFFFE6C4), w * 0.09f, Offset(w * 0.24f, sea * 0.78f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7D86A8), Color(0xFF454D73)), startY = sea, endY = h), Offset(0f, sea), Size(w, h - sea))
            // The headland and the lighthouse on it, its lamp lit.
            val land = Path().apply {
                moveTo(w * 0.45f, sea)
                cubicTo(w * 0.6f, sea - h * 0.12f, w * 0.75f, sea - h * 0.2f, w, sea - h * 0.22f)
                lineTo(w, sea); close()
            }
            drawPath(land, ink)
            val base = Offset(w * 0.74f, sea - h * 0.17f)
            drawPath(Path().apply { moveTo(base.x - w * 0.03f, base.y); lineTo(base.x - w * 0.018f, base.y - h * 0.22f); lineTo(base.x + w * 0.018f, base.y - h * 0.22f); lineTo(base.x + w * 0.03f, base.y); close() }, Color(0xFFF4EDE4))
            for (k in 0..1) drawRect(Color(0xFFCF5634), Offset(base.x - w * 0.028f + k * w * 0.004f, base.y - h * (0.06f + k * 0.08f)), Size(w * 0.056f - k * w * 0.008f, h * 0.03f))
            val lamp = Offset(base.x, base.y - h * 0.25f)
            drawRect(ink, Offset(lamp.x - w * 0.022f, lamp.y - h * 0.01f), Size(w * 0.044f, h * 0.04f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9B0), Color.Transparent), lamp, w * 0.16f), w * 0.16f, lamp)
            drawPath(Path().apply { moveTo(lamp.x, lamp.y); lineTo(0f, lamp.y - h * 0.12f); lineTo(0f, lamp.y + h * 0.06f); close() }, Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFF1C8).copy(alpha = 0.35f)), 0f, lamp.x))
        }
        2 -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF2A07A), Color(0xFFF7C68E), Color(0xFFFFE0B0))))
            val sun = Offset(w * 0.5f, h * 0.62f)
            drawCircle(Color(0xFFFFF2DA), w * 0.3f, sun)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE48C6A), Color(0xFF8B5A78)), startY = h * 0.78f, endY = h), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            // Palms: a leaning trunk and a crown of fronds.
            for (k in 0..1) {
                val root = Offset(w * (if (k == 0) 0.16f else 0.86f), h * 0.8f)
                val lean = if (k == 0) 1f else -1f
                val top = Offset(root.x + lean * w * (0.12f + 0.06f * hash01(seed, 4 + k)), h * (0.22f + 0.1f * k))
                drawPath(Path().apply { moveTo(root.x, root.y); quadraticTo(root.x + lean * w * 0.02f, (root.y + top.y) / 2f, top.x, top.y) }, ink, style = Stroke(w * 0.022f, cap = StrokeCap.Round))
                for (f in 0 until 7) {
                    val a = (-170f + f * 30f + lean * 10f) * PI.toFloat() / 180f
                    val reach = w * (0.17f + 0.04f * (f % 2))
                    val tip = Offset(top.x + kotlin.math.cos(a) * reach, top.y + sin(a) * reach * 0.6f + reach * 0.35f)
                    val mid = Offset((top.x + tip.x) / 2f, minOf(top.y, tip.y) - reach * 0.12f)
                    drawPath(Path().apply { moveTo(top.x, top.y); quadraticTo(mid.x, mid.y, tip.x, tip.y) }, ink, style = Stroke(w * 0.016f, cap = StrokeCap.Round))
                }
            }
        }
        else -> {
            val sea = h * 0.64f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF111833), Color(0xFF2C2A55), Color(0xFF6A4566)), endY = sea), size = Size(w, sea))
            for (i in 0 until 14) drawCircle(Color.White.copy(alpha = 0.3f + 0.5f * hash01(seed * 7 + i, 5)), w * 0.004f, Offset(w * hash01(seed * 7 + i, 6), sea * 0.6f * hash01(seed * 7 + i, 7)))
            // The new moon, a crescent.
            val moon = Offset(w * 0.76f, h * 0.18f)
            val crescent = Path.combine(
                androidx.compose.ui.graphics.PathOperation.Difference,
                Path().apply { addOval(Rect(moon, w * 0.07f)) },
                Path().apply { addOval(Rect(moon + Offset(w * 0.03f, -w * 0.015f), w * 0.065f)) },
            )
            drawPath(crescent, Color(0xFFFFEBC8))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2B4E), Color(0xFF0E1328)), startY = sea, endY = h), Offset(0f, sea), Size(w, h - sea))
            // The town along the shore, its windows lit, and their light in the water.
            var x = 0f
            var n = 0
            while (x < w) {
                val bw = w * (0.06f + 0.06f * hash01(seed * 13 + n, 8))
                val bh = h * (0.05f + 0.1f * hash01(seed * 13 + n, 9))
                drawRect(Color(0xFF151A33), Offset(x, sea - bh), Size(bw, bh))
                if (hash01(seed * 13 + n, 10) > 0.3f) {
                    val win = Offset(x + bw * 0.4f, sea - bh * 0.6f)
                    drawRect(Color(0xFFFFC77A), win, Size(w * 0.012f, h * 0.014f))
                    drawLine(Color(0xFFFFC77A).copy(alpha = 0.45f), Offset(win.x, sea + h * 0.02f), Offset(win.x, sea + h * (0.08f + 0.08f * hash01(n, 11))), w * 0.01f)
                }
                x += bw
                n++
            }
        }
    }
    drawGrain(false, 1.3f)
}

/**
 * A luggage tag: kraft card with its end cut off at the corners, an eyelet through a band of
 * [band], and [label] and [note] written on it. Set down at a slight [tilt].
 */
@Composable
fun LuggageTag(icon: LineIcon, label: String, note: String, band: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier, tilt: Float = 0f) {
    val style = Lanyin.style
    val c = style.colors
    val kraft = if (style.dark) c.surfaceAlt else Color(0xFFEBDCC3)
    val bandInk = if (style.dark) lerp(band, Color.White, 0.4f) else band
    androidx.compose.foundation.layout.Row(
        modifier
            .heightIn(min = 58.dp)
            .graphicsLayer { rotationZ = tilt }
            .drawBehind {
                val bevel = 12.dp.toPx()
                val tag = Path().apply {
                    moveTo(bevel, 0f)
                    lineTo(size.width - 6.dp.toPx(), 0f)
                    quadraticTo(size.width, 0f, size.width, 6.dp.toPx())
                    lineTo(size.width, size.height - 6.dp.toPx())
                    quadraticTo(size.width, size.height, size.width - 6.dp.toPx(), size.height)
                    lineTo(bevel, size.height)
                    lineTo(0f, size.height - bevel)
                    lineTo(0f, bevel)
                    close()
                }
                translate(0f, 2.dp.toPx()) { drawPath(tag, Color.Black.copy(alpha = if (style.dark) 0.3f else 0.06f)) }
                drawPath(tag, kraft)
                // The band across the tag's end, and the eyelet through the card.
                drawRect(band.copy(alpha = if (style.dark) 0.75f else 0.9f), Offset(26.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
                val eye = Offset(13.dp.toPx(), size.height / 2f)
                drawCircle(c.bg, 4.dp.toPx(), eye)
                drawCircle(c.muted.copy(alpha = 0.7f), 5.5.dp.toPx(), eye, style = Stroke(1.5.dp.toPx()))
            }
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(start = 38.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedIcon(icon, null, tint = bandInk, size = 18.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, style = style.type.caption.copy(fontSize = fixedCaption()), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun fixedCaption() = with(androidx.compose.ui.platform.LocalDensity.current) { 10.5.dp.toSp() }
