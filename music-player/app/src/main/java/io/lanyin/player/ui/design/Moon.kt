package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Mean length of a lunation in days. */
private const val SYNODIC_MONTH = 29.530588853

/** A new moon to count from: 2000-01-06 18:14 UTC, as days since the epoch. */
private const val REFERENCE_NEW_MOON = 10_962.76

/**
 * Where the moon is in its cycle on [date] (at noon UTC): 0 new, 0.25 first quarter, 0.5 full,
 * 0.75 last quarter. Accurate to within about a day, which is all a glyph needs.
 */
fun moonPhase(date: LocalDate): Float {
    val days = date.toEpochDay() + 0.5 - REFERENCE_NEW_MOON
    val cycles = days / SYNODIC_MONTH
    return (cycles - kotlin.math.floor(cycles)).toFloat()
}

/** The phase's traditional name. */
fun moonName(phase: Float): String = when (((phase * 8f) + 0.5f).toInt() % 8) {
    0 -> "新月"
    1 -> "蛾眉月"
    2 -> "上弦月"
    3 -> "盈凸月"
    4 -> "满月"
    5 -> "亏凸月"
    6 -> "下弦月"
    else -> "残月"
}

/** Spring tides come with the new and full moon, neap tides with the quarters. */
fun tideName(phase: Float): String {
    val fromSyzygy = minOf(phase, abs(phase - 0.5f), 1f - phase)
    return when {
        fromSyzygy < 0.07f -> "大潮"
        fromSyzygy > 0.18f -> "小潮"
        else -> "中潮"
    }
}

/** Fraction of the disc that is lit, 0..1. */
fun moonIllumination(phase: Float): Float = ((1 - cos(2 * PI * phase)) / 2).toFloat()

/**
 * The moon at [phase] as seen from the northern hemisphere: lit from the right while waxing, from
 * the left while waning. [dark] fills the unlit part (transparent for none).
 */
fun DrawScope.drawMoon(center: Offset, radius: Float, phase: Float, lit: Brush, dark: Color = Color.Transparent) {
    if (dark.alpha > 0f) drawCircle(dark, radius, center)
    val c = cos(2 * PI * phase).toFloat()
    val waxing = phase < 0.5f
    val steps = 48
    val path = Path()
    // Right edge of the lit part, top to bottom, then its left edge back up.
    for (i in 0..steps) {
        val dy = -radius + 2 * radius * i / steps
        val w = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        val x = if (waxing) center.x + w else center.x - c * w
        if (i == 0) path.moveTo(x, center.y + dy) else path.lineTo(x, center.y + dy)
    }
    for (i in steps downTo 0) {
        val dy = -radius + 2 * radius * i / steps
        val w = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        val x = if (waxing) center.x + c * w else center.x - w
        path.lineTo(x, center.y + dy)
    }
    path.close()
    drawPath(path, lit)
}

/** A small moon glyph for today's phase, lit in [color] with the dark part faintly shown. */
@Composable
fun MoonGlyph(phase: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawMoon(center, r, phase, Brush.linearGradient(listOf(color, color)), dark = color.copy(alpha = 0.16f))
    }
}
