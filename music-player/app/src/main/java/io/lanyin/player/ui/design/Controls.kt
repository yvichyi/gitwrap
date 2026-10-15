package io.lanyin.player.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.RectangleShape
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.ButtonKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SeekKind
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Play/pause whose glyph morphs between the triangle and the two bars. */
@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val morph by animateFloatAsState(if (playing) 1f else 0f, style.bouncy.spec(), label = "playMorph")
    val shape = when (style.button) {
        ButtonKind.Blob -> RoundedCornerShape(38)
        ButtonKind.Retro -> RoundedCornerShape(30)
        ButtonKind.Brutal -> RectangleShape
        else -> CircleShape
    }
    val glyphColor = when (style.button) {
        ButtonKind.Outline -> c.content
        ButtonKind.NeonRing -> c.accent
        else -> c.onAccent
    }
    val container = when (style.button) {
        // 澜: a bubble, lit from the upper left with a specular glint.
        ButtonKind.Glow -> Modifier
            .shadow(size * 0.28f, shape, ambientColor = c.accent, spotColor = c.accent)
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(androidx.compose.ui.graphics.lerp(c.accent, Color.White, 0.28f), c.accent, androidx.compose.ui.graphics.lerp(c.accent, Color.Black, 0.18f)),
                        center = Offset(this.size.width * 0.34f, this.size.height * 0.3f), radius = this.size.width * 0.9f,
                    ),
                )
                drawOval(Color.White.copy(alpha = 0.3f), Offset(this.size.width * 0.2f, this.size.height * 0.11f), Size(this.size.width * 0.34f, this.size.height * 0.18f))
            }
        ButtonKind.Outline -> Modifier.border(1.5.dp, c.content, shape)
        ButtonKind.Blob -> Modifier
            .shadow(size * 0.22f, shape, ambientColor = c.accent, spotColor = c.accent)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.accent, c.accentAlt)))
        ButtonKind.NeonRing -> Modifier
            .neonGlow(shape, c.accent)
            .border(2.dp, c.accent, shape)
        ButtonKind.Retro -> Modifier
            .retroShadow(shape, c.outline, 3.dp)
            .clip(shape)
            .background(c.accent)
            .border(2.dp, c.outline, shape)
        ButtonKind.Brutal -> Modifier
            .retroShadow(shape, c.outline, 5.dp)
            .clip(shape)
            .background(c.accent)
            .border(style.strokeWidth, c.outline, shape)
    }
    Box(
        modifier
            .size(size)
            .pressable(onClick = onClick)
            .then(container),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * if (style.button == ButtonKind.Outline) 0.32f else 0.42f)) {
            drawPlayPauseGlyph(morph, glyphColor)
        }
    }
}

/** 0 = play triangle, 1 = pause bars; two quads whose corners interpolate. */
fun DrawScope.drawPlayPauseGlyph(morph: Float, color: Color) {
    val a = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val top = cy - a / 2f
    val bottom = cy + a / 2f
    // Play triangle, nudged right so it looks optically centred.
    val left = cx - a * 0.36f
    val right = cx + a * 0.5f
    val mid = cx + a * 0.07f
    val midTop = top + (cy - top) * (mid - left) / (right - left)
    val midBottom = bottom - (bottom - cy) * (mid - left) / (right - left)
    val play = listOf(
        listOf(Offset(left, top), Offset(mid, midTop), Offset(mid, midBottom), Offset(left, bottom)),
        listOf(Offset(mid, midTop), Offset(right, cy), Offset(right, cy), Offset(mid, midBottom)),
    )
    val bar = a * 0.32f
    val pause = listOf(
        listOf(Offset(cx - a * 0.42f, top), Offset(cx - a * 0.42f + bar, top), Offset(cx - a * 0.42f + bar, bottom), Offset(cx - a * 0.42f, bottom)),
        listOf(Offset(cx + a * 0.42f - bar, top), Offset(cx + a * 0.42f, top), Offset(cx + a * 0.42f, bottom), Offset(cx + a * 0.42f - bar, bottom)),
    )
    for (q in 0..1) {
        val path = Path()
        for (p in 0..3) {
            val from = play[q][p]
            val to = pause[q][p]
            val x = from.x + (to.x - from.x) * morph
            val y = from.y + (to.y - from.y) * morph
            if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
        // A same-colour stroke rounds the corners.
        drawPath(path, color, style = Stroke(a * 0.09f, join = StrokeJoin.Round))
    }
}

/** Icon with the theme's press feedback; 48dp touch target. */
@Composable
fun IconAction(
    icon: LineIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    Box(modifier.size(48.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        ThemedIcon(icon, contentDescription, tint = tint, size = iconSize)
    }
}

/** Heart that pops and throws a ring when liked. */
@Composable
fun LikeButton(liked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val color = if (liked) style.colors.like else LocalContentColor.current
    Box(
        modifier
            .size(48.dp)
            .pressable {
                val nowLiked = !liked
                onToggle()
                scope.launch {
                    scale.snapTo(0.55f)
                    scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
                }
                if (nowLiked) scope.launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(480))
                }
            }
            .drawBehind {
                val b = burst.value
                if (b < 1f) {
                    drawCircle(style.colors.like.copy(alpha = 1f - b), radius = size.minDimension * (0.2f + 0.45f * b), style = Stroke(3.dp.toPx() * (1f - b)))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ThemedIcon(
            if (liked) LineIcon.HeartFilled else LineIcon.Heart,
            contentDescription = if (liked) "取消喜欢" else "喜欢",
            tint = color,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Time · track · time, or the track full width with the times beneath it ([timesBelow]). The track
 * is drawn per theme and redraws every frame from [position] without recomposing; tap or drag to
 * seek. While dragging, [onScrub] reports where the finger is (null on release) so the lyrics can
 * follow along before the seek lands.
 */
@Composable
fun SeekBar(
    position: () -> Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timesBelow: Boolean = false,
    onScrub: ((Long?) -> Unit)? = null,
) {
    val style = Lanyin.style
    val c = style.colors
    val clock = rememberFrameClock(playing && style.seek == SeekKind.Wave)
    val waveAmp by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "waveAmp")
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seconds by remember { derivedStateOf { position() / 1000 } }
    // Tabular figures so the time doesn't shuffle sideways as digits change.
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = style.numeric, color = c.muted, fontFeatureSettings = "tnum")
    val shown = dragFraction?.let { (it * durationMs).toLong() / 1000 } ?: seconds
    fun scrubTo(f: Float?) {
        dragFraction = f
        onScrub?.invoke(f?.let { (it * durationMs).toLong() })
    }
    val track = @Composable { trackModifier: Modifier ->
        Canvas(
            trackModifier
                .height(32.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong()) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> scrubTo((o.x / size.width).coerceIn(0f, 1f)) },
                        onDragEnd = {
                            dragFraction?.let { onSeek((it * durationMs).toLong()) }
                            scrubTo(null)
                        },
                        onDragCancel = { scrubTo(null) },
                    ) { change, _ ->
                        change.consume()
                        scrubTo((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            val f = dragFraction ?: (position().toFloat() / durationMs).coerceIn(0f, 1f)
            when (style.seek) {
                SeekKind.Wave -> drawWaveTrack(f, c.accent, c.content.copy(alpha = 0.18f), clock.floatValue, waveAmp)
                SeekKind.Hairline -> drawHairlineTrack(f, c.content)
                SeekKind.Pill -> drawPillTrack(f, c.accent, c.accentAlt, c.content.copy(alpha = 0.12f))
                SeekKind.Segments -> drawSegmentTrack(f, c.accent, c.accentAlt, c.content.copy(alpha = 0.12f))
                SeekKind.Tape -> drawTapeTrack(f, c.accent, c.outline, c.surface)
                SeekKind.Block -> drawBlockTrack(f, c.accent, c.outline, c.surface)
            }
        }
    }

    if (timesBelow) {
        Column(modifier.fillMaxWidth()) {
            track(Modifier.fillMaxWidth().padding(horizontal = 2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TimeLabel(shown * 1000, timeStyle)
                Spacer(Modifier.weight(1f))
                TimeLabel(durationMs, timeStyle)
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(shown * 1000, timeStyle)
            track(Modifier.weight(1f).padding(horizontal = 14.dp))
            TimeLabel(durationMs, timeStyle)
        }
    }
}

/** Elapsed or total time; 暮 shows it as a little tape counter. */
@Composable
private fun TimeLabel(ms: Long, style: TextStyle) {
    val s = Lanyin.style
    if (s.seek == SeekKind.Tape || s.seek == SeekKind.Block) {
        val total = (ms / 1000).coerceAtLeast(0)
        Text(
            "%02d:%02d".format(total / 60, total % 60),
            style = style.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 0.08.em, fontWeight = FontWeight.Bold),
            color = s.colors.surface,
            modifier = Modifier
                .clip(if (s.seek == SeekKind.Block) RectangleShape else RoundedCornerShape(4.dp))
                .background(s.colors.outline)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    } else {
        Text(formatTime(ms), style = style)
    }
}

private fun DrawScope.drawWaveTrack(f: Float, played: Color, rest: Color, t: Float, amp: Float) {
    val cy = size.height / 2f
    val x = size.width * f
    val stroke = 3.dp.toPx()
    drawLine(rest, Offset(x, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
    val wavelength = 26.dp.toPx()
    val a = 3.5.dp.toPx() * amp
    val path = Path().apply {
        moveTo(0f, cy)
        var px = 0f
        while (px < x) {
            lineTo(px, cy + a * sin((px / wavelength) * 2f * PI.toFloat() - t * 7f))
            px += 2f
        }
        lineTo(x, cy + a * sin((x / wavelength) * 2f * PI.toFloat() - t * 7f))
    }
    drawPath(path, played, style = Stroke(stroke, cap = StrokeCap.Round))
    drawRoundRect(played, Offset(x - 2.dp.toPx(), cy - 9.dp.toPx()), Size(4.dp.toPx(), 18.dp.toPx()), CornerRadius(2.dp.toPx()))
}

private fun DrawScope.drawHairlineTrack(f: Float, ink: Color) {
    val cy = size.height / 2f
    val x = size.width * f
    drawLine(ink.copy(alpha = 0.2f), Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
    drawLine(ink, Offset(0f, cy), Offset(x, cy), 1.5.dp.toPx())
    drawCircle(ink, 3.5.dp.toPx(), Offset(x, cy))
}

private fun DrawScope.drawPillTrack(f: Float, a: Color, b: Color, rest: Color) {
    val h = 6.dp.toPx()
    val top = (size.height - h) / 2f
    val x = size.width * f
    drawRoundRect(rest, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
    drawRoundRect(Brush.horizontalGradient(listOf(a, b), 0f, size.width), Offset(0f, top), Size(x.coerceAtLeast(h), h), CornerRadius(h / 2))
    drawCircle(Color.Black.copy(alpha = 0.12f), 10.dp.toPx(), Offset(x, size.height / 2f + 1.5.dp.toPx()))
    drawCircle(Color.White, 9.dp.toPx(), Offset(x, size.height / 2f))
    drawCircle(a, 9.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.5.dp.toPx()))
}

private fun DrawScope.drawSegmentTrack(f: Float, lit: Color, head: Color, dim: Color) {
    val segW = 3.dp.toPx()
    val gap = 3.dp.toPx()
    val h = 12.dp.toPx()
    val top = (size.height - h) / 2f
    val n = (size.width / (segW + gap)).toInt().coerceAtLeast(1)
    val litCount = (f * n).toInt()
    for (i in 0 until n) {
        val x = i * (segW + gap)
        when {
            i < litCount -> {
                drawRect(lit.copy(alpha = 0.22f), Offset(x - 1.5.dp.toPx(), top - 3.dp.toPx()), Size(segW + 3.dp.toPx(), h + 6.dp.toPx()))
                drawRect(lit, Offset(x, top), Size(segW, h))
            }
            i == litCount -> {
                drawRect(head.copy(alpha = 0.3f), Offset(x - 2.dp.toPx(), top - 5.dp.toPx()), Size(segW + 4.dp.toPx(), h + 10.dp.toPx()))
                drawRect(head, Offset(x, top - 2.dp.toPx()), Size(segW, h + 4.dp.toPx()))
            }
            else -> drawRect(dim, Offset(x, top + h * 0.3f), Size(segW, h * 0.4f))
        }
    }
}

/** 格: a thick ruled bar with a hard shadow, ruler ticks and a square yellow handle. */
private fun DrawScope.drawBlockTrack(f: Float, fill: Color, ink: Color, paper: Color) {
    val h = 14.dp.toPx()
    val top = (size.height - h) / 2f
    val x = size.width * f
    val stroke = 2.5.dp.toPx()
    val shadow = 3.dp.toPx()
    drawRect(ink, Offset(shadow, top + shadow), Size(size.width, h))
    drawRect(paper, Offset(0f, top), Size(size.width, h))
    drawRect(fill, Offset(0f, top), Size(x, h))
    for (i in 1..9) {
        val tx = size.width * i / 10f
        drawLine(ink.copy(alpha = 0.55f), Offset(tx, top + h * 0.5f), Offset(tx, top + h), 1.dp.toPx())
    }
    drawRect(ink, Offset(0f, top), Size(size.width, h), style = Stroke(stroke))
    val knob = 20.dp.toPx()
    val kx = (x - knob / 2).coerceIn(0f, size.width - knob)
    val ky = (size.height - knob) / 2f
    drawRect(ink, Offset(kx + shadow, ky + shadow), Size(knob, knob))
    drawRect(io.lanyin.player.ui.theme.GridYellow, Offset(kx, ky), Size(knob, knob))
    drawRect(ink, Offset(kx, ky), Size(knob, knob), style = Stroke(stroke))
}

private fun DrawScope.drawTapeTrack(f: Float, fill: Color, ink: Color, paper: Color) {
    val h = 12.dp.toPx()
    val top = (size.height - h) / 2f
    val r = CornerRadius(h / 2)
    val x = size.width * f
    drawRoundRect(paper, Offset(0f, top), Size(size.width, h), r)
    clipRect(right = x) {
        drawRoundRect(fill, Offset(0f, top), Size(size.width, h), r)
        // Barber-pole stripes across the played part.
        var sx = -h
        while (sx < x + h) {
            drawLine(ink.copy(alpha = 0.18f), Offset(sx, top + h), Offset(sx + h, top), 2.dp.toPx())
            sx += 7.dp.toPx()
        }
    }
    drawRoundRect(ink, Offset(0f, top), Size(size.width, h), r, style = Stroke(2.dp.toPx()))
    val knob = 18.dp.toPx()
    val kx = (x - knob / 2).coerceIn(0f, size.width - knob)
    val ky = (size.height - knob) / 2f
    drawRoundRect(ink, Offset(kx + 2.dp.toPx(), ky + 2.dp.toPx()), Size(knob, knob), CornerRadius(5.dp.toPx()))
    drawRoundRect(paper, Offset(kx, ky), Size(knob, knob), CornerRadius(5.dp.toPx()))
    drawRoundRect(ink, Offset(kx, ky), Size(knob, knob), CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
}

/** Selectable chip in the theme's chip shape. 墨 uses [TextTabs] instead (see [ChoiceRow]). */
@Composable
fun ThemedChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val shape = style.chipShape
    val fill by animateFloatAsState(if (selected) 1f else 0f, style.motion.spec(), label = "chip")
    val onFill = if (style.button == ButtonKind.NeonRing) c.accent else c.onAccent
    if (style.button == ButtonKind.Brutal) {
        Box(
            modifier
                .pressable(onClick = onClick)
                .retroShadow(shape, c.outline, 3.dp)
                .background(lerp(c.surface, c.content, fill))
                .border(2.dp, c.outline, shape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(text, style = style.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = lerp(c.content, c.surface, fill))
        }
        return
    }
    Box(
        modifier
            .pressable(onClick = onClick)
            .then(if (style.button == ButtonKind.Retro && selected) Modifier.retroShadow(shape, c.outline, 2.dp) else Modifier)
            .clip(shape)
            .background(c.accent.copy(alpha = fill * (if (style.button == ButtonKind.NeonRing) 0.16f else 1f)))
            .border(1.dp, if (selected) c.accent else c.outline, shape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(text, style = style.type.body.copy(fontSize = 14.sp), color = lerp(c.content, onFill, fill))
    }
}

/** Text-only tabs with a sliding underline: the quiet alternative to chips. */
@Composable
fun TextTabs(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        options.forEach { option ->
            val active = option == selected
            val underline by animateFloatAsState(if (active) 1f else 0f, style.motion.spec(), label = "tabLine")
            Text(
                option,
                style = style.type.body,
                color = lerp(c.muted, c.content, underline),
                modifier = Modifier
                    .pressable { onSelect(option) }
                    .drawBehind {
                        val w = size.width * underline
                        val y = size.height + 4.dp.toPx()
                        drawLine(c.content, Offset((size.width - w) / 2, y), Offset((size.width + w) / 2, y), 1.5.dp.toPx())
                    }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** [TextTabs] in hairline themes, chips elsewhere. */
@Composable
fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (Lanyin.style.edge == EdgeKind.Hairline) {
        TextTabs(options, selected, onSelect, modifier)
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { ThemedChip(it, it == selected, { onSelect(it) }) }
        }
    }
}

/** Primary action in the theme's accent treatment; 墨 draws it as an outlined rectangle. */
@Composable
fun ThemedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    if (style.edge == EdgeKind.Brutal && !enabled) {
        // 格 prints a button that isn't ready yet as a hatched box, not a faded one.
        Box(
            modifier
                .drawBehind {
                    drawRect(c.bgDeep)
                    val gap = 9.dp.toPx()
                    val ink = c.content.copy(alpha = 0.16f)
                    clipRect {
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), 1.5.dp.toPx())
                            x += gap
                        }
                    }
                }
                .border(style.strokeWidth, c.outline)
                .padding(horizontal = 22.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = c.muted)
        }
        return
    }
    Box(
        modifier
            .then(
                if (hairline) {
                    Modifier.pressable(enabled = enabled, onClick = onClick).border(1.dp, c.content, style.chipShape)
                } else {
                    Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Accent, onClick = if (enabled) onClick else null)
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = style.type.body.copy(fontWeight = FontWeight.Medium),
            color = when {
                hairline -> c.content
                style.button == ButtonKind.NeonRing -> c.accent
                else -> c.onAccent
            },
        )
    }
}

/** An inline text action such as 「扫码登录 ›」. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Lanyin.colors.content) {
    val style = Lanyin.style
    Row(modifier.pressable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style.type.body.copy(fontWeight = FontWeight.Medium), color = color)
        ThemedIcon(LineIcon.Forward, null, tint = color, size = 16.dp, modifier = Modifier.padding(start = 2.dp))
    }
}

/** A switch drawn in the theme's language: hairline track and square knob in 墨, accent fill elsewhere. */
@Composable
fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val on by animateFloatAsState(if (checked) 1f else 0f, style.bouncy.spec(), label = "switch")
    val hairline = style.edge == EdgeKind.Hairline
    val square = style.chipShape == RectangleShape
    Canvas(modifier.size(44.dp, 26.dp).pressable { onChange(!checked) }) {
        val h = size.height
        val r = if (square) 0f else h / 2
        if (hairline) {
            drawRoundRect(c.content.copy(alpha = 0.3f + 0.7f * on), cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
        } else {
            drawRoundRect(lerp(c.content.copy(alpha = 0.14f), c.accent, on), cornerRadius = CornerRadius(r))
        }
        val inset = 4.dp.toPx()
        val knob = h - inset * 2
        val x = inset + (size.width - knob - inset * 2) * on
        val knobColor = if (hairline) lerp(c.content.copy(alpha = 0.3f), c.content, on) else lerp(c.surface, c.onAccent, on)
        if (square) drawRect(knobColor, Offset(x, inset), Size(knob, knob)) else drawCircle(knobColor, knob / 2, Offset(x + knob / 2, h / 2))
    }
}

/** Section heading: a small tracked label in quiet themes, a title elsewhere. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: String? = null, index: Int? = null) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        if (style.edge == EdgeKind.Brutal) {
            // 格 numbers its sections like an editorial index: 「01」 then a heavy headline.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index != null) {
                    IndexTag(index)
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 20.sp), color = c.content)
            }
        } else if (style.quietSections) {
            Text(title, style = style.type.label, color = c.muted)
        } else if (style.backdrop == io.lanyin.player.ui.theme.BackdropKind.Tide) {
            // 澜 marks its sections with a small swell.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(16.dp, 8.dp)) {
                    val path = Path().apply {
                        moveTo(0f, this@Canvas.size.height / 2f)
                        var x = 0f
                        while (x <= this@Canvas.size.width) {
                            lineTo(x, this@Canvas.size.height / 2f + this@Canvas.size.height * 0.35f * kotlin.math.sin(x / this@Canvas.size.width * 2f * PI.toFloat()))
                            x += 1f
                        }
                    }
                    drawPath(path, c.accent, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = style.type.title, color = c.content)
            }
        } else {
            Text(title, style = style.type.title, color = c.content)
        }
        if (trailing != null) {
            // The title keeps its room; a long trailing note gives way on one line.
            Spacer(Modifier.width(16.dp))
            Text(
                trailing,
                style = if (style.edge == EdgeKind.Brutal) style.type.caption.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold) else style.type.caption,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Page title in the theme's display face. */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val style = Lanyin.style
    androidx.compose.foundation.layout.Column(modifier) {
        Text(title, style = style.type.display, color = style.colors.content)
        if (subtitle != null) {
            Text(subtitle, style = style.type.caption, color = style.colors.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A 1dp rule in the theme's outline colour. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Canvas(modifier.fillMaxWidth().height(1.dp)) { drawRect(c.outline) }
}
