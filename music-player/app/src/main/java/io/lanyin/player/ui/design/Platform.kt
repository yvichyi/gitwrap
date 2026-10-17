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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.DockKind
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
 * True where nothing drawn can be seen: the pages under the full player. Frame clocks there stand
 * still, so a page's tickers, meters and dancing bars don't redraw sixty times a second unseen.
 */
val LocalFramesPaused = androidx.compose.runtime.compositionLocalOf { false }

/**
 * True when motion should be kept to what's needed: chosen in settings, or the system's animations
 * are off. Frame clocks stand still (backdrops, meters, tickers, the turning record), neon lights
 * without flickering and pages simply fade from one to the next.
 */
val LocalReduceMotion = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Whether 点, 报 and 游 redraw covers in their medium (LED lights, halftone, pixels) or show them
 * as they are (see the 封面按主题重绘 setting).
 */
val LocalCoverMedium = androidx.compose.runtime.compositionLocalOf { true }

/** Whether presses give a tap under the finger (see the 触感反馈 setting). */
val LocalHapticsOn = androidx.compose.runtime.compositionLocalOf { false }

/** Whether the system has animations turned off (developer options or accessibility), checked again each time the app comes back. */
@Composable
fun systemAnimationsOff(): Boolean {
    var off by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!android.animation.ValueAnimator.areAnimatorsEnabled()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        off = !android.animation.ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { }
    }
    return off
}

/**
 * Seconds that advance only while [running] (and the drawing can be seen, see
 * [LocalFramesPaused]; and motion isn't reduced, see [LocalReduceMotion]), read in draw lambdas so animated backdrops redraw every frame without
 * recomposing anything.
 */
@Composable
fun rememberFrameClock(running: Boolean): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    val live = running && !LocalFramesPaused.current && !LocalReduceMotion.current
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        var last = -1L
        var pending = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last >= 0) pending += now - last
                last = now
                // On 120Hz screens and up, decoration moves every other frame: smooth enough for
                // drifting clouds and rain, and half the drawing.
                if (pending >= MIN_TICK_NANOS) {
                    seconds.floatValue += pending / 1_000_000_000f
                    pending = 0L
                }
            }
        }
    }
    return seconds
}

/** The shortest step a frame clock takes: about 100 a second at most. */
private const val MIN_TICK_NANOS = 10_000_000L

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
    val docked = Lanyin.style.dock == DockKind.Docked
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
                clipInOverlayDuringTransition = OverlayClip(if (docked) RectangleShape else RoundedCornerShape(28.dp)),
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
