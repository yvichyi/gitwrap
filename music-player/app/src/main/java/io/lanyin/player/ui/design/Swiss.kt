package io.lanyin.player.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 格's studio: a Swiss concert poster built from circles in the manner of the 1950s concert
 * posters, drawn with its construction lines still on it; photographs as the Swiss printed them,
 * black and white and hard; colour as swatch chips from the printer's book.
 */

/** One ring of the poster: its radius and weight as fractions of the poster's radius, where it starts, how far it runs, how fast it turns and in what ink. */
private class Ring(val radius: Float, val weight: Float, val start: Float, val sweep: Float, val speed: Float, val ink: Int)

/** The poster's rings for a track: seven, nested, each cut short somewhere, turning at its own speed. */
private fun rings(seed: Int): List<Ring> = List(7) { k ->
    val h = { salt: Int -> hash01(seed * 37 + k, salt) }
    Ring(
        radius = 0.44f + 0.088f * k,
        weight = 0.03f + 0.04f * h(1),
        start = h(2) * 360f,
        sweep = 110f + 210f * h(3),
        speed = (0.4f + 1.2f * h(4)) * (if (k % 2 == 0) 1f else -1f),
        ink = when (k) {
            2 -> 1
            5 -> 2
            else -> 0
        },
    )
}

/**
 * The concert poster's geometry in a square of side [size].minDimension centred in the draw
 * area: a red disc low on the left swelling with the [bass], a field of nested rings cut short
 * at different places and turning at their own speeds with the record ([angle]), a yellow square
 * up on the right that jumps on the [beat], a blue bar along the foot that runs out with the
 * [treble]. With [construction], the drawing's construction is left on it: the module grid, the
 * axes and a diagonal, the guide circles dashed, and its measurements in small mono.
 */
internal fun DrawScope.drawConcertPoster(
    style: LanyinStyle,
    seed: Int,
    angle: Float,
    bass: Float,
    beat: Float,
    treble: Float,
    measurer: TextMeasurer?,
    construction: Boolean = true,
) {
    val c = style.colors
    val ink = c.outline
    val inks = listOf(ink, c.accent, c.accentAlt)
    val side = size.minDimension
    val r = side * 0.43f
    val o = center
    val hair = 0.8f * density
    val guide = ink.copy(alpha = 0.32f)
    if (construction) {
        // The module grid, four by four over the square.
        val left = o.x - side / 2f
        val top = o.y - side / 2f
        for (i in 0..4) {
            drawLine(ink.copy(alpha = 0.07f), Offset(left + side * i / 4f, top), Offset(left + side * i / 4f, top + side), hair)
            drawLine(ink.copy(alpha = 0.07f), Offset(left, top + side * i / 4f), Offset(left + side, top + side * i / 4f), hair)
        }
    }
    // The red disc behind the rings, low on the left.
    val disc = o + Offset(-r * 0.62f, r * 0.58f)
    val discR = r * 0.34f * (1f + 0.22f * bass)
    drawCircle(c.accent, discR, disc)
    // The rings.
    for (ring in rings(seed)) {
        val rr = r * ring.radius
        val w = r * ring.weight
        drawArc(
            inks[ring.ink], ring.start + angle * ring.speed, ring.sweep, false,
            o - Offset(rr, rr), Size(rr * 2f, rr * 2f), style = Stroke(w, cap = StrokeCap.Butt),
        )
    }
    // The yellow square, up on the right, jumping on the beat.
    val sq = r * 0.3f * (1f + 0.16f * beat)
    val sqAt = o + Offset(r * 0.78f, -r * 0.82f)
    rotate(12f + 18f * beat, sqAt) {
        drawRect(GridYellow, sqAt - Offset(sq / 2f, sq / 2f), Size(sq, sq))
        drawRect(ink, sqAt - Offset(sq / 2f, sq / 2f), Size(sq, sq), style = Stroke(2f * density))
    }
    // The blue bar along the foot, running out with the treble.
    val barY = o.y + r * 1.02f
    val barW = r * (0.5f + 0.9f * treble)
    drawRect(c.accentAlt, Offset(o.x + r * 0.05f, barY), Size(barW, r * 0.07f))
    if (!construction) return
    // The construction left on the drawing.
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density))
    drawCircle(guide, r * 1.06f, o, style = Stroke(hair, pathEffect = dash))
    drawCircle(guide, r * 0.36f, o, style = Stroke(hair, pathEffect = dash))
    drawCircle(guide, discR, disc, style = Stroke(hair))
    drawLine(guide, Offset(o.x - side / 2f, o.y), Offset(o.x + side / 2f, o.y), hair)
    drawLine(guide, Offset(o.x, o.y - side / 2f), Offset(o.x, o.y + side / 2f), hair)
    val d = side / 2f
    drawLine(guide, o - Offset(d, -d), o + Offset(d, -d), hair, pathEffect = dash)
    // A dimension line for the outer radius, with its arrowheads.
    val dimY = o.y - r * 1.1f
    drawLine(guide, Offset(o.x, dimY), Offset(o.x + r, dimY), hair)
    for (x in floatArrayOf(o.x, o.x + r)) drawLine(guide, Offset(x, dimY - 3f * density), Offset(x, dimY + 3f * density), hair)
    // The angle of the diagonal.
    drawArc(guide, -45f, 45f, false, o - Offset(r * 0.2f, r * 0.2f), Size(r * 0.4f, r * 0.4f), style = Stroke(hair))
    // Its centre: a registration target.
    drawRegistration(o, 4f * density, ink.copy(alpha = 0.6f))
    if (measurer != null) {
        val tiny = TextStyle(fontFamily = SpaceMonoFamily, fontSize = (8f / fontScale).sp, color = ink.copy(alpha = 0.6f))
        drawText(measurer, "R = 1.000", Offset(o.x + r * 0.3f, dimY + 2f * density), tiny)
        drawText(measurer, "45°", o + Offset(r * 0.24f, -r * 0.16f), tiny)
        drawText(measurer, "Ø %.2f".format(discR / r), disc + Offset(discR * 0.72f, discR * 0.72f), tiny)
    }
}

/**
 * A photograph as the Swiss printed them: black and white and hard, the greys pushed apart.
 * The generated covers are already drawn in the poster's inks and stay as they are. With
 * 封面按主题重绘 off, the cover as it is.
 */
@Composable
fun SwissPhoto(url: String?, seed: Int, modifier: Modifier = Modifier) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    val painter = rememberCoverPainter(url, 480)
    Box(modifier) {
        if (painter == null) {
            CoverArt(seed, Modifier.matchParentSize())
        } else {
            Image(painter, null, contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(HardMono), modifier = Modifier.matchParentSize())
        }
    }
}

/** Grey, and contrast pushed up round the middle grey. */
private val HardMono: ColorMatrix = ColorMatrix().apply {
    setToSaturation(0f)
    val k = 1.55f
    val t = (1f - k) * 0.5f * 255f
    timesAssign(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f)))
}

/**
 * A chip from the swatch book: a field of the colour, and under it a white label with its name
 * and number set as the book sets them, [label] in heavy type.
 */
@Composable
fun SwatchChip(fill: Color, ink: Color, code: String, label: String, modifier: Modifier = Modifier, icon: @Composable (Color) -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.border(2.dp, c.outline)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(fill).padding(8.dp)) {
            Box(Modifier.align(Alignment.TopStart)) { icon(ink) }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 7.dp, vertical = 5.dp)) {
            Text(label, style = style.type.body.copy(fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 17.sp), color = Color(0xFF111111), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(code, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = 9.sp, letterSpacing = 0.04.sp), color = Color(0xFF111111), maxLines = 1)
        }
    }
}
