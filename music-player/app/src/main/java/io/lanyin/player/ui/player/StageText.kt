package io.lanyin.player.ui.player

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * The part of [layout] already sung when [progress] (0..1) of its characters are, line by line:
 * whole lines up to the one being sung, and that one as far as the character in hand, partway
 * through it by the fraction left over.
 */
internal fun sungPath(layout: TextLayoutResult, progress: Float): Path {
    val path = Path()
    val total = layout.layoutInput.text.length
    if (total == 0 || progress <= 0f) return path
    val sung = progress.coerceAtMost(1f) * total
    for (line in 0 until layout.lineCount) {
        val start = layout.getLineStart(line)
        if (start >= sung) break
        val end = layout.getLineEnd(line, visibleEnd = true)
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        val x = if (sung >= end) {
            right
        } else {
            val k = sung.toInt()
            val box = layout.getBoundingBox(k)
            (box.left + (box.right - box.left) * (sung - k)).coerceIn(left, right)
        }
        path.addRect(Rect(left, layout.getLineTop(line), x, layout.getLineBottom(line)))
    }
    return path
}

/**
 * A line set in [style] whose sung part [paintSung] draws over it as the singing reaches it, with
 * [progress] read at draw time so the sweep moves without recomposing. [decorate] draws after
 * both, with the measured layout (a reflection, say).
 */
@Composable
internal fun SungText(
    text: String,
    style: TextStyle,
    progress: () -> Float,
    paintSung: DrawScope.(TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    decorate: (DrawScope.(TextLayoutResult) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            clipPath(sungPath(l, progress())) { paintSung(l) }
            decorate?.invoke(this, l)
        },
    )
}
