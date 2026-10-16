package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import kotlin.math.floor

/**
 * 墨: the lyrics written down the page in columns and read right to left, as on a handscroll. The
 * sheet is ruled in faint red lanes (朱丝栏), a lane to each column and a narrow one for the
 * translation beside it. The line being sung is written character by character as it is sung,
 * the ink spreading under the brush, a wash following down the lane; when the line is done the
 * scroll moves on a lane, the lines already sung passing off to the right. The title is
 * inscribed at the right edge with its seal, and a second seal gives the tempo in beats.
 */
@Composable
internal fun ScrollStage(stage: StageContext) {
    val state = stage.state
    Row(Modifier.fillMaxSize()) {
        StageArtwork(stage, Modifier.weight(0.36f).fillMaxHeight().then(stage.dismissDrag))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(0.64f).fillMaxHeight().enter(stage.entrance, 1)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // As many characters of the title as its column has room for above the tempo seal.
                val row = with(LocalDensity.current) { 25.sp.toDp() }
                val titleRuns = ((maxHeight - 84.dp) / row).toInt().coerceIn(2, 9)
                Row(Modifier.fillMaxSize()) {
                    ScrollLyrics(stage, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End) {
                        Inscription(state, Modifier.padding(top = 4.dp), titleRuns = titleRuns, artistRuns = (titleRuns - 3).coerceAtLeast(1))
                        Spacer(Modifier.weight(1f))
                        TempoSeal(state)
                    }
                }
            }
            AnimatedVisibility(stage.chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                StageFooter(state, centered = false)
            }
        }
    }
}

/** 闲章: the tempo in a red-lined seal, its figures in Chinese, read downward: 九六拍. */
@Composable
private fun TempoSeal(state: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    // Holds the last tempo heard through a pause rather than vanishing.
    var held by remember { mutableStateOf<Int?>(null) }
    rememberBpm(state)?.let { held = it }
    val bpm = held ?: return
    val figures = bpm.toString().map { "〇一二三四五六七八九"[it - '0'] }.joinToString("") + "拍"
    Column(
        Modifier.border(1.5.dp, c.accentAlt.copy(alpha = 0.85f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-2).dp),
    ) {
        for (ch in figures) {
            Text(
                ch.toString(),
                style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Bold, fontSize = fixedSp(12f), lineHeight = fixedSp(15f)),
                color = c.accentAlt.copy(alpha = 0.9f),
            )
        }
    }
}

/** A run of a line set for the column: one character upright, or a Latin word to turn sideways. */
private class Glyph(val layout: TextLayoutResult, val upright: Boolean, val chars: Int)

private fun measureRuns(measurer: TextMeasurer, text: String, style: TextStyle): List<Glyph> =
    verticalRuns(text, Int.MAX_VALUE).map { (run, upright) -> Glyph(measurer.measure(run, style, softWrap = false), upright, run.length) }

/** [glyphs] packed into columns no taller than [height]: the glyph indices in each. */
private fun pack(glyphs: List<Glyph>, cell: Float, height: Float): List<IntRange> {
    if (glyphs.isEmpty()) return emptyList()
    val columns = mutableListOf<IntRange>()
    var start = 0
    var used = 0f
    for (i in glyphs.indices) {
        val e = extent(glyphs[i], cell)
        if (i > start && used + e > height) {
            columns += start until i
            start = i
            used = 0f
        }
        used += e
    }
    columns += start until glyphs.size
    return columns
}

/** How far down the column a glyph runs: a cell upright, its length (and a little air) sideways. */
private fun extent(g: Glyph, cell: Float): Float = if (g.upright) cell else g.layout.size.width + cell * 0.3f

@Composable
private fun ScrollLyrics(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val style = Lanyin.style
    val c = style.colors
    val empty = state.lyrics.isEmpty()
    val note = if (empty) emptyLyricsNote(state) else ""
    // With nothing to sing, the note stands in as the one line, written out in full.
    val lines = remember(state.lyrics, note) { if (empty) listOf(LyricLine(note, null, 0, Long.MAX_VALUE)) else state.lyrics }
    val index = if (empty) 0 else stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    val shown by animateFloatAsState(index.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "scroll")
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val mainSp = lyricTextStyle(LyricSize.BASE_SP * 1.08f, stage.lyricScale).fontSize
    val mainStyle = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Medium, fontSize = mainSp)
    val noteStyle = TextStyle(fontFamily = style.display, fontSize = mainSp * 0.56f)
    // Measured as they come into view, kept for the song.
    val mains = remember(lines, mainStyle) { HashMap<Int, List<Glyph>>() }
    val notes = remember(lines, noteStyle) { HashMap<Int, List<Glyph>>() }
    val retry = if (empty && state.lyricsGap == LyricsGap.Failed && !state.lyricsLoading) Modifier.pressable(onClick = state::retryLyrics) else Modifier
    Canvas(modifier.then(retry)) {
        val mainPx = mainSp.toPx()
        val notePx = noteStyle.fontSize.toPx()
        val cell = mainPx * 1.14f
        val noteCell = notePx * 1.14f
        val pitch = mainPx * 1.75f
        val notePitch = notePx * 1.6f
        val pad = 8.dp.toPx()
        val room = size.height - pad * 2
        fun mainOf(i: Int) = mains.getOrPut(i) { measureRuns(measurer, lines[i].text, mainStyle) }
        fun noteOf(i: Int) = notes.getOrPut(i) { lines[i].translation?.let { measureRuns(measurer, it, noteStyle) } ?: emptyList() }
        fun widthOf(i: Int) = pack(mainOf(i), cell, room).size.coerceAtLeast(1) * pitch + pack(noteOf(i), noteCell, room).size * notePitch

        // Where each line's right edge falls with line [anchor] in the writing position, two lanes
        // in from the right so the lines just sung stay in view.
        val anchorRight = size.width - pitch * 2.2f
        val base = floor(shown).toInt().coerceIn(0, lines.lastIndex)
        val lo = (base - 4).coerceAtLeast(0)
        val hi = (base + 14).coerceAtMost(lines.lastIndex)
        val widths = FloatArray(hi - lo + 1) { widthOf(lo + it) }
        fun rightOf(i: Int, anchor: Int): Float {
            var x = anchorRight
            if (i > anchor) for (k in anchor until i) x -= widths[k - lo] else for (k in i until anchor) x += widths[k - lo]
            return x
        }
        val next = (base + 1).coerceAtMost(hi)
        val f = shown - base

        val fade = 28.dp.toPx()
        fun edgeAlpha(x: Float) = ((x / fade).coerceIn(0f, 1f)) * (((size.width - x) / fade).coerceIn(0f, 1f))
        val rule = c.accentAlt.copy(alpha = if (style.dark) 0.16f else 0.13f)
        drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())

        val progress = if (empty) 1f else if (stage.scrubMs != null) 0f else state.lineProgress()
        for (i in lo..hi) {
            val right = rightOf(i, base) + (rightOf(i, next) - rightOf(i, base)) * f
            val width = widths[i - lo]
            if (right - width > size.width || right < 0f) continue
            val mainCols = pack(mainOf(i), cell, room)
            val noteCols = pack(noteOf(i), noteCell, room)
            // The lanes: a rule at each column's edge, the translation's lanes narrower.
            var edge = right
            drawLane(rule, edge, edgeAlpha(edge))
            repeat(mainCols.size.coerceAtLeast(1)) { edge -= pitch; drawLane(rule, edge, edgeAlpha(edge)) }
            repeat(noteCols.size) { edge -= notePitch; drawLane(rule, edge, edgeAlpha(edge)) }

            val current = i == index
            val ink = when {
                current -> c.content
                i < index -> c.content.copy(alpha = 0.5f * ((3.5f + i - index) / 3f).coerceIn(0f, 1f))
                else -> c.content.copy(alpha = if (style.dark) 0.34f else 0.3f)
            }
            val glyphs = mainOf(i)
            val total = glyphs.sumOf { it.chars }.coerceAtLeast(1)
            var sung = if (current) progress * total else 0f
            mainCols.forEachIndexed { col, range ->
                val cx = right - pitch * (col + 0.5f)
                val a = edgeAlpha(cx)
                if (a <= 0f) {
                    for (g in range) sung -= glyphs[g].chars
                    return@forEachIndexed
                }
                // How far down the lane the brush has been, for the wash behind the writing.
                var washTo = 0f
                var y = pad
                for (g in range) {
                    val glyph = glyphs[g]
                    val e = extent(glyph, cell)
                    if (current) {
                        val done = (sung / glyph.chars).coerceIn(0f, 1f)
                        if (done > 0f) washTo = y + e * done
                        if (done > 0f && done < 1f && !empty) {
                            // The brush on the paper: ink spreading round the character being written.
                            val r = cell * (0.5f + 0.4f * done)
                            drawCircle(Brush.radialGradient(listOf(c.content.copy(alpha = 0.16f * a), Color.Transparent), Offset(cx, y + e / 2f), r), r, Offset(cx, y + e / 2f))
                        }
                        val sungInk = c.content.copy(alpha = a)
                        val wet = c.content.copy(alpha = (if (style.dark) 0.3f else 0.26f) * a)
                        drawGlyph(glyph, cx, y, e, if (done >= 1f) sungInk else wet)
                        // Written partway: the strokes so far in full ink.
                        if (done > 0f && done < 1f) clipRect(top = y, bottom = y + e * done) { drawGlyph(glyph, cx, y, e, sungInk) }
                        sung -= glyph.chars
                    } else {
                        drawGlyph(glyph, cx, y, e, ink.copy(alpha = ink.alpha * a))
                    }
                    y += e
                }
                if (washTo > 0f && !empty) {
                    val top = pad - 4.dp.toPx()
                    drawRoundRect(c.content.copy(alpha = 0.045f * a), Offset(cx - pitch * 0.42f, top), Size(pitch * 0.84f, washTo - top + 4.dp.toPx()), CornerRadius(pitch * 0.3f))
                }
            }
            val noteInk = c.muted.copy(alpha = if (current) 0.9f else if (i < index) ink.alpha else 0.45f)
            val notes = noteOf(i)
            noteCols.forEachIndexed { col, range ->
                val cx = right - pitch * mainCols.size.coerceAtLeast(1) - notePitch * (col + 0.5f)
                val a = edgeAlpha(cx)
                var y = pad
                for (g in range) {
                    val e = extent(notes[g], noteCell)
                    drawGlyph(notes[g], cx, y, e, noteInk.copy(alpha = noteInk.alpha * a))
                    y += e
                }
            }
        }
    }
}

private fun DrawScope.drawLane(rule: Color, x: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawLine(rule.copy(alpha = rule.alpha * alpha), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
}

/** A glyph in its column, centred on [cx] in the [extent] from [top]: upright, or turned to read downward. */
private fun DrawScope.drawGlyph(g: Glyph, cx: Float, top: Float, extent: Float, color: Color) {
    val w = g.layout.size.width.toFloat()
    val h = g.layout.size.height.toFloat()
    val mid = top + extent / 2f
    if (g.upright) {
        drawText(g.layout, color, Offset(cx - w / 2f, mid - h / 2f))
    } else {
        rotate(90f, Offset(cx, mid)) { drawText(g.layout, color, Offset(cx - w / 2f, mid - h / 2f)) }
    }
}
