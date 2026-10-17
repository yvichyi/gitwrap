package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.GameLcd
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.LocalLanyinStyle
import kotlin.math.abs

/** The console's dark parts: the screen's bezel and the D-pad. */
internal val GameBezel = Color(0xFF4A4955)
internal val GamePad = Color(0xFF2D2C33)

/** 游: the console's grey case with the faint texture of moulded plastic. */
internal fun DrawScope.drawGameCase(style: LanyinStyle) {
    drawRect(style.colors.bg)
    drawRect(grainBrush, alpha = 0.05f)
}

/** Inside 游's screen the theme turns to the four greens of the original LCD. */
fun LanyinStyle.lcdStyle(): LanyinStyle = copy(
    dark = false,
    colors = colors.copy(
        bg = GameLcd[0], bgDeep = GameLcd[1], surface = GameLcd[0], surfaceAlt = GameLcd[1],
        content = GameLcd[3], muted = Color(0xFF244524), accent = GameLcd[3], accentAlt = GameLcd[2],
        onAccent = GameLcd[0], outline = GameLcd[3].copy(alpha = 0.35f), like = GameLcd[3], vinyl = GameLcd[3],
    ),
)

/**
 * 游's screen: a dark bezel, round at three corners and swept at the fourth, with the power
 * light and the lettering above the glass; inside, a green LCD with its faint pixel grid, where
 * [content] is drawn in [lcdStyle].
 */
@Composable
fun GameScreen(playing: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val style = Lanyin.style
    val lcd = remember(style) { style.lcdStyle() }
    val bezel = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 44.dp, bottomStart = 12.dp)
    val label = style.type.label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp)
    Column(
        modifier
            .clip(bezel)
            .background(GameBezel)
            .drawWithContent {
                drawContent()
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endY = 6.dp.toPx()))
            }
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (playing) Color(0xFFFF2E4D) else Color(0xFF6B2A36), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text("BATTERY", style = label, color = Color(0xFFA9A6B8), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).height(6.dp)) {
                drawRect(Color(0xFF8D3D73), Offset(0f, 0f), Size(size.width, 1.5f * density))
                drawRect(Color(0xFF2F3A8F), Offset(0f, 3.5f * density), Size(size.width, 1.5f * density))
            }
            Spacer(Modifier.width(8.dp))
            Text("LANYIN STEREO", style = label, color = Color(0xFFA9A6B8), maxLines = 1)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(lcd.colors.bg)
                .drawWithContent {
                    drawContent()
                    // The LCD's pixel grid and the shadow the bezel casts on the glass.
                    val pitch = 3.dp.toPx()
                    val line = GameLcd[3].copy(alpha = 0.035f)
                    var x = 0f
                    while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += pitch }
                    var y = 0f
                    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += pitch }
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), endY = 8.dp.toPx()))
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            CompositionLocalProvider(LocalLanyinStyle provides lcd, LocalContentColor provides lcd.colors.content) {
                content()
            }
        }
    }
}

/** 4×4 ordered dither thresholds, in 0..1. */
private val bayer = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f).map { (it + 0.5f) / 16f }

/** The LCD shade (0 lightest … 3 darkest) for [lightness] at pixel ([x], [y]), with ordered dithering. */
internal fun lcdShade(lightness: Float, x: Int, y: Int): Int {
    val dark = (1f - lightness).coerceIn(0f, 1f) * 3f
    val base = dark.toInt().coerceAtMost(2)
    val frac = dark - base
    return (base + if (frac > bayer[(y and 3) * 4 + (x and 3)]) 1 else 0).coerceIn(0, 3)
}

/**
 * A cover as the console's screen would show it: [n] pixels across, dithered into the four LCD
 * greens and scaled up without smoothing, so each pixel stays a sharp square. With 封面按主题重绘
 * off, the cover as it is.
 */
@Composable
fun DitheredCover(url: String?, seed: Int, modifier: Modifier = Modifier, n: Int = 48) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    val image = rememberDitheredImage(url, seed, n)
    Canvas(modifier) { drawPixels(image) }
}

/** The cover dithered into the LCD greens at [n] pixels across. */
@Composable
fun rememberDitheredImage(url: String?, seed: Int, n: Int = 48): ImageBitmap {
    val cells = rememberCoverLuminance(url, seed, n)
    return remember(cells) {
        val pixels = IntArray(n * n) { i ->
            // Lift the contrast a little: covers are rarely as punchy as four flat greens need.
            val l = ((cells[i] - 0.5f) * 1.25f + 0.52f).coerceIn(0f, 1f)
            GameLcd[lcdShade(l, i % n, i / n)].toArgb()
        }
        android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** [rememberDitheredImage] as a painter that keeps its pixels square, for a record's label; the cover as it is with 封面按主题重绘 off. */
@Composable
fun rememberDitheredPainter(url: String?, seed: Int): androidx.compose.ui.graphics.painter.Painter? {
    if (!LocalCoverMedium.current) return rememberCoverPainter(url, 480)
    val image = rememberDitheredImage(url, seed, 32)
    return remember(image) { androidx.compose.ui.graphics.painter.BitmapPainter(image, filterQuality = FilterQuality.None) }
}

private fun DrawScope.drawPixels(image: ImageBitmap) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
    )
}

/**
 * 游's progress: a bar of blocks in a dark frame, as a game draws a health bar; the block being
 * played in magenta.
 */
internal fun DrawScope.drawBlocksTrack(f: Float, style: LanyinStyle) {
    val c = style.colors
    val h = 14f * density
    val top = (size.height - h) / 2f
    val stroke = 2f * density
    drawRect(c.content, Offset(0f, top), Size(size.width, h), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    val inset = stroke + 1.5f * density
    val block = 5f * density
    val gap = 2f * density
    val inner = size.width - inset * 2
    val n = ((inner + gap) / (block + gap)).toInt().coerceAtLeast(1)
    val step = (inner + gap) / n
    val lit = f * n
    for (k in 0 until n) {
        val x = inset + k * step
        val color = when {
            k < lit.toInt() -> c.content
            k == lit.toInt() -> c.accent
            else -> continue
        }
        drawRect(color, Offset(x, top + inset), Size(step - gap, h - inset * 2))
    }
}

/** The A button: a magenta disc standing on a darker rim, lit from above. */
internal fun DrawScope.drawAButton(style: LanyinStyle) {
    val c = style.colors
    val r = size.minDimension / 2f
    drawCircle(lerp(c.accent, Color.Black, 0.35f), r, center + Offset(0f, 4f * density))
    drawCircle(c.accent, r)
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.78f, center - Offset(r * 0.12f, r * 0.2f))
    drawCircle(c.accent, r * 0.74f, center - Offset(r * 0.04f, r * 0.08f))
}

/** A dark D-pad key behind this element, standing on its rim. */
fun Modifier.padKey(shape: Shape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(0f, 3f * density) { drawOutline(outline, Color(0xFF17161B)) }
    drawOutline(outline, GamePad)
    translate(0f, 1f * density) { drawOutline(outline, Color.White.copy(alpha = 0.04f)) }
}

/** The light grey of the D-pad's arrows. */
internal val GamePadGlyph = Color(0xFFC9C6D1)

/**
 * 游's generated sleeves: 16-pixel scenes dithered in the four greens: mountains under the sun,
 * a heart, a record and a tree under the stars.
 */
internal fun DrawScope.gameCover(style: LanyinStyle, seed: Int) {
    val n = 16
    val w = size.width / n
    val h = size.height / n
    val shift = (hash01(seed, 1) - 0.5f) * 0.3f
    for (y in 0 until n) for (x in 0 until n) {
        val u = (x + 0.5f) / n
        val v = (y + 0.5f) / n
        val l = when (seed % 4) {
            0 -> {
                val ridge = 0.62f + 0.12f * kotlin.math.sin(u * 7f + seed) - 0.18f * (1f - abs(u - 0.5f - shift) * 2f).coerceAtLeast(0f)
                val sun = (u - 0.72f - shift) * (u - 0.72f - shift) + (v - 0.26f) * (v - 0.26f) < 0.012f
                when {
                    v > ridge -> 0.15f - (v - ridge) * 0.3f
                    sun -> 1f
                    else -> 0.95f - v * 0.6f
                }
            }
            1 -> {
                val px = (u - 0.5f) * 2.6f
                val py = (0.45f - v) * 2.6f
                val f = (px * px + py * py - 1f).let { it * it * it } - px * px * py * py * py
                when {
                    f <= 0f && px < -0.35f && py > 0.25f -> 0.62f
                    f <= 0f -> 0.08f
                    else -> 0.9f - 0.25f * ((x + y) % 2)
                }
            }
            2 -> {
                val d = kotlin.math.sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f))
                when {
                    d < 0.07f -> 0.95f
                    d < 0.18f -> 0.6f
                    d < 0.44f -> if (((d * 40f).toInt() % 2) == 0) 0.1f else 0.25f
                    else -> 0.85f
                }
            }
            else -> {
                val trunk = abs(u - 0.5f - shift) < 0.07f && v > 0.62f
                val crown = abs(u - 0.5f - shift) < (v - 0.15f) * 0.6f && v in 0.15f..0.68f
                val star = hash01(seed * 31 + x * 16 + y, 2) > 0.93f && v < 0.5f
                when {
                    trunk -> 0.3f
                    crown -> 0.1f
                    v > 0.86f -> 0.4f
                    star -> 1f
                    else -> 0.7f
                }
            }
        }
        drawRect(GameLcd[lcdShade(l, x, y)], Offset(x * w, y * h), Size(w + 0.5f, h + 0.5f))
    }
}

/** 游's motif: a pixel heart. */
internal fun DrawScope.gameMotif(color: Color) {
    val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
    val px = size.minDimension * 0.6f / 7f
    val left = (size.width - px * 7) / 2f
    val top = (size.height - px * rows.size) / 2f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> if (ch == '1') drawRect(color, Offset(left + x * px, top + y * px), Size(px, px)) }
    }
}

/** A play triangle cursor, as a game marks the menu item it's on. */
internal fun DrawScope.drawMenuCursor(color: Color) {
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}
