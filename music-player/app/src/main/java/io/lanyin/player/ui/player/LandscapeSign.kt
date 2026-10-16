package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.audio.beatPulse
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize

/**
 * 霓: the stage as a neon sign over the strip. The line being sung hangs in a tube-lit frame, its
 * characters cold glass until the singing reaches them and they light; a new line stutters on
 * as neon does. The frame breathes with the beat and now and then buzzes. Above it an ON AIR
 * box flashes on the beats, beside the tempo; the next line waits below. The artwork is on the
 * right, the sun going down behind it.
 */
@Composable
internal fun SignStage(stage: StageContext) {
    val state = stage.state
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.6f).fillMaxHeight().enter(stage.entrance, 1)) {
            SignHead(state)
            NeonSign(stage, Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp))
            AnimatedVisibility(stage.chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                StageFooter(state, centered = false)
            }
        }
        Spacer(Modifier.width(22.dp))
        StageArtwork(stage, Modifier.weight(0.4f).fillMaxHeight().then(stage.dismissDrag))
    }
}

private val Hud = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)

@Composable
internal fun SignHead(state: PlayerState) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(state.isPlaying)
    val bpm = rememberBpm(state)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Lit on each beat and dimming between them; dark while paused.
        Text(
            if (state.isPlaying) "ON AIR" else "OFF AIR",
            style = Hud.copy(fontSize = fixedSp(11f)),
            color = Color.White,
            modifier = Modifier
                .graphicsLayer {
                    clock.floatValue
                    alpha = if (state.isPlaying) 0.45f + 0.55f * state.audioFeed.beatPulse(0.4f) else 0.25f
                }
                .drawBehind { tube(c.accentAlt, 4.dp.toPx(), 1f) }
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            (bpm?.let { "BPM $it  ·  SYNC ▮▮▮" } ?: "BPM --"),
            style = Hud.copy(fontSize = fixedSp(10f)), color = c.accent, maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text("CH %02d".format(state.trackNumber), style = Hud.copy(fontSize = fixedSp(10f)), color = c.muted)
    }
}

/** A neon tube along this drawing's bounds: a soft glow round it, the lit glass at its core. */
private fun DrawScope.tube(color: Color, corner: Float, lit: Float) {
    val r = CornerRadius(corner)
    for (k in 1..3) drawRoundRect(color.copy(alpha = 0.09f * lit), cornerRadius = r, style = Stroke((1.5f + k * 3.5f) * density))
    drawRoundRect(lerp(color, Color.White, 0.35f).copy(alpha = 0.35f + 0.65f * lit), cornerRadius = r, style = Stroke(1.6f * density))
}

/** How lit a tube is [v] (0..1) of the way through switching on: dark, a stutter, then on. */
private fun ignite(v: Float): Float = when {
    v >= 1f -> 1f
    v < 0.1f -> 0f
    v < 0.18f -> 0.9f
    v < 0.28f -> 0.15f
    v < 0.36f -> 1f
    v < 0.42f -> 0.4f
    else -> 1f
}

@Composable
internal fun NeonSign(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val style = Lanyin.style
    val c = style.colors
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    val clock = rememberFrameClock(state.isPlaying)
    val ignition = remember { Animatable(0f) }
    LaunchedEffect(index) {
        ignition.snapTo(0f)
        ignition.animateTo(1f, tween(650, easing = LinearEasing))
    }
    val line = lines.getOrNull(index)
    val text = line?.text?.takeIf { it.isNotBlank() } ?: if (lines.isEmpty()) emptyLyricsNote(state) else "♪"
    val glass = with(LocalDensity.current) { 1.1.dp.toPx() }
    val big = lyricTextStyle(LyricSize.BASE_SP * 1.4f, stage.lyricScale).copy(
        fontFamily = style.display, fontWeight = FontWeight.Bold,
        color = c.accent.copy(alpha = 0.55f), drawStyle = Stroke(glass),
    )
    val lit = lerp(c.accent, Color.White, 0.5f)
    // Switched off at the wall while paused: the tubes fall to a dull glow.
    val power by animateFloatAsState(if (state.isPlaying) 1f else 0.35f, tween(if (state.isPlaying) 260 else 700), label = "signPower")
    Column(modifier, verticalArrangement = Arrangement.Center) {
        // As tall as the room allows up to a sign's height, centred in the room left over.
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 96.dp, max = 190.dp)
                .fillMaxSize()
                .drawBehind {
                    val t = clock.floatValue
                    val pulse = if (state.isPlaying) state.audioFeed.beatPulse(0.35f) else 0f
                    // Every so often the transformer buzzes and the frame dips for a moment.
                    val buzz = if (state.isPlaying && (t % 7.3f) in 3.1f..3.17f) 0.35f else 1f
                    // The glass behind the tubes, and the two wires it hangs from.
                    // A dark backing board, so what's behind the sign doesn't show through the glass.
                    drawRoundRect(c.bg.copy(alpha = 0.84f), cornerRadius = CornerRadius(16.dp.toPx()))
                    drawRoundRect(c.accentAlt.copy(alpha = 0.06f), cornerRadius = CornerRadius(16.dp.toPx()))
                    for (x in listOf(size.width * 0.18f, size.width * 0.82f)) {
                        drawLine(c.muted.copy(alpha = 0.5f), Offset(x, -12.dp.toPx()), Offset(x, 0f), 1.dp.toPx())
                        drawRect(c.muted.copy(alpha = 0.7f), Offset(x - 3.dp.toPx(), -2.dp.toPx()), Size(6.dp.toPx(), 4.dp.toPx()))
                    }
                    tube(c.accentAlt, 16.dp.toPx(), (0.7f + 0.3f * pulse) * buzz * power)
                }
                .padding(horizontal = 22.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(Modifier.graphicsLayer { alpha = (0.3f + 0.7f * ignite(ignition.value)) * (0.4f + 0.6f * power) }) {
                SungText(
                    text, big,
                    progress = {
                        when {
                            lines.isEmpty() -> 1f
                            stage.scrubMs != null -> 0f
                            else -> state.lineProgress()
                        }
                    },
                    paintSung = { l ->
                        drawText(l, c.accent, shadow = Shadow(c.accent, Offset.Zero, 14.dp.toPx()), drawStyle = androidx.compose.ui.graphics.drawscope.Fill)
                        drawText(l, lit, drawStyle = androidx.compose.ui.graphics.drawscope.Fill)
                    },
                )
                line?.translation?.let {
                    Text(
                        it,
                        style = style.type.body.copy(shadow = Shadow(c.accentAlt, Offset.Zero, 10f)),
                        color = lerp(c.accentAlt, Color.White, 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        lines.getOrNull(index + 1)?.text?.takeIf { it.isNotBlank() }?.let { next ->
            Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT ›", style = Hud.copy(fontSize = fixedSp(10f)), color = c.accentAlt)
                Spacer(Modifier.width(10.dp))
                Text(next, style = style.type.body, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
