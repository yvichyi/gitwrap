package io.lanyin.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.lanyin.player.ui.design.InkWashCover
import io.lanyin.player.ui.design.drawBrushArm
import io.lanyin.player.ui.design.drawBrushRest
import io.lanyin.player.ui.design.drawInkSheen
import io.lanyin.player.ui.design.drawInkstone
import io.lanyin.player.ui.design.sharedPlayer

/**
 * 墨's record: an inkstone on the desk, turning with the music, the cover shining up out of the
 * ink in its well like the lamp reflected; the brush lies across it as a record's arm does, its
 * tip in the ink while the song plays, lifted back onto its rest when it's paused. Swipe it to
 * change song; tap for the lyrics.
 */
@Composable
internal fun InkstoneStage(stage: StageContext, modifier: Modifier, onClick: (() -> Unit)?) {
    val state = stage.state
    val down by animateFloatAsState(if (state.isPlaying) 1f else 0f, tween(700), label = "brush")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight * 1.0f) * 0.86f
        val tap = if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier
        SwipeToSkip(state, Modifier.fillMaxSize().then(tap).then(stage.dismissDrag)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(d)
                        .sharedPlayer("artwork", zIndex = 1f)
                        .semantics { contentDescription = "砚台：${state.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    // The stone and its carved rim turn; the light on it stays.
                    Canvas(Modifier.fillMaxSize()) { drawInkstone(stage.spin.angle) }
                    Box(Modifier.size(d * 0.7f).clip(CircleShape).graphicsLayer { rotationZ = stage.spin.angle }) {
                        InkWashCover(state.coverUrl, state.coverSeed, Modifier.fillMaxSize(), ink = Color(0xFFE9E4D8), light = true)
                    }
                    Canvas(Modifier.size(d * 0.7f)) { drawInkSheen() }
                }
            }
        }
        // The brush and its rest, at the stone's upper right.
        Canvas(Modifier.size(maxWidth, maxHeight)) {
            val r = d.toPx() / 2f
            val c = center
            val pivot = c + Offset(r * 0.92f, -r * 1.02f)
            drawBrushRest(pivot + Offset(r * 0.02f, r * 0.08f), r * 0.32f)
            // Rests pointing down the side when lifted; swings in so its tip sits in the ink.
            drawBrushArm(pivot, r * 1.35f, -12f + 42f * down)
        }
    }
}
