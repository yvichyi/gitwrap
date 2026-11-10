package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.LocalLanyinStyle
import io.lanyin.player.ui.theme.LongCangFamily
import io.lanyin.player.ui.theme.PostcardSerifFamily
import io.lanyin.player.ui.theme.paperStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 暮's inks, its paper and the things on the desk. */
object Riso {
    val Pink = Color(0xFFFF48B0)
    val Yellow = Color(0xFFFFB511)
    val Blue = Color(0xFF3255A4)
    /** Pink printed over blue. */
    val Violet = Color(0xFF3B1F78)
    /** Pink over yellow. */
    val Orange = Color(0xFFFF5A3C)
    val Paper = Color(0xFFF2EBDD)
    val Graphite = Color(0xFF4E4C56)
    val RedPencil = Color(0xFFC0392B)
    val Tape = Color(0xD9E7DAB4)
    val Inks = listOf(Pink, Yellow, Blue)
    /** The colours a riso shop sells these drums as. */
    val InkNames = listOf("FL.PINK", "SUNFLOWER", "MED.BLUE")
    /** Each ink and each pair overprinted, for swatches. */
    val Swatches = listOf(Pink, Yellow, Blue, Orange, Violet, overprint(Yellow, Blue))
}

/** One ink printed over another: the colours multiply. */
fun overprint(a: Color, b: Color): Color = Color(a.red * b.red, a.green * b.green, a.blue * b.blue, 1f)

/** The handwriting a print's margin is annotated in. */
internal fun pencilStyle(size: TextUnit, color: Color = Riso.Graphite) = TextStyle(fontFamily = LongCangFamily, fontSize = size, lineHeight = size * 1.2f, color = color)

// ── the mat ──

/**
 * The cutting mat under everything: centimetre squares, millimetre lines, a heavier line every
 * five centimetres and the 45° guides, worn a little; the desk lamp's warm pool on it, stronger
 * once it's dark. The grid starts a little in from the corner, as a mat's does.
 */
internal fun DrawScope.drawCuttingMat(style: LanyinStyle, hour: Float, measurer: TextMeasurer?) {
    val c = style.colors
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    drawRect(Brush.radialGradient(listOf(c.bg, c.bgDeep), Offset(w * 0.35f, h * 0.25f), max(w, h) * 0.95f))
    val line = c.content
    val cm = 36.dp.toPx()
    val origin = Offset(10.dp.toPx(), 6.dp.toPx())
    val faint = if (style.dark) 0.035f else 0.06f
    val strong = if (style.dark) 0.09f else 0.16f
    // Millimetre lines (every two, as they're printed), then centimetres, then every fifth heavier.
    var x = origin.x
    var i = 0
    while (x < w) {
        val a = when {
            i % 25 == 0 -> strong * 1.5f
            i % 5 == 0 -> strong
            else -> faint
        }
        val sw = if (i % 25 == 0) 1.3.dp.toPx() else if (i % 5 == 0) 0.8.dp.toPx() else 0.5.dp.toPx()
        drawLine(line.copy(alpha = a), Offset(x, 0f), Offset(x, h), sw)
        x += cm / 5f
        i++
    }
    var y = origin.y
    i = 0
    while (y < h) {
        val a = when {
            i % 25 == 0 -> strong * 1.5f
            i % 5 == 0 -> strong
            else -> faint
        }
        val sw = if (i % 25 == 0) 1.3.dp.toPx() else if (i % 5 == 0) 0.8.dp.toPx() else 0.5.dp.toPx()
        drawLine(line.copy(alpha = a), Offset(0f, y), Offset(w, y), sw)
        y += cm / 5f
        i++
    }
    // The 45° guides from the corner.
    val step = cm * 5f
    var d = step
    while (d < w + h) {
        drawLine(line.copy(alpha = faint * 1.2f), Offset(origin.x + d, origin.y), Offset(origin.x + d - h * 1.2f, origin.y + h * 1.2f), 0.6.dp.toPx())
        d += step
    }
    // The centimetre figures along the left edge.
    if (measurer != null) {
        val label = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.SemiBold, color = line.copy(alpha = if (style.dark) 0.3f else 0.45f))
        var k = 1
        var yy = origin.y + cm
        while (yy < h - cm / 2f) {
            if (k % 2 == 0) drawText(measurer, "$k", Offset(origin.x + 3.dp.toPx(), yy - 9.dp.toPx()), label)
            yy += cm
            k++
        }
    }
    // Worn spots where the knife has gone over and over.
    drawRect(grainBrush, alpha = if (style.dark) 0.05f else 0.07f, blendMode = BlendMode.Overlay)
    // The lamp: warm, up and to the left, brighter in the evening and at night.
    val night = nightness(hour)
    val dusk = ((hour - 16f) / 3f).coerceIn(0f, 1f)
    val lamp = (if (style.dark) 0.12f else 0.05f) + 0.07f * maxOf(night, dusk)
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = lamp), Color.Transparent), Offset(w * 0.18f, -h * 0.02f), max(w, h) * 0.75f))
}

// ── paper and what's on it ──

/** Where a strip of masking tape holds a sheet down. */
enum class TapeAt { TopLeft, TopRight, Top, BottomLeft, BottomRight, Left, Right }

/**
 * A sheet of paper taped to the mat: cream with a fibre to it, a soft shadow under its lifted
 * edge, set down at a slight [tilt]; [tapes] say where the masking tape goes. What's inside
 * is drawn in the paper's own style (ink on cream), see [paperStyle].
 */
@Composable
fun RisoSheet(
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    tapes: List<TapeAt> = emptyList(),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = Lanyin.style
    val paper = remember(style) { style.paperStyle() }
    Box(
        modifier
            .graphicsLayer { rotationZ = tilt }
            .drawBehind { drawPaper(paper.colors.bg, style.dark) }
            .drawWithContent {
                drawContent()
                for (t in tapes) drawTapeAt(t)
            }
            .then(if (onClick != null) Modifier.pressable(label = label, onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalLanyinStyle provides paper, LocalContentColor provides paper.colors.content) {
            content()
        }
    }
}

/** A sheet's paper and its shadow on the mat, its edge lifting off a little more at the corners. */
internal fun DrawScope.drawPaper(paper: Color, dark: Boolean) {
    val lift = 3.dp.toPx()
    val shade = if (dark) 0.5f else 0.32f
    for (k in 1..3) {
        val spread = k * 2.dp.toPx()
        drawRect(
            Color.Black.copy(alpha = shade * 0.22f / k),
            Offset(-spread * 0.5f + lift * 0.4f, -spread * 0.3f + lift),
            Size(size.width + spread, size.height + spread * 0.6f),
        )
    }
    drawRect(paper)
    drawRect(grainBrush, alpha = 0.07f, blendMode = BlendMode.Multiply)
    // The light falls off a touch towards the bottom of the sheet.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.035f))))
}

private fun DrawScope.drawTapeAt(at: TapeAt) {
    val w = size.width
    val h = size.height
    val len = 46.dp.toPx()
    val wide = 15.dp.toPx()
    when (at) {
        TapeAt.TopLeft -> drawTape(Offset(6.dp.toPx(), 4.dp.toPx()), len, wide, -38f)
        TapeAt.TopRight -> drawTape(Offset(w - 6.dp.toPx(), 4.dp.toPx()), len, wide, 38f)
        TapeAt.Top -> drawTape(Offset(w / 2f, 0f), len * 1.15f, wide, -3f)
        TapeAt.BottomLeft -> drawTape(Offset(6.dp.toPx(), h - 4.dp.toPx()), len, wide, 38f)
        TapeAt.BottomRight -> drawTape(Offset(w - 6.dp.toPx(), h - 4.dp.toPx()), len, wide, -38f)
        TapeAt.Left -> drawTape(Offset(0f, h / 2f), len, wide, 88f)
        TapeAt.Right -> drawTape(Offset(w, h / 2f), len, wide, 92f)
    }
}

/** A strip of masking tape centred on [center], torn at both ends, a little see-through. */
internal fun DrawScope.drawTape(center: Offset, length: Float, width: Float, angle: Float) {
    rotate(angle, center) {
        val l = center.x - length / 2f
        val r = center.x + length / 2f
        val t = center.y - width / 2f
        val b = center.y + width / 2f
        val tooth = width / 6f
        val path = Path().apply {
            moveTo(l, t)
            lineTo(r, t)
            // Torn ends: a zigzag down each.
            var y = t
            var k = 0
            while (y < b) {
                y += tooth
                lineTo(r + if (k % 2 == 0) -tooth * 0.7f else 0f, minOf(y, b))
                k++
            }
            lineTo(l, b)
            y = b
            k = 0
            while (y > t) {
                y -= tooth
                lineTo(l + if (k % 2 == 0) tooth * 0.7f else 0f, maxOf(y, t))
                k++
            }
            close()
        }
        translate(0.6.dp.toPx(), 1.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.12f)) }
        drawPath(path, Riso.Tape)
        // The crêpe of the paper, in faint lines across it.
        var x = l + 3.dp.toPx()
        while (x < r - 2.dp.toPx()) {
            drawLine(Color.White.copy(alpha = 0.16f), Offset(x, t + 1.dp.toPx()), Offset(x + 1.dp.toPx(), b - 1.dp.toPx()), 0.6.dp.toPx())
            x += 2.6.dp.toPx()
        }
    }
}

/** Crop marks outside the corners of [rect]: where the guillotine goes. */
internal fun DrawScope.drawCropMarks(rect: Rect, color: Color, length: Float = 9.dp.toPx(), gap: Float = 3.dp.toPx()) {
    val sw = 0.7.dp.toPx()
    for ((cx, sx) in listOf(rect.left to -1f, rect.right to 1f)) for ((cy, sy) in listOf(rect.top to -1f, rect.bottom to 1f)) {
        drawLine(color, Offset(cx + sx * gap, cy), Offset(cx + sx * (gap + length), cy), sw)
        drawLine(color, Offset(cx, cy + sy * gap), Offset(cx, cy + sy * (gap + length)), sw)
    }
}

/** A registration mark: a ring, a cross through it and a dot in the middle. */
internal fun DrawScope.drawRegMark(center: Offset, r: Float, color: Color) {
    val sw = max(0.7.dp.toPx(), r * 0.11f)
    drawCircle(color, r * 0.55f, center, style = Stroke(sw))
    drawLine(color, center - Offset(r, 0f), center + Offset(r, 0f), sw)
    drawLine(color, center - Offset(0f, r), center + Offset(0f, r), sw)
    drawCircle(color, r * 0.2f, center)
}

/**
 * A 2B pencil lying on the desk, its point at [tip], pointing back along [angle] degrees: the
 * sharpened wood and graphite, the green lacquered body with its lettering, the ferrule and the
 * eraser; and its shadow.
 */
internal fun DrawScope.drawPencil(tip: Offset, angle: Float, length: Float, measurer: TextMeasurer?) {
    val wide = 7.dp.toPx()
    rotate(angle, tip) {
        val x0 = tip.x
        val y = tip.y
        val cone = length * 0.1f
        val ferrule = length * 0.07f
        val eraser = length * 0.06f
        val body = length - cone - ferrule - eraser
        // Shadow on the mat, offset down and away from the lamp.
        translate(2.dp.toPx(), 4.dp.toPx()) {
            drawPath(Path().apply {
                moveTo(x0, y)
                lineTo(x0 + cone, y - wide / 2f)
                lineTo(x0 + length, y - wide / 2f)
                lineTo(x0 + length, y + wide / 2f)
                lineTo(x0 + cone, y + wide / 2f)
                close()
            }, Color.Black.copy(alpha = 0.28f))
        }
        // Sharpened wood, then the graphite point.
        drawPath(Path().apply {
            moveTo(x0, y)
            lineTo(x0 + cone, y - wide / 2f)
            lineTo(x0 + cone, y + wide / 2f)
            close()
        }, Color(0xFFE6C79C))
        drawPath(Path().apply {
            moveTo(x0, y)
            lineTo(x0 + cone * 0.35f, y - wide * 0.18f)
            lineTo(x0 + cone * 0.35f, y + wide * 0.18f)
            close()
        }, Color(0xFF3A3A3E))
        // The lacquered body: three faces of the hexagon.
        val bx = x0 + cone
        drawRect(Color(0xFF2F5D4A), Offset(bx, y - wide / 2f), Size(body, wide))
        drawRect(Color(0xFF417A62), Offset(bx, y - wide / 2f), Size(body, wide * 0.32f))
        drawRect(Color(0xFF234A3A), Offset(bx, y + wide * 0.18f), Size(body, wide * 0.32f))
        if (measurer != null) {
            val label = TextStyle(fontSize = (wide * 0.5f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.12.em, color = Color(0xFFE3C26A))
            drawText(measurer, "LANYIN · 2B · RISO DRAW", Offset(bx + body * 0.12f, y - wide * 0.42f), label)
        }
        // Ferrule with its rings, and the eraser.
        val fx = bx + body
        drawRect(Color(0xFFC9CDD1), Offset(fx, y - wide / 2f), Size(ferrule, wide))
        for (k in 1..3) drawLine(Color(0xFF8E9399), Offset(fx + ferrule * k / 4f, y - wide / 2f), Offset(fx + ferrule * k / 4f, y + wide / 2f), 0.8.dp.toPx())
        drawRoundRect(Color(0xFFE88A9A), Offset(fx + ferrule, y - wide / 2f), Size(eraser, wide), CornerRadius(wide * 0.3f))
    }
}

/** A pencil drawn across the composable's box, point at the bottom left. */
@Composable
fun Pencil(modifier: Modifier = Modifier, angle: Float = -18f) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier) {
        val len = size.width / cos(angle * PI.toFloat() / 180f)
        drawPencil(Offset(0f, size.height * 0.85f), angle, len * 0.98f, measurer)
    }
}

/**
 * Lettering printed twice, from the pink drum and the blue, the pink landing a hair off: where
 * they overlap it's violet, and a sliver of pink shows along one side.
 */
@Composable
fun Overprint(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    under: Color = Riso.Pink,
    over: Color = Riso.Blue,
    shift: DpOffset = DpOffset(1.6.dp, 1.2.dp),
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    Box(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        Text(text, style = style, color = under, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign, modifier = Modifier.offset(shift.x, shift.y))
        Text(text, style = style, color = over, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign, modifier = Modifier.graphicsLayer { blendMode = BlendMode.Multiply })
    }
}

/** A note in pencil on a margin. */
@Composable
fun PencilNote(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, color: Color = Riso.Graphite, maxLines: Int = 1) {
    Text(text, style = pencilStyle(size, color), maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 * A colour bar as printed along a sheet's edge to check the inks: each ink and each pair
 * overprinted, in little squares, across the canvas from the left.
 */
internal fun DrawScope.drawInkBar(cell: Float = 7f * density, alpha: Float = 1f) {
    val gap = cell * 0.25f
    var x = 0f
    var i = 0
    val y = (size.height - cell) / 2f
    while (x + cell <= size.width + 0.5f) {
        drawRect(Riso.Swatches[i % Riso.Swatches.size].copy(alpha = alpha), Offset(x, y), Size(cell, cell))
        x += cell + gap
        i++
    }
}

/** The mark before a section's title: a registration mark in the set's next ink. */
@Composable
internal fun InkMark(index: Int?) {
    val ink = Riso.Inks[((index ?: 1) - 1).mod(Riso.Inks.size)]
    Canvas(Modifier.size(15.dp)) { drawRegMark(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, ink) }
}

/**
 * A round rubber stamp, inked a little unevenly and struck at an angle: two rings with [top] and
 * [bottom] small inside and [middle] large across the centre. A printer's edition stamp.
 */
@Composable
fun InkStamp(top: String, middle: String, bottom: String, ink: Color, modifier: Modifier = Modifier, diameter: Dp = 50.dp, angle: Float = -12f) {
    Box(modifier.size(diameter).graphicsLayer { rotationZ = angle; compositingStrategy = CompositingStrategy.Offscreen }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            drawCircle(ink, r - 1.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
            drawCircle(ink, r - 5.dp.toPx(), c, style = Stroke(0.8.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sp = { v: Float -> with(density) { (diameter.value * v).dp.toSp() } }
            val label = TextStyle(fontWeight = FontWeight.Bold, fontSize = sp(0.12f), lineHeight = sp(0.15f), letterSpacing = 0.1.em, textAlign = TextAlign.Center)
            Text(top, style = label, color = ink, maxLines = 1)
            Text(middle, style = TextStyle(fontFamily = PostcardSerifFamily, fontSize = sp(0.28f), lineHeight = sp(0.32f), textAlign = TextAlign.Center), color = ink, maxLines = 1)
            Text(bottom, style = label, color = ink, maxLines = 1)
        }
        // Where the stamp didn't take: flecks of the paper showing through the ink.
        Canvas(Modifier.size(diameter).graphicsLayer { blendMode = BlendMode.DstOut }) {
            val n = 22
            for (k in 0 until n) {
                val a = RisoScreen.noise(k, 3, 11) * 2f * PI.toFloat()
                val d = RisoScreen.noise(k, 5, 11) * size.minDimension * 0.5f
                drawCircle(Color.Black.copy(alpha = 0.7f), (0.6f + RisoScreen.noise(k, 7, 11) * 1.4f).dp.toPx(), Offset(size.width / 2f + cos(a) * d, size.height / 2f + sin(a) * d))
            }
        }
    }
}

/**
 * An ink swatch, as torn from a printer's swatch book: a block of [ink] (one drum, or two
 * overprinted) above a white strip with [label] and [note], [icon] beside them.
 */
@Composable
fun SwatchChip(icon: LineIcon, label: String, note: String, ink: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier, tilt: Float = 0f) {
    RisoSheet(modifier.heightIn(min = 60.dp), tilt = tilt, onClick = onClick, contentPadding = PaddingValues(0.dp), label = label) {
        val c = Lanyin.colors
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp, 60.dp)
                    .drawBehind {
                        drawRect(ink)
                        drawRect(grainBrush, alpha = 0.12f, blendMode = BlendMode.Multiply)
                    },
                contentAlignment = Alignment.Center,
            ) { ThemedIcon(icon, null, tint = Color.White.copy(alpha = 0.92f), size = 18.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                Text(label, style = Lanyin.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(note, style = Lanyin.type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A finished print on its sheet: the picture with a margin of paper round it, crop marks at the
 * corners and whatever's written on the margin below ([caption]).
 */
@Composable
fun PrintFrame(
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    tapes: List<TapeAt> = listOf(TapeAt.TopLeft, TapeAt.TopRight),
    margin: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    label: String? = null,
    /** The sheet has a set height and the picture takes what the caption leaves. */
    fill: Boolean = false,
    picture: @Composable BoxScope.() -> Unit,
    caption: (@Composable () -> Unit)? = null,
) {
    RisoSheet(modifier, tilt = tilt, tapes = tapes, onClick = onClick, contentPadding = PaddingValues(margin), label = label) {
        Column(if (fill) Modifier.fillMaxSize() else Modifier) {
            Box(
                (if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier).drawWithContent {
                    drawContent()
                    drawCropMarks(Rect(Offset.Zero, size), Riso.Graphite.copy(alpha = 0.55f), 6.dp.toPx(), 2.dp.toPx())
                },
                content = picture,
            )
            if (caption != null) Box(Modifier.padding(top = 6.dp)) { caption() }
        }
    }
}


/**
 * A print of [field] filling the box, dots [cell] apart ([only] for one drum's proof). Plain
 * paper shows while it's being made.
 */
@Composable
internal fun RisoPicture(key: Any, modifier: Modifier, cell: Dp = 2.4.dp, only: Int = -1, field: (Int, Int) -> InkField) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cellPx = with(androidx.compose.ui.platform.LocalDensity.current) { cell.toPx() }
        val image = rememberPrint(listOf(key, only, cellPx), w, h) { pw, ph -> RisoScreen.print(pw, ph, cellPx, field(pw, ph), only = only) }
        Canvas(Modifier.fillMaxSize()) {
            if (image != null) drawImage(image) else drawRect(Riso.Paper)
        }
    }
}

/** A cover printed in the three inks (the generated sleeve until the real one has loaded). */
@Composable
internal fun RisoCover(url: String?, seed: Int, modifier: Modifier, cell: Dp = 2.2.dp, warm: Float = 0f) {
    val plates = rememberCoverPlates(url, seed) ?: return
    RisoPicture(listOf("cover", url ?: "", seed, System.identityHashCode(plates), warm), modifier, cell) { w, h -> plates.field(w, h, warm) }
}
