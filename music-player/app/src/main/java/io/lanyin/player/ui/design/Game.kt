package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.abs

/**
 * 游 is a game in one bit: ink and paper and nothing between, greys only by an ordered dither.
 * Its pixels are [BitPx] square, everything snapped to them.
 */
object Bit {
    val Ink = Color(0xFF1C1B18)
    val Paper = Color(0xFFE8E5D8)
}

/** The size of one of 游's pixels. */
val BitPx: Dp = 2.dp

/** The page: plain paper. */
internal fun DrawScope.drawGameCase(style: LanyinStyle) {
    drawRect(style.colors.bg)
}

/** In one bit the page already is the screen: the same two colours inside a window. */
fun LanyinStyle.lcdStyle(): LanyinStyle = this

// ─── Dither ──────────────────────────────────────────────────────────────────

/** 4×4 ordered dither thresholds, in 0..1. */
private val bayer = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f).map { (it + 0.5f) / 16f }

/** The Bayer threshold at pixel ([x], [y]). */
internal fun bayerAt(x: Int, y: Int): Float = bayer[(y and 3) * 4 + (x and 3)]

/** Whether a pixel of [lightness] (0 black … 1 white) at ([x], [y]) is inked, dithered. */
internal fun inked(lightness: Float, x: Int, y: Int): Boolean = lightness < bayerAt(x, y)

/** Four shades for the old screens that still ask: 0 and 1 paper, 2 and 3 ink, dithered between. */
internal fun lcdShade(lightness: Float, x: Int, y: Int): Int = if (inked(lightness, x, y)) 3 else 0

/**
 * A cover in one bit: [n] pixels across, dithered to ink and paper (the contrast lifted a
 * little first, as covers are seldom as punchy as two colours need) and scaled up without
 * smoothing. With 封面按主题重绘 off, the cover as it is.
 */
@Composable
fun DitheredCover(url: String?, seed: Int, modifier: Modifier = Modifier, n: Int = 64) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    val image = rememberDitheredImage(url, seed, n)
    Canvas(modifier) { drawPixels(image) }
}

/** The cover dithered to one bit at [n] pixels across. */
@Composable
fun rememberDitheredImage(url: String?, seed: Int, n: Int = 64): ImageBitmap {
    val cells = rememberCoverLuminance(url, seed, n)
    return remember(cells) {
        val ink = Bit.Ink.toArgb()
        val paper = Bit.Paper.toArgb()
        val pixels = IntArray(n * n) { i ->
            val l = ((cells[i] - 0.5f) * 1.35f + 0.55f).coerceIn(0f, 1f)
            if (inked(l, i % n, i / n)) ink else paper
        }
        android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** [rememberDitheredImage] as a painter that keeps its pixels square; the cover as it is with 封面按主题重绘 off. */
@Composable
fun rememberDitheredPainter(url: String?, seed: Int): androidx.compose.ui.graphics.painter.Painter? {
    if (!LocalCoverMedium.current) return rememberCoverPainter(url, 480)
    val image = rememberDitheredImage(url, seed, 48)
    return remember(image) { androidx.compose.ui.graphics.painter.BitmapPainter(image, filterQuality = FilterQuality.None) }
}

internal fun DrawScope.drawPixels(image: ImageBitmap, topLeft: IntOffset = IntOffset.Zero, size: IntSize = IntSize(this.size.width.toInt(), this.size.height.toInt())) {
    drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), topLeft, size, filterQuality = FilterQuality.None)
}

/** [area] filled with ink at [density] (0..1) by the ordered dither, on the pixel grid of [px]. */
internal fun DrawScope.ditherRect(topLeft: Offset, area: Size, density: Float, px: Float, ink: Color) {
    if (density <= 0f) return
    val cols = (area.width / px).toInt()
    val rows = (area.height / px).toInt()
    val gx = (topLeft.x / px).toInt()
    val gy = (topLeft.y / px).toInt()
    for (j in 0 until rows) for (i in 0 until cols) {
        if (bayerAt(gx + i, gy + j) < density) drawRect(ink, Offset(topLeft.x + i * px, topLeft.y + j * px), Size(px, px))
    }
}

// ─── Sprites and the tiny font ───────────────────────────────────────────────

/** 游's sprites, as rows of pixels: `#` ink, `o` paper (knocked out of what's behind), anything else clear. */
internal object Sprites {
    val Heart = listOf(".##.##.", "#######", "#######", ".#####.", "..###..", "...#...")
    val Note = listOf("..###", "..#.#", "..#..", "###..", "###..")
    val CatSit = listOf("...#.....#...", "..###...###..", "..#########..", ".##.##.##.##.", ".###########.", "..#########.#", ".##########.#", ".###########.", "#############", "#.##.....##.#")
    val CatRun = listOf("....#.....#......", "...###...###.....", "...#########.....", "..##.##.##.##....", "..###########....", ".#############..#", "################.", ".#############...", "..###......###...", ".##..........##..")
    val CatRun2 = listOf("....#.....#......", "...###...###.....", "...#########.....", "..##.##.##.##....", "..###########...#", ".#############.#.", "################.", ".#############...", "...##.....##.....", "...##.....##.....")
    val Tree = listOf("..#..", ".###.", "#####", ".###.", "#####", "..#..")
    val Castle = listOf("#.#.#......#.#.#", "#####......#####", ".###...##...###.", ".###..####..###.", ".##############.", ".###.##..##.###.", ".###.#oooo#.###.", ".###.#oooo#.###.", "################")
    val Tower = listOf("...#...", "..#.#..", "...#...", "..###..", "..#.#..", ".#...#.", ".#.#.#.", "#.#.#.#", "#.#.#.#", "#######")
    val House = listOf(".....##.....", "....####....", "...######...", "..########..", ".##########.", "############", ".#oo####oo#.", ".#oo####oo#.", ".##########.")
    val Mountain = listOf(".....##.....", "....#..#....", "...#....#...", "..#..##..#..", ".#..#..#..#.", "#..#....#..#", "############")
    val Clock = listOf("..###..", ".#.#.#.", "#..#..#", "#..##.#", ".#...#.", "..###..", "..#.#..", "..#.#..", ".#####.")
    val Cave = listOf("...######...", "..#......#..", ".#..####..#.", "#..#oooo#..#", "#.#oooooo#.#", "############")
    val Boat = listOf("...#....", "..##....", ".###....", "####....", "...#....", "########", ".######.")
    val Cloud = listOf("...####.....", ".#....###...", "#..........#", ".##########.")
    val CloudFill = listOf("...####.....", ".########...", "############", ".##########.")
    val Pointer = listOf("#####", ".###.", "..#..")
    val Play = listOf("#...", "##..", "###.", "##..", "#...")
    val Prev = listOf("#....#", "#...##", "#..###", "#...##", "#....#")
    val Next = listOf("#....#", "##...#", "###..#", "##...#", "#....#")
    val Pause = listOf("##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##")
    val Repeat = listOf(".####.", "#....#", "#.....", ".....#", "#....#", ".####.")
    val RepeatOne = listOf(".####.", "#..#.#", "#.##..", "...#.#", "#..#.#", ".####.")
    val Shuffle = listOf("##..##", "..##..", "..##..", "##..##", "......", "......")
    val List = listOf("######", "......", "######", "......", "####..")
    val Radio = listOf("#......", ".#.....", "..#####", ".#....#", ".#.##.#", ".#####.")
    val Chart = listOf("......#", "...#..#", "...#..#", "#..#..#", "#..#..#", "#######")
    val History = listOf(".#####.", "#..#..#", "#..#..#", "#..##.#", "#.....#", ".#####.")
    val Download = listOf("...#...", "...#...", ".#####.", "..###..", "...#...", "#######")
    val Flag = listOf("#####", "####.", "###..", "#....", "#....", "#....", "#....", "#....")
}

/** [rows] at ([x], [y]) with pixels [px] across: `#` in [ink], `o` in [paper] when given. */
internal fun DrawScope.drawSprite(rows: List<String>, x: Float, y: Float, px: Float, ink: Color, paper: Color? = null) {
    for ((j, row) in rows.withIndex()) for ((i, ch) in row.withIndex()) {
        val color = when (ch) {
            '#' -> ink
            'o' -> paper ?: continue
            else -> continue
        }
        drawRect(color, Offset(x + i * px, y + j * px), Size(px, px))
    }
}

/** Width and height of [rows] in pixels. */
internal fun spriteSize(rows: List<String>) = rows.maxOf { it.length } to rows.size

internal val tinyGlyphs: Map<Char, List<String>> = mapOf(
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"), '1' to listOf(".#.", "##.", ".#.", ".#.", "###"), '2' to listOf("###", "..#", "###", "#..", "###"),
    '3' to listOf("###", "..#", ".##", "..#", "###"), '4' to listOf("#.#", "#.#", "###", "..#", "..#"), '5' to listOf("###", "#..", "###", "..#", "###"),
    '6' to listOf("###", "#..", "###", "#.#", "###"), '7' to listOf("###", "..#", ".#.", ".#.", ".#."), '8' to listOf("###", "#.#", "###", "#.#", "###"),
    '9' to listOf("###", "#.#", "###", "..#", "###"), '-' to listOf("...", "...", "###", "...", "..."), ':' to listOf("...", ".#.", "...", ".#.", "..."),
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."), 'C' to listOf("###", "#..", "#..", "#..", "###"),
    'D' to listOf("##.", "#.#", "#.#", "#.#", "##."), 'E' to listOf("###", "#..", "##.", "#..", "###"), 'F' to listOf("###", "#..", "##.", "#..", "#.."),
    'G' to listOf("###", "#..", "#.#", "#.#", "###"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"), 'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
    'J' to listOf("..#", "..#", "..#", "#.#", "###"), 'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"), 'L' to listOf("#..", "#..", "#..", "#..", "###"),
    'M' to listOf("#.#", "###", "###", "#.#", "#.#"), 'N' to listOf("##.", "#.#", "#.#", "#.#", "#.#"), 'O' to listOf("###", "#.#", "#.#", "#.#", "###"),
    'P' to listOf("###", "#.#", "###", "#..", "#.."), 'Q' to listOf("###", "#.#", "#.#", "###", "..#"), 'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'S' to listOf("###", "#..", "###", "..#", "###"), 'T' to listOf("###", ".#.", ".#.", ".#.", ".#."), 'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
    'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."), 'W' to listOf("#.#", "#.#", "#.#", "###", "#.#"), 'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
    'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."), 'Z' to listOf("###", "..#", ".#.", "#..", "###"), 'x' to listOf("...", "#.#", ".#.", "#.#", "..."),
    '.' to listOf("...", "...", "...", "...", ".#."), '/' to listOf("..#", "..#", ".#.", "#..", "#.."), '%' to listOf("#.#", "..#", ".#.", "#..", "#.#"),
    '+' to listOf("...", ".#.", "###", ".#.", "..."), '!' to listOf(".#.", ".#.", ".#.", "...", ".#."), '?' to listOf("###", "..#", ".##", "...", ".#."),
    ' ' to listOf("...", "...", "...", "...", "..."),
)

/** How wide [text] sets in the tiny 3×5 face at [px] a pixel. */
internal fun tinyWidth(text: String, px: Float): Float = (text.length * 4 - 1).coerceAtLeast(0) * px

/** [text] in the tiny 3×5 face, upper case, from ([x], [y]); returns where it ends. */
internal fun DrawScope.drawTiny(text: String, x: Float, y: Float, px: Float, color: Color): Float {
    var cx = x
    for (ch in text.uppercase()) {
        val glyph = tinyGlyphs[ch] ?: tinyGlyphs.getValue(' ')
        for ((j, row) in glyph.withIndex()) for ((i, c) in row.withIndex()) {
            if (c == '#') drawRect(color, Offset(cx + i * px, y + j * px), Size(px, px))
        }
        cx += 4 * px
    }
    return cx
}

/** [text] in the tiny face as an element of its own, [BitPx] a pixel. */
@Composable
fun TinyText(text: String, color: Color, modifier: Modifier = Modifier, px: Dp = BitPx) {
    val p = with(LocalDensity.current) { px.toPx() }
    val w = with(LocalDensity.current) { tinyWidth(text, p).toDp() }
    Canvas(modifier.size(w, px * 5)) { drawTiny(text, 0f, 0f, p, color) }
}

// ─── Windows ─────────────────────────────────────────────────────────────────

/**
 * A game window over [size]: paper inside a double ink border a pixel apart, the outer corners
 * notched; with [title], a tab of ink on the top border with the title knocked out of it.
 */
internal fun DrawScope.drawGameFrame(ink: Color, paper: Color, title: String? = null, px: Float = BitPx.toPx()) {
    val w = size.width
    val h = size.height
    drawRect(paper)
    fun ring(inset: Float) {
        drawRect(ink, Offset(inset, inset), Size(w - 2 * inset, px))
        drawRect(ink, Offset(inset, h - inset - px), Size(w - 2 * inset, px))
        drawRect(ink, Offset(inset, inset), Size(px, h - 2 * inset))
        drawRect(ink, Offset(w - inset - px, inset), Size(px, h - 2 * inset))
    }
    ring(0f)
    ring(2 * px)
    for (c in listOf(Offset(0f, 0f), Offset(w - px, 0f), Offset(0f, h - px), Offset(w - px, h - px))) drawRect(paper, c, Size(px, px))
    if (title != null) {
        val tw = tinyWidth(title, px) + 4 * px
        drawRect(ink, Offset(6 * px, 0f), Size(tw, 7 * px))
        drawTiny(title, 8 * px, px, px, paper)
    }
}

/** How far a window's border reaches in: two lines and the gap between, and a pixel to spare. */
val GameFrameInset: Dp = BitPx * 4

/**
 * A role-playing game's window: paper inside a double ink border with notched corners
 * ([fill] and [frame] for the two colours).
 */
fun Modifier.gameWindow(fill: Color, frame: Color, title: String? = null): Modifier = drawBehind { drawGameFrame(frame, fill, title) }

/** A [gameWindow] holding [content], padded clear of its border (and its tab, with a [title]). */
@Composable
fun GameWindow(
    modifier: Modifier = Modifier,
    title: String? = null,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Lanyin.colors
    Column(modifier.gameWindow(c.bg, c.content, title).padding(top = if (title != null) 6.dp else 0.dp).padding(padding), content = content)
}

/** The same, for a box. */
@Composable
fun GameBox(modifier: Modifier = Modifier, title: String? = null, content: @Composable BoxScope.() -> Unit) {
    val c = Lanyin.colors
    Box(modifier.gameWindow(c.bg, c.content, title).padding(GameFrameInset), content = content)
}

/** 游's window for a page's main content (the lyrics, when the stage gives way to them). */
@Composable
fun GameScreen(playing: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GameWindow(modifier, title = if (playing) "PLAY" else "PAUSE", padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), content = content)
}

/**
 * The bar across the top of a game: ink, the stage in the tiny face, [hearts] lives (a heart
 * left hollow for each one lost), and [right] at the far end; [middle] between when there's room.
 */
@Composable
fun GameHud(left: String, hearts: Int, right: String, modifier: Modifier = Modifier, middle: String? = null, filled: Int = hearts) {
    val c = Lanyin.colors
    Canvas(modifier.drawBehind { drawRect(c.content) }) {
        val px = BitPx.toPx()
        val y = (size.height - 5 * px) / 2f
        var x = drawTiny(left, 4 * px, y, px, c.bg) + 3 * px
        for (k in 0 until hearts) {
            drawSprite(Sprites.Heart, x, (size.height - 6 * px) / 2f, px, c.bg)
            if (k >= filled) drawSprite(listOf(".......", ".##.##.", ".#####.", "..###..", "...#...", "......."), x, (size.height - 6 * px) / 2f, px, c.content)
            x += 9 * px
        }
        drawTiny(right, size.width - 4 * px - tinyWidth(right, px), y, px, c.bg)
        if (middle != null) {
            val mx = maxOf(x + 6 * px, (size.width - tinyWidth(middle, px)) / 2f)
            if (mx + tinyWidth(middle, px) < size.width - 8 * px - tinyWidth(right, px)) drawTiny(middle, mx, y, px, c.bg)
        }
    }
}

// ─── Controls ────────────────────────────────────────────────────────────────

/**
 * 游's progress: a stage map in a line. Dots ahead, a solid line behind, the player's marker
 * where the song is and the goal flag at the end.
 */
internal fun DrawScope.drawBlocksTrack(f: Float, style: LanyinStyle) {
    val ink = style.colors.content
    val px = BitPx.toPx()
    val y = (size.height / 2f / px).toInt() * px
    val end = size.width - 6 * px
    val x = (f.coerceIn(0f, 1f) * end / px).toInt() * px
    var dx = 0f
    while (dx < end) {
        if (dx < x || ((dx / px).toInt() % 2 == 0)) drawRect(ink, Offset(dx, y), Size(px, px))
        dx += px
    }
    // The start post, the marker, the flag.
    drawRect(ink, Offset(0f, y - 2 * px), Size(px, 5 * px))
    drawRect(ink, Offset(x - px, y - 2 * px), Size(3 * px, 5 * px))
    drawRect(style.colors.bg, Offset(x, y - px), Size(px, 3 * px))
    drawRect(ink, Offset(size.width - px, y - 3 * px), Size(px, 7 * px))
    drawRect(ink, Offset(size.width - 5 * px, y - 3 * px), Size(4 * px, 3 * px))
}

/** The play key: solid ink, standing a pixel proud on its own shadow. */
internal fun DrawScope.drawAButton(style: LanyinStyle) {
    val px = BitPx.toPx()
    drawRect(style.colors.content, Offset(0f, px), Size(size.width, size.height - px))
    drawRect(style.colors.content, size = Size(size.width, size.height - 2 * px))
    drawRect(style.colors.bg, Offset(px, size.height - 2 * px), Size(size.width - 2 * px, px))
}

/** A key behind this element: paper inside an ink border, thicker below where it stands. */
fun Modifier.padKey(@Suppress("UNUSED_PARAMETER") shape: Shape): Modifier = drawBehind { drawPixelButton(Bit.Ink, Bit.Paper, on = false) }

/** A pixel button over [size]: an ink border, its foot two pixels deep; [on] fills it with ink. */
internal fun DrawScope.drawPixelButton(ink: Color, paper: Color, on: Boolean, px: Float = BitPx.toPx()) {
    drawRect(ink)
    drawRect(if (on) ink else paper, Offset(px, px), Size(size.width - 2 * px, size.height - 4 * px))
    if (on) drawRect(paper, Offset(px, size.height - 3 * px), Size(size.width - 2 * px, px))
}

/** The glyphs on 游's keys. */
internal val GamePadGlyph = Bit.Ink

// ─── Covers and motifs ───────────────────────────────────────────────────────

/**
 * 游's generated sleeves: little scenes in one bit, dithered: mountains under the sun, a heart,
 * a record, a tree under the stars.
 */
internal fun DrawScope.gameCover(style: LanyinStyle, seed: Int) {
    val n = 32
    val w = size.width / n
    val h = size.height / n
    val shift = (hash01(seed, 1) - 0.5f) * 0.3f
    drawRect(style.colors.bg)
    for (y in 0 until n) for (x in 0 until n) {
        val u = (x + 0.5f) / n
        val v = (y + 0.5f) / n
        val l = when (seed % 4) {
            0 -> {
                val ridge = 0.62f + 0.12f * kotlin.math.sin(u * 7f + seed) - 0.18f * (1f - abs(u - 0.5f - shift) * 2f).coerceAtLeast(0f)
                val sun = (u - 0.72f - shift) * (u - 0.72f - shift) + (v - 0.26f) * (v - 0.26f) < 0.012f
                when {
                    v > ridge -> 0.12f - (v - ridge) * 0.3f
                    sun -> 1f
                    else -> 0.95f - v * 0.7f
                }
            }
            1 -> {
                val px = (u - 0.5f) * 2.6f
                val py = (0.45f - v) * 2.6f
                val f = (px * px + py * py - 1f).let { it * it * it } - px * px * py * py * py
                when {
                    f <= 0f && px < -0.35f && py > 0.25f -> 0.62f
                    f <= 0f -> 0.05f
                    else -> 0.85f
                }
            }
            2 -> {
                val d = kotlin.math.sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f))
                when {
                    d < 0.07f -> 0.95f
                    d < 0.18f -> 0.55f
                    d < 0.44f -> if (((d * 40f).toInt() % 2) == 0) 0.06f else 0.22f
                    else -> 0.88f
                }
            }
            else -> {
                val trunk = abs(u - 0.5f - shift) < 0.07f && v > 0.62f
                val crown = abs(u - 0.5f - shift) < (v - 0.15f) * 0.6f && v in 0.15f..0.68f
                val star = hash01(seed * 31 + x * 32 + y, 2) > 0.96f && v < 0.5f
                when {
                    trunk -> 0.25f
                    crown -> 0.05f
                    v > 0.86f -> 0.35f
                    star -> 1f
                    else -> 0.62f
                }
            }
        }
        if (inked(l, x, y)) drawRect(style.colors.content, Offset(x * w, y * h), Size(w + 0.5f, h + 0.5f))
    }
}

/** 游's motif: the cat, sitting. */
internal fun DrawScope.gameMotif(color: Color) {
    val (sw, sh) = spriteSize(Sprites.CatSit)
    val px = (size.minDimension * 0.6f / sw).toInt().coerceAtLeast(1).toFloat()
    drawSprite(Sprites.CatSit, (size.width - sw * px) / 2f, (size.height - sh * px) / 2f, px, color)
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
