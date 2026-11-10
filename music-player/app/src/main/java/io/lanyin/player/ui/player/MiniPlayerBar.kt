package io.lanyin.player.ui.player

import io.lanyin.player.ui.design.drawKeycap
import io.lanyin.player.ui.design.drawGlassStrip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.keyColors
import io.lanyin.player.ui.design.drawPixelButton
import io.lanyin.player.ui.design.drawSprite
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.radioLcdStyle
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.drawTube
import io.lanyin.player.ui.design.drawGlass
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

/** Mini player: a floating pill, or a plain docked row in themes with a docked bottom bar. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onPaper: Boolean = false,
) {
    val style = Lanyin.style
    val c = style.colors
    val docked = style.dock == DockKind.Docked
    // 暮: a strip of paper taped across the edge of the mat.
    if (style.edge == io.lanyin.player.ui.theme.EdgeKind.Soft && !onPaper) {
        Box(modifier.fillMaxWidth().height(60.dp).padding(horizontal = style.margin - 6.dp, vertical = 7.dp)) {
            io.lanyin.player.ui.design.RisoSheet(
                Modifier.fillMaxSize(), tilt = -0.4f,
                tapes = listOf(io.lanyin.player.ui.design.TapeAt.Left, io.lanyin.player.ui.design.TapeAt.Right),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { MiniPlayer(state, skin, spin, onExpand, Modifier.fillMaxSize(), onPaper = true) }
        }
        return
    }
    // 霓: the car radio's LCD, between its VOL and TUNE knobs.
    if (style.edge == io.lanyin.player.ui.theme.EdgeKind.Glow && !onPaper) {
        val lcd = androidx.compose.runtime.remember(style) { style.radioLcdStyle() }
        Row(modifier.fillMaxWidth().height(60.dp).padding(horizontal = style.margin - 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(30.dp)) { drawRadioKnob(-40f) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val px = 3.dp.toPx()
                        drawRect(Color(0xFF05060A))
                        drawRect(lcd.colors.bg, Offset(px, px), Size(size.width - 2 * px, size.height - 2 * px))
                        var y = px * 2
                        while (y < size.height - px) {
                            drawRect(Color.Black.copy(alpha = 0.12f), Offset(px, y), Size(size.width - 2 * px, px * 0.4f))
                            y += px
                        }
                    }
                    .padding(3.dp),
            ) {
                CompositionLocalProvider(io.lanyin.player.ui.theme.LocalLanyinStyle provides lcd, LocalContentColor provides lcd.colors.content) {
                    MiniPlayer(state, skin, spin, onExpand, Modifier.fillMaxSize(), onPaper = true)
                }
            }
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.size(30.dp)) { drawRadioKnob(30f) }
        }
        return
    }
    // 游: a strip of the game: the cover in one bit, the song, two pixel keys.
    if (style.backdrop == BackdropKind.Game && !onPaper) {
        BitStrip(state, onExpand, modifier)
        return
    }
    // 机: the cassette deck's strip: the tape in its window, the display, the counter and two keys.
    if (style.backdrop == BackdropKind.Device && !onPaper) {
        DeckStrip(state, onExpand, modifier)
        return
    }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Row(
            modifier
                .fillMaxWidth()
                .then(if (onPaper) Modifier.fillMaxHeight() else Modifier.height(if (docked) 60.dp else 64.dp))
                .testTag("miniPlayer")
                .sharedPlayer("player-container", container = true)
                .then(
                    if (onPaper) {
                        Modifier
                            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
                            .padding(start = 8.dp, end = 0.dp)
                    } else if (docked) {
                        Modifier
                            .background(c.bg)
                            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
                            .padding(start = style.margin, end = style.margin - 12.dp)
                    } else {
                        Modifier
                            .themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome, onClick = onExpand)
                            .padding(start = 10.dp, end = 4.dp)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniArtwork(skin, spin, state.coverSeed, Modifier.size(if (onPaper) 34.dp else if (docked) 40.dp else 44.dp).sharedPlayer("artwork", zIndex = 1f), state.coverUrl)
            Spacer(Modifier.width(12.dp))
            // Swipe the titles sideways to change track, as on the full player's artwork.
            SwipeToSkip(state, Modifier.weight(1f)) { Column(Modifier.fillMaxWidth()) {
                Text(
                    state.title,
                    style = style.type.body.copy(fontFamily = style.display, fontWeight = if (docked) FontWeight.Medium else FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true),
                )
                AnimatedContent(
                    targetState = state.currentLine?.text ?: state.artist,
                    // Keep the outgoing line from sliding up over the title.
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically { it }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it })
                    },
                    label = "miniLyric",
                ) { line ->
                    Text(line, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } }
            MiniPlayButton(state)
            val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
            IconAction(LineIcon.Queue, "播放队列", { openSheet(io.lanyin.player.ui.music.Sheet.Queue) }, tint = c.muted)
        }
    }
}

/** Play/pause wrapped in how far the song has got, drawn in the theme's hand (see [drawMiniProgress]). */
@Composable
private fun MiniPlayButton(state: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    val morph by animateFloatAsState(if (state.isPlaying) 1f else 0f, style.bouncy.spec(), label = "miniMorph")
    Box(
        Modifier
            .size(48.dp)
            .pressable(onClick = state::togglePlay)
            .semantics { contentDescription = if (state.isPlaying) "暂停" else "播放" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val f = (state.positionMs.toFloat() / state.durationMs).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            drawMiniProgress(style, f, 0f)
        }
        Canvas(Modifier.size(13.dp)) { drawPlayPauseGlyph(morph, c.content) }
    }
}

/**
 * How far the song has got, round the mini player's play button. 墨: an ensō drawn with the
 * brush, thick where it starts and drying out. 霓: a lit tube bent round a cold glass ring. 暮:
 * an arc in the sunset's colours with the sun at its head. 格: a square key with a bar filling
 * along its foot. 点: a ring of
 * dots lighting one by one, the red one at the head. 机: a knob's scale of ticks. 报: a pie,
 * printed. 游: a pixel frame and a row of blocks. 酸: a ring of lime gel with its gloss.
 */
internal fun DrawScope.drawMiniProgress(style: LanyinStyle, f: Float, t: Float) {
    val c = style.colors
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    when (style.backdrop) {
        BackdropKind.Grid -> {
            val stroke = style.strokeWidth.toPx()
            val bar = 5.dp.toPx()
            drawRect(c.outline, Offset(3.dp.toPx(), 3.dp.toPx()), size)
            drawRect(c.surface)
            drawRect(c.accent, Offset(0f, size.height - bar), Size(size.width * f, bar))
            drawRect(c.outline, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        }
        BackdropKind.Ink -> {
            // The brush goes round once over the song: heavy at the start, thinning as it dries.
            drawCircle(c.content.copy(alpha = 0.08f), r - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
            val steps = (72 * f).toInt()
            for (i in 0 until steps) {
                val a0 = (-100f + i * 5f) * PI.toFloat() / 180f
                val a1 = (-100f + (i + 1) * 5f) * PI.toFloat() / 180f
                val k = i / 72f
                val rr = r - 2.5.dp.toPx() + sin(i * 0.7f) * 0.3.dp.toPx()
                drawLine(
                    c.content.copy(alpha = 0.85f - 0.35f * k),
                    center + Offset(cos(a0) * rr, sin(a0) * rr), center + Offset(cos(a1) * rr, sin(a1) * rr),
                    (3.4f - 2.2f * k).dp.toPx(), StrokeCap.Round,
                )
            }
        }
        BackdropKind.Neon -> {
            val inset = 3.dp.toPx()
            val rect = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            drawGlass(Path().apply { addOval(rect) }, 2.dp.toPx())
            if (f > 0f) drawTube(Path().apply { addArc(rect, -90f, 360f * f) }, c.accent, 2.dp.toPx())
        }
        // 暮: a pencil circle round the button, gone over in pink as the song goes.
        BackdropKind.Dusk -> {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            drawCircle(c.content.copy(alpha = 0.18f), r - inset, style = Stroke(1.dp.toPx()))
            drawArc(
                c.accent, -90f, 360f * f, false,
                Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        BackdropKind.Dot -> {
            val n = 24
            val lit = (f * n).toInt()
            for (k in 0 until n) {
                val a = (-90f + k * 360f / n) * PI.toFloat() / 180f
                val p = center + Offset(cos(a) * (r - 2.dp.toPx()), sin(a) * (r - 2.dp.toPx()))
                val color = when {
                    k == lit -> c.accentAlt
                    k < lit -> c.content
                    else -> c.content.copy(alpha = 0.15f)
                }
                drawCircle(color, 1.5.dp.toPx(), p)
            }
        }
        BackdropKind.Device -> {
            val n = 20
            for (k in 0 until n) {
                val a = (-90f + k * 360f / n) * PI.toFloat() / 180f
                val on = k < (f * n).toInt() + 1
                drawLine(
                    if (on) c.accent else c.content.copy(alpha = 0.18f),
                    center + Offset(cos(a) * (r - 4.dp.toPx()), sin(a) * (r - 4.dp.toPx())), center + Offset(cos(a) * r, sin(a) * r),
                    1.6.dp.toPx(), StrokeCap.Round,
                )
            }
        }
        BackdropKind.Paper -> {
            drawArc(c.content.copy(alpha = 0.14f), -90f, 360f * f, true, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()))
            drawCircle(c.content, r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        BackdropKind.Game -> {
            val px = 2.dp.toPx()
            drawRect(c.content, style = Stroke(px * 1.5f))
            val n = 6
            val gap = px
            val w = (size.width - px * 4 - gap * (n - 1)) / n
            for (k in 0 until (f * n).toInt().coerceAtMost(n)) drawRect(c.accent, Offset(px * 2 + k * (w + gap), size.height - px * 2 - px * 2), Size(w, px * 2))
        }
        BackdropKind.Acid -> {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            drawCircle(c.content.copy(alpha = 0.14f), r - inset, style = Stroke(stroke))
            drawArc(c.accent, -90f, 360f * f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.6f), -90f, 360f * f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke * 0.25f, cap = StrokeCap.Round))
        }
    }
}

/** 霓's radio knob in pixels: a dark ring of grip, a lighter cap, an amber line for where it's turned. */
private fun DrawScope.drawRadioKnob(angle: Float) {
    val px = 3.dp.toPx()
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color(0xFF05060A), r, c)
    drawCircle(Color(0xFF2B354B), r - px, c)
    for (k in 0 until 12) {
        val a = k * PI.toFloat() / 6f
        drawRect(Color(0xFF171D2B), c + Offset(cos(a) * (r - px * 1.5f) - px / 2f, sin(a) * (r - px * 1.5f) - px / 2f), Size(px, px))
    }
    drawCircle(Color(0xFF3A4660), r - px * 2.5f, c)
    drawRect(Color(0xFF6D7E9A), c + Offset(-px * 1.5f, -px * 2f), Size(px, px))
    val a = (angle - 90f) * PI.toFloat() / 180f
    for (k in 1..2) drawRect(Color(0xFFE89A3E), c + Offset(cos(a) * px * k * 1.2f - px / 2f, sin(a) * px * k * 1.2f - px / 2f), Size(px, px))
}

/**
 * 机's mini player: the deck's strip along the foot of the stack. The cassette turning in its
 * smoked window, the display with the song and singer, the counter, and keys to pause and skip.
 */
@Composable
private fun DeckStrip(state: PlayerState, onExpand: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val seconds by remember(state) { androidx.compose.runtime.derivedStateOf { (state.positionMs / 1000).toInt() } }
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("miniPlayer")
            .sharedPlayer("player-container", container = true)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
            .padding(horizontal = style.margin - 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(74.dp)
                .fillMaxHeight()
                .drawBehind { drawGlassStrip(lamps = 0.3f) }
                .padding(horizontal = 5.dp, vertical = 4.dp),
        ) {
            io.lanyin.player.ui.design.Cassette(state.title, state.artist, { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) }, state.isPlaying, Modifier.fillMaxSize().sharedPlayer("artwork", zIndex = 1f))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind { drawGlassStrip(lamps = 0f) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vfd = androidx.compose.ui.text.TextStyle(
                fontFamily = io.lanyin.player.ui.theme.PixelFamily, color = io.lanyin.player.ui.design.Hifi.Vfd,
                shadow = androidx.compose.ui.graphics.Shadow(io.lanyin.player.ui.design.Hifi.Vfd.copy(alpha = 0.6f), Offset.Zero, 8f),
            )
            SwipeToSkip(state, Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(state.title, style = vfd.copy(fontSize = fixedSp(14f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true))
                    Text("${state.artist} · A面", style = vfd.copy(fontSize = fixedSp(10.5f), color = io.lanyin.player.ui.design.Hifi.Vfd.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                io.lanyin.player.ui.design.SevenSeg("%04d".format(seconds % 10000), 16.dp)
                Text(if (state.isPlaying) "PLAY ▶" else "PAUSE ❚❚", style = androidx.compose.ui.text.TextStyle(fontFamily = io.lanyin.player.ui.theme.SpaceMonoFamily, fontSize = fixedSp(7f), fontWeight = FontWeight.Bold), color = io.lanyin.player.ui.design.Hifi.Vfd, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        DeckKey(if (state.isPlaying) "暂停" else "播放", state::togglePlay) {
            val morph = if (state.isPlaying) 1f else 0f
            Canvas(Modifier.size(12.dp)) { drawPlayPauseGlyph(morph, c.content) }
        }
        Spacer(Modifier.width(5.dp))
        DeckKey("下一首", state::skipNext) {
            io.lanyin.player.ui.design.ThemedIcon(LineIcon.Next, null, tint = c.content, size = 16.dp)
        }
    }
}

/** A square aluminium key on the deck's strip. */
@Composable
private fun DeckKey(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val style = Lanyin.style
    Box(
        Modifier
            .size(36.dp, 40.dp)
            .pressable(label = label, onClick = onClick)
            .drawBehind { drawKeycap(androidx.compose.foundation.shape.RoundedCornerShape(3.dp), style.keyColors()) },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/**
 * 游's mini player: the cover in one bit in an ink frame, the song and what's being sung in the
 * pixel face, and the play and skip keys; the progress as a line of dots along its foot.
 */
@Composable
private fun BitStrip(state: PlayerState, onExpand: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    val px = io.lanyin.player.ui.design.BitPx
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .testTag("miniPlayer")
            .sharedPlayer("player-container", container = true)
            .background(c.bg)
            .drawBehind {
                // The progress: solid behind, dotted ahead, a marker where the song is.
                val p = px.toPx()
                val f = (state.positionMs.toFloat() / state.durationMs).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
                val y = size.height - 2 * p
                val x = ((size.width * f) / p).toInt() * p
                var dx = 0f
                while (dx < size.width) {
                    if (dx < x || (dx / p).toInt() % 2 == 0) drawRect(c.content, Offset(dx, y), Size(p, p))
                    dx += p
                }
                drawRect(c.content, Offset(x - p, y - p), Size(3 * p, 3 * p))
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
            .padding(horizontal = Lanyin.style.margin - 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(c.content).padding(px).sharedPlayer("artwork", zIndex = 1f)) {
            io.lanyin.player.ui.design.DitheredCover(state.coverUrl, state.coverSeed, Modifier.fillMaxSize(), n = 32)
        }
        Spacer(Modifier.width(10.dp))
        SwipeToSkip(state, Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth()) {
                val pixel = androidx.compose.ui.text.TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily)
                Text(state.title, style = pixel.copy(fontSize = fixedSp(15f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true))
                Text(state.currentLine?.text ?: state.artist, style = pixel.copy(fontSize = fixedSp(12f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        BitMiniKey(if (state.isPlaying) io.lanyin.player.ui.design.Sprites.Pause else io.lanyin.player.ui.design.Sprites.Play, if (state.isPlaying) "暂停" else "播放", on = true, onClick = state::togglePlay)
        Spacer(Modifier.width(6.dp))
        BitMiniKey(io.lanyin.player.ui.design.Sprites.Next, "下一首", on = false, onClick = state::skipNext)
    }
}

/** A small pixel key with [glyph] on it. */
@Composable
private fun BitMiniKey(glyph: List<String>, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Lanyin.colors
    Canvas(Modifier.size(40.dp, 36.dp).pressable(label = label, onClick = onClick)) {
        val p = io.lanyin.player.ui.design.BitPx.toPx()
        drawPixelButton(c.content, c.bg, on, p)
        val w = glyph.maxOf { it.length }
        val h = glyph.size
        drawSprite(glyph, ((size.width - w * p) / 2f / p).toInt() * p, ((size.height - 2 * p - h * p) / 2f / p).toInt() * p, p, if (on) c.bg else c.content)
    }
}
