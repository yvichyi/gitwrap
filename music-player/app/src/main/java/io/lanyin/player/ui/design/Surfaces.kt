package io.lanyin.player.ui.design

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Draws a surface the way the active theme draws surfaces (hairline, soft shadow, neon glow, retro
 * outline with a hard shadow, grey tile, a machine's panel or key) and, with [onClick], gives it
 * the theme's press feedback.
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
    // 机: bars, sheets and buttons are keys standing on the case; cards are panels set into it.
    val key = style.edge == EdgeKind.Panel && (tone == SurfaceTone.Chrome || tone == SurfaceTone.Accent)
    // 游: buttons are the console's magenta buttons, standing on a darker rim.
    val button = style.edge == EdgeKind.Bezel && tone == SurfaceTone.Accent
    var m = when {
        hardShadow > 0.dp -> retroShadow(shape, c.outline, hardShadow)
        key -> keycap(shape, style, accent = tone == SurfaceTone.Accent) { press }
        button -> drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(0f, 3.dp.toPx()) { drawOutline(outline, androidx.compose.ui.graphics.lerp(c.accent, Color.Black, 0.35f)) }
        }
        flat -> this
        // 霓: a sign's tube round what you can press; everything else matte, standing off the wall on a dark shadow.
        style.edge == EdgeKind.Glow -> if (tone == SurfaceTone.Accent) neonGlow(shape, c.accent, 0.9f) else shadow(if (tone == SurfaceTone.Card) 10.dp else 6.dp, shape)
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
        } else if (key) {
            // The cap is drawn behind; the content rides down with its face.
            translationY = keyTravel.toPx() * press
        } else if (button) {
            translationY = 3.dp.toPx() * press
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
        tone == SurfaceTone.Accent && style.edge == EdgeKind.Glow -> m.background(c.accent.copy(alpha = 0.12f))
        // 酸: lime gel, lighter at the top, with the gloss across it.
        tone == SurfaceTone.Accent && style.edge == EdgeKind.Chrome -> m.background(Brush.verticalGradient(listOf(Color(0xFFE2FF6B), c.accent, Color(0xFF8FC400)))).drawBehind { drawGloss(0.45f) }
        tone == SurfaceTone.Accent -> m.background(style.accentBrush())
        style.edge == EdgeKind.Hairline -> m.background(c.bg)
        // Matte, a little light catching its top edge, and the light of a sign falling on it while it's pressed.
        style.edge == EdgeKind.Glow -> m.background(if (flat) c.surfaceAlt else c.surface).drawBehind {
            drawRect(Color.White.copy(alpha = 0.05f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            if (press > 0f) drawRect(c.accent.copy(alpha = 0.10f * press))
        }
        // The cap was drawn behind, face and all.
        key -> m
        // A panel recessed into the case: a shadow along its top edge.
        style.edge == EdgeKind.Panel -> m.background(if (flat) c.surfaceAlt else c.surface).drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (style.dark) 0.22f else 0.07f), Color.Transparent), endY = 7.dp.toPx()))
        }
        // 报: no box, just the page; a story is ruled off above, a heavy rule over a fine one.
        style.edge == EdgeKind.Rule -> m.background(if (tone == SurfaceTone.Chrome) c.bg else Color.Transparent).drawBehind {
            if (tone == SurfaceTone.Card) {
                drawRect(c.content, size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                drawRect(c.content, androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 0.75.dp.toPx()))
            } else {
                drawRect(c.content.copy(alpha = 0.8f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
        }
        // 酸: dark plastic, the light catching its top.
        style.edge == EdgeKind.Chrome -> m.background(
            if (flat) Brush.verticalGradient(listOf(c.surfaceAlt, c.surfaceAlt)) else Brush.verticalGradient(listOf(c.surfaceAlt, c.surface)),
        ).drawBehind { if (!flat) drawGloss(0.06f) }
        // 游: a little LCD, a shade darker when it's secondary.
        style.edge == EdgeKind.Bezel -> m.background(if (flat) c.surfaceAlt else c.surface)
        else -> m.background(c.surface)
    }
    // 4. Edge.
    m = when (style.edge) {
        EdgeKind.Hairline -> m.border(1.dp, if (tone == SurfaceTone.Accent) c.content else c.outline, shape)
        EdgeKind.Glow -> if (tone == SurfaceTone.Accent) m.border(1.5.dp, neonCore(c.accent), shape) else m
        EdgeKind.Brutal -> m.border(style.strokeWidth, c.outline, shape)
        // In the dark a shadow barely shows; a faint rim keeps the card's edge.
        EdgeKind.Soft -> if (style.dark) m.border(1.dp, c.content.copy(alpha = 0.07f), shape) else m
        // A print's thin rim.
        EdgeKind.Film -> m.border(1.dp, c.content.copy(alpha = if (flat) 0.08f else 0.14f), shape)
        // Grey tiles on black need no edge; bars over the content get a faint one.
        EdgeKind.Dot -> if (tone == SurfaceTone.Chrome) m.border(1.dp, c.content.copy(alpha = 0.1f), shape) else m
        EdgeKind.Panel -> if (key) m else m.border(1.dp, c.outline, shape)
        EdgeKind.Rule -> m
        // The screen's dark bezel; buttons have none.
        EdgeKind.Bezel -> if (button) m else m.border(if (flat) 2.dp else 3.dp, GameBezel, shape)
        // 酸: a soft chrome rim on cards, polished chrome round bars and sheets; gel and flat have none.
        EdgeKind.Chrome -> when (tone) {
            SurfaceTone.Card -> m.border(1.dp, ChromeSoftBrush, shape)
            SurfaceTone.Chrome -> m.border(1.5.dp, ChromeBrush, shape)
            SurfaceTone.Flat, SurfaceTone.Accent -> m
        }
    }
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    return m
}

/**
 * Plain press feedback for icons and other non-surface elements. A button drawn rather than
 * written (a play glyph, say) passes its [label], which is what a screen reader says for it.
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, label: String? = null, onClick: () -> Unit): Modifier {
    val style = Lanyin.style
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, style.bouncy.spec(), label = "press")
    val depth = if (style.pressScale >= 1f) 0.08f else (1f - style.pressScale) * 1.4f
    return graphicsLayer {
        val s = 1f - depth * press
        scaleX = s
        scaleY = s
    }.then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier).clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = if (label != null) Role.Button else null,
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
