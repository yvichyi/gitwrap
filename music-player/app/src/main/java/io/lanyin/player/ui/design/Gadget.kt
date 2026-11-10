package io.lanyin.player.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 点's device, seen through its clear back: smoked acrylic over the board. A printed circuit
 * board with its traces and vias, the chips with their markings, a charging coil of silver-grey
 * wire where the record sits, a pale flex cable folding away to the edge, the battery under its label and the
 * screws that hold it all; drawn once into a picture for its size. The lights and the record go
 * on top.
 */

object Gadget {
    val Board = Color(0xFF1B1D21)
    val Trace = Color(0xFF3B3F47)
    // Everything inside is in the theme's greys: the one colour stays the red dot.
    val Wire = Color(0xFF8E9298)
    val Flex = Color(0xFFB9BCC1)
    val Chip = Color(0xFF0C0D0F)
    val Silk = Color(0xFFD9D9D4)
    val Steel = Color(0xFF9EA2A8)
    val Acrylic = Color(0xFF101114)
}

/**
 * The clear back with the board under it, filling the box with rounded corners; [coil] is where
 * the charging coil sits (the record's centre) as fractions of the box, [coilRadius] its radius
 * as a fraction of the box's smaller side. [content] goes on top: the lights, the record.
 */
@Composable
fun ClearBack(modifier: Modifier = Modifier, coil: Offset = Offset(0.5f, 0.48f), coilRadius: Float = 0.42f, content: @Composable BoxScope.() -> Unit) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier.drawWithCache {
            val picture = boardPicture(this, measurer, coil, coilRadius)
            onDrawBehind { drawImage(picture) }
        },
    ) {
        content()
        // The acrylic's sheen over everything: a broad soft streak across the corner.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val streak = Brush.linearGradient(
                    0f to Color.Transparent, 0.32f to Color.Transparent, 0.4f to Color.White.copy(alpha = 0.06f),
                    0.46f to Color.Transparent, 0.52f to Color.White.copy(alpha = 0.03f), 0.56f to Color.Transparent, 1f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val corner = CornerRadius(28f * density)
                onDrawBehind {
                    drawRoundRect(streak, cornerRadius = corner)
                    // The edge of the acrylic catching the light.
                    drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = corner, style = Stroke(1f * density))
                    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(1.5f * density, 1.5f * density), Size(size.width - 3f * density, size.height - 3f * density), CornerRadius(26.5f * density), style = Stroke(1f * density))
                }
            },
        )
    }
}

private fun boardPicture(scope: CacheDrawScope, measurer: TextMeasurer, coil: Offset, coilRadius: Float): ImageBitmap {
    val w = scope.size.width
    val h = scope.size.height
    val image = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(scope, scope.layoutDirection, androidx.compose.ui.graphics.Canvas(image), scope.size) {
        drawBoard(measurer, Offset(w * coil.x, h * coil.y), minOf(w, h) * coilRadius)
    }
    return image
}

/** Draws the device's inside and its clear back, for the whole draw area. */
private fun DrawScope.drawBoard(measurer: TextMeasurer, coilAt: Offset, coilR: Float) {
    val w = size.width
    val h = size.height
    val d = density
    val corner = CornerRadius(28f * d)
    val rnd = java.util.Random(19)
    // The frame and, inside it, the board.
    drawRoundRect(Gadget.Acrylic, cornerRadius = corner)
    val inset = 10f * d
    val board = Size(w - inset * 2, h - inset * 2)
    drawRoundRect(Gadget.Board, Offset(inset, inset), board, CornerRadius(20f * d))
    // A fine hatch of the ground pour.
    var x = inset
    while (x < w - inset) {
        drawLine(Color.White.copy(alpha = 0.012f), Offset(x, inset), Offset(x + h * 0.3f, inset + h * 0.3f), d)
        x += 7f * d
    }
    // Traces: runs across and down with 45° bends, ending in vias; avoiding nothing, as a board does.
    fun trace(points: List<Offset>, width: Float = 1.2f * d) {
        val p = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (k in 1 until points.size) lineTo(points[k].x, points[k].y)
        }
        drawPath(p, Gadget.Trace, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (end in listOf(points.first(), points.last())) {
            drawCircle(Gadget.Trace, 2.6f * d, end)
            drawCircle(Gadget.Board, 1.1f * d, end)
        }
    }
    for (k in 0 until 26) {
        val sx = inset + 14f * d + rnd.nextFloat() * (board.width - 28f * d)
        val sy = inset + 14f * d + rnd.nextFloat() * (board.height - 28f * d)
        val run1 = (rnd.nextFloat() - 0.5f) * w * 0.4f
        val diag = (10f + rnd.nextFloat() * 30f) * d * (if (rnd.nextBoolean()) 1f else -1f)
        val run2 = (rnd.nextFloat() - 0.5f) * h * 0.25f
        val a = Offset(sx, sy)
        val b = Offset(sx + run1, sy)
        val c = Offset(b.x + diag, b.y + kotlin.math.abs(diag))
        val e = Offset(c.x, (c.y + run2).coerceIn(inset + 8f * d, h - inset - 8f * d))
        trace(listOf(a, b, c, e), if (k % 5 == 0) 2.2f * d else 1f * d)
    }
    // A bus of parallel traces down the side.
    for (k in 0 until 6) {
        val bx = inset + 18f * d + k * 4f * d
        trace(listOf(Offset(bx, h * 0.12f), Offset(bx, h * 0.6f), Offset(bx + 30f * d, h * 0.6f + 30f * d)), 0.9f * d)
    }
    // The charging coil under the record: enamelled wire wound round and round, in silver-grey,
    // a lit band across it where the acrylic catches the light.
    for (k in 0 until 22) {
        val r = coilR * (0.62f + 0.018f * k)
        drawCircle(Gadget.Wire.copy(alpha = 0.32f + 0.18f * (k % 2)), r, coilAt, style = Stroke(1.6f * d))
    }
    drawCircle(
        Brush.sweepGradient(0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.18f), 0.16f to Color.Transparent, 0.58f to Color.Transparent, 0.66f to Color.White.copy(alpha = 0.1f), 0.74f to Color.Transparent, 1f to Color.Transparent, center = coilAt),
        coilR * 0.81f, coilAt, style = Stroke(coilR * 0.4f),
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), coilR * 0.6f, coilAt)
    // The coil's leads.
    trace(listOf(coilAt + Offset(coilR * 1.0f, 0f), coilAt + Offset(coilR * 1.1f, coilR * 0.1f), coilAt + Offset(coilR * 1.1f, coilR * 0.6f)), 2f * d)
    // Chips: black packages with pins along their sides and their markings in white.
    val small = TextStyle(fontFamily = SpaceMonoFamily, fontSize = (7f / fontScale).sp, color = Gadget.Silk.copy(alpha = 0.75f))
    val chipMark = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = (7.5f / fontScale).sp, color = Gadget.Silk.copy(alpha = 0.6f))
    fun chip(at: Offset, cw: Float, ch: Float, mark: String, pins: Boolean = true) {
        if (pins) {
            var px = at.x + 3f * d
            while (px < at.x + cw - 3f * d) {
                drawRect(Gadget.Steel.copy(alpha = 0.7f), Offset(px, at.y - 2.5f * d), Size(1.4f * d, 2.5f * d))
                drawRect(Gadget.Steel.copy(alpha = 0.7f), Offset(px, at.y + ch), Size(1.4f * d, 2.5f * d))
                px += 3.2f * d
            }
        }
        drawRoundRect(Gadget.Chip, at, Size(cw, ch), CornerRadius(1.5f * d))
        drawRoundRect(Color.White.copy(alpha = 0.05f), at, Size(cw, ch * 0.4f), CornerRadius(1.5f * d))
        drawCircle(Color.White.copy(alpha = 0.2f), 1.2f * d, at + Offset(3.5f * d, 3.5f * d))
        drawText(measurer, mark, at + Offset(5f * d, ch / 2f - 5f * d), chipMark)
    }
    chip(Offset(w * 0.62f, h * 0.06f), w * 0.2f, h * 0.06f, "LY8250")
    chip(Offset(w * 0.12f, h * 0.075f), w * 0.14f, h * 0.045f, "DAC32")
    chip(Offset(w * 0.62f, h * 0.88f), w * 0.1f, h * 0.035f, "RF", pins = false)
    // Little passives scattered: capacitors in tan, resistors in black with their ends.
    for (k in 0 until 40) {
        val px = inset + 8f * d + rnd.nextFloat() * (board.width - 16f * d)
        val py = inset + 8f * d + rnd.nextFloat() * (board.height - 16f * d)
        if ((Offset(px, py) - coilAt).getDistance() < coilR * 1.06f) continue
        val vertical = rnd.nextBoolean()
        val s = if (vertical) Size(2.4f * d, 4.6f * d) else Size(4.6f * d, 2.4f * d)
        val cap = rnd.nextInt(3) != 0
        drawRect(if (cap) Color(0xFF5E6268) else Gadget.Chip, Offset(px, py), s)
        val end = if (vertical) Size(s.width, 1f * d) else Size(1f * d, s.height)
        drawRect(Gadget.Steel, Offset(px, py), end)
        drawRect(Gadget.Steel, Offset(px + s.width - end.width, py + s.height - end.height), end)
    }
    // Silk-screen labels.
    drawText(measurer, "LANYIN · DOT  REV.B", Offset(inset + 24f * d, inset + 3f * d), small)
    drawText(measurer, "J1", Offset(w * 0.3f, h * 0.9f), small)
    drawText(measurer, "C12", Offset(w * 0.86f, h * 0.36f), small)
    drawText(measurer, "TP3 ○", Offset(w * 0.08f, h * 0.7f), small)
    // The flex cable: an amber ribbon folding from the main chip down the right edge.
    val ribbon = Path().apply {
        val x0 = w * 0.84f
        moveTo(x0, h * 0.12f)
        lineTo(x0, h * 0.62f)
        quadraticTo(x0, h * 0.7f, x0 - w * 0.08f, h * 0.7f)
        lineTo(w * 0.58f, h * 0.7f)
    }
    // Pale grey film, and the lines in it showing through as stripes.
    drawPath(ribbon, Gadget.Flex.copy(alpha = 0.55f), style = Stroke(10f * d, join = StrokeJoin.Round))
    drawPath(ribbon, Color(0xFF4A4D53).copy(alpha = 0.7f), style = Stroke(6.5f * d, join = StrokeJoin.Round))
    drawPath(ribbon, Gadget.Flex.copy(alpha = 0.5f), style = Stroke(3.5f * d, join = StrokeJoin.Round))
    drawPath(ribbon, Color(0xFF4A4D53).copy(alpha = 0.7f), style = Stroke(1f * d, join = StrokeJoin.Round))
    drawRect(Gadget.Chip, Offset(w * 0.56f, h * 0.7f - 7f * d), Size(8f * d, 14f * d))
    // The battery along the foot, under its label.
    val batTop = h * 0.78f
    val bat = Offset(inset + 26f * d, batTop)
    val batSize = Size(w * 0.5f, h * 0.16f)
    drawRoundRect(Color(0xFF26282D), bat, batSize, CornerRadius(6f * d))
    drawRoundRect(Gadget.Silk.copy(alpha = 0.9f), bat + Offset(6f * d, 6f * d), Size(batSize.width - 12f * d, batSize.height - 12f * d), CornerRadius(3f * d))
    val label = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = (8f / fontScale).sp, color = Color(0xFF111214))
    val fine = TextStyle(fontFamily = SpaceMonoFamily, fontSize = (6.5f / fontScale).sp, color = Color(0xFF111214).copy(alpha = 0.75f))
    drawText(measurer, "LI-ION  4500mAh", bat + Offset(12f * d, 10f * d), label)
    drawText(measurer, "3.87V · LANYIN · DO NOT PUNCTURE", bat + Offset(12f * d, 24f * d), fine)
    // A row of dots on the label: the maker's mark.
    for (k in 0 until 8) drawCircle(Color(0xFF111214), 1.4f * d, bat + Offset(batSize.width - 20f * d - k * 5f * d, batSize.height - 14f * d))
    drawCircle(Color(0xFFD71921), 1.6f * d, bat + Offset(batSize.width - 20f * d + 5f * d, batSize.height - 14f * d))
    // The screws: in the four corners of the board and one at the head.
    val screws = listOf(
        Offset(inset + 12f * d, inset + 12f * d), Offset(w - inset - 12f * d, inset + 12f * d),
        Offset(inset + 12f * d, h - inset - 12f * d), Offset(w - inset - 12f * d, h - inset - 12f * d),
        Offset(w * 0.5f, inset + 12f * d),
    )
    screws.forEachIndexed { k, at -> drawScrewHead(at, 4.4f * d, 20f + k * 37f) }
}

/** A small screw head, seen from above: steel with a lit edge and a cross recess turned to [deg]. */
private fun DrawScope.drawScrewHead(at: Offset, r: Float, deg: Float) {
    drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.15f, at + Offset(0.6f * density, 0.8f * density))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFD7DADF), Gadget.Steel, Color(0xFF5C6066)), at - Offset(r * 0.4f, r * 0.4f), r * 1.6f), r, at)
    rotate(deg, at) {
        drawLine(Color(0xFF2A2C30), at - Offset(r * 0.6f, 0f), at + Offset(r * 0.6f, 0f), r * 0.28f)
        drawLine(Color(0xFF2A2C30), at - Offset(0f, r * 0.6f), at + Offset(0f, r * 0.6f), r * 0.28f)
    }
}
