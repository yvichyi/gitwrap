package io.lanyin.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle

/**
 * 格's ribbon: a black band of monospaced capitals scrolling sideways forever, the items set off
 * by alternating yellow stars and red sparks. Other themes pass their own [fill], [ink], [marks]
 * and [font].
 */
@Composable
fun Marquee(
    items: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    speed: Dp = 26.dp,
    fill: Color = Lanyin.colors.content,
    ink: Color = Lanyin.colors.bg,
    marks: Pair<Color, Color> = GridYellow to Lanyin.colors.accent,
    font: FontFamily = FontFamily.Monospace,
) {
    val clock = rememberFrameClock(true)
    val measurer = rememberTextMeasurer()
    // A ribbon is decoration with a fixed height: keep its type at a fixed size too.
    val fontScale = LocalDensity.current.fontScale
    val text = remember(items, marks) {
        buildAnnotatedString {
            items.forEachIndexed { i, item ->
                withStyle(SpanStyle(color = if (i % 2 == 0) marks.first else marks.second)) { append(if (i % 2 == 0) "  ★  " else "  ✦  ") }
                append(item)
            }
            append("   ")
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(fill)
            .drawWithCache {
                val layout = measurer.measure(
                    text,
                    TextStyle(
                        fontFamily = font, fontWeight = FontWeight.Bold,
                        fontSize = (12f / fontScale).sp, letterSpacing = 0.1.em, color = ink,
                    ),
                    softWrap = false,
                )
                onDrawBehind {
                    val unit = layout.size.width.toFloat().coerceAtLeast(1f)
                    var x = -((clock.floatValue * speed.toPx()) % unit)
                    val y = (size.height - layout.size.height) / 2f
                    clipRect {
                        while (x < size.width) {
                            drawText(layout, topLeft = Offset(x, y))
                            x += unit
                        }
                    }
                }
            },
    )
}

/** A round yellow sticker slapped over a corner, turning slowly. */
@Composable
fun Sticker(lines: List<String>, modifier: Modifier = Modifier, size: Dp = 66.dp) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(true)
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationZ = (clock.floatValue * 360f / 14f) % 360f }
            .drawBehind {
                val r = this.size.minDimension / 2f - 3.dp.toPx()
                drawCircle(c.outline, r, center + Offset(3.dp.toPx(), 3.dp.toPx()))
                drawCircle(GridYellow, r)
                drawCircle(c.outline, r, style = Stroke(2.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            lines.joinToString("\n"),
            style = TextStyle(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = (9f / fontScale).sp, lineHeight = (11f / fontScale).sp, textAlign = TextAlign.Center,
            ),
            color = c.outline,
        )
    }
}

/** A section number in a red box, set in mono: 「01」. */
@Composable
fun IndexTag(index: Int, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Text(
        "%02d".format(index),
        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.05.em),
        color = c.onAccent,
        modifier = modifier
            .background(c.accent)
            .border(2.dp, c.outline)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * 格's paper: the twelve-column grid the layout is set on, baseline rows and crop marks, all
 * faint. On the player the track number stands huge and outlined like a Swiss poster, with a red
 * disc and a blue bar in landscape.
 */
internal fun DrawScope.drawGridPaper(style: LanyinStyle, page: Boolean, measurer: TextMeasurer?, trackNumber: Int?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    val column = c.content.copy(alpha = 0.05f)
    for (i in 1 until 12) {
        val x = w * i / 12f
        drawLine(column, Offset(x, 0f), Offset(x, h), 1f)
    }
    val row = 48.dp.toPx()
    var y = row
    while (y < h) {
        drawLine(c.content.copy(alpha = 0.028f), Offset(0f, y), Offset(w, y), 1f)
        y += row
    }
    // Crop marks, as on a print sheet.
    val mark = 14.dp.toPx()
    val inset = 8.dp.toPx()
    val ink = c.content.copy(alpha = 0.3f)
    for ((x0, dx) in listOf(inset to 1f, w - inset to -1f)) {
        for ((y0, dy) in listOf(inset to 1f, h - inset to -1f)) {
            drawLine(ink, Offset(x0, y0), Offset(x0 + dx * mark, y0), 1.dp.toPx())
            drawLine(ink, Offset(x0, y0), Offset(x0, y0 + dy * mark), 1.dp.toPx())
        }
    }
    if (page || measurer == null || trackNumber == null) return

    val landscape = w > h
    val numeral = "%02d".format(trackNumber)
    val sizePx = if (landscape) h * 0.66f else w * 0.6f
    val layout = measurer.measure(
        numeral,
        TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = (sizePx / density / fontScale).sp, letterSpacing = (-0.04).em),
    )
    val topLeft = if (landscape) {
        Offset(w - layout.size.width * 0.94f, h - layout.size.height * 0.98f)
    } else {
        Offset(-layout.size.width * 0.06f, h * 0.02f)
    }
    if (landscape) {
        drawText(layout, color = c.content.copy(alpha = 0.14f), topLeft = topLeft, drawStyle = Stroke(2.dp.toPx()))
    } else {
        // Mostly behind the record in portrait: a flat printed tint reads better than an outline.
        drawText(layout, color = c.content.copy(alpha = 0.055f), topLeft = topLeft)
    }
    if (landscape) {
        drawCircle(c.accent, h * 0.07f, Offset(w * 0.9f, h * 0.24f))
        drawCircle(c.outline, h * 0.07f, Offset(w * 0.9f, h * 0.24f), style = Stroke(2.5.dp.toPx()))
        val bar = Size(w * 0.12f, h * 0.035f)
        drawRect(c.accentAlt, Offset(w * 0.78f, h * 0.36f), bar)
        drawRect(c.outline, Offset(w * 0.78f, h * 0.36f), bar, style = Stroke(2.5.dp.toPx()))
    }
}

/** Swiss-poster sleeves: big geometry in the three primaries on paper, framed in ink. */
internal fun DrawScope.gridCover(style: LanyinStyle, seed: Int) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val ink = c.outline
    val stroke = w * 0.025f
    when (seed % 4) {
        0 -> {
            // A red disc over a black rule.
            drawRect(c.bg)
            drawRect(ink, Offset(0f, h * 0.74f), Size(w, h * 0.07f))
            val center = Offset(w * (0.4f + 0.1f * hash01(seed, 1)), h * 0.44f)
            drawCircle(c.accent, w * 0.3f, center)
            drawCircle(ink, w * 0.3f, center, style = Stroke(stroke))
            drawRect(GridYellow, Offset(w * 0.74f, h * 0.12f), Size(w * 0.14f, w * 0.14f))
            drawRect(ink, Offset(w * 0.74f, h * 0.12f), Size(w * 0.14f, w * 0.14f), style = Stroke(stroke * 0.8f))
        }
        1 -> {
            // A blue field on a grid with a yellow quarter circle.
            drawRect(c.accentAlt)
            val white = Color.White.copy(alpha = 0.35f)
            for (i in 1..3) {
                drawLine(white, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), w * 0.008f)
                drawLine(white, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), w * 0.008f)
            }
            drawArc(GridYellow, 270f, 90f, true, Offset(-w * 0.5f, h * 0.5f), Size(w, h))
            drawRect(ink, Offset(w * 0.62f, h * 0.14f), Size(w * 0.24f, w * 0.24f))
        }
        2 -> {
            // Hazard stripes under a white sun.
            drawRect(GridYellow)
            val band = w * 0.1f
            clipRect(top = h * 0.55f) {
                var x = -h
                while (x < w + h) {
                    val p = Path().apply {
                        moveTo(x, h)
                        lineTo(x + band, h)
                        lineTo(x + band + h * 0.45f, h * 0.55f)
                        lineTo(x + h * 0.45f, h * 0.55f)
                        close()
                    }
                    drawPath(p, ink)
                    x += band * 2
                }
            }
            drawRect(ink, Offset(0f, h * 0.53f), Size(w, h * 0.03f))
            val sun = Offset(w * 0.66f, h * 0.28f)
            drawCircle(Color.White, w * 0.17f, sun)
            drawCircle(ink, w * 0.17f, sun, style = Stroke(stroke))
        }
        else -> {
            // A bar chart: black columns, one red, one blue.
            drawRect(c.bg)
            val bars = 6
            val gap = w * 0.04f
            val bw = (w * 0.76f - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val level = 0.3f + 0.6f * hash01(seed * 7 + i, 3)
                val color = when (i) {
                    2 -> c.accent
                    4 -> c.accentAlt
                    else -> ink
                }
                val left = w * 0.12f + i * (bw + gap)
                drawRect(color, Offset(left, h * 0.82f - h * 0.62f * level), Size(bw, h * 0.62f * level))
            }
            drawRect(ink, Offset(w * 0.08f, h * 0.82f), Size(w * 0.84f, h * 0.025f))
        }
    }
    drawRect(ink, style = Stroke(stroke))
}

/** 格's empty-state mark: a red disc pinned under an ink square, a yellow chip in the corner. */
internal fun DrawScope.gridMotif(style: LanyinStyle) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val r = minOf(w, h) * 0.26f
    val stroke = 2.5.dp.toPx()
    val disc = Offset(w * 0.4f, h * 0.56f)
    drawCircle(c.outline, r, disc + Offset(stroke * 1.4f, stroke * 1.4f))
    drawCircle(c.accent, r, disc)
    drawCircle(c.outline, r, disc, style = Stroke(stroke))
    drawRect(c.outline, Offset(w * 0.46f, h * 0.2f), Size(r * 1.5f, r * 1.5f), style = Stroke(stroke))
    val chip = r * 0.5f
    drawRect(GridYellow, Offset(w * 0.46f + r * 1.5f - chip * 0.6f, h * 0.2f - chip * 0.4f), Size(chip, chip))
    drawRect(c.outline, Offset(w * 0.46f + r * 1.5f - chip * 0.6f, h * 0.2f - chip * 0.4f), Size(chip, chip), style = Stroke(stroke))
}
