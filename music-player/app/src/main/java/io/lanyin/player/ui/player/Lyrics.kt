package io.lanyin.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Line positions measured in layout and read from draw-phase transforms. */
internal class LyricGeometry(count: Int) {
    val tops = IntArray(count)
    val heights = IntArray(count)

    /** Bumped when measurements change so layer blocks that read them re-run. */
    val version = mutableIntStateOf(0)

    fun center(i: Int): Float = tops[i] + heights[i] / 2f

    /** Vertical centre at a fractional line index, for smooth scrolling between lines. */
    fun centerAt(focus: Float): Float {
        if (tops.isEmpty()) return 0f
        val f = focus.coerceIn(0f, (tops.size - 1).toFloat())
        val i = floor(f).toInt()
        val j = (i + 1).coerceAtMost(tops.size - 1)
        val frac = f - i
        return center(i) + (center(j) - center(i)) * frac
    }
}

/**
 * Lyrics that move like a wave: each line chases the active line with a small per-line delay
 * (the theme's stagger), the active line fills karaoke-style, and the list can be dragged to
 * browse before drifting back. Tap a line to jump there.
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    currentIndex: Int,
    progress: () -> Float,
    onSeek: (Long) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    centered: Boolean = Lanyin.style.lyric.centered,
    anchorFraction: Float = 0.36f,
    /** False for a read-only preview: no dragging, no tap-to-seek. */
    interactive: Boolean = true,
    showTranslation: Boolean = true,
) {
    val style = Lanyin.style
    val geometry = remember(lines) { LyricGeometry(lines.size) }
    val focus = remember(lines) { List(lines.size) { Animatable(currentIndex.toFloat()) } }
    val scope = rememberCoroutineScope()
    var drag by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(currentIndex, lines) {
        val target = currentIndex.toFloat()
        lines.indices.forEach { i ->
            val d = i - currentIndex
            if (d < -6 || d > 14) {
                focus[i].snapTo(target)
            } else {
                launch {
                    delay(d.coerceAtLeast(0).toLong() * style.lyric.staggerMs)
                    focus[i].animateTo(target, style.motion.spec())
                }
            }
        }
    }

    fun dragBounds(): ClosedFloatingPointRange<Float> {
        if (lines.isEmpty()) return 0f..0f
        val here = geometry.center(currentIndex)
        return -(geometry.center(lines.lastIndex) - here)..(here - geometry.center(0))
    }

    val draggableState = rememberDraggableState { delta -> drag = (drag + delta).coerceIn(dragBounds()) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .fadingEdges()
            .draggable(
                draggableState,
                Orientation.Vertical,
                enabled = interactive,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    settleJob = scope.launch {
                        animateDecay(drag, velocity, FloatExponentialDecaySpec(frictionMultiplier = 1.6f)) { v, _ ->
                            drag = v.coerceIn(dragBounds())
                        }
                        delay(2600)
                        animate(drag, 0f, animationSpec = style.motion.spec()) { v, _ -> drag = v }
                    }
                },
            ),
    ) {
        val anchorPx = constraints.maxHeight * anchorFraction
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { (textStyle.fontSize.toPx() * 0.6f).toInt() }
        Layout(
            content = {
                lines.forEachIndexed { i, line ->
                    val distance = abs(i - currentIndex)
                    LyricRow(
                        line = line,
                        active = i == currentIndex,
                        distance = distance,
                        progress = progress,
                        centered = centered,
                        textStyle = textStyle,
                        showTranslation = showTranslation,
                        modifier = Modifier
                            .graphicsLayer {
                                geometry.version.intValue
                                translationY = anchorPx - geometry.centerAt(focus[i].value) + drag
                            }
                            .then(
                                if (interactive) {
                                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        settleJob?.cancel()
                                        drag = 0f
                                        onSeek(line.startMs)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            },
        ) { measurables, cons ->
            // Lines are laid out narrower by the active scale, so the enlarged line still fits.
            val childCons = Constraints(maxWidth = (cons.maxWidth / style.lyric.activeScale).toInt())
            val placeables = measurables.map { it.measure(childCons) }
            var y = 0
            var changed = false
            placeables.forEachIndexed { i, p ->
                if (geometry.tops[i] != y || geometry.heights[i] != p.height) changed = true
                geometry.tops[i] = y
                geometry.heights[i] = p.height
                y += p.height + gapPx
            }
            if (changed) geometry.version.intValue++
            layout(cons.maxWidth, cons.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val x = if (centered) (cons.maxWidth - p.width) / 2 else 0
                    p.place(x, geometry.tops[i])
                }
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    active: Boolean,
    distance: Int,
    progress: () -> Float,
    centered: Boolean,
    textStyle: TextStyle,
    showTranslation: Boolean,
    modifier: Modifier,
) {
    val style = Lanyin.style
    val c = style.colors
    val ls = style.lyric
    val activeAmount by animateFloatAsState(if (active) 1f else 0f, style.bouncy.spec(), label = "lyricActive")
    val sweep = if (style.lyric.serif && style.edge == io.lanyin.player.ui.theme.EdgeKind.Hairline) c.content else c.accent
    val base = c.content.copy(alpha = ls.inactiveAlpha + (0.42f - ls.inactiveAlpha).coerceAtLeast(0f) * activeAmount)
    val blur = ls.blurStep * (distance - 1).coerceIn(0, 3)
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val face = textStyle.copy(
        fontFamily = if (ls.serif) style.display else textStyle.fontFamily,
        fontWeight = if (ls.serif) FontWeight.Medium else FontWeight.Bold,
        textAlign = align,
    )
    Column(
        modifier
            .graphicsLayer {
                val s = 1f + (ls.activeScale - 1f) * activeAmount
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
            }
            .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
            .padding(vertical = 2.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        SweepText(
            text = line.text,
            style = face,
            baseColor = base,
            sweepColor = sweep,
            progress = if (active) progress else null,
            glow = if (ls.glow) sweep else null,
        )
        if (showTranslation && line.translation != null) {
            Text(
                line.translation,
                style = textStyle.copy(fontSize = textStyle.fontSize * 0.58f, lineHeight = textStyle.fontSize * 0.8f, fontWeight = FontWeight.Normal, textAlign = align),
                color = c.content.copy(alpha = (ls.inactiveAlpha + 0.3f * activeAmount).coerceAtMost(0.75f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Text whose active part is painted in [sweepColor] up to [progress], across wrapped lines, with
 * a feathered leading edge. The overlay is masked in the draw phase only.
 */
@Composable
fun SweepText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    sweepColor: Color,
    progress: (() -> Float)?,
    glow: Color?,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier) {
        Text(text, style = style.copy(color = baseColor), onTextLayout = { layout = it })
        if (progress != null) {
            Text(
                text,
                style = style.copy(color = sweepColor, shadow = glow?.let { Shadow(it.copy(alpha = 0.75f), Offset.Zero, 12f) }),
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        val feather = 22.dp.toPx()
                        var total = 0f
                        for (li in 0 until l.lineCount) total += l.getLineRight(li) - l.getLineLeft(li)
                        var remaining = progress() * (total + feather)
                        for (li in 0 until l.lineCount) {
                            val left = l.getLineLeft(li)
                            val right = l.getLineRight(li)
                            val edge = left + remaining
                            val mask: Brush = when {
                                remaining >= right - left + feather -> Brush.horizontalGradient(listOf(Color.Black, Color.Black))
                                remaining <= 0f -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                else -> Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge - feather, endX = edge)
                            }
                            val top = if (li == 0) -size.height else l.getLineTop(li)
                            val bottom = if (li == l.lineCount - 1) size.height * 2 else l.getLineBottom(li)
                            drawRect(mask, Offset(-feather * 2, top), Size(size.width + feather * 4, bottom - top), blendMode = BlendMode.DstIn)
                            remaining -= right - left
                        }
                    },
            )
        }
    }
}

/** Fades content out at the top and bottom edges instead of clipping it. */
fun Modifier.fadingEdges(fraction: Float = 0.16f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                fraction to Color.Black,
                1f - fraction to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
