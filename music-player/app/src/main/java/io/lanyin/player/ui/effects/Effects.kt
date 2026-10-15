package io.lanyin.player.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.audio.EQ_FREQUENCIES
import io.lanyin.player.core.audio.EQ_RANGE_DB
import io.lanyin.player.core.audio.EffectPreset
import io.lanyin.player.core.audio.EffectsSettings
import io.lanyin.player.core.model.Quality
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.ThemedSlider
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/**
 * 音效: a preset, the ten-band curve (drag any band; a preset's curve becomes your own), and
 * how much bass, width and room. Changes are heard at once.
 */
@Composable
fun EffectsSheet(fx: EffectsSettings, onChange: (EffectsSettings) -> Unit, onDone: () -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("音效", style = style.type.title)
            Spacer(Modifier.width(10.dp))
            Text(
                if (fx.enabled) "耳机和外放都有效" else "原声：不做任何处理",
                style = style.type.caption, color = c.muted, modifier = Modifier.weight(1f),
            )
        }
        // Presets, three to a row.
        EffectPreset.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    ThemedChip(p.label, fx.preset == p, { onChange(fx.choose(p)); onDone() }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        EqualizerPanel(
            fx.curve, enabled = fx.enabled,
            onBand = { i, db -> onChange(fx.withBand(i, db)) },
            onDone = onDone,
            modifier = Modifier.padding(top = 22.dp),
        )
        AmountRow("低音增强", fx.bass, fx.enabled, { onChange(fx.copy(bass = it)) }, onDone)
        AmountRow("环绕", fx.width, fx.enabled, { onChange(fx.copy(width = it)) }, onDone)
        AmountRow("混响", fx.reverb, fx.enabled, { onChange(fx.copy(reverb = it)) }, onDone)
        Text(
            "音效在澜音里处理，不改动下载的文件。听无损时，选「原声」最接近原样。",
            style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun AmountRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style.type.body, color = if (enabled) c.content else c.muted, modifier = Modifier.width(76.dp))
        ThemedSlider(value, onChange, Modifier.weight(1f), enabled = enabled, onDone = onDone)
        Text(
            "${(value * 100).toInt()}%", style = style.type.caption.copy(fontFamily = style.numeric), color = c.muted,
            textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
        )
    }
}

/**
 * The curve across ten bands: a grid with the 0 dB line, the curve through the bands' points,
 * and a knob on each. Drag up or down anywhere in a band's column to set it.
 */
@Composable
fun EqualizerPanel(curve: FloatArray, enabled: Boolean, onBand: (Int, Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val bands = EQ_FREQUENCIES.size
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(enabled, curve.contentHashCode()) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val col = ((down.position.x / size.width) * bands).toInt().coerceIn(0, bands - 1)
                        fun dbAt(y: Float) = ((0.5f - y / size.height) * 2f * EQ_RANGE_DB * 1.15f).coerceIn(-EQ_RANGE_DB, EQ_RANGE_DB)
                        onBand(col, dbAt(down.position.y))
                        drag(down.id) { change ->
                            change.consume()
                            onBand(col, dbAt(change.position.y))
                        }
                        onDone()
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val col = w / bands
            val alpha = if (enabled) 1f else 0.35f
            fun x(i: Int) = col * (i + 0.5f)
            fun y(db: Float) = h / 2f - db / (EQ_RANGE_DB * 1.15f) * (h / 2f)
            val grid = c.content.copy(alpha = 0.08f)
            for (i in 0 until bands) drawLine(grid, Offset(x(i), y(EQ_RANGE_DB)), Offset(x(i), y(-EQ_RANGE_DB)), 1.dp.toPx())
            for (db in listOf(EQ_RANGE_DB, -EQ_RANGE_DB)) drawLine(grid, Offset(0f, y(db)), Offset(w, y(db)), 1.dp.toPx())
            drawLine(c.content.copy(alpha = 0.22f), Offset(0f, y(0f)), Offset(w, y(0f)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            // A smooth curve through the points (Catmull-Rom as cubic Béziers).
            val pts = List(bands) { Offset(x(it), y(curve[it])) }
            val line = Path().apply {
                moveTo(0f, pts[0].y)
                lineTo(pts[0].x, pts[0].y)
                for (i in 0 until bands - 1) {
                    val p0 = pts[maxOf(i - 1, 0)]
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts[minOf(i + 2, bands - 1)]
                    cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                }
                lineTo(w, pts.last().y)
            }
            val ink = (if (style.edge == EdgeKind.Hairline) c.content else c.accent).copy(alpha = alpha)
            val fill = Path().apply {
                addPath(line)
                lineTo(w, y(0f))
                lineTo(0f, y(0f))
                close()
            }
            if (style.edge != EdgeKind.Hairline) drawPath(fill, Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f * alpha), ink.copy(alpha = 0.02f))))
            if (style.edge == EdgeKind.Glow) drawPath(line, ink.copy(alpha = 0.3f * alpha), style = Stroke(7.dp.toPx()))
            drawPath(line, ink, style = Stroke((if (style.edge == EdgeKind.Brutal) 3.dp else 2.dp).toPx()))
            val k = 6.dp.toPx()
            for (p in pts) {
                if (style.edge == EdgeKind.Brutal) {
                    drawRect(c.outline, Offset(p.x - k, p.y - k), Size(k * 2, k * 2))
                    drawRect(io.lanyin.player.ui.theme.GridYellow.copy(alpha = alpha), Offset(p.x - k + 2.dp.toPx(), p.y - k + 2.dp.toPx()), Size(k * 2 - 4.dp.toPx(), k * 2 - 4.dp.toPx()))
                } else {
                    drawCircle(c.bg, k, p)
                    drawCircle(ink, k, p, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            EQ_FREQUENCIES.forEach { hz ->
                Text(
                    if (hz >= 1_000) "${(hz / 1_000).toInt()}k" else "${hz.toInt()}",
                    style = style.type.label.copy(fontSize = 10.sp, letterSpacing = 0.sp, fontFamily = style.numeric),
                    color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** What each quality means, in words a listener can choose by. */
private fun Quality.blurb(): String = when (this) {
    Quality.STANDARD -> "128k · 省流量"
    Quality.EXHIGH -> "320k · MP3"
    Quality.LOSSLESS -> "FLAC · 最高 48kHz/16bit"
    Quality.HIRES -> "FLAC · 最高 192kHz/24bit · VIP"
    Quality.MASTER -> "母带级 · SVIP"
}

/** Pick the stream quality; the song playing reloads at it from where it was. */
@Composable
fun QualitySheet(selected: Quality, playing: String, onPick: (Quality) -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("音质", style = style.type.title)
            Spacer(Modifier.width(10.dp))
            if (playing.isNotEmpty()) Text("正在播放 $playing", style = style.type.caption, color = c.muted, maxLines = 1, modifier = Modifier.weight(1f))
        }
        Quality.entries.reversed().forEach { q ->
            val on = q == selected
            Row(
                Modifier.fillMaxWidth().pressable { onPick(q) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(q.label, style = style.type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = if (on) c.accent else c.content)
                    Text(q.blurb(), style = style.type.caption, color = c.muted)
                }
                if (on) ThemedIcon(LineIcon.Check, "已选", tint = c.accent, size = 20.dp)
            }
        }
        Text(
            "没有这个音质的歌会自动降一档。切换后，正在放的歌从当前位置接着放。",
            style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    }
}
