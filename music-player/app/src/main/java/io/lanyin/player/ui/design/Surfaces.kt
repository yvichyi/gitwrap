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
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle


enum class SurfaceTone {
    /** The one card a screen is about: gets the theme's full shadow or glow. */
    Card,
    /** Secondary cards and fields: same fill and edge, no shadow or glow, so they don't compete. */
    Flat,
    /** Floating bars over content: the mini player, the tab bar, sheets. */
    Chrome,
    /** Call-to-action fill. */
    Accent,
}

/** The theme's "accent fill": a gradient where the theme likes gradients, flat elsewhere. */
fun LanyinStyle.accentBrush(): Brush = when (edge) {
    EdgeKind.Soft -> Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
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
    // 格's surfaces spring back past their rest position when released.
    val press by animateFloatAsState(if (pressed) 1f else 0f, if (style.edge == EdgeKind.Brutal) style.bouncy.spec() else style.motion.spec(), label = "press")

    // 1. Decoration that stays put while the surface itself moves on press.
    val flat = tone == SurfaceTone.Flat
    // Hard shadows: 格 gives every surface one, deepest on cards.
    val hardShadow = when {
        !style.hardEdge -> 0.dp
        flat -> 3.dp
        tone == SurfaceTone.Card -> style.shadowOffset
        else -> 4.dp
    }
    var m = when {
        hardShadow > 0.dp -> retroShadow(shape, c.outline, hardShadow)
        flat -> this
        style.edge == EdgeKind.Glow -> neonGlow(shape, if (tone == SurfaceTone.Accent) c.accent else c.accent.copy(alpha = 0.55f))
        // 暮: lifted off the paper on a soft, warm shadow (a faint glow of the sun at night).
        style.edge == EdgeKind.Soft -> shadow(
            elevation = if (tone == SurfaceTone.Card) 8.dp else 14.dp, shape = shape,
            ambientColor = (if (style.dark) c.accent else c.content).copy(alpha = if (style.dark) 0.18f else 0.10f),
            spotColor = (if (style.dark) c.accent else c.content).copy(alpha = if (style.dark) 0.22f else 0.16f),
        )
        else -> this
    }
    // 2. Press feedback: retro surfaces sink into their shadow, others shrink (墨 also fades).
    m = m.graphicsLayer {
        if (hardShadow > 0.dp) {
            translationX = hardShadow.toPx() * press
            translationY = hardShadow.toPx() * press
        } else {
            val s = 1f - (1f - minOf(style.pressScale, 0.97f)) * press
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
        style.edge == EdgeKind.Hairline -> m.background(c.bg)
        style.edge == EdgeKind.Glow -> m.background(c.surface.copy(alpha = 0.9f))
        else -> m.background(c.surface)
    }
    // 4. Edge.
    m = when (style.edge) {
        EdgeKind.Hairline -> m.border(1.dp, if (tone == SurfaceTone.Accent) c.content else c.outline, shape)
        EdgeKind.Glow -> m.border(1.dp, c.accent.copy(alpha = if (tone == SurfaceTone.Accent) 1f else if (flat) 0.35f else 0.6f), shape)
        EdgeKind.Brutal -> m.border(style.strokeWidth, c.outline, shape)
        // In the dark a shadow barely shows; a faint rim keeps the card's edge.
        EdgeKind.Soft -> if (style.dark) m.border(1.dp, c.content.copy(alpha = 0.07f), shape) else m
    }
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    return m
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
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
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
