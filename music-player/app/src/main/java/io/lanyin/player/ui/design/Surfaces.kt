package io.lanyin.player.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle

/** Set only around floating chrome (mini player, tab bar) that sits over a `hazeSource`. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

enum class SurfaceTone {
    /** Content cards inside a page. */
    Card,
    /** Floating bars over content: glass in 澜. */
    Chrome,
    /** Call-to-action fill. */
    Accent,
}

/** The theme's "accent fill": a gradient where the theme likes gradients, flat elsewhere. */
fun LanyinStyle.accentBrush(): Brush = when (edge) {
    EdgeKind.Glass, EdgeKind.Soft -> Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
    else -> Brush.linearGradient(listOf(colors.accent, colors.accent))
}

/**
 * Draws a surface the way the active theme draws surfaces (glass, hairline, soft shadow, neon glow,
 * or retro outline with a hard shadow) and, with [onClick], gives it the theme's press feedback.
 */
@Composable
fun Modifier.themedSurface(
    shape: Shape = Lanyin.style.cardShape,
    tone: SurfaceTone = SurfaceTone.Card,
    onClick: (() -> Unit)? = null,
): Modifier {
    val style = Lanyin.style
    val c = style.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, style.motion.spec(), label = "press")
    val haze = LocalHazeState.current

    // 1. Decoration that stays put while the surface itself moves on press.
    var m = when (style.edge) {
        EdgeKind.Retro -> retroShadow(shape, c.content, 4.dp)
        EdgeKind.Glow -> neonGlow(shape, if (tone == SurfaceTone.Accent) c.accent else c.accent.copy(alpha = 0.55f))
        EdgeKind.Soft -> shadow(
            elevation = if (tone == SurfaceTone.Card) 10.dp else 18.dp, shape = shape,
            ambientColor = c.accent.copy(alpha = 0.25f), spotColor = c.accent.copy(alpha = 0.35f),
        )
        else -> this
    }
    // 2. Press feedback: retro surfaces sink into their shadow, others shrink (墨 also fades).
    m = m.graphicsLayer {
        if (style.edge == EdgeKind.Retro) {
            translationX = 4.dp.toPx() * press
            translationY = 4.dp.toPx() * press
        } else {
            val s = 1f - (1f - style.pressScale) * press
            scaleX = s
            scaleY = s
        }
        if (style.edge == EdgeKind.Hairline) alpha = 1f - 0.4f * press
    }
    m = m.clip(shape)
    // 3. Fill.
    m = when {
        tone == SurfaceTone.Accent && style.edge == EdgeKind.Glow -> m.background(c.surface)
        tone == SurfaceTone.Accent -> m.background(style.accentBrush())
        style.edge == EdgeKind.Glass && tone == SurfaceTone.Chrome && haze != null ->
            m.hazeEffect(state = haze, style = glassStyle(style))
        style.edge == EdgeKind.Glass -> m.background(c.surface.copy(alpha = if (style.dark) 0.5f else 0.7f))
        style.edge == EdgeKind.Hairline -> m.background(c.bg)
        style.edge == EdgeKind.Glow -> m.background(c.surface.copy(alpha = 0.9f))
        else -> m.background(c.surface)
    }
    // 4. Edge.
    m = when (style.edge) {
        EdgeKind.Glass -> m.border(1.dp, Color.White.copy(alpha = if (style.dark) 0.10f else 0.65f), shape)
        EdgeKind.Hairline -> m.border(1.dp, if (tone == SurfaceTone.Accent) c.content else c.outline, shape)
        EdgeKind.Glow -> m.border(1.dp, c.accent.copy(alpha = if (tone == SurfaceTone.Accent) 1f else 0.6f), shape)
        EdgeKind.Retro -> m.border(2.dp, c.content, shape)
        EdgeKind.Soft -> m
    }
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    return m
}

private fun glassStyle(style: LanyinStyle): HazeStyle {
    val c = style.colors
    return HazeStyle(
        backgroundColor = c.bg,
        tint = HazeTint(c.surface.copy(alpha = if (style.dark) 0.55f else 0.62f)),
        blurRadius = 28.dp,
        noiseFactor = 0.06f,
        fallbackTint = HazeTint(c.surface.copy(alpha = 0.94f)),
    )
}

/** Plain press feedback for icons and other non-surface elements. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val style = Lanyin.style
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, style.bouncy.spec(), label = "press")
    val depth = if (style.pressScale >= 1f) 0.08f else (1f - style.pressScale) * 1.4f
    return graphicsLayer {
        val s = 1f - depth * press
        scaleX = s
        scaleY = s
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Neo-retro hard shadow: the shape, filled solid, offset down and to the right. */
fun Modifier.retroShadow(shape: Shape, color: Color, offset: Dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(offset.toPx(), offset.toPx()) { drawOutline(outline, color) }
}

/** Layered strokes that read as a neon tube's glow without needing a blur. */
fun Modifier.neonGlow(shape: Shape, color: Color, intensity: Float = 1f): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val layers = listOf(14.dp to 0.05f, 9.dp to 0.10f, 5.dp to 0.20f, 2.dp to 0.45f)
    for ((width, alpha) in layers) {
        drawOutline(outline, color.copy(alpha = color.alpha * alpha * intensity), style = Stroke(width.toPx()))
    }
}
