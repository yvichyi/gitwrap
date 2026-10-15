package io.lanyin.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

/** How far, as a fraction of the width, a drag must travel to count as a skip without a fling. */
internal const val SKIP_DISTANCE = 0.28f

/** Fling speed in px/s that skips even a short drag. */
internal const val SKIP_VELOCITY = 1_100f

/** Whether a released drag of [offset] px at [velocity] px/s over a [width] px element skips. */
internal fun isSkip(offset: Float, velocity: Float, width: Float): Boolean {
    if (offset == 0f) return false
    val far = abs(offset) > width * SKIP_DISTANCE
    val flung = abs(velocity) > SKIP_VELOCITY && sign(velocity) == sign(offset)
    return far || flung
}

/**
 * Lets [content] be flung sideways to change track. It follows the finger, flies off once the
 * drag passes a threshold, and the next track slides in from the opposite side. Skips that come
 * from the buttons ([state] changes track) slide in the same way, so the motion always tells
 * you which direction you went.
 */
@Composable
fun SwipeToSkip(state: PlayerState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    var shownIndex by remember { mutableIntStateOf(state.index) }

    LaunchedEffect(state.index) {
        if (state.index == shownIndex) return@LaunchedEffect
        shownIndex = state.index
        offset.snapTo(state.skipDirection * width * 0.55f)
        offset.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 320f))
    }

    Box(
        modifier
            .testTag("swipeToSkip")
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInputSkip(
                onDrag = { dx -> scope.launch { offset.snapTo(offset.value + dx) } },
                onRelease = { velocity ->
                    scope.launch {
                        val x = offset.value
                        if (isSkip(x, velocity, width.toFloat())) {
                            offset.animateTo(sign(x) * width, tween(150))
                            val before = state.index
                            // A swipe always changes track, even part-way through a song.
                            if (x < 0) state.skipNext() else state.skipPrevious(restartFirst = false)
                            // The real player answers a moment later; a one-track queue never does,
                            // so bring the artwork back if nothing changed.
                            val moved = withTimeoutOrNull(500) { snapshotFlow { state.index }.first { it != before } } != null
                            if (!moved) offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 320f))
                        } else {
                            offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                        }
                    }
                },
            )
            .graphicsLayer {
                val f = (offset.value / width).coerceIn(-1f, 1f)
                translationX = offset.value
                alpha = 1f - abs(f) * 0.75f
                val s = 1f - abs(f) * 0.12f
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Modifier.pointerInputSkip(onDrag: (Float) -> Unit, onRelease: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { onRelease(tracker.calculateVelocity().x) },
            onDragCancel = { onRelease(0f) },
        ) { change, dx ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            onDrag(dx)
        }
    }
