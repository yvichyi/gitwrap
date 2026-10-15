package io.lanyin.player.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlinx.coroutines.launch

/**
 * 澜's press feedback: rings spreading from where the finger landed, like a touch on still water.
 * [bounded] rings fill the surface (clipped by its shape); unbounded ones spread a little beyond
 * small targets such as icons.
 */
class WaterRipple(private val color: Color, private val bounded: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = WaterRippleNode(interactionSource, color, bounded)

    override fun equals(other: Any?): Boolean = other is WaterRipple && other.color == color && other.bounded == bounded

    override fun hashCode(): Int = color.hashCode() * 31 + bounded.hashCode()
}

private class WaterRippleNode(
    private val source: InteractionSource,
    private val color: Color,
    private val bounded: Boolean,
) : Modifier.Node(), DrawModifierNode {

    private class Drop(val at: Offset, val progress: Animatable<Float, AnimationVector1D>)

    private val drops = mutableStateListOf<Drop>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    val drop = Drop(interaction.pressPosition, Animatable(0f))
                    drops += drop
                    launch {
                        drop.progress.animateTo(1f, tween(950, easing = LinearOutSlowInEasing))
                        drops.remove(drop)
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (drops.isEmpty()) return
        val reach = if (bounded) hypot(size.width, size.height) * 0.9f else size.minDimension * 0.8f
        for (drop in drops) {
            val p = drop.progress.value
            // A faint swell where the drop landed, then two rings, the second trailing the first.
            drawCircle(color.copy(alpha = 0.07f * (1f - p)), reach * p * 0.55f, drop.at)
            for (k in 0..1) {
                val lag = k * 0.2f
                val q = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
                if (q <= 0f) continue
                val alpha = (1f - q) * (if (k == 0) 0.38f else 0.22f)
                val width = (2.5f * (1f - q) + 0.5f) * 1.dp.toPx()
                drawCircle(color.copy(alpha = alpha), reach * q, drop.at, style = Stroke(width))
            }
        }
    }
}
