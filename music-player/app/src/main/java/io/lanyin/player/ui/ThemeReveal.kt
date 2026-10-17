package io.lanyin.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import io.lanyin.player.ui.design.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * Switching theme opens the new one from where it was chosen: the old screen, held as a picture,
 * gives way through a circle growing from the tapped card until the new theme fills it.
 */
class ThemeReveal {
    internal var layer: GraphicsLayer? = null
    internal var picture by mutableStateOf<ImageBitmap?>(null)
    internal var origin by mutableStateOf(Offset.Unspecified)
    internal val opened = Animatable(1f)

    /** Holds the screen as it is, to open the next theme from [from] (the screen's centre when unspecified). Call just before the change. */
    suspend fun before(from: Offset = Offset.Unspecified) {
        val current = layer ?: return
        picture = runCatching { current.toImageBitmap() }.getOrNull() ?: return
        origin = from
        opened.snapTo(0f)
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/** Draws [content], keeping a recording of it for [ThemeReveal]; when [key] (the theme and its mode) changes after [ThemeReveal.before], opens the new one. */
@Composable
fun ThemeRevealHost(reveal: ThemeReveal, key: Any, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    reveal.layer = layer
    val reduced = LocalReduceMotion.current
    LaunchedEffect(key) {
        if (reveal.picture == null) return@LaunchedEffect
        reveal.opened.animateTo(1f, tween(if (reduced) 150 else 650, easing = FastOutSlowInEasing))
        reveal.picture = null
    }
    // A picture held for a change that never came (the theme already chosen) is let go.
    LaunchedEffect(reveal.picture) {
        val held = reveal.picture ?: return@LaunchedEffect
        delay(1_500)
        if (reveal.picture === held && reveal.opened.value == 0f) reveal.picture = null
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) { content() }
        val picture = reveal.picture
        if (picture != null) {
            Canvas(Modifier.fillMaxSize()) {
                val o = if (reveal.origin.isSpecified) reveal.origin else Offset(size.width / 2f, size.height / 2f)
                // Far enough to reach the furthest corner.
                val reach = maxOf(hypot(o.x, o.y), hypot(size.width - o.x, o.y), hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y))
                val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, reach * reveal.opened.value)) }
                clipPath(hole, ClipOp.Difference) {
                    drawImage(picture)
                }
            }
        }
    }
}
