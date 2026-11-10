package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 墨's scholar's desk: xuan paper with its fibres, ink-wash landscapes painted in code, seals in
 * cinnabar, the silk mount of a hanging scroll, and the inkstone the record turns into.
 */
object InkDesk {
    val Cinnabar = Color(0xFFB0302A)
    val CinnabarNight = Color(0xFFC8463C)
    /** The pale celadon silk of a mount by day, and its dark indigo by lamplight. */
    val Silk = Color(0xFFD7D5C6)
    val SilkNight = Color(0xFF23272A)
    val Wood = Color(0xFF4A2F1E)
    val Stone = Color(0xFF2A2927)
    val Bamboo = Color(0xFFB89A62)
}

/**
 * A tile of xuan paper, made once: a grey ground (overlaid on the page's colour) with soft
 * cloudy mottling and long thin fibres, some lighter, some darker, wrapped so it tiles.
 */
internal val xuanTile: ImageBitmap by lazy {
    val n = 384
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    cv.drawColor(0xFF808080.toInt())
    val rnd = java.util.Random(19)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // Cloudy mottling.
    repeat(36) {
        val x = rnd.nextFloat() * n
        val y = rnd.nextFloat() * n
        val r = 20f + rnd.nextFloat() * 70f
        val light = rnd.nextBoolean()
        paint.shader = android.graphics.RadialGradient(x, y, r, if (light) 0x14FFFFFF else 0x10000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        for (dx in intArrayOf(-n, 0, n)) for (dy in intArrayOf(-n, 0, n)) {
            cv.save(); cv.translate(dx.toFloat(), dy.toFloat()); cv.drawCircle(x, y, r, paint); cv.restore()
        }
    }
    paint.shader = null
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    // Fibres: thin curved strands, mostly lighter than the paper, some darker.
    repeat(260) {
        val x = rnd.nextFloat() * n
        val y = rnd.nextFloat() * n
        val len = 18f + rnd.nextFloat() * 80f
        val a = rnd.nextFloat() * PI.toFloat() * 2f
        val bend = (rnd.nextFloat() - 0.5f) * len * 0.6f
        val path = android.graphics.Path().apply {
            moveTo(x, y)
            quadTo(x + cos(a) * len / 2f - sin(a) * bend, y + sin(a) * len / 2f + cos(a) * bend, x + cos(a) * len, y + sin(a) * len)
        }
        paint.strokeWidth = 0.5f + rnd.nextFloat() * 1.1f
        paint.color = if (rnd.nextFloat() < 0.75f) 0x55FFFFFF else 0x22000000
        for (dx in intArrayOf(-n, 0, n)) for (dy in intArrayOf(-n, 0, n)) {
            cv.save(); cv.translate(dx.toFloat(), dy.toFloat()); cv.drawPath(path, paint); cv.restore()
        }
    }
    bmp.asImageBitmap()
}

internal val xuanBrush: Brush by lazy { ShaderBrush(ImageShader(xuanTile, TileMode.Repeated, TileMode.Repeated)) }

/** Xuan paper over a draw scope, for callers outside this package. */
fun drawXuanPage(scope: DrawScope, style: LanyinStyle) = scope.drawXuan(style)

/** Xuan paper over [size]: the page's colour with its fibres and mottling laid over it. */
internal fun DrawScope.drawXuan(style: LanyinStyle, fill: Color = style.colors.bg) {
    drawRect(fill)
    drawRect(xuanBrush, alpha = if (style.dark) 0.16f else 0.32f, blendMode = BlendMode.Overlay)
}

// ─── Seals ───────────────────────────────────────────────────────────────────

/**
 * A seal stamped in cinnabar: a square of red with the slightly ragged edge and the bare specks
 * a stone seal leaves, [text] carved out of it (白文, [carved]) or standing in red inside a red
 * border (朱文). One character fills it; two run down it; four read in two columns from the
 * right.
 */
@Composable
fun InkSeal(text: String, size: Dp, modifier: Modifier = Modifier, carved: Boolean = true, color: Color = sealColor(), seed: Int = text.hashCode()) {
    val paper = Lanyin.colors.bg
    Box(modifier.size(size).drawBehind { drawSealStone(color, paper, carved, seed) }, contentAlignment = Alignment.Center) {
        val ink = if (carved) paper else color
        val face = TextStyle(fontFamily = FontFamily.Serif, color = ink)
        val chars = text.toList()
        when (chars.size) {
            1 -> Text(text, style = face.copy(fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.62f).toSp() }, lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.66f).toSp() }))
            2, 3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val s = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.36f).toSp() }
                for (ch in chars) Text("$ch", style = face.copy(fontSize = s, lineHeight = s * 1.02f))
            }
            else -> Row {
                val s = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.34f).toSp() }
                // Two columns, read from the right: the first two characters on the right.
                Column(horizontalAlignment = Alignment.CenterHorizontally) { for (ch in chars.drop(2).take(2)) Text("$ch", style = face.copy(fontSize = s, lineHeight = s * 1.04f)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { for (ch in chars.take(2)) Text("$ch", style = face.copy(fontSize = s, lineHeight = s * 1.04f)) }
            }
        }
    }
}

/** The seal's red for the page: a little brighter by night. */
@Composable
fun sealColor(): Color = if (Lanyin.style.dark) InkDesk.CinnabarNight else InkDesk.Cinnabar

/** The stamped stone: its ragged square, the specks where the paper shows through. */
internal fun DrawScope.drawSealStone(color: Color, paper: Color, carved: Boolean, seed: Int) {
    val w = size.width
    val h = size.height
    val j = minOf(w, h) * 0.025f
    val edge = Path().apply {
        val steps = 10
        moveTo(0f, 0f)
        for (k in 1..steps) lineTo(w * k / steps, (hash01(seed + k, 1) - 0.5f) * j)
        for (k in 1..steps) lineTo(w + (hash01(seed + k, 2) - 0.5f) * j, h * k / steps)
        for (k in steps - 1 downTo 0) lineTo(w * k / steps, h + (hash01(seed + k, 3) - 0.5f) * j)
        for (k in steps - 1 downTo 0) lineTo((hash01(seed + k, 4) - 0.5f) * j, h * k / steps)
        close()
    }
    if (carved) {
        drawPath(edge, color)
    } else {
        val border = minOf(w, h) * 0.08f
        drawPath(edge, color, style = Stroke(border))
    }
    // The ink takes unevenly: bare specks and a lighter patch.
    val specks = (w * h / (40f * density * density)).toInt().coerceIn(6, 40)
    for (k in 0 until specks) {
        val p = Offset(w * hash01(seed * 7 + k, 5), h * hash01(seed * 7 + k, 6))
        drawCircle(paper.copy(alpha = 0.5f + 0.4f * hash01(k, 7)), (0.4f + 0.9f * hash01(seed + k, 8)) * density, p)
    }
}

// ─── Landscape ───────────────────────────────────────────────────────────────

/**
 * A landscape in ink, painted in code (山水): far peaks in pale wash, nearer ones darker with
 * hemp-fibre strokes (披麻皴) down their flanks and moss dots along their ridges, mist lying
 * between, pines on a near bluff, a pavilion on the slope, a fishing boat on the water and three
 * birds; the sun in cinnabar by day, by night the moon left as bare paper in a ring of wash.
 * [hour] picks day or night; [seed] varies the hills.
 */
@Composable
fun ShanShui(modifier: Modifier = Modifier, hour: Float = 14f, seed: Int = 3) {
    val style = Lanyin.style
    val seal = sealColor()
    androidx.compose.foundation.layout.Spacer(
        modifier.clipToBounds().drawWithCache {
            val scene = paintShanShui(size, style, hour, seed, seal)
            onDrawBehind { scene(this) }
        },
    )
}

private fun nightAt(hour: Float): Boolean = hour < 6f || hour >= 19f

/** Everything the painting needs, traced once for its size, returning how to draw it. */
private fun paintShanShui(size: Size, style: LanyinStyle, hour: Float, seed: Int, seal: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val ink = style.colors.content
    val paper = style.colors.bg
    val night = nightAt(hour)
    fun ridge(base: Float, amp: Float, freq: Float, phase: Float, from: Float, to: Float): Path = Path().apply {
        moveTo(from, h)
        var x = from
        while (x <= to + 4f) {
            val u = (x - from) / (to - from)
            val shape = sin(u * PI.toFloat()).coerceAtLeast(0f)
            val y = base - amp * shape * (0.7f + 0.3f * sin(u * freq + phase)) - amp * 0.12f * sin(u * freq * 3.1f + phase * 2f)
            lineTo(x, y)
            x += 4f
        }
        lineTo(to, h)
        close()
    }
    fun ridgeTop(base: Float, amp: Float, freq: Float, phase: Float, from: Float, to: Float, x: Float): Float {
        val u = ((x - from) / (to - from)).coerceIn(0f, 1f)
        val shape = sin(u * PI.toFloat()).coerceAtLeast(0f)
        return base - amp * shape * (0.7f + 0.3f * sin(u * freq + phase)) - amp * 0.12f * sin(u * freq * 3.1f + phase * 2f)
    }
    val ph = seed * 1.7f
    // Far, middle and near hills: (base, height, frequency, phase, from, to, alpha).
    data class Hill(val base: Float, val amp: Float, val freq: Float, val phase: Float, val from: Float, val to: Float, val alpha: Float)
    val hills = listOf(
        Hill(h * 0.55f, h * 0.36f, 5f, ph, w * 0.25f, w * 1.15f, 0.10f),
        Hill(h * 0.62f, h * 0.30f, 7f, ph + 1f, -w * 0.15f, w * 0.62f, 0.16f),
        Hill(h * 0.74f, h * 0.36f, 9f, ph + 2.3f, w * 0.38f, w * 1.05f, 0.30f),
        Hill(h * 0.86f, h * 0.30f, 11f, ph + 3.1f, -w * 0.08f, w * 0.48f, 0.42f),
    )
    val paths = hills.map { ridge(it.base, it.amp, it.freq, it.phase, it.from, it.to) }
    // Hemp-fibre strokes on the two near hills, and moss dots along their tops.
    val rnd = java.util.Random(seed.toLong())
    class Stroke2(val a: Offset, val b: Offset, val c: Offset, val alpha: Float, val width: Float)
    val strokes = mutableListOf<Stroke2>()
    val moss = mutableListOf<Pair<Offset, Float>>()
    for (k in 2..3) {
        val hl = hills[k]
        repeat(70) {
            val x = hl.from + (hl.to - hl.from) * (0.08f + 0.84f * rnd.nextFloat())
            val top = ridgeTop(hl.base, hl.amp, hl.freq, hl.phase, hl.from, hl.to, x)
            val depth = (hl.base - top) * (0.15f + 0.7f * rnd.nextFloat())
            val y = top + depth * 0.2f
            val len = (h * 0.05f) * (0.6f + rnd.nextFloat())
            val lean = if (x < (hl.from + hl.to) / 2f) -0.35f else 0.35f
            strokes += Stroke2(Offset(x, y), Offset(x + lean * len * 0.4f + (rnd.nextFloat() - 0.5f) * len * 0.3f, y + len * 0.5f), Offset(x + lean * len, y + len), 0.08f + 0.14f * rnd.nextFloat(), (0.6f + rnd.nextFloat() * 0.9f))
        }
        repeat(7) {
            val x = hl.from + (hl.to - hl.from) * (0.15f + 0.7f * rnd.nextFloat())
            moss += Offset(x, ridgeTop(hl.base, hl.amp, hl.freq, hl.phase, hl.from, hl.to, x) + 3f) to (0.8f + rnd.nextFloat() * 1.2f)
        }
    }
    // A bluff of pines on the near-left hill.
    val pineBaseX = w * 0.16f
    val pineTop = ridgeTop(hills[3].base, hills[3].amp, hills[3].freq, hills[3].phase, hills[3].from, hills[3].to, pineBaseX)
    // The pavilion on the mid-right slope.
    val pavX = w * 0.7f
    val pavY = ridgeTop(hills[2].base, hills[2].amp, hills[2].freq, hills[2].phase, hills[2].from, hills[2].to, pavX)
    return {
        val d = density
        // The sky: by night a faint wash over the paper.
        if (night) drawRect(Brush.verticalGradient(listOf(ink.copy(alpha = 0.08f), Color.Transparent), endY = h * 0.6f))
        // Sun or moon.
        val orb = Offset(w * 0.24f, h * 0.15f)
        val r = minOf(w, h) * 0.07f
        if (night) {
            drawCircle(Brush.radialGradient(0f to Color.Transparent, 0.35f to ink.copy(alpha = 0.12f), 0.6f to ink.copy(alpha = 0.05f), 1f to Color.Transparent, center = orb, radius = r * 3.4f), r * 3.4f, orb)
            drawCircle(paper, r, orb)
            drawCircle(ink.copy(alpha = 0.2f), r, orb, style = Stroke(0.8f * d))
        } else {
            drawCircle(seal.copy(alpha = 0.85f), r * 0.8f, orb)
        }
        // Hills, far to near, each darkest at its crest and lost in mist at its foot.
        hills.forEachIndexed { i, hl ->
            drawPath(paths[i], Brush.verticalGradient(listOf(ink.copy(alpha = hl.alpha), ink.copy(alpha = hl.alpha * 0.55f), ink.copy(alpha = 0f)), startY = hl.base - hl.amp, endY = hl.base + h * 0.12f))
            // Mist lying in front of each but the last.
            if (i < 3) drawRect(Brush.verticalGradient(listOf(Color.Transparent, paper.copy(alpha = 0.85f), Color.Transparent), startY = hl.base - h * 0.04f, endY = hl.base + h * 0.1f), Offset(0f, hl.base - h * 0.04f), Size(w, h * 0.14f))
        }
        for (s in strokes) {
            val p = Path().apply { moveTo(s.a.x, s.a.y); quadraticTo(s.b.x, s.b.y, s.c.x, s.c.y) }
            drawPath(p, ink.copy(alpha = s.alpha), style = Stroke(s.width * d, cap = StrokeCap.Round))
        }
        for ((p, rr) in moss) { drawCircle(ink.copy(alpha = 0.45f), rr * d, p); drawCircle(ink.copy(alpha = 0.3f), rr * 0.7f * d, p + Offset(rr * 1.8f * d, rr * 0.6f * d)) }
        // Pines: tapering trunks with a bend in them and flecks of bark, a branch out to each
        // tier, and on the tiers the needles as painters do them, in little fans of strokes over
        // a pale wash.
        for (k in 0 until 3) {
            val pr = kotlin.random.Random(seed * 13 + k)
            val bx = pineBaseX + k * w * 0.05f
            val by = pineTop + 4f * d
            val th = h * (0.13f + 0.04f * k)
            val lean = (k - 1) * 0.14f
            val top = Offset(bx + th * lean, by - th)
            val bend = Offset(bx + th * lean * 0.5f + th * 0.05f * (if (k % 2 == 0) 1f else -1f), by - th * 0.5f)
            val wb = 2.2f * d
            val wt = 0.5f * d
            val trunk = Path().apply {
                moveTo(bx - wb, by)
                quadraticTo(bend.x - (wb + wt) / 2f, bend.y, top.x - wt, top.y)
                lineTo(top.x + wt, top.y)
                quadraticTo(bend.x + (wb + wt) / 2f, bend.y, bx + wb, by)
                close()
            }
            drawPath(trunk, ink.copy(alpha = 0.7f))
            for (b in 0 until 4) {
                val t = 0.12f + b * 0.17f
                // Along the trunk's middle, the same curve its edges follow.
                val u = 1f - t
                val p = Offset(u * u * bx + 2f * u * t * bend.x + t * t * top.x, u * u * by + 2f * u * t * bend.y + t * t * top.y)
                drawLine(paper.copy(alpha = 0.55f), p + Offset(-wb * 0.5f, 0f), p + Offset(wb * 0.3f, -1.4f * d), 0.6f * d, StrokeCap.Round)
            }
            for (n in 0 until 4) {
                val t = 0.45f + n * 0.16f
                val stem = Offset(bx + (top.x - bx) * t, by - th * t)
                val side = if ((n + k) % 2 == 0) -1f else 1f
                val span = th * (0.34f - n * 0.05f)
                val cx = stem.x + side * span * 0.32f
                val cy = stem.y - 1.5f * d
                drawLine(ink.copy(alpha = 0.6f), stem, Offset(cx + side * span * 0.22f, cy + 1f * d), 1f * d, StrokeCap.Round)
                drawOval(ink.copy(alpha = 0.15f), Offset(cx - span / 2f, cy - span * 0.2f), Size(span, span * 0.34f))
                val fans = 4 + (span / (7f * d)).toInt().coerceAtMost(4)
                for (f in 0 until fans) {
                    val fx = cx - span * 0.42f + span * 0.84f * (f + 0.5f) / fans + (pr.nextFloat() - 0.5f) * 2f * d
                    val fy = cy + span * 0.05f + (pr.nextFloat() - 0.5f) * span * 0.08f
                    val len = span * (0.13f + 0.05f * pr.nextFloat())
                    for (g in 0 until 6) {
                        val a = (-160f + g * 28f + (pr.nextFloat() - 0.5f) * 10f) * PI.toFloat() / 180f
                        drawLine(ink.copy(alpha = 0.5f), Offset(fx, fy), Offset(fx + cos(a) * len, fy + sin(a) * len * 0.75f), 0.6f * d, StrokeCap.Round)
                    }
                }
            }
        }
        // The pavilion: a sweeping roof on two posts.
        run {
            val pw = w * 0.05f
            val roof = Path().apply {
                moveTo(pavX - pw * 0.75f, pavY - pw * 0.55f)
                quadraticTo(pavX, pavY - pw * 1.0f, pavX + pw * 0.75f, pavY - pw * 0.55f)
                lineTo(pavX + pw * 0.5f, pavY - pw * 0.62f)
                quadraticTo(pavX, pavY - pw * 0.78f, pavX - pw * 0.5f, pavY - pw * 0.62f)
                close()
            }
            drawPath(roof, ink.copy(alpha = 0.7f))
            drawLine(ink.copy(alpha = 0.6f), Offset(pavX - pw * 0.4f, pavY - pw * 0.6f), Offset(pavX - pw * 0.4f, pavY), 1f * d)
            drawLine(ink.copy(alpha = 0.6f), Offset(pavX + pw * 0.4f, pavY - pw * 0.6f), Offset(pavX + pw * 0.4f, pavY), 1f * d)
        }
        // Water at the foot: a few long faint strokes, and a fishing boat with its fisherman.
        for (k in 0 until 4) {
            val y = h * (0.93f + k * 0.015f)
            drawLine(ink.copy(alpha = 0.12f), Offset(w * (0.45f + 0.08f * k), y), Offset(w * (0.75f + 0.05f * k), y), 0.8f * d)
        }
        run {
            val bx = w * 0.62f
            val by = h * 0.91f
            val bw = w * 0.1f
            val hull = Path().apply {
                moveTo(bx - bw / 2f, by - bw * 0.06f)
                quadraticTo(bx, by + bw * 0.16f, bx + bw / 2f, by - bw * 0.1f)
                quadraticTo(bx, by + bw * 0.04f, bx - bw / 2f, by - bw * 0.06f)
                close()
            }
            drawPath(hull, ink.copy(alpha = 0.8f))
            drawLine(ink.copy(alpha = 0.8f), Offset(bx + bw * 0.1f, by - bw * 0.02f), Offset(bx + bw * 0.08f, by - bw * 0.2f), 1.4f * d, StrokeCap.Round)
            drawCircle(ink.copy(alpha = 0.8f), bw * 0.05f, Offset(bx + bw * 0.08f, by - bw * 0.24f))
            drawLine(ink.copy(alpha = 0.5f), Offset(bx + bw * 0.05f, by - bw * 0.15f), Offset(bx - bw * 0.55f, by - bw * 0.45f), 0.8f * d)
        }
        // Three birds high up.
        for (k in 0 until 3) {
            val p = Offset(w * (0.42f + 0.05f * k), h * (0.2f + 0.03f * (k % 2)))
            val s = w * 0.012f
            val bird = Path().apply { moveTo(p.x - s, p.y - s * 0.3f); quadraticTo(p.x - s * 0.4f, p.y - s * 0.5f, p.x, p.y); quadraticTo(p.x + s * 0.4f, p.y - s * 0.5f, p.x + s, p.y - s * 0.3f) }
            drawPath(bird, ink.copy(alpha = 0.6f), style = Stroke(1f * d, cap = StrokeCap.Round))
        }
    }
}

// ─── Mounts ──────────────────────────────────────────────────────────────────

/**
 * A hanging scroll (立轴): the cord and the rod at the top, the silk mount with its two bands
 * (惊燕) hanging from the top border, a fine border round the picture (隔水), and the roller at
 * the foot with its knobs. [content] is the picture.
 */
@Composable
fun HangingScroll(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = Lanyin.style
    val silk = if (style.dark) InkDesk.SilkNight else InkDesk.Silk
    val ink = style.colors.content
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The cord and the top rod.
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val cx = size.width / 2f
            drawLine(ink.copy(alpha = 0.5f), Offset(cx - size.width * 0.18f, size.height - 6.dp.toPx()), Offset(cx, 2.dp.toPx()), 1.dp.toPx())
            drawLine(ink.copy(alpha = 0.5f), Offset(cx + size.width * 0.18f, size.height - 6.dp.toPx()), Offset(cx, 2.dp.toPx()), 1.dp.toPx())
            drawCircle(ink.copy(alpha = 0.6f), 2.dp.toPx(), Offset(cx, 2.dp.toPx()))
            drawRoundRect(InkDesk.Wood, Offset(size.width * 0.03f, size.height - 7.dp.toPx()), Size(size.width * 0.94f, 7.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRect(Color.White.copy(alpha = 0.18f), Offset(size.width * 0.03f, size.height - 6.dp.toPx()), Size(size.width * 0.94f, 1.5.dp.toPx()))
        }
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .drawBehind {
                    drawRect(silk)
                    drawRect(xuanBrush, alpha = 0.2f, blendMode = BlendMode.Overlay)
                    // A faint woven diaper in the silk.
                    val pitch = 6.dp.toPx()
                    var x = 0f
                    while (x < size.width + size.height) {
                        drawLine(ink.copy(alpha = 0.025f), Offset(x, 0f), Offset(x - size.height, size.height), 0.6f * density)
                        x += pitch
                    }
                    // 惊燕: two narrow bands hanging from the top border.
                    for (fx in floatArrayOf(0.35f, 0.65f)) drawRect(ink.copy(alpha = 0.06f), Offset(size.width * fx - 2.dp.toPx(), 0f), Size(4.dp.toPx(), 26.dp.toPx()))
                    drawRect(Color.Black.copy(alpha = 0.08f), Offset(0f, 0f), Size(size.width, 2.dp.toPx()))
                }
                .padding(start = 12.dp, end = 12.dp, top = 30.dp, bottom = 36.dp)
                .drawBehind {
                    // 隔水: a darker band framing the picture.
                    val b = 3.dp.toPx()
                    drawRect(ink.copy(alpha = 0.12f), Offset(-b, -b), Size(size.width + 2 * b, size.height + 2 * b))
                    drawXuan(style)
                },
            content = content,
        )
        // The roller and its knobs.
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val y = 2.dp.toPx()
            drawRoundRect(InkDesk.Wood, Offset(size.width * 0.02f, y), Size(size.width * 0.96f, 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRect(Color.White.copy(alpha = 0.16f), Offset(size.width * 0.02f, y + 1.dp.toPx()), Size(size.width * 0.96f, 2.dp.toPx()))
            for (x in floatArrayOf(0f, size.width - 12.dp.toPx())) {
                drawOval(Color(0xFFBFD0BE), Offset(x, 0f), Size(12.dp.toPx(), 13.dp.toPx()))
                drawOval(Color.White.copy(alpha = 0.35f), Offset(x + 2.dp.toPx(), 2.dp.toPx()), Size(4.dp.toPx(), 5.dp.toPx()))
            }
            drawRect(Color.Black.copy(alpha = 0.15f), Offset(size.width * 0.04f, y + 9.dp.toPx()), Size(size.width * 0.92f, 3.dp.toPx()))
        }
    }
}

// ─── Covers in ink ───────────────────────────────────────────────────────────

/**
 * A cover painted in ink: its light and dark turned into washes of the page's ink on the paper,
 * softened as wet ink spreads, its edges fading into the paper rather than cut. With 封面按主题重绘
 * off, the cover as it is.
 */
@Composable
fun InkWashCover(url: String?, seed: Int, modifier: Modifier = Modifier, ink: Color = Lanyin.colors.content, light: Boolean = false) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    // Fine enough not to show its cells on a scroll two hundred dp wide; the wash softens the rest.
    val n = 112
    val cells = rememberCoverLuminance(url, seed, n)
    val image = remember(cells, ink, light) {
        val argb = ink.toArgb() and 0x00FFFFFF
        // Stretch the cover's own range, as a painter would: a dim photo still gets its full
        // run of washes, from the palest to the deepest.
        val sorted = cells.copyOf().also { it.sort() }
        val lo = sorted[(sorted.size * 0.04f).toInt()]
        val hi = sorted[(sorted.size * 0.96f).toInt().coerceAtMost(sorted.size - 1)]
        val span = (hi - lo).takeIf { it > 0.08f }
        val px = IntArray(n * n) { i ->
            val l = if (span != null) ((cells[i] - lo) / span).coerceIn(0f, 1f) else cells[i]
            // Light shows as wash when drawn light-on-dark (a reflection in the ink); else darkness does.
            val amount = if (light) (l - 0.25f).coerceIn(0f, 1f) * 0.62f else ((1f - l) * 1.1f - 0.08f).coerceIn(0f, 1f)
            val x = i % n
            val y = i / n
            // Feather the edges into the paper (round, for a reflection in the well).
            val edge = if (light) (1f - kotlin.math.hypot(x - n / 2f + 0.5f, y - n / 2f + 0.5f) / (n * 0.5f)) / 0.22f else minOf(x, y, n - 1 - x, n - 1 - y) / (n * 0.12f)
            val a = (amount * edge.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
            (a shl 24) or argb
        }
        android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Canvas(modifier) {
        drawImage(image, IntOffset.Zero, IntSize(n, n), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
    }
}

// ─── The inkstone ────────────────────────────────────────────────────────────

/**
 * The inkstone the record becomes: a round stone, a band of carved clouds round its rim, the ink
 * pool in its well with the cover shining in the ink like a reflection; it turns with the music
 * ([angle]). The cover is drawn by the caller inside [pool].
 */
internal fun DrawScope.drawInkstone(angle: Float, seed: Int = 1) {
    val r = size.minDimension / 2f
    val c = center
    // The lamp's pool of light on the paper, its shadow, then the stone.
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE8C0).copy(alpha = 0.10f), Color.Transparent), c - Offset(r * 0.2f, r * 0.3f), r * 1.6f), r * 1.6f, c - Offset(r * 0.2f, r * 0.3f))
    drawCircle(Color.Black.copy(alpha = 0.22f), r * 1.01f, c + Offset(r * 0.03f, r * 0.05f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3835), InkDesk.Stone, Color(0xFF1C1B1A)), c - Offset(r * 0.3f, r * 0.35f), r * 1.5f), r, c)
    // The bevel round its edge catching the lamp.
    drawCircle(Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.05f)), c), r - 1.2f * density, c, style = Stroke(1.6f * density))
    // Speckled stone (端石's "eyes" and its fleck).
    for (k in 0 until 70) {
        val a = hash01(seed * 31 + k, 1) * PI.toFloat() * 2f
        val d = r * sqrtF(hash01(seed * 31 + k, 2)) * 0.97f
        drawCircle(Color.White.copy(alpha = 0.04f + 0.05f * hash01(k, 3)), (0.5f + hash01(k, 4)) * density, c + Offset(cos(a) * d, sin(a) * d))
    }
    rotate(angle, c) {
        // The carved rim: clouds curling round it, cut into the stone.
        val band = r * 0.84f
        val count = 14
        for (k in 0 until count) {
            val a = k * 2f * PI.toFloat() / count
            val p = c + Offset(cos(a) * band, sin(a) * band)
            val s = r * 0.07f
            val curl = Path().apply {
                moveTo(p.x - s, p.y)
                cubicTo(p.x - s, p.y - s * 1.2f, p.x + s * 0.8f, p.y - s * 1.2f, p.x + s * 0.6f, p.y)
                cubicTo(p.x + s * 0.4f, p.y + s * 0.6f, p.x - s * 0.2f, p.y + s * 0.4f, p.x, p.y - s * 0.1f)
            }
            rotate(a * 180f / PI.toFloat() + 90f, p) {
                drawPath(curl, Color.Black.copy(alpha = 0.5f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
                translate(-0.6f * density, -0.6f * density) {
                    drawPath(curl, Color.White.copy(alpha = 0.12f), style = Stroke(1f * density, cap = StrokeCap.Round))
                }
            }
        }
    }
    // The well: a step down into it, its far edge lit.
    val well = r * 0.7f
    drawCircle(Color.Black.copy(alpha = 0.55f), well + 2f * density, c)
    drawCircle(Color(0xFF0C0B0A), well, c)
    drawCircle(Brush.sweepGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0f)), c), well + 1.5f * density, c, style = Stroke(1.5f * density))
}

/** The ink's sheen over the pool: a soft arc of light where the lamp catches the wet ink. */
internal fun DrawScope.drawInkSheen() {
    val r = size.minDimension / 2f
    drawArc(Color.White.copy(alpha = 0.08f), 200f, 70f, false, center - Offset(r * 0.8f, r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
}

/**
 * The brush resting across the stone as a record's arm: a bamboo shaft with its nodes and the
 * tuft of hair, black with ink at its tip. [lift] (0..1) swings it off the stone onto its rest.
 */
internal fun DrawScope.drawBrushArm(pivot: Offset, length: Float, angle: Float) {
    rotate(angle, pivot) {
        val w = length * 0.045f
        val tip = Offset(pivot.x, pivot.y + length)
        // Shaft.
        drawLine(Color.Black.copy(alpha = 0.25f), pivot + Offset(w * 0.4f, w * 0.6f), Offset(pivot.x + w * 0.4f, pivot.y + length * 0.78f + w * 0.6f), w, StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF8E7444), InkDesk.Bamboo, Color(0xFF7A6136)), pivot.x - w / 2f, pivot.x + w / 2f), pivot, Offset(pivot.x, pivot.y + length * 0.78f), w, StrokeCap.Round)
        for (k in 1..3) {
            val y = pivot.y + length * 0.78f * k / 4f
            drawLine(Color(0xFF6E5530), Offset(pivot.x - w / 2f, y), Offset(pivot.x + w / 2f, y), 1.2f * density)
        }
        // The ferrule and the hair.
        drawRect(Color(0xFF2B2622), Offset(pivot.x - w * 0.6f, pivot.y + length * 0.76f), Size(w * 1.2f, length * 0.05f))
        val hair = Path().apply {
            moveTo(pivot.x - w * 0.6f, pivot.y + length * 0.81f)
            quadraticTo(pivot.x - w * 0.9f, pivot.y + length * 0.9f, tip.x, tip.y)
            quadraticTo(pivot.x + w * 0.9f, pivot.y + length * 0.9f, pivot.x + w * 0.6f, pivot.y + length * 0.81f)
            close()
        }
        drawPath(hair, Brush.verticalGradient(listOf(Color(0xFFD8CCB4), Color(0xFF3A3632), Color(0xFF0B0B0A)), pivot.y + length * 0.81f, tip.y))
    }
}

/** A brush rest (笔架) in pale celadon: three little peaks. */
internal fun DrawScope.drawBrushRest(at: Offset, w: Float) {
    val h = w * 0.45f
    val rest = Path().apply {
        moveTo(at.x - w / 2f, at.y)
        quadraticTo(at.x - w * 0.38f, at.y - h * 0.7f, at.x - w * 0.22f, at.y - h * 0.3f)
        quadraticTo(at.x - w * 0.1f, at.y - h * 1.2f, at.x, at.y - h * 0.4f)
        quadraticTo(at.x + w * 0.1f, at.y - h * 1.2f, at.x + w * 0.22f, at.y - h * 0.3f)
        quadraticTo(at.x + w * 0.38f, at.y - h * 0.7f, at.x + w / 2f, at.y)
        close()
    }
    drawPath(rest, Color.Black.copy(alpha = 0.2f), style = Stroke(w * 0.08f))
    drawPath(rest, Brush.verticalGradient(listOf(Color(0xFFDDE8DA), Color(0xFFA9C0AA)), at.y - h, at.y))
}

private fun sqrtF(v: Float) = kotlin.math.sqrt(v)
