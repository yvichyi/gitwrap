package io.lanyin.player.ui.player

import io.lanyin.player.ui.theme.PlayerSkin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.audio.pulseAfter
import io.lanyin.player.ui.design.SwissPhoto
import io.lanyin.player.ui.design.drawConcertPoster
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.Lanyin

/**
 * 格's record: the song's concert poster, built of circles in the Swiss manner and drawn with its
 * construction still on it. Its nested rings turn with the music, each at its own speed; the red
 * disc swells with the bass, the yellow square jumps on the beat, the blue bar runs out with the
 * treble; the cover sits in the middle as a hard black-and-white photograph. Swipe it to change
 * song; tap for the lyrics.
 */
@Composable
internal fun ConcertPosterStage(stage: StageContext, modifier: Modifier, onClick: (() -> Unit)?) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    val clock = rememberFrameClock(state.isPlaying)
    val measurer = rememberTextMeasurer()
    val levels = remember { FloatArray(32) }
    val eased = remember { FloatArray(3) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val tap = if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            // Read so the poster is redrawn each frame while the music plays.
            clock.floatValue
            val live = state.isPlaying && state.audioFeed.levels(levels)
            val bands = arrayOf(0 until 6, 6 until 16, 16 until 32)
            for (k in 0..2) {
                var v = 0f
                if (live) for (i in bands[k]) v += levels[i] / bands[k].count()
                eased[k] += (v - eased[k]) * 0.3f
            }
            val beat = if (state.isPlaying) pulseAfter(state.audioFeed.sinceBeat(), state.audioFeed.beatPeriod(), 0.35f) else 0f
            drawConcertPoster(style, state.coverSeed, stage.spin.angle, eased[0], beat, eased[2], measurer)
        }
        SwipeToSkip(state, Modifier.fillMaxSize().then(tap).then(stage.dismissDrag)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // A record's label in the rings' middle; or, with the square cover chosen, the
                // photograph square and larger, laid over the rings as a poster's picture is.
                val square = stage.skin == PlayerSkin.COVER
                val shape = if (square) androidx.compose.ui.graphics.RectangleShape else CircleShape
                SwissPhoto(
                    state.coverUrl, state.coverSeed,
                    Modifier
                        .size(side * if (square) 0.5f else 0.31f)
                        .sharedPlayer("artwork", zIndex = 1f)
                        .clip(shape)
                        .border(2.5.dp, c.outline, shape)
                        .semantics { contentDescription = "海报：${state.title}" },
                )
            }
        }
    }
}
