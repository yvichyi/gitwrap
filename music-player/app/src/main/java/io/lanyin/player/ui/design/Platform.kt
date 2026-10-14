package io.lanyin.player.ui.design

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.isActive

/** Lets the player rotate the screen on demand instead of relying on system auto-rotate. */
interface OrientationController {
    fun lockLandscape()
    fun lockPortrait()
    /** Back to following the system's rotation setting. */
    fun release()
}

class ActivityOrientationController(private val activity: Activity) : OrientationController {
    override fun lockLandscape() = set(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    override fun lockPortrait() = set(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    override fun release() = set(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

    private fun set(orientation: Int) {
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }
}

val LocalOrientationController = staticCompositionLocalOf<OrientationController> {
    object : OrientationController {
        override fun lockLandscape() = Unit
        override fun lockPortrait() = Unit
        override fun release() = Unit
    }
}

/**
 * Seconds that advance only while [running], read in draw lambdas so animated backdrops redraw
 * every frame without recomposing anything.
 */
@Composable
fun rememberFrameClock(running: Boolean): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (last >= 0) seconds.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return seconds
}

/** The scopes needed to morph the mini player into the full player. */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedPlayerScope(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalSharedPlayerScope = staticCompositionLocalOf<SharedPlayerScope?> { null }

/**
 * Marks an element that morphs between the mini player and the full player. A no-op outside the
 * transition (previews, screenshot tests, settings thumbnails).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPlayer(
    key: String,
    zIndex: Float = 0f,
    scaleContent: Boolean = false,
    container: Boolean = false,
): Modifier {
    val scope = LocalSharedPlayerScope.current ?: return this
    val motion = Lanyin.style.motion
    return with(scope.transition) {
        if (container) {
            // Container transform: each side keeps its own layout, scaled into the growing bounds
            // and clipped to a rounded card, and turns opaque almost at once so the two never smear.
            this@sharedPlayer.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = scope.visibility,
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(90)),
                boundsTransform = { _, _ -> motion.spec(Rect.VisibilityThreshold) },
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.FillWidth, Alignment.Center),
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp)),
                zIndexInOverlay = zIndex,
            )
        } else {
            this@sharedPlayer.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = scope.visibility,
                enter = fadeIn(tween(160, delayMillis = 40)),
                exit = fadeOut(tween(120)),
                boundsTransform = { _, _ -> motion.spec(Rect.VisibilityThreshold) },
                resizeMode = if (scaleContent) {
                    SharedTransitionScope.ResizeMode.ScaleToBounds()
                } else {
                    SharedTransitionScope.ResizeMode.RemeasureToBounds
                },
                zIndexInOverlay = zIndex,
            )
        }
    }
}
