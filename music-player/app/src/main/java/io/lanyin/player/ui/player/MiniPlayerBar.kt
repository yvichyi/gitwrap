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
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

/** Play/pause wrapped in a progress ring that fills as the song plays. */
@Composable
private fun MiniPlayButton(state: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    val morph by animateFloatAsState(if (state.isPlaying) 1f else 0f, style.bouncy.spec(), label = "miniMorph")
    Box(
        Modifier
            .size(48.dp)
            .pressable(onClick = state::togglePlay)
            .semantics { contentDescription = if (state.isPlaying) "暂停" else "播放" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            if (style.edge == EdgeKind.Brutal) {
                // 格: a square key with the progress as a bar filling along its foot.
                val stroke = style.strokeWidth.toPx()
                val bar = 5.dp.toPx()
                val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                drawRect(c.outline, Offset(3.dp.toPx(), 3.dp.toPx()), size)
                drawRect(c.surface)
                drawRect(c.accent, Offset(0f, size.height - bar), Size(size.width * f, bar))
                drawRect(c.outline, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                return@Canvas
            }
            val stroke = (if (style.dock == DockKind.Docked) 1.5.dp else 2.5.dp).toPx()
            val inset = stroke / 2
            drawCircle(c.content.copy(alpha = 0.14f), size.minDimension / 2 - inset, style = Stroke(stroke))
            drawArc(
                c.accent, -90f, 360f * state.positionMs / state.durationMs, false,
                Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Canvas(Modifier.size(13.dp)) { drawPlayPauseGlyph(morph, c.content) }
    }
}
