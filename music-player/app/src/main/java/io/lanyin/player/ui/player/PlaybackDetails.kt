package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlinx.coroutines.delay

/**
 * A stamp struck over the stage when the music stops: it lands a little large and settles at an
 * angle, and lifts away when the music starts again.
 */
@Composable
private fun Stamp(visible: Boolean, modifier: Modifier, angle: Float, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 600f), initialScale = 1.7f) + fadeIn(tween(90)),
        exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f),
    ) {
        Box(Modifier.graphicsLayer { rotationZ = angle }) { content() }
    }
}

/** 格: a yellow label in a heavy black frame, slapped across the poster: PAUSED. */
@Composable
internal fun PosterPauseStamp(state: PlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Stamp(!state.isPlaying, modifier, angle = -7f) {
        Text(
            "PAUSED",
            style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Black, fontSize = fixedSp(15f), letterSpacing = 0.12.em),
            color = c.outline,
            modifier = Modifier.background(GridYellow).border(2.5.dp, c.outline).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** 报: a red rubber stamp across the photograph, 「暂停」 in a double frame, the ink a little thin. */
@Composable
internal fun PaperPauseStamp(state: PlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Stamp(!state.isPlaying, modifier, angle = -11f) {
        Box(
            Modifier
                .graphicsLayer { alpha = 0.85f }
                .border(2.dp, c.accent)
                .padding(3.dp)
                .border(1.dp, c.accent)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Text("暂　停", style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = fixedSp(20f), letterSpacing = 0.2.em), color = c.accent)
        }
    }
}

/**
 * 游's LCD over the artwork: PAUSE blinking in a box while the music is stopped, and as each
 * track begins a stage card, 「STAGE 02」 and the title, as a game opens a level.
 */
@Composable
internal fun LcdOverlay(state: PlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    val pixel = TextStyle(fontFamily = PixelFamily)
    var card by remember { mutableStateOf(false) }
    var shown by remember { mutableLongStateOf(state.track.id) }
    LaunchedEffect(state.track.id) {
        // Not on opening the player: only when the track changes under it.
        if (state.track.id == shown) return@LaunchedEffect
        shown = state.track.id
        card = true
        delay(1_500)
        card = false
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!state.isPlaying && !card) {
            val blink by rememberInfiniteTransition(label = "pause").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1_000, easing = LinearEasing)), label = "blink",
            )
            if (blink < 0.6f) {
                Text(
                    "PAUSE",
                    style = pixel.copy(fontSize = fixedSp(18f)),
                    color = c.content,
                    modifier = Modifier.background(c.bg).border(2.dp, c.content).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        AnimatedVisibility(card, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.background(c.content).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("STAGE %02d".format(state.trackNumber), style = pixel.copy(fontSize = fixedSp(18f)), color = c.bg)
                Text(state.title, style = pixel.copy(fontSize = fixedSp(11f)), color = c.bg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
