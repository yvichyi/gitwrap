package io.lanyin.player.ui.design

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.R
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.PI

/**
 * A cover run through a photocopier: [lines] rows of halftone dots in [ink] on [paper], each as
 * big as the picture is dark there ([positive] flips it: dots as big as it is light, for lime on
 * black). With 封面按主题重绘 off, the cover as it is.
 */
@Composable
fun PhotocopyCover(
    url: String?,
    seed: Int,
    modifier: Modifier = Modifier,
    ink: Color = Acid.Black,
    paper: Color = Acid.Paper,
    lines: Float = 38f,
    positive: Boolean = false,
) {
    if (!LocalCoverMedium.current) {
        CoverImage(url, seed, 360.dp, modifier)
        return
    }
    val style = Lanyin.style
    val n = 56
    val cells = rememberCoverLuminance(url, seed, n)
    val print = remember(style, positive) { style.copy(dark = positive) }
    Canvas(modifier) {
        drawRect(paper)
        drawHalftone(print, lines, ink) { u, v -> cells.sampleCells(n, u, v) }
    }
}

/** Archivo Black as a platform typeface, for type set along a path. */
@Composable
internal fun rememberArchivo(): Typeface? {
    val context = LocalContext.current
    return remember(context) { runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.archivo_black) }.getOrNull() }
}

/**
 * [text] set round a circle of [radius] at [center], starting at the top, in [color] and
 * [typeface] at [size] px, its letters spaced to fill the ring.
 */
internal fun DrawScope.drawTextRing(text: String, center: Offset, radius: Float, color: Color, typeface: Typeface?, size: Float) {
    val path = android.graphics.Path().apply {
        addCircle(center.x, center.y, radius, android.graphics.Path.Direction.CW)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = size
        this.typeface = typeface ?: Typeface.DEFAULT_BOLD
    }
    // Spread the letters so the text goes once round.
    val natural = paint.measureText(text)
    val circumference = (2 * PI * radius).toFloat()
    if (natural > 0f) paint.letterSpacing = ((circumference - natural) / text.length / size).coerceIn(-0.05f, 1.5f)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-90f, center.x, center.y)
        canvas.nativeCanvas.drawTextOnPath(text, path, 0f, size * 0.35f, paint)
        canvas.nativeCanvas.restore()
    }
}

/**
 * The PLAY sticker: a black disc with PLAY ✱ PLAY ✱ … round its rim in lime and a lime play
 * triangle in the middle, turned a little off true; [turn] spins the ring.
 */
@Composable
fun PlaySticker(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 76.dp, label: String = "播放", turn: () -> Float = { 0f }) {
    val face = rememberArchivo()
    Box(
        modifier
            .size(size)
            .pressable(label = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).graphicsLayer { rotationZ = -14f + turn() }) {
            val r = this.size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.4f), r, center + Offset(0f, 2.dp.toPx()))
            drawCircle(Acid.Black, r)
            drawCircle(Acid.Lime, r - 1.5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawTextRing("PLAY ✱ PLAY ✱ PLAY ✱ ", center, r * 0.74f, Acid.Lime, face, r * 0.2f)
        }
        Canvas(Modifier.size(size * 0.3f)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(this@Canvas.size.width * 0.18f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2f)
                lineTo(this@Canvas.size.width * 0.18f, this@Canvas.size.height)
                close()
            }
            drawPath(p, Acid.Lime)
        }
    }
}

/** A row of tear-off tabs' perforation: a dashed line across [size]'s top. */
internal fun DrawScope.drawPerforation(color: Color) {
    var x = 0f
    val dash = 4.dp.toPx()
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset((x + dash * 0.6f).coerceAtMost(size.width), 0f), 1.dp.toPx())
        x += dash
    }
}
