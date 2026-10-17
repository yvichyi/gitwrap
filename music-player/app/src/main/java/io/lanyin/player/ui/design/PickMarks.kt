package io.lanyin.player.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.floor

/**
 * A moment of acknowledgement behind a row the moment it starts playing, in the theme's hand: an
 * ink wash spreading (墨), the row's edge lighting like a tube (霓), the sunset passing over it
 * (暮), the highlighter swiping across (格), the flash of a cut (影), red dots running along (点),
 * an LED bar filling (机), the red pen underlining it (报), three blinks (游), a lime sweep with a
 * sparkle (酸). Only when it changes to playing, not for the row already playing when the list
 * opens; nothing with motion reduced.
 */
@Composable
fun Modifier.pickedFlash(playing: Boolean): Modifier {
    val style = Lanyin.style
    val reduced = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    val was = remember { booleanArrayOf(playing) }
    LaunchedEffect(playing) {
        if (playing && !was[0] && !reduced) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(if (style.backdrop == BackdropKind.Game) 600 else 750, easing = LinearEasing))
        }
        was[0] = playing
    }
    return this.drawBehind {
        val p = progress.value
        if (p < 1f) drawPicked(style, p)
    }
}

private fun DrawScope.drawPicked(style: LanyinStyle, p: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val fade = 1f - p
    val grow = FastOutSlowInEasing.transform((p * 1.6f).coerceAtMost(1f))
    when (style.backdrop) {
        BackdropKind.Ink -> drawRect(
            Brush.horizontalGradient(listOf(c.content.copy(alpha = 0.16f * fade), c.content.copy(alpha = 0.06f * fade), Color.Transparent), 0f, w * grow + 1f),
            size = Size(w * grow, h),
        )
        BackdropKind.Neon -> {
            val r = CornerRadius(10.dp.toPx())
            drawRoundRect(c.accent.copy(alpha = 0.18f * fade), size = size, cornerRadius = r, style = Stroke(8.dp.toPx()))
            drawRoundRect(c.accent.copy(alpha = 0.9f * fade), size = size, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        }
        BackdropKind.Dusk -> {
            val x = (w * 1.4f) * p - w * 0.4f
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFB27A).copy(alpha = 0.35f * fade), Color(0xFFE88BA0).copy(alpha = 0.25f * fade), Color.Transparent), x - w * 0.4f, x + w * 0.2f))
        }
        BackdropKind.Grid -> drawRect(GridYellow.copy(alpha = 0.85f * fade), size = Size(w * grow, h))
        BackdropKind.Film -> drawRect(Color.White.copy(alpha = 0.32f * fade * fade))
        BackdropKind.Dot -> {
            val pitch = 7.dp.toPx()
            val n = (w / pitch).toInt()
            val lit = (n * grow).toInt()
            for (k in 0 until lit) drawCircle(c.accentAlt.copy(alpha = fade), 1.6.dp.toPx(), Offset(k * pitch + pitch / 2f, h - 2.dp.toPx()))
        }
        BackdropKind.Device -> {
            val bar = 3.dp.toPx()
            drawRect(c.accent.copy(alpha = fade), Offset(0f, h - bar), Size(w * grow, bar))
        }
        BackdropKind.Paper -> drawLine(c.accent.copy(alpha = 0.4f + 0.6f * fade), Offset(0f, h - 3.dp.toPx()), Offset(w * grow, h - 3.dp.toPx()), 1.8.dp.toPx())
        BackdropKind.Game -> if (floor(p * 6f).toInt() % 2 == 0) drawRect(c.content.copy(alpha = 0.18f))
        BackdropKind.Acid -> {
            val x = w * grow
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.3f * fade)), x - w * 0.5f, x), Offset(0f, 0f), Size(x, h))
            drawPath(starPath(Offset(x, h / 2f), 7.dp.toPx() * fade + 2f, 1.6.dp.toPx(), 4), Color.White.copy(alpha = fade))
        }
    }
}
