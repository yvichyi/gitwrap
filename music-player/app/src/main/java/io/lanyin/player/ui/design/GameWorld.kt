package io.lanyin.player.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Where the six places stand on the island, as fractions of the map: the order of the home page's doors. */
internal val WorldNodes = listOf(0.26f to 0.3f, 0.74f to 0.24f, 0.82f to 0.6f, 0.6f to 0.84f, 0.22f to 0.76f, 0.5f to 0.52f)

/** The places, in the same order: castle, radio tower, the house with a heart, the flag on the mountain, clock tower, cave. */
private val WorldSprites = listOf(Sprites.Castle, Sprites.Tower, Sprites.House, Sprites.Mountain, Sprites.Clock, Sprites.Cave)

/**
 * 游's world map: an island in a hatched sea, drawn once in one bit for its size. Six places on
 * it are the ways in, each a level with its number (1-1 … 1-6), joined by dotted paths; trees,
 * grass, a boat and two clouds. The cat sits by [selected] with the pointer over it and walks
 * when another is chosen; a tap on a place chooses it.
 */
@Composable
fun WorldMap(selected: Int, labels: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val px = with(density) { BitPx.toPx() }
        val cols = with(density) { (maxWidth.toPx() / px).toInt() }.coerceAtLeast(40)
        val rows = with(density) { (maxHeight.toPx() / px).toInt() }.coerceAtLeast(40)
        val ink = c.content
        val paper = c.bg
        val map = remember(cols, rows, ink, paper) { renderWorld(cols, rows, ink.toArgb(), paper.toArgb()) }
        // The cat walks from place to place.
        val at = remember { Animatable(selected.toFloat()) }
        LaunchedEffect(selected) { at.animateTo(selected.toFloat(), tween(520)) }
        var bob by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(480)
                bob = !bob
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawPixels(map, IntOffset.Zero, IntSize((cols * px).toInt(), (rows * px).toInt()))
            // Between two places the cat walks the straight line, two frames of its stride.
            val f = at.value
            val i0 = f.toInt().coerceIn(0, WorldNodes.size - 1)
            val i1 = (i0 + 1).coerceAtMost(WorldNodes.size - 1)
            val t = f - i0
            val walking = abs(f - selected) > 0.01f
            val (ax, ay) = WorldNodes[i0]
            val (bx, by) = WorldNodes[i1]
            val gx = ((ax + (bx - ax) * t) * cols).toInt()
            val gy = ((ay + (by - ay) * t) * rows).toInt()
            val cat = if (walking) (if ((f * 8).toInt() % 2 == 0) Sprites.CatRun else Sprites.CatRun2) else Sprites.CatSit
            val (cw, ch) = spriteSize(cat)
            val cx = (gx + 9).toFloat()
            val cy = (gy - ch).toFloat()
            drawRect(paper, Offset((cx - 1) * px, (cy - 1) * px), androidx.compose.ui.geometry.Size((cw + 2) * px, (ch + 2) * px))
            drawSprite(cat, cx * px, cy * px, px, ink, paper)
            if (!walking) {
                val py = gy - 24 + if (bob) 1 else 0
                drawRect(paper, Offset((gx - 3) * px, (py - 1) * px), androidx.compose.ui.geometry.Size(7 * px, 5 * px))
                drawSprite(Sprites.Pointer, (gx - 2) * px, py * px, px, ink)
            }
        }
        // Each place is a button of its own, for touch and for the screen reader.
        WorldNodes.forEachIndexed { i, (fx, fy) ->
            val (sw, sh) = spriteSize(WorldSprites[i])
            val w = with(density) { ((sw + 10) * px).toDp() }
            val h = with(density) { ((sh + 14) * px).toDp() }
            Box(
                Modifier
                    .offset(maxWidth * fx - w / 2, maxHeight * fy - with(density) { ((sh + 2) * px).toDp() })
                    .size(w, h)
                    .pressable(label = labels.getOrNull(i)) { onSelect(i) },
            )
        }
    }
}

/** The map, [cols] by [rows] pixels of [ink] and [paper]. */
private fun renderWorld(cols: Int, rows: Int, ink: Int, paper: Int): ImageBitmap {
    val img = IntArray(cols * rows) { paper }
    fun put(x: Int, y: Int, v: Int) {
        if (x in 0 until cols && y in 0 until rows) img[y * cols + x] = v
    }
    fun sprite(rowsOf: List<String>, x: Int, y: Int, color: Int = ink, back: Int? = null) {
        for ((j, row) in rowsOf.withIndex()) for ((i, ch) in row.withIndex()) {
            when (ch) {
                '#' -> put(x + i, y + j, color)
                'o' -> if (back != null) put(x + i, y + j, back)
            }
        }
    }
    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
        for (y in y0..y1) for (x in x0..x1) put(x, y, v)
    }
    fun tiny(text: String, x0: Int, y0: Int, v: Int) {
        var x = x0
        for (chr in text) {
            val glyph = tinyGlyphs[chr] ?: continue
            for ((j, row) in glyph.withIndex()) for ((i, c) in row.withIndex()) if (c == '#') put(x + i, y0 + j, v)
            x += 4
        }
    }
    val w = cols.toFloat()
    val h = rows.toFloat()
    // The sea: short strokes of hatching, staggered row to row.
    for (y in 0 until rows) for (x in 0 until cols) {
        val k = (x + (y / 7) * 3) % 9
        if ((y % 7 == 0 && k <= 1) || (y % 7 == 1 && k == 2)) put(x, y, ink)
    }
    // The island: a lumpy circle; its shore inked, a dithered beach just inside.
    val cx = w * 0.5f
    val cy = h * 0.52f
    val land = BooleanArray(cols * rows)
    for (y in 3 until rows - 3) for (x in 3 until cols - 3) {
        val a = atan2(y - cy, (x - cx) * (h / w))
        val r = 0.43f * h + 0.05f * h * sin(a * 3 + 1) + 0.035f * h * sin(a * 5 + 2) + 0.02f * h * sin(a * 9)
        if (hypot((x - cx) * (h / w), y - cy) < r) land[y * cols + x] = true
    }
    fun isLand(x: Int, y: Int) = x in 0 until cols && y in 0 until rows && land[y * cols + x]
    for (y in 0 until rows) for (x in 0 until cols) {
        if (!isLand(x, y)) continue
        val edge = !(isLand(x, y - 1) && isLand(x, y + 1) && isLand(x - 1, y) && isLand(x + 1, y))
        put(x, y, if (edge) ink else paper)
        if (!edge && !isLand(x, y + 2) && (x + y) % 2 == 0) put(x, y, ink)
    }
    val rnd = java.util.Random(7)
    // Grass: little tufts.
    repeat((w * h / 90).toInt()) {
        val x = 6 + rnd.nextInt(max(1, cols - 12))
        val y = 6 + rnd.nextInt(max(1, rows - 12))
        if (isLand(x, y) && isLand(x, y + 3) && isLand(x, y - 3) && isLand(x + 3, y)) {
            put(x, y, ink); put(x + 2, y, ink); put(x + 1, y - 1, ink)
        }
    }
    val nodes = WorldNodes.map { (fx, fy) -> (fx * w).toInt() to (fy * h).toInt() }
    // Paths: dotted, between neighbours.
    for ((a, b) in listOf(0 to 5, 5 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0, 5 to 3)) {
        val (x0, y0) = nodes[a]
        val (x1, y1) = nodes[b]
        val n = max(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        var t = 0
        while (t < n) {
            put(x0 + (x1 - x0) * t / n, y0 + (y1 - y0) * t / n, ink)
            t += 3
        }
    }
    // Trees, clear of the places.
    repeat((w * h / 1400).toInt()) {
        val x = 8 + rnd.nextInt(max(1, cols - 16))
        val y = 8 + rnd.nextInt(max(1, rows - 20))
        if (isLand(x + 2, y + 7) && isLand(x, y) && nodes.all { (nx, ny) -> hypot((x - nx).toFloat(), (y - ny).toFloat()) > 16f }) sprite(Sprites.Tree, x, y)
    }
    // The places, each on a patch of paper, and what marks them.
    nodes.forEachIndexed { i, (x, y) ->
        val spr = WorldSprites[i]
        val sw = spr.maxOf { it.length }
        val sh = spr.size
        rect(x - sw / 2 - 1, y - sh - 1, x + sw / 2 + 1, y + 1, paper)
        sprite(spr, x - sw / 2, y - sh, back = paper)
    }
    // The tower's signal, the heart over the house, the flag on the mountain.
    val (fx, fy) = nodes[1]
    for (r in listOf(5f, 8f)) {
        var a = -0.9f
        while (a <= 0.9f) {
            put((fx + r * sin(a)).toInt(), (fy - 11 - r * kotlin.math.cos(a) * 0.8f).toInt(), ink)
            a += 0.3f
        }
    }
    sprite(Sprites.Heart, nodes[2].first - 3, nodes[2].second - 18)
    val (mx, my) = nodes[3]
    rect(mx, my - 12, mx, my - 6, ink)
    rect(mx + 1, my - 12, mx + 4, my - 10, ink)
    // Level numbers, knocked out of ink tags under each place.
    nodes.forEachIndexed { i, (x, y) ->
        rect(x - 7, y + 4, x + 5, y + 10, ink)
        tiny("1-${i + 1}", x - 6, y + 5, paper)
    }
    // A boat in the corner and clouds over the sea.
    sprite(Sprites.Boat, 8, rows - 14)
    for ((x, y) in listOf(20 to 10, cols - 50 to rows - 24)) {
        sprite(Sprites.CloudFill, x, y, paper)
        sprite(Sprites.Cloud, x, y - 1)
    }
    return android.graphics.Bitmap.createBitmap(img, cols, rows, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
