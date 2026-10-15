package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** A 0..1 slider in the theme's hand: a hairline in 墨, a square block in 格, a glowing tube in 霓. */
@Composable
fun ThemedSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, onDone: () -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    onChange((o.x / size.width).coerceIn(0f, 1f))
                    onDone()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(onDragEnd = onDone) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val v = value.coerceIn(0f, 1f)
        val y = size.height / 2
        val r = 8.dp.toPx()
        val x = r + (size.width - r * 2) * v
        val alpha = if (enabled) 1f else 0.35f
        val rest = c.content.copy(alpha = 0.14f * alpha)
        val fill = (if (style.edge == EdgeKind.Hairline) c.content else c.accent).copy(alpha = alpha)
        when (style.edge) {
            EdgeKind.Hairline -> {
                drawLine(rest, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                drawLine(fill, Offset(0f, y), Offset(x, y), 1.5.dp.toPx())
                drawCircle(c.bg, r * 0.7f, Offset(x, y))
                drawCircle(fill, r * 0.7f, Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
            EdgeKind.Brutal -> {
                val h = 10.dp.toPx()
                drawRect(c.surface, Offset(0f, y - h / 2), Size(size.width, h))
                drawRect(fill, Offset(0f, y - h / 2), Size(x, h))
                drawRect(c.outline, Offset(0f, y - h / 2), Size(size.width, h), style = Stroke(2.dp.toPx()))
                val k = 18.dp.toPx()
                drawRect(c.outline, Offset(x - k / 2 + 2.dp.toPx(), y - k / 2 + 2.dp.toPx()), Size(k, k))
                drawRect(io.lanyin.player.ui.theme.GridYellow.copy(alpha = alpha), Offset(x - k / 2, y - k / 2), Size(k, k))
                drawRect(c.outline, Offset(x - k / 2, y - k / 2), Size(k, k), style = Stroke(2.dp.toPx()))
            }
            else -> {
                val h = 4.dp.toPx()
                drawRoundRect(rest, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
                if (style.edge == EdgeKind.Glow) drawLine(fill.copy(alpha = 0.3f * alpha), Offset(0f, y), Offset(x, y), h * 3, StrokeCap.Round)
                drawRoundRect(fill, Offset(0f, y - h / 2), Size(x, h), CornerRadius(h / 2))
                drawCircle(fill, r, Offset(x, y))
                drawCircle(c.bg, r * 0.45f, Offset(x, y))
            }
        }
    }
}
