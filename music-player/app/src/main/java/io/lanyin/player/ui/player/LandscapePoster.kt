package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.floor

/**
 * 格: the stage as a Swiss poster. The artwork on the left; on the right a head in mono (the
 * track's number in a black block, the line's number, the tempo on a yellow label) over a heavy
 * rule, and the line being sung set big and black, filled a character at a time as it is sung,
 * with a red bar under it running to the end of the line. A new line doesn't fade in: a red block
 * wipes across and takes the old one with it. The poster's disc, bar and square keep to the
 * right, so the type stops short of them.
 */
@Composable
internal fun PosterStage(stage: StageContext) {
    val state = stage.state
    Row(Modifier.fillMaxSize()) {
        StageArtwork(stage, Modifier.weight(0.36f).fillMaxHeight().then(stage.dismissDrag))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(0.64f).fillMaxHeight().enter(stage.entrance, 1)) {
            Column(Modifier.weight(1f).fillMaxWidth(0.72f)) {
                PosterHead(stage)
                PosterLine(stage, Modifier.weight(1f).fillMaxWidth())
            }
            AnimatedVisibility(stage.chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                StageFooter(state, centered = false)
            }
        }
    }
}

@Composable
internal fun PosterHead(stage: StageContext) {
    val state = stage.state
    val c = Lanyin.colors
    val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(11f), letterSpacing = 0.08.em)
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NO.%02d".format(state.trackNumber), style = mono, color = c.bg, modifier = Modifier.background(c.outline).padding(horizontal = 7.dp, vertical = 2.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (lines.isEmpty()) "—" else "LINE %02d / %02d".format(index + 1, lines.size),
                style = mono, color = c.content, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f),
            )
            rememberBpm(state)?.let { bpm ->
                Text(
                    "♩ = $bpm",
                    style = mono.copy(fontWeight = FontWeight.Black),
                    color = c.outline,
                    modifier = Modifier.background(GridYellow).border(2.dp, c.outline).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(c.outline))
    }
}

@Composable
internal fun PosterLine(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val style = Lanyin.style
    val c = style.colors
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    // The line on the poster changes halfway through the wipe, under the block.
    var shown by remember { mutableIntStateOf(index) }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(index) {
        if (index == shown) return@LaunchedEffect
        wipe.snapTo(0f)
        wipe.animateTo(0.5f, tween(150, easing = LinearEasing))
        shown = index
        wipe.animateTo(1f, tween(170, easing = LinearEasing))
    }
    val line = lines.getOrNull(shown)
    val text = line?.text?.takeIf { it.isNotBlank() } ?: if (lines.isEmpty()) emptyLyricsNote(state) else "♪"
    val big = lyricTextStyle(LyricSize.BASE_SP * 1.85f, stage.lyricScale).let {
        it.copy(fontFamily = style.display, fontWeight = FontWeight.Black, lineHeight = it.fontSize * 1.14f, letterSpacing = (-0.01).em, color = c.content.copy(alpha = 0.2f))
    }
    val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(10f), letterSpacing = 0.1.em)
    // Live only for the line actually being sung: anything else stands either done or not begun.
    val progress = {
        when {
            lines.isEmpty() -> 1f
            stage.scrubMs != null || shown != state.currentLyricIndex -> if (shown < state.currentLyricIndex) 1f else 0f
            else -> state.lineProgress()
        }
    }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(top = 14.dp).drawWithContent {
                drawContent()
                val w = wipe.value
                if (w < 1f) {
                    // Grows from the left to cover the old line, then leaves to the right off the new.
                    val from = if (w < 0.5f) 0f else size.width * (w - 0.5f) * 2f
                    val to = if (w < 0.5f) size.width * w * 2f else size.width
                    drawRect(c.accent, Offset(from, 0f), Size(to - from, size.height))
                    drawRect(c.outline, Offset(from, 0f), Size(to - from, size.height), style = Stroke(2.dp.toPx()))
                }
            },
        ) {
            SungText(
                text, big,
                // A character at a time, as a printer would set it.
                progress = { val n = text.length.coerceAtLeast(1); floor(progress() * n) / n },
                paintSung = { drawText(it, c.content) },
                modifier = Modifier.padding(bottom = 18.dp),
                decorate = { l ->
                    // The bar under the line, filling smoothly with the singing.
                    val top = l.size.height + 8.dp.toPx()
                    val bar = Size(l.size.width.toFloat(), 7.dp.toPx())
                    drawRect(c.surface, Offset(0f, top), bar)
                    drawRect(c.accent, Offset(0f, top), Size(bar.width * progress(), bar.height))
                    drawRect(c.outline, Offset(0f, top), bar, style = Stroke(2.dp.toPx()))
                },
            )
            line?.translation?.let {
                Text(it, style = mono.copy(fontSize = fixedSp(12f), letterSpacing = 0.02.em), color = c.content.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            if (lines.size > 1) LineIndex(lines.size, shown, Modifier.padding(bottom = 10.dp).fillMaxWidth().height(10.dp))
            lines.getOrNull(shown + 1)?.text?.takeIf { it.isNotBlank() }?.let { next ->
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("NEXT →", style = mono, color = c.bg, modifier = Modifier.background(c.outline).padding(horizontal = 5.dp, vertical = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(next, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        PosterPauseStamp(state, Modifier.align(Alignment.TopEnd).padding(top = 2.dp))
    }
}

/**
 * The song's lines as a row of squares, like the index on a poster's edge: those sung filled in
 * black, the one being sung in red, those to come left open. A long song's squares narrow to bars.
 */
@Composable
internal fun LineIndex(count: Int, current: Int, modifier: Modifier) {
    val c = Lanyin.colors
    Canvas(modifier) {
        val cell = minOf(size.height + 4.dp.toPx(), size.width / count)
        val side = Size((cell - minOf(4.dp.toPx(), cell * 0.35f)).coerceAtLeast(1f), size.height)
        for (i in 0 until count) {
            val at = Offset(i * cell, 0f)
            when {
                i < current -> drawRect(c.outline, at, side)
                i == current -> {
                    drawRect(c.accent, at, side)
                    drawRect(c.outline, at, side, style = Stroke(1.5.dp.toPx()))
                }
                else -> drawRect(c.outline.copy(alpha = 0.5f), at, side, style = Stroke(1.dp.toPx()))
            }
        }
    }
}
