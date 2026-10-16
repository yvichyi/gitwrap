package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.drawTube
import io.lanyin.player.ui.design.drawGlass
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

/** Mini player: a floating pill, or a plain docked row in themes with a docked bottom bar. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Lanyin.style
    val c = style.colors
    val docked = style.dock == DockKind.Docked
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Row(
            modifier
                .fillMaxWidth()
                .height(if (docked) 60.dp else 64.dp)
                .testTag("miniPlayer")
                .sharedPlayer("player-container", container = true)
                .then(
                    if (docked) {
                        Modifier
                            .background(c.bg)
                            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
                            .padding(start = style.margin, end = style.margin - 12.dp)
                    } else {
                        Modifier
                            .themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome, onClick = onExpand)
                            .padding(start = 10.dp, end = 4.dp)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniArtwork(skin, spin, state.coverSeed, Modifier.size(if (docked) 40.dp else 44.dp).sharedPlayer("artwork", zIndex = 1f), state.coverUrl)
            Spacer(Modifier.width(12.dp))
            // Swipe the titles sideways to change track, as on the full player's artwork.
            SwipeToSkip(state, Modifier.weight(1f)) { Column(Modifier.fillMaxWidth()) {
                Text(
                    state.title,
                    style = style.type.body.copy(fontFamily = style.display, fontWeight = if (docked) FontWeight.Medium else FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true),
                )
                AnimatedContent(
                    targetState = state.currentLine?.text ?: state.artist,
                    // Keep the outgoing line from sliding up over the title.
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically { it }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it })
                    },
                    label = "miniLyric",
                ) { line ->
                    Text(line, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } }
            MiniPlayButton(state)
            val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
            IconAction(LineIcon.Queue, "播放队列", { openSheet(io.lanyin.player.ui.music.Sheet.Queue) }, tint = c.muted)
        }
    }
}

/** Play/pause wrapped in how far the song has got, drawn in the theme's hand (see [drawMiniProgress]). */
@Composable
private fun MiniPlayButton(state: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    val morph by animateFloatAsState(if (state.isPlaying) 1f else 0f, style.bouncy.spec(), label = "miniMorph")
    // Only 影's reel turns; the others are still between frames of the position.
    val clock = if (style.backdrop == BackdropKind.Film) rememberFrameClock(state.isPlaying) else null
    Box(
        Modifier
            .size(48.dp)
            .pressable(onClick = state::togglePlay)
            .semantics { contentDescription = if (state.isPlaying) "暂停" else "播放" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val f = (state.positionMs.toFloat() / state.durationMs).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            drawMiniProgress(style, f, clock?.floatValue ?: 0f)
        }
        Canvas(Modifier.size(13.dp)) { drawPlayPauseGlyph(morph, c.content) }
    }
}

/**
 * How far the song has got, round the mini player's play button. 墨: an ensō drawn with the
 * brush, thick where it starts and drying out. 霓: a lit tube bent round a cold glass ring. 暮:
 * an arc in the sunset's colours with the sun at its head. 格: a square key with a bar filling
 * along its foot. 影: a take-up reel, the tape wound onto it growing as it turns. 点: a ring of
 * dots lighting one by one, the red one at the head. 机: a knob's scale of ticks. 报: a pie,
 * printed. 游: a pixel frame and a row of blocks. 酸: a ring of lime gel with its gloss.
 */
internal fun DrawScope.drawMiniProgress(style: LanyinStyle, f: Float, t: Float) {
    val c = style.colors
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    when (style.backdrop) {
        BackdropKind.Grid -> {
            val stroke = style.strokeWidth.toPx()
            val bar = 5.dp.toPx()
            drawRect(c.outline, Offset(3.dp.toPx(), 3.dp.toPx()), size)
            drawRect(c.surface)
            drawRect(c.accent, Offset(0f, size.height - bar), Size(size.width * f, bar))
            drawRect(c.outline, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        }
        BackdropKind.Ink -> {
            // The brush goes round once over the song: heavy at the start, thinning as it dries.
            drawCircle(c.content.copy(alpha = 0.08f), r - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
            val steps = (72 * f).toInt()
            for (i in 0 until steps) {
                val a0 = (-100f + i * 5f) * PI.toFloat() / 180f
                val a1 = (-100f + (i + 1) * 5f) * PI.toFloat() / 180f
                val k = i / 72f
                val rr = r - 2.5.dp.toPx() + sin(i * 0.7f) * 0.3.dp.toPx()
                drawLine(
                    c.content.copy(alpha = 0.85f - 0.35f * k),
                    center + Offset(cos(a0) * rr, sin(a0) * rr), center + Offset(cos(a1) * rr, sin(a1) * rr),
                    (3.4f - 2.2f * k).dp.toPx(), StrokeCap.Round,
                )
            }
        }
        BackdropKind.Neon -> {
            val inset = 3.dp.toPx()
            val rect = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            drawGlass(Path().apply { addOval(rect) }, 2.dp.toPx())
            if (f > 0f) drawTube(Path().apply { addArc(rect, -90f, 360f * f) }, c.accent, 2.dp.toPx())
        }
        BackdropKind.Dusk -> {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            drawCircle(c.content.copy(alpha = 0.12f), r - inset, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(c.accentAlt, c.accent, c.accentAlt), center), -90f, 360f * f, false,
                Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val a = (-90f + 360f * f) * PI.toFloat() / 180f
            drawCircle(c.accent, 3.dp.toPx(), center + Offset(cos(a) * (r - inset), sin(a) * (r - inset)))
        }
        BackdropKind.Film -> {
            // The take-up reel: the tape wound on grows with the song; the reel turns as it plays.
            drawCircle(c.content.copy(alpha = 0.3f), r - 1.dp.toPx(), style = Stroke(1.2.dp.toPx()))
            drawCircle(c.content.copy(alpha = 0.16f), (r * 0.42f) + (r * 0.5f) * f)
            rotate(t * 90f, center) {
                for (k in 0 until 3) {
                    val a = (k * 120f) * PI.toFloat() / 180f
                    drawCircle(c.bg.copy(alpha = 0.9f), r * 0.13f, center + Offset(cos(a) * r * 0.62f, sin(a) * r * 0.62f))
                }
            }
        }
        BackdropKind.Dot -> {
            val n = 24
            val lit = (f * n).toInt()
            for (k in 0 until n) {
                val a = (-90f + k * 360f / n) * PI.toFloat() / 180f
                val p = center + Offset(cos(a) * (r - 2.dp.toPx()), sin(a) * (r - 2.dp.toPx()))
                val color = when {
                    k == lit -> c.accentAlt
                    k < lit -> c.content
                    else -> c.content.copy(alpha = 0.15f)
                }
                drawCircle(color, 1.5.dp.toPx(), p)
            }
        }
        BackdropKind.Device -> {
            val n = 20
            for (k in 0 until n) {
                val a = (-90f + k * 360f / n) * PI.toFloat() / 180f
                val on = k < (f * n).toInt() + 1
                drawLine(
                    if (on) c.accent else c.content.copy(alpha = 0.18f),
                    center + Offset(cos(a) * (r - 4.dp.toPx()), sin(a) * (r - 4.dp.toPx())), center + Offset(cos(a) * r, sin(a) * r),
                    1.6.dp.toPx(), StrokeCap.Round,
                )
            }
        }
        BackdropKind.Paper -> {
            drawArc(c.content.copy(alpha = 0.14f), -90f, 360f * f, true, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()))
            drawCircle(c.content, r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        BackdropKind.Game -> {
            val px = 2.dp.toPx()
            drawRect(c.content, style = Stroke(px * 1.5f))
            val n = 6
            val gap = px
            val w = (size.width - px * 4 - gap * (n - 1)) / n
            for (k in 0 until (f * n).toInt().coerceAtMost(n)) drawRect(c.accent, Offset(px * 2 + k * (w + gap), size.height - px * 2 - px * 2), Size(w, px * 2))
        }
        BackdropKind.Acid -> {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            drawCircle(c.content.copy(alpha = 0.14f), r - inset, style = Stroke(stroke))
            drawArc(c.accent, -90f, 360f * f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.6f), -90f, 360f * f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke * 0.25f, cap = StrokeCap.Round))
        }
    }
}
