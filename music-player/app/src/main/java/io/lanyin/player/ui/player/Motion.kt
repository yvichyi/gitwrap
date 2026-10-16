package io.lanyin.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin
import kotlin.math.pow

/** Total length of the entrance timeline; each element starts [ENTRANCE_STAGGER] after the last. */
private const val ENTRANCE_MS = 900
private const val ENTRANCE_STAGGER = 0.08f
private const val ENTRANCE_SPAN = 0.45f

/**
 * A 0→1 timeline that plays once when the player opens (and again whenever [key] changes, e.g. on
 * rotation). Elements read it in the draw phase through [enter], so nothing recomposes per frame.
 */
@Composable
fun rememberEntrance(key: Any?): State<Float> {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(ENTRANCE_MS, easing = LinearEasing)) }
    return progress.asState()
}

/** 0..1 for the element at [index] in the entrance, eased. */
internal fun entranceFraction(timeline: Float, index: Int): Float {
    val local = ((timeline - index * ENTRANCE_STAGGER) / ENTRANCE_SPAN).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(local)
}

/** Rise and fade into place as the entrance reaches this element's turn. */
fun Modifier.enter(entrance: State<Float>, index: Int, distance: Dp = 20.dp): Modifier = graphicsLayer {
    val p = entranceFraction(entrance.value, index)
    alpha = p
    translationY = (1f - p) * distance.toPx()
}

/** Beats per second of the demo track (96 bpm). */
internal const val DEMO_BEATS_PER_SECOND = 1.6f

/** How long a beat's ripple takes to spread and fade. */
private const val RIPPLE_SECONDS = 0.62f

/**
 * A ring that leaves the artwork's edge on each beat [feed] hears and fades as it spreads, in the
 * theme's own hand: an ink hairline for 墨, square ink frames for 格, the accent elsewhere, a
 * glowing tube for 霓. Music without a clear beat makes few. Only while playing; draw-phase only.
 */
@Composable
fun Modifier.beatRipples(skin: PlayerSkin, playing: Boolean, feed: io.lanyin.player.core.audio.AudioFeed): Modifier {
    if (skin == PlayerSkin.LYRICS) return this
    val style = Lanyin.style
    val clock = rememberFrameClock(playing)
    val energy = animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "rippleEnergy")
    val brutal = style.edge == EdgeKind.Brutal
    val color = if (style.edge == EdgeKind.Hairline || brutal || style.edge == EdgeKind.Dot) style.colors.content else style.colors.accent
    val glow = style.edge == EdgeKind.Glow
    return drawBehind {
        val e = energy.value
        if (e <= 0f) return@drawBehind
        clock.floatValue
        // Gone before the next beat, at a quick tempo, so each ring is one beat.
        val life = feed.beatPeriod().let { if (it > 0f) minOf(RIPPLE_SECONDS, it * 0.9f) else RIPPLE_SECONDS }
        val p = feed.sinceBeat() / life
        if (p >= 1f) return@drawBehind
        val spread = 26.dp.toPx() * FastOutSlowInEasing.transform(p)
        val alpha = (1f - p).pow(1.6f) * 0.32f * e
        val stroke = (if (style.edge == EdgeKind.Hairline) 1.dp else if (brutal) 2.dp else 1.5.dp).toPx()
        when (skin) {
            PlayerSkin.VINYL -> {
                // The record sits 14dp inside these bounds.
                val r = size.minDimension / 2f - 14.dp.toPx() + 4.dp.toPx() + spread
                if (glow) drawCircle(color.copy(alpha = alpha * 0.35f), r, style = Stroke(stroke * 4))
                drawCircle(color.copy(alpha = alpha), r, style = Stroke(stroke))
            }
            else -> {
                val inset = (if (skin == PlayerSkin.COVER) 18.dp else 16.dp).toPx() - 4.dp.toPx() - spread
                val topLeft = Offset(inset, inset)
                val rect = Size(size.width - inset * 2, size.height - inset * 2)
                // 格's frames stay square.
                val corner = if (brutal) CornerRadius.Zero else CornerRadius(14.dp.toPx() + spread * 0.5f)
                if (glow) drawRoundRect(color.copy(alpha = alpha * 0.35f), topLeft, rect, corner, style = Stroke(stroke * 4))
                drawRoundRect(color.copy(alpha = alpha), topLeft, rect, corner, style = Stroke(stroke))
            }
        }
    }
}
