package io.lanyin.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.design.InkField
import io.lanyin.player.ui.design.LikeButton
import io.lanyin.player.ui.design.Overprint
import io.lanyin.player.ui.design.Pencil
import io.lanyin.player.ui.design.PencilNote
import io.lanyin.player.ui.design.PrintFrame
import io.lanyin.player.ui.design.Riso
import io.lanyin.player.ui.design.RisoScenes
import io.lanyin.player.ui.design.RisoScreen
import io.lanyin.player.ui.design.RisoSheet
import io.lanyin.player.ui.design.TapeAt
import io.lanyin.player.ui.design.pencilStyle
import io.lanyin.player.ui.design.rememberCoverPlates
import io.lanyin.player.ui.design.rememberPrint
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * 暮's portrait stage: the song as a print taped to the mat, then a slip of paper with its name,
 * the line being sung and the next, a pencil across its corner. Tap either for the full lyrics;
 * swipe the print to change track.
 */
@Composable
internal fun RisoPortraitStage(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SwipeToSkip(stage.state, Modifier.weight(1f).fillMaxWidth().then(stage.dismissDrag)) {
            SongPrint(stage, Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp), onShowLyrics)
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).enter(stage.entrance, 2)) {
            TitleSlip(stage.state, Modifier.fillMaxWidth().padding(end = 14.dp), onShowLyrics)
            Pencil(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 18.dp).size(176.dp, 46.dp), angle = -13f)
        }
    }
}

/** 暮 in landscape: the print wide on the left, the slip beside it. */
@Composable
internal fun RisoWideStage(stage: StageContext) {
    Row(Modifier.fillMaxSize().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SwipeToSkip(stage.state, Modifier.weight(1.5f).fillMaxHeight().then(stage.dismissDrag)) {
            SongPrint(stage, Modifier.fillMaxSize().padding(4.dp), onShowLyrics = null)
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f).enter(stage.entrance, 2)) {
            TitleSlip(stage.state, Modifier.fillMaxWidth(), onShowLyrics = null, shared = false)
        }
    }
}

/**
 * The print of the song: an evening sky and three lines of hills, the cover printed over the sky
 * as the sun, moving down a pencilled arc as the song plays and sinking behind the hills as it
 * ends; a loupe set on the print shows the dots. The margin has the song's name and number on
 * it in pencil.
 */
@Composable
private fun SongPrint(stage: StageContext, modifier: Modifier, onShowLyrics: (() -> Unit)?) {
    val state = stage.state
    PrintFrame(
        modifier,
        tilt = -0.6f,
        margin = 11.dp,
        onClick = onShowLyrics,
        label = if (onShowLyrics != null) "打开歌词" else null,
        fill = true,
        picture = { PrintPicture(state, Modifier.fillMaxSize()) },
        caption = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PencilNote("${state.title} · 第 ${state.trackNumber} 号", size = 16.sp, modifier = Modifier.weight(1f))
                PencilNote("${state.trackNumber}/${state.trackCount}", size = 18.sp)
            }
        },
    )
}

/** Where the sun is over the song: along an arc from high on the left down behind the hills. */
private class SunPath(val w: Int, val h: Int, val seed: Int, val d: Int) {
    /** Everything above the hills: the sun is clipped to it, so it goes down behind them. */
    val sky: Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w.toFloat(), 0f)
        var x = w.toFloat()
        while (x > 0f) {
            lineTo(x, RisoScenes.ridge(x, w, h, seed))
            x -= 4f
        }
        lineTo(0f, RisoScenes.ridge(0f, w, h, seed))
        close()
    }

    fun at(p: Float): Offset {
        val x = w * (0.3f + 0.36f * p)
        val start = min(h * 0.32f, d * 0.62f)
        val end = RisoScenes.ridge(x, w, h, seed) + d * 0.56f
        return Offset(x, start + (end - start) * p.pow(1.3f))
    }
}

@Composable
private fun PrintPicture(state: PlayerState, modifier: Modifier) {
    val plates = rememberCoverPlates(state.coverUrl, state.coverSeed) ?: return
    val seed = state.coverSeed
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0) return@BoxWithConstraints
        val density = LocalDensity.current
        val cell = with(density) { 2.4.dp.toPx() }
        val hills = rememberPrint(listOf("hills", seed, cell), w, h) { pw, ph -> RisoScreen.print(pw, ph, cell, RisoScenes.hills(pw, ph, seed)) }
        val d = (min(w, h) * 0.46f).toInt()
        val pid = System.identityHashCode(plates)
        val sun = rememberPrint(listOf("sun", pid, cell), d, d) { pw, _ ->
            val r = pw / 2f
            RisoScreen.print(pw, pw, cell, plates.field(pw, pw, warm = 0.22f), paper = false, inside = { x, y -> ((r - hypot(x - r, y - r)) / 1.5f).coerceIn(0f, 1f) })
        }
        val path = remember(w, h, seed, d) { SunPath(w, h, seed, d) }
        val measurer = rememberTextMeasurer()
        val pencil = pencilStyle(13.sp)
        val total = formatTimeShort(state.durationMs)
        Canvas(Modifier.fillMaxSize()) {
            if (hills != null) drawImage(hills) else drawRect(Riso.Paper)
            // The sun's way, pencilled in dots, with the times at either end.
            for (k in 0..46) drawCircle(Riso.Graphite.copy(alpha = 0.8f), 1.35.dp.toPx(), path.at(k / 46f))
            val start = path.at(0f)
            drawText(measurer, "0:00", Offset((start.x - d * 0.62f - 16.dp.toPx()).coerceAtLeast(4.dp.toPx()), (start.y - 10.dp.toPx()).coerceAtLeast(2.dp.toPx())), pencil)
            val p = (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            val c = path.at(p)
            if (sun != null) clipPath(path.sky) { drawImage(sun, Offset(c.x - d / 2f, c.y - d / 2f), blendMode = BlendMode.Multiply) }
            val end = path.at(1f)
            drawText(measurer, total, Offset(end.x + 6.dp.toPx(), RisoScenes.ridge(end.x, w, h, seed) - 22.dp.toPx()), pencil)
        }
        Loupe(state, plates, path, w, h, d, cell, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 12.dp))
    }
}

/** "3:44" for a duration. */
private fun formatTimeShort(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * A loupe lying on the print, three times over: the dots of the three inks as they really sit,
 * redrawn as the sun passes under it.
 */
@Composable
private fun Loupe(state: PlayerState, plates: RisoScreen.Plates, path: SunPath, w: Int, h: Int, d: Int, cell: Float, modifier: Modifier) {
    val side = 92.dp
    val zoom = 3f
    val sidePx = with(LocalDensity.current) { side.roundToPx() }
    val pad = with(LocalDensity.current) { Offset(8.dp.toPx(), 12.dp.toPx()) }
    val step by remember(state) { derivedStateOf { (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1) * 48f).toInt().coerceIn(0, 48) } }
    val seed = path.seed
    val lcx = w - pad.x - sidePx / 2f
    val lcy = h - pad.y - sidePx / 2f
    val image = rememberPrint(listOf("loupe", System.identityHashCode(plates), seed, step, cell, w, h), sidePx, sidePx) { pw, _ ->
        val base = RisoScenes.hills(w, h, seed)
        val sunField = plates.field(d, d, 0.22f)
        val sc = path.at(step / 48f)
        val tmp = FloatArray(3)
        val r2 = (d / 2f) * (d / 2f)
        val field = InkField { x, y, out ->
            val px = lcx + (x - pw / 2f) / zoom
            val py = lcy + (y - pw / 2f) / zoom
            base.sample(px, py, out)
            val dx = px - sc.x
            val dy = py - sc.y
            if (dx * dx + dy * dy < r2 && py < RisoScenes.ridge(px, w, h, seed)) {
                sunField.sample(dx + d / 2f, dy + d / 2f, tmp)
                for (k in 0 until 3) out[k] = 1f - (1f - out[k]) * (1f - tmp[k])
            }
        }
        RisoScreen.print(pw, pw, cell * zoom, field, misregister = cell * zoom * 0.32f)
    }
    Canvas(modifier.size(side)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = 0.35f), r + 5.dp.toPx(), c + Offset(3.dp.toPx(), 6.dp.toPx()))
        clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }) {
            if (image != null) drawImage(image) else drawRect(Riso.Paper)
            // The glass: a highlight up to the left, darker at its rim.
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), c + Offset(-r * 0.4f, -r * 0.45f), r * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), c, r))
        }
        drawCircle(Color(0xFF2A2A2C), r + 2.5.dp.toPx(), c, style = Stroke(5.dp.toPx()))
        drawCircle(Color(0xFF8D9196), r + 5.5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
    }
}

/**
 * A slip of paper with the song's name overprinted, the artist and album in italic blue, the
 * line being sung in ink and the next one faint; the heart to keep it.
 */
@Composable
private fun TitleSlip(state: PlayerState, modifier: Modifier, onShowLyrics: (() -> Unit)?, shared: Boolean = true) {
    RisoSheet(
        modifier, tilt = 0.9f, tapes = listOf(TapeAt.TopLeft), onClick = onShowLyrics, label = if (onShowLyrics != null) "打开歌词" else null,
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
    ) {
        val c = Lanyin.colors
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // In landscape the top bar carries the shared title.
                TrackSwitch(state, if (shared) Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true) else Modifier) { track ->
                    Overprint(track.title, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp))
                }
                TrackSwitch(state, Modifier.padding(top = 2.dp)) { track ->
                    Text(
                        "${track.artist} — 《${track.album}》",
                        style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
                        color = c.accentAlt, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val now = state.currentLine?.text ?: emptyLyricsNote(state)
                val next = state.lyrics.getOrNull(state.currentLyricIndex + 1)?.text
                Text(
                    now, style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 21.sp),
                    color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                )
                if (next != null) Text(next, style = Lanyin.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LikeButton(state.liked, onToggle = state::toggleLike)
        }
    }
}
