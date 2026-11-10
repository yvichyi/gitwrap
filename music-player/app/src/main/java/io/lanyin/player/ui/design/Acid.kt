package io.lanyin.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 酸 is a wall of rave flyers: wheat-pasted on concrete, layer over layer, the old ones torn.
 * Its colours are the flyers': acid lime, photocopy black, the white of copier paper and the
 * grey of the wall.
 */
object Acid {
    val Lime = Color(0xFFD4FF26)
    val Black = Color(0xFF141413)
    val Charcoal = Color(0xFF1E1E1C)
    val Paper = Color(0xFFEFEDE6)
    val Grey = Color(0xFF8C8B86)
    val Concrete = Color(0xFF3A3A38)
    /** Masking tape, a little see-through. */
    val Tape = Color(0xCCD9D1B8)
}

/** Kept for the places that set type in "chrome": on the wall, that's the lime. */
val ChromeStops = arrayOf(0f to Acid.Lime, 1f to Acid.Lime)

/** Lime, as a brush: what was chrome lettering is now the flyers' acid green. */
val ChromeBrush: Brush = SolidColor(Acid.Lime)

/** A rim that shouldn't shout: the paper's white, faint. */
val ChromeSoftBrush: Brush = SolidColor(Acid.Paper.copy(alpha = 0.22f))

/** The paper stocks on the wall. The dark ones carry white type; lime and white carry black. */
object Flavor {
    val Bondi = Acid.Lime
    val Tangerine = Acid.Paper
    val Grape = Acid.Black
    val Strawberry = Acid.Lime
    val Blueberry = Acid.Charcoal
    /** Dark stocks only, for the flyers that hold the page's own white text. */
    val all = listOf(Acid.Black, Acid.Charcoal, Color(0xFF191918), Acid.Charcoal, Acid.Black)
}

/** Photocopies have no gloss: a few specks of toner instead. */
fun DrawScope.drawGloss(strength: Float = 0.38f) {
    val n = (size.width * size.height / (900f * density * density)).toInt().coerceIn(0, 60)
    for (k in 0 until n) {
        val p = Offset(size.width * hash01(k, 301), size.height * hash01(k, 302))
        drawCircle(Color.Black.copy(alpha = 0.08f * strength / 0.38f), (0.4f + hash01(k, 303)) * density, p)
    }
}

/**
 * A flyer of [tint] stock, cut to [shape]: flat paper with toner specks and, with [rim], the
 * faint light edge where it lifts from the wall. ([glossOver] is kept for its callers.)
 */
fun Modifier.candyPlastic(tint: Color, shape: Shape, rim: Boolean = true, @Suppress("UNUSED_PARAMETER") glossOver: Boolean = false): Modifier = this
    .clip(shape)
    .background(tint)
    .drawBehind { drawGloss(0.3f) }
    .then(if (rim) Modifier.border(1.dp, Acid.Paper.copy(alpha = 0.16f), shape) else Modifier)

/** A round sticker of [tint]: dark ones get a lime ring and a dot, light ones a black ring. */
fun DrawScope.drawGelBead(tint: Color) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val dark = tint.luminance() < 0.3f
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c + Offset(0f, 1f * density))
    drawCircle(tint, r, c)
    drawCircle(if (dark) Acid.Lime else Acid.Black, r * 0.86f, c, style = Stroke(r * 0.16f))
    drawCircle(if (dark) Acid.Lime else Acid.Black, r * 0.22f, c)
}

/** A round sticker button of [tint], its icon in ink or paper to suit. */
@Composable
fun GelButton(icon: LineIcon, tint: Color, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val dark = tint.luminance() < 0.3f
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(Color.Black.copy(alpha = 0.4f), this.size.minDimension / 2f, center + Offset(0f, 2.dp.toPx()))
                drawCircle(tint)
                drawCircle(if (dark) Acid.Lime else Acid.Black, this.size.minDimension / 2f - 1.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
            .pressable(label = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ThemedIcon(icon, null, tint = if (dark) Acid.Paper else Acid.Black, size = size * 0.42f)
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

/** The rave asterisk: six strokes from a point, [r] long. */
internal fun DrawScope.drawAsterisk(center: Offset, r: Float, color: Color, width: Float = r * 0.32f) {
    for (k in 0 until 3) {
        val a = k * PI.toFloat() / 3f + PI.toFloat() / 2f
        val d = Offset(cos(a) * r, sin(a) * r)
        drawLine(color, center - d, center + d, width)
    }
}

/** A crosshair registration mark of [r] at [center]. */
internal fun DrawScope.drawCrosshair(center: Offset, r: Float, color: Color) {
    val w = 1.2f * density
    drawCircle(color, r * 0.6f, center, style = Stroke(w))
    drawLine(color, center - Offset(r, 0f), center + Offset(r, 0f), w)
    drawLine(color, center - Offset(0f, r), center + Offset(0f, r), w)
}

/** A strip of masking tape [w] by [h] centred at [at], turned [deg] degrees, its ends torn. */
internal fun DrawScope.drawTapeStrip(at: Offset, w: Float, h: Float, deg: Float, color: Color = Acid.Tape, seed: Int = 0) {
    rotate(deg, at) {
        val left = at.x - w / 2f
        val top = at.y - h / 2f
        val path = Path().apply {
            moveTo(left, top)
            lineTo(left + w, top)
            // Torn ends: a zigzag down each.
            val teeth = 5
            for (k in 1..teeth) lineTo(left + w - (if (k % 2 == 1) 2.5f else 0f) * density * (0.6f + hash01(seed + k, 5)), top + h * k / teeth)
            lineTo(left, top + h)
            for (k in teeth - 1 downTo 0) lineTo(left + (if (k % 2 == 1) 2.5f else 0f) * density * (0.6f + hash01(seed + k, 6)), top + h * k / teeth)
            close()
        }
        drawPath(path, Color.Black.copy(alpha = 0.18f))
        drawPath(path, color)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(left, top + 1f), Offset(left + w, top + 1f), 1f * density)
    }
}

/** A staple at [at], [len] long, turned [deg] degrees: a silver bar with its shadow. */
internal fun DrawScope.drawStaple(at: Offset, len: Float, deg: Float) {
    rotate(deg, at) {
        drawLine(Color.Black.copy(alpha = 0.45f), at + Offset(-len / 2f, 1.2f * density), at + Offset(len / 2f, 1.2f * density), 1.6f * density)
        drawLine(Color(0xFFC9CAC8), at + Offset(-len / 2f, 0f), at + Offset(len / 2f, 0f), 1.4f * density)
        drawLine(Color.White.copy(alpha = 0.6f), at + Offset(-len / 2f, -0.4f * density), at + Offset(len / 4f, -0.4f * density), 0.6f * density)
    }
}

/**
 * Paper torn along the bottom edge of [size]: the outline of a sheet whose foot is ragged.
 * [depth] is how deep the tears go.
 */
internal fun tornPath(size: Size, depth: Float, seed: Int, top: Boolean = false): Path = Path().apply {
    val step = depth * 1.6f
    val n = (size.width / step).toInt().coerceAtLeast(2)
    if (top) {
        moveTo(0f, depth * hash01(seed, 1))
        for (k in 1..n) lineTo(size.width * k / n, depth * hash01(seed + k, 1))
    } else {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
    }
    lineTo(size.width, size.height - depth * hash01(seed + n + 1, 2))
    for (k in n - 1 downTo 0) lineTo(size.width * k / n, size.height - depth * hash01(seed + k, 2))
    close()
}

/**
 * A flyer filling [size]: [stock] paper with its ragged foot, a soft shadow on the wall, toner
 * specks; a staple at the top for a [stapled] one.
 */
internal fun DrawScope.drawFlyer(stock: Color, seed: Int, torn: Boolean = true, stapled: Boolean = false) {
    val path = if (torn) tornPath(size, 5f * density, seed) else Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
    translate(2f * density, 3f * density) { drawPath(path, Color.Black.copy(alpha = 0.35f)) }
    drawPath(path, stock)
    // Creases from the paste brush, faint.
    val crease = if (stock.luminance() > 0.4f) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.03f)
    for (k in 0 until 2) {
        val y = size.height * (0.25f + 0.45f * hash01(seed + k, 9))
        drawLine(crease, Offset(0f, y), Offset(size.width, y + size.height * 0.04f * (hash01(seed + k, 10) - 0.5f)), 2f * density)
    }
    drawGloss(0.3f)
    if (stapled) drawStaple(Offset(size.width / 2f, 6f * density), 10f * density, -8f + 16f * hash01(seed, 11))
}

/**
 * 酸: the wall. Grey concrete with its pores and stains; behind the pages, the ghosts of torn
 * posters: big type half-scraped off, scraps of white paper with their paste marks. Nothing
 * moves; the wall is a wall.
 */
internal fun DrawScope.drawAcidNight(style: LanyinStyle, @Suppress("UNUSED_PARAMETER") t: Float, @Suppress("UNUSED_PARAMETER") e: Float, page: Boolean, @Suppress("UNUSED_PARAMETER") audio: BackdropAudio?) {
    val w = size.width
    val h = size.height
    drawRect(style.colors.bg)
    drawRect(concreteBrush, alpha = 0.55f)
    // Damp at the foot and a lighter wash where the streetlight falls.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = h * 0.6f, endY = h))
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), Offset(w * 0.3f, h * 0.15f), w * 0.9f), w * 0.9f, Offset(w * 0.3f, h * 0.15f))
    // Scraps of old posters, torn.
    for (k in 0 until 5) {
        val x = w * hash01(k, 401)
        val y = h * hash01(k, 402)
        val sw = w * (0.18f + 0.25f * hash01(k, 403))
        val sh = sw * (0.4f + 0.6f * hash01(k, 404))
        rotate(-6f + 12f * hash01(k, 405), Offset(x, y)) {
            translate(x - sw / 2f, y - sh / 2f) {
                val scrap = tornPath(Size(sw, sh), 7f * density, k * 13, top = true)
                drawPath(scrap, (if (k % 3 == 0) Acid.Lime else Acid.Paper).copy(alpha = if (page) 0.07f else 0.1f))
            }
        }
    }
    // Ghost type: what's left of last month's headliner.
    if (page) return
    val big = w * 0.36f
    for ((k, word) in listOf("ACID", "303").withIndex()) {
        val y = h * (0.12f + 0.5f * k)
        for ((i, _) in word.withIndex()) {
            val x = w * (0.05f + 0.24f * i)
            drawRect(Color.Black.copy(alpha = 0.08f), Offset(x, y), Size(big * 0.5f, big * 0.62f))
        }
    }
}

/** A tile of concrete, made once: grey noise, the odd pore, larger blotches of damp. */
private val concreteTile: ImageBitmap by lazy {
    val n = 256
    val rnd = java.util.Random(23)
    val px = IntArray(n * n)
    val blot = FloatArray(n * n)
    // Soft blotches.
    repeat(18) {
        val cx = rnd.nextInt(n)
        val cy = rnd.nextInt(n)
        val r = 10 + rnd.nextInt(40)
        val s = (rnd.nextFloat() - 0.5f) * 0.5f
        for (y in -r..r) for (x in -r..r) {
            val d = hypot(x.toFloat(), y.toFloat()) / r
            if (d < 1f) {
                val i = ((cy + y).mod(n)) * n + (cx + x).mod(n)
                blot[i] += s * (1f - d * d)
            }
        }
    }
    for (i in 0 until n * n) {
        var v = 128 + (rnd.nextGaussian() * 18).toInt() + (blot[i] * 60).toInt()
        if (rnd.nextFloat() < 0.004f) v -= 70
        v = v.coerceIn(0, 255)
        px[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val concreteBrush: Brush by lazy {
    androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(concreteTile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))
}

/** Kept for the old player windows: a speck of toner where a bubble used to rise. */
internal fun DrawScope.drawBubble(center: Offset, r: Float, tint: Color, alpha: Float) {
    drawCircle(tint.copy(alpha = 0.3f * alpha), r * 0.2f, center)
}

/**
 * 酸's progress: a ruler printed on the flyer. Ticks along it (every fourth longer, every
 * sixteenth longest), the part played filled lime under them, and a lime asterisk where the
 * song is; it grows while dragged.
 */
internal fun DrawScope.drawStripeTrack(f: Float, @Suppress("UNUSED_PARAMETER") phase: Float, dragging: Float, style: LanyinStyle) {
    val c = style.colors
    val base = size.height / 2f + 7f * density
    val x = size.width * f
    drawRect(Acid.Lime, Offset(0f, base - 3f * density), Size(x, 3f * density))
    drawLine(c.content.copy(alpha = 0.7f), Offset(0f, base), Offset(size.width, base), 1f * density)
    val pitch = 4f * density
    var i = 0
    var tx = 0f
    while (tx <= size.width + 0.5f) {
        val len = when {
            i % 16 == 0 -> 14f
            i % 4 == 0 -> 9f
            else -> 5f
        } * density
        val played = tx <= x
        drawLine(if (played) Acid.Lime else c.content.copy(alpha = 0.55f), Offset(tx, base), Offset(tx, base - len), 1f * density)
        tx += pitch
        i++
    }
    drawAsterisk(Offset(x, base - 12f * density), (6f + 2f * dragging) * density, Acid.Lime, 2.2f * density)
}

/** 酸's play key: a lime disc in a black ring inside a lime one, as stickered on the poster. */
internal fun DrawScope.drawLimeDisc(@Suppress("UNUSED_PARAMETER") style: LanyinStyle, glow: Float) {
    val r = size.minDimension / 2f
    drawCircle(Acid.Lime, r * (1f + 0.03f * glow), style = Stroke(2.5f * density))
    drawCircle(Acid.Black, r - 2.5f * density)
    drawCircle(Acid.Lime, r - 6f * density)
}

/** Lime asterisks flashing out round 酸's disc on the beat. */
internal fun DrawScope.drawBeatBurst(@Suppress("UNUSED_PARAMETER") style: LanyinStyle, beat: Float, turn: Float) {
    if (beat <= 0f) return
    val r = size.minDimension / 2f
    val out = r * (1.25f + 0.35f * (1f - beat))
    for (k in 0 until 6) {
        val a = turn + k * PI.toFloat() / 3f
        drawAsterisk(center + Offset(cos(a) * out, sin(a) * out), 3.5f * density, Acid.Lime.copy(alpha = beat), 1.4f * density)
    }
}

/**
 * 酸's generated sleeves, photocopied: a full moon over black water, a crowd under strobes, a
 * lighthouse throwing its beam, a road at night; each printed in a coarse halftone, black on
 * copier paper.
 */
internal fun DrawScope.acidCover(style: LanyinStyle, seed: Int) {
    drawRect(Acid.Paper)
    val k = seed % 4
    val turn = hash01(seed, 1)
    drawHalftone(style.copy(dark = false), lines = 34f, ink = Acid.Black) { u, v -> acidScene(k, turn, u, v) }
}

/** How light each of the four photocopied scenes is at ([u], [v]). */
internal fun acidScene(kind: Int, turn: Float, u: Float, v: Float): Float = when (kind) {
    0 -> {
        // A moon over the sea, its path on the water.
        val moon = hypot(u - 0.62f + turn * 0.1f, v - 0.3f)
        when {
            moon < 0.14f -> 0.95f
            v < 0.58f -> 0.15f + 0.25f * (1f - moon).coerceAtLeast(0f) * 0.5f
            else -> if (abs(u - 0.62f + turn * 0.1f) < 0.06f + (v - 0.58f) * 0.4f) 0.75f - (v - 0.58f) else 0.1f
        }
    }
    1 -> {
        // A crowd under the strobes: beams from the top, heads at the foot.
        val beam = (0..2).maxOf { b ->
            val bx = 0.2f + 0.3f * b + 0.1f * turn
            (1f - abs(u - bx - (v - 0.1f) * (b - 1) * 0.35f) * 6f).coerceIn(0f, 1f) * (1f - v * 0.6f)
        }
        val heads = v > 0.78f - 0.05f * sin(u * 40f)
        if (heads) 0.05f else 0.12f + 0.8f * beam
    }
    2 -> {
        // A lighthouse on a rock, its beam across the dark.
        val tower = abs(u - 0.3f) < 0.04f + (v - 0.4f) * 0.05f && v in 0.4f..0.8f
        val rock = v > 0.8f - 0.06f * sin(u * 9f)
        val beam = (1f - abs((v - 0.42f) - (u - 0.3f) * 0.25f) * 8f).coerceIn(0f, 1f) * (if (u > 0.3f) 1f else 0f)
        when {
            tower -> if ((v * 20).toInt() % 3 == 0) 0.2f else 0.85f
            rock -> 0.05f
            else -> 0.1f + 0.85f * beam
        }
    }
    else -> {
        // A road at night: streetlights to a vanishing point.
        val road = abs(u - 0.5f) < (v - 0.45f) * 0.9f && v > 0.45f
        val lights = (0 until 5).maxOf { l ->
            val ly = 0.45f + l * l * 0.025f
            val lx = 0.5f + (0.08f + l * 0.09f) * (if (l % 2 == 0) 1 else -1)
            (1f - hypot(u - lx, v - ly) * (18f - l * 2f)).coerceIn(0f, 1f)
        }
        when {
            road -> 0.3f + 0.3f * ((v * 30).toInt() % 2)
            else -> 0.08f + 0.9f * lights
        }
    }
}

/** 酸's motif: the acid smiley, its grin dripping. */
internal fun DrawScope.acidMotif(@Suppress("UNUSED_PARAMETER") color: Color, @Suppress("UNUSED_PARAMETER") accent: Color) {
    val r = size.minDimension * 0.34f
    drawSmiley(center, r, Acid.Lime, Acid.Black)
}

/** The smiley sticker: a [face] disc with [ink] eyes and grin, a drip off its chin. */
internal fun DrawScope.drawSmiley(center: Offset, r: Float, face: Color, ink: Color, turn: Float = -12f) {
    rotate(turn, center) {
        drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, r * 0.06f))
        drawCircle(face, r, center)
        drawCircle(face, r * 0.18f, center + Offset(r * 0.35f, r * 1.0f))
        drawRect(face, center + Offset(r * 0.27f, r * 0.75f), Size(r * 0.16f, r * 0.3f))
        drawOval(ink, center + Offset(-r * 0.42f, -r * 0.5f), Size(r * 0.22f, r * 0.42f))
        drawOval(ink, center + Offset(r * 0.2f, -r * 0.5f), Size(r * 0.22f, r * 0.42f))
        val grin = Path().apply {
            moveTo(center.x - r * 0.62f, center.y + r * 0.08f)
            quadraticTo(center.x, center.y + r * 0.95f, center.x + r * 0.62f, center.y + r * 0.08f)
        }
        drawPath(grin, ink, style = Stroke(r * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** A barcode [size], its bars from [seed]. */
internal fun DrawScope.drawBarcode(color: Color, seed: Int) {
    var x = 0f
    var k = 0
    while (x < size.width) {
        val w = (1f + 2.2f * hash01(seed + k, 31)) * density
        if (k % 2 == 0) drawRect(color, Offset(x, 0f), Size(w, size.height))
        x += w + (0.8f + 1.4f * hash01(seed + k, 32)) * density
        k++
    }
}
