package io.lanyin.player.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.delay

/*
 * 霓's vocabulary: the street at night after rain, where the walls are matte and dark and the
 * only colour is the light of the signs. Neon lettering, tubes bent along a path, signs hung
 * down the side of a building, the spill of light a sign throws on the wall round it.
 */

/** How lit a tube is [v] (0..1) of the way through switching on: dark, a stutter, then on. */
fun neonIgnite(v: Float): Float = when {
    v >= 1f -> 1f
    v < 0.1f -> 0f
    v < 0.18f -> 0.9f
    v < 0.28f -> 0.15f
    v < 0.36f -> 1f
    v < 0.42f -> 0.4f
    else -> 1f
}

/** The colour of the glass at a lit tube's core: its colour run almost to white. */
fun neonCore(color: Color): Color = lerp(color, Color.White, 0.6f)

/**
 * Lit neon lettering: the core of the glass near white, the colour glowing round it, tight and
 * then wide. Coming into view it switches on as neon does, with a stutter, after [delayMillis];
 * [lit] turns it down (a sign half off, or one that's off but for a dull glow).
 */
@Composable
fun NeonText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    lit: Float = 1f,
    ignite: Boolean = true,
    delayMillis: Int = 0,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    val density = LocalDensity.current
    // No flicker when motion is reduced: the sign is simply lit.
    @Suppress("NAME_SHADOWING")
    val ignite = ignite && !LocalReduceMotion.current
    val on = remember { Animatable(if (ignite) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!ignite) return@LaunchedEffect
        delay(delayMillis.toLong())
        on.animateTo(1f, tween(750, easing = LinearEasing))
    }
    val wide = with(density) { 16.dp.toPx() }
    val tight = with(density) { 5.dp.toPx() }
    val aligned = if (textAlign != null) style.copy(textAlign = textAlign) else style
    Box(modifier.graphicsLayer { alpha = (0.18f + 0.82f * neonIgnite(on.value)) * (0.25f + 0.75f * lit.coerceIn(0f, 1f)) }) {
        // The halo: the letters in the tube's colour, spread wide.
        Text(
            text, style = aligned.copy(color = color.copy(alpha = 0.5f), shadow = Shadow(color.copy(alpha = 0.9f), Offset.Zero, wide)),
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        // The glass: near white at the core, a tight glow of its colour round it.
        Text(
            text, style = aligned.copy(color = neonCore(color), shadow = Shadow(color, Offset.Zero, tight)),
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A neon tube along [path], [width] thick: three widening halos of its colour, the glass, and a
 * hot thread at its core. [lit] at 0 leaves only the cold glass.
 */
fun DrawScope.drawTube(path: Path, color: Color, width: Float, lit: Float = 1f) {
    val cap = StrokeCap.Round
    val join = StrokeJoin.Round
    if (lit > 0f) {
        for ((k, a) in listOf(6f to 0.05f, 3.4f to 0.09f, 1.9f to 0.18f)) {
            drawPath(path, color.copy(alpha = a * lit), style = Stroke(width * k, cap = cap, join = join))
        }
    }
    drawPath(path, lerp(Color(0xFF3A3640), lerp(color, Color.White, 0.3f), lit), style = Stroke(width, cap = cap, join = join))
    if (lit > 0f) drawPath(path, Color.White.copy(alpha = 0.6f * lit), style = Stroke(width * 0.35f, cap = cap, join = join))
}

/** A straight tube from [from] to [to]; see [drawTube]. */
fun DrawScope.drawTubeLine(from: Offset, to: Offset, color: Color, width: Float, lit: Float = 1f) {
    drawTube(Path().apply { moveTo(from.x, from.y); lineTo(to.x, to.y) }, color, width, lit)
}

/** The soft light a sign throws on the wall round it, centred at ([fx], [fy]) of the bounds. */
fun Modifier.lightSpill(color: Color, fx: Float = 0.5f, fy: Float = 0.5f, reach: Float = 0.9f, strength: Float = 0.22f): Modifier = drawBehind {
    val center = Offset(size.width * fx, size.height * fy)
    val r = size.maxDimension * reach
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center, r))
}

/**
 * A sign hung down the side of a building: a bracket off the wall, a dark box in a tube frame,
 * and [text] set down it a character at a time in neon, as on the streets of Hong Kong. Its
 * width is the caller's.
 */
@Composable
fun HangingSign(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    letterSize: Float = 24f,
    lit: Float = 1f,
    delayMillis: Int = 0,
) {
    val style = Lanyin.style
    val c = style.colors
    val letters = text.filter { !it.isWhitespace() }.take(5)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The bracket: a bar off the wall with two hangers.
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val bar = c.muted.copy(alpha = 0.6f)
            drawLine(bar, Offset(0f, 2.dp.toPx()), Offset(size.width, 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            for (x in listOf(size.width * 0.25f, size.width * 0.75f)) drawLine(bar, Offset(x, 2.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
        }
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val r = CornerRadius(8.dp.toPx())
                    drawRoundRect(Color(0xFF09080D), cornerRadius = r)
                    // The frame: a tube inset round the box's edge.
                    val inset = 5.dp.toPx()
                    val frame = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(5.dp.toPx())))
                    }
                    drawTube(frame, color, 1.6.dp.toPx(), lit)
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (ch in letters) {
                    NeonText(
                        ch.toString(),
                        TextStyle(fontWeight = FontWeight.Black, fontSize = letterSize.sp, lineHeight = (letterSize * 1.2f).sp),
                        color, lit = lit, delayMillis = delayMillis,
                    )
                }
            }
        }
    }
}

/** A box sign in a tube frame, for a word or two: OPEN, ON AIR. */
@Composable
fun BoxSign(text: String, color: Color, modifier: Modifier = Modifier, lit: Float = 1f, size: Float = 13f, delayMillis: Int = 0) {
    Box(
        modifier
            .drawBehind {
                val r = CornerRadius(6.dp.toPx())
                drawRoundRect(Color(0xFF09080D), cornerRadius = r)
                val inset = 3.dp.toPx()
                drawTube(
                    Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, this@drawBehind.size.width - inset, this@drawBehind.size.height - inset, CornerRadius(4.dp.toPx()))) },
                    color, 1.4.dp.toPx(), lit,
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        NeonText(text, TextStyle(fontFamily = io.lanyin.player.ui.theme.TiltNeonFamily, fontSize = size.sp, letterSpacing = 1.sp), color, lit = lit, delayMillis = delayMillis)
    }
}

/** An unlit glass tube: what a sign's letters look like switched off. */
fun DrawScope.drawGlass(path: Path, width: Float) {
    drawPath(path, Color(0x26FFFFFF), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A neon outline of [size] at [topLeft] with rounded corners, for frames drawn in a canvas. */
fun DrawScope.drawTubeFrame(topLeft: Offset, size: Size, corner: Float, color: Color, width: Float, lit: Float = 1f) {
    drawTube(
        Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(corner))) },
        color, width, lit,
    )
}
