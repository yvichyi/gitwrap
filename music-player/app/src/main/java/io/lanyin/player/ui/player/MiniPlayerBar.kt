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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin

@Composable
fun MiniPlayer(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Lanyin.style
    val c = style.colors
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Row(
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("miniPlayer")
                .sharedPlayer("player-container", container = true)
                .themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome, onClick = onExpand)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniArtwork(skin, spin, state.coverSeed, Modifier.size(44.dp).sharedPlayer("artwork", zIndex = 1f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = style.display, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true),
                )
                AnimatedContent(
                    targetState = state.lyrics[state.currentLyricIndex].text,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically { it }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it })
                    },
                    label = "miniLyric",
                ) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MiniPlayButton(state)
            IconAction(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", {}, tint = c.muted)
        }
    }
}

/** Play/pause wrapped in a progress ring that fills as the song plays. */
@Composable
private fun MiniPlayButton(state: DemoPlayerState) {
    val c = Lanyin.colors
    val morph by animateFloatAsState(if (state.isPlaying) 1f else 0f, Lanyin.style.bouncy.spec(), label = "miniMorph")
    Box(Modifier.size(48.dp).pressable(onClick = state::togglePlay), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(38.dp)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2
            drawCircle(c.content.copy(alpha = 0.12f), size.minDimension / 2 - inset, style = Stroke(stroke))
            drawArc(
                c.accent, -90f, 360f * state.positionMs / state.durationMs, false,
                Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Canvas(Modifier.size(14.dp)) { drawPlayPauseGlyph(morph, c.content) }
    }
}
