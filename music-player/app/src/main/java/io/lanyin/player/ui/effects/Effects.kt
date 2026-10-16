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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import io.lanyin.player.core.audio.PresetGroup
import io.lanyin.player.core.model.Quality
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.ThemedSlider
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** One line on what a preset does, shown under the grid. */
fun EffectPreset.blurb(): String = when (this) {
    EffectPreset.OFF -> "不做任何处理，最接近原样"
    EffectPreset.CUSTOM -> "拖动均衡器上的点，调出自己的曲线"
    EffectPreset.POP -> "中低频饱满，人声往前站"
    EffectPreset.ROCK -> "鼓和吉他更有冲劲，高频明亮"
    EffectPreset.SOFT_ROCK -> "收一点中频，耳朵不累"
    EffectPreset.METAL -> "低频扎实，失真吉他颗粒清楚"
    EffectPreset.JAZZ -> "铜管和贝斯温润，带一点小房间"
    EffectPreset.BLUES -> "暖一点，吉他和人声更贴近"
    EffectPreset.CLASSICAL -> "两头舒展，带音乐厅的余韵"
    EffectPreset.FOLK -> "木吉他和人声清楚，稍带暖意"
    EffectPreset.COUNTRY -> "拨弦和人声明亮"
    EffectPreset.GUOFENG -> "琵琶古筝的泛音更亮，空间更开阔"
    EffectPreset.HIPHOP -> "底鼓和 808 更重，人声干净"
    EffectPreset.RNB -> "低频圆润，人声丝滑"
    EffectPreset.ELECTRONIC -> "低频和高频都推上去，声场更宽"
    EffectPreset.DANCE -> "低频推满，适合跟着节拍动"
    EffectPreset.CLUB -> "中频厚实，像在场子里"
    EffectPreset.TECHNO -> "底鼓和高频都更锐利"
    EffectPreset.PARTY -> "低频和高频一起抬高"
    EffectPreset.REGGAE -> "反拍的中高频更突出"
    EffectPreset.SKA -> "铜管和吉他更跳"
    EffectPreset.LIVE -> "中等混响、人声靠前、声场更宽"
    EffectPreset.HALL -> "大厅的长混响，低频宽厚"
    EffectPreset.CHURCH -> "很长的混响，高处有回声"
    EffectPreset.KTV -> "人声突出，带回声和混响"
    EffectPreset.STUDIO -> "干净贴耳，轻微压缩"
    EffectPreset.SURROUND -> "把左右拉开，声音包围过来"
    EffectPreset.ROTATE -> "声音绕着头慢慢转圈，戴耳机听"
    EffectPreset.PSYCHEDELIC -> "宽声场、回声和缓慢旋转"
    EffectPreset.CAR -> "补低频、压动态，路噪里也听得清"
    EffectPreset.HEADPHONES -> "补耳机常缺的低频和空气感"
    EffectPreset.NIGHT -> "压低忽大忽小，小音量也听得清细节"
    EffectPreset.VOCAL -> "人声频段推前，伴奏退后"
    EffectPreset.CLEAR_VOCAL -> "齿音和气声更清楚，轻微压缩"
    EffectPreset.BASS -> "低频加厚"
    EffectPreset.FULL_BASS -> "低频推满，其余让路"
    EffectPreset.BASS_TREBLE -> "低频和高频都加，中频收一点"
    EffectPreset.TREBLE -> "细节和空气感更多"
    EffectPreset.LOUDNESS -> "小音量时补回低频和高频"
    EffectPreset.SOFT -> "高频柔下来，适合长时间听"
    EffectPreset.VINYL -> "温暖的饱和、柔和的高频和唱片底噪"
    EffectPreset.TUBE -> "电子管式的饱和，厚而暖"
    EffectPreset.CASSETTE -> "频宽稍窄，轻微磁带底噪"
    EffectPreset.LOFI -> "窄频、底噪和暖色，适合学习时听"
    EffectPreset.RADIO -> "老式收音机的窄频单声道"
}

/**
 * 音效: presets by group (曲风, 场景, 人声 · 低音, 复古), the ten-band curve (drag any band; a
 * preset's curve becomes your own), and how much of each effect. Changes are heard at once.
 */
@Composable
fun EffectsSheet(fx: EffectsSettings, onChange: (EffectsSettings) -> Unit, onDone: () -> Unit = {}) {
    val style = Lanyin.style
    val c = style.colors
    var group by rememberSaveable { mutableStateOf(fx.preset.group ?: PresetGroup.STYLE) }
    val a = fx.amounts
    var more by rememberSaveable { mutableStateOf(a.echo + a.compress + a.warmth + a.lofi + a.crackle + a.rotate > 0f) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("音效", style = style.type.title)
            Spacer(Modifier.width(10.dp))
            Text(
                if (fx.enabled) "耳机和外放都有效" else "原声：不做任何处理",
                style = style.type.caption, color = c.muted, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in listOf(EffectPreset.OFF, EffectPreset.CUSTOM)) {
                ThemedChip(p.label, fx.preset == p, { onChange(fx.choose(p)); onDone() }, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
        }
        ChoiceRow(
            PresetGroup.entries.map { it.label }, group.label,
            { label -> group = PresetGroup.entries.first { it.label == label } },
            Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
        )
        // The group's presets, three to a row.
        EffectPreset.inGroup(group).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    ThemedChip(p.label, fx.preset == p, { onChange(fx.choose(p)); onDone() }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            buildString {
                append(fx.preset.label)
                fx.preset.group?.let { append(" · ").append(it.label) }
                append("：").append(fx.preset.blurb())
            },
            style = style.type.caption, color = if (fx.enabled) c.accent else c.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
        EqualizerPanel(
            fx.curve, enabled = fx.enabled,
            onBand = { i, db -> onChange(fx.withBand(i, db)) },
            onDone = onDone,
            modifier = Modifier.padding(top = 18.dp),
        )
        AmountRow("低音增强", a.bass, fx.enabled, { v -> onChange(fx.with { copy(bass = v) }) }, onDone)
        AmountRow("环绕", a.width, fx.enabled, { v -> onChange(fx.with { copy(width = v) }) }, onDone)
        AmountRow("混响", a.reverb, fx.enabled, { v -> onChange(fx.with { copy(reverb = v) }) }, onDone)
        AmountRow("空间大小", a.room, fx.enabled && a.reverb > 0f, { v -> onChange(fx.with { copy(room = v) }) }, onDone)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).pressable { more = !more }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("更多效果", style = style.type.body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Text("回声 · 压缩 · 温暖 · 复古 · 8D", style = style.type.caption, color = c.muted)
            ThemedIcon(LineIcon.CaretDown, null, tint = c.muted, size = 18.dp, modifier = Modifier.padding(start = 4.dp).rotate(if (more) 180f else 0f))
        }
        if (more) {
            AmountRow("回声", a.echo, fx.enabled, { v -> onChange(fx.with { copy(echo = v) }) }, onDone)
            AmountRow("动态压缩", a.compress, fx.enabled, { v -> onChange(fx.with { copy(compress = v) }) }, onDone)
            AmountRow("温暖", a.warmth, fx.enabled, { v -> onChange(fx.with { copy(warmth = v) }) }, onDone)
            AmountRow("复古窄频", a.lofi, fx.enabled, { v -> onChange(fx.with { copy(lofi = v) }) }, onDone)
            AmountRow("唱片底噪", a.crackle, fx.enabled, { v -> onChange(fx.with { copy(crackle = v) }) }, onDone)
            AmountRow("8D 旋转", a.rotate, fx.enabled, { v -> onChange(fx.with { copy(rotate = v) }) }, onDone)
        }
        Text(
            "音效在澜音里处理，不改动下载的文件。听无损时，选「原声」最接近原样。部分均衡曲线参考 VLC 的经典预设。",
            style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun AmountRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style.type.body, color = if (enabled) c.content else c.muted, maxLines = 1, modifier = Modifier.width(84.dp))
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
    // Keyed on [enabled] only: keyed on the curve, the first change would restart the gesture
    // mid-drag and lose it. The latest callbacks are read as the finger moves.
    val band by androidx.compose.runtime.rememberUpdatedState(onBand)
    val done by androidx.compose.runtime.rememberUpdatedState(onDone)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val col = ((down.position.x / size.width) * bands).toInt().coerceIn(0, bands - 1)
                        fun dbAt(y: Float) = ((0.5f - y / size.height) * 2f * EQ_RANGE_DB * 1.15f).coerceIn(-EQ_RANGE_DB, EQ_RANGE_DB)
                        band(col, dbAt(down.position.y))
                        drag(down.id) { change ->
                            change.consume()
                            band(col, dbAt(change.position.y))
                        }
                        done()
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
