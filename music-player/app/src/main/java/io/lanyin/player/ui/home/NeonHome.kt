package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.neonGlow
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberNow
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.music.PlaylistTile
import io.lanyin.player.ui.theme.Lanyin

/**
 * 霓: the home page as a heads-up display. A system readout over the greeting; today's picks and
 * FM as two channels on monitors with targeting brackets at their corners; your own music on a
 * segmented control bar; the recommended playlists as numbered channels; the charts as a live
 * data panel with signal bars; the picks with glowing numbers.
 */
@Composable
internal fun NeonHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Readout(model, Modifier.padding(horizontal = m)) }
        item {
            Row(Modifier.padding(start = m, end = m, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Monitor("CH.01 / DAILY", Modifier.weight(1f)) {
                    FeatureTile("每日推荐", model.dailyNote, model.openDaily, model.playDaily, Modifier.fillMaxWidth(), art = { DailyArt(model.pickSongs) }, mark = { DayMark(model.now) })
                }
                Monitor("CH.02 / FM", Modifier.weight(1f)) {
                    FeatureTile(
                        "私人 FM", model.fmNote, model.playFm, model.playFm, Modifier.fillMaxWidth(),
                        art = { CoverArt(77, Modifier.fillMaxSize()) },
                        mark = { ThemedIcon(LineIcon.Radio, null, tint = Color.White, size = 24.dp) },
                    )
                }
            }
        }
        item { ControlBar(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionTitle("推荐歌单", Modifier.padding(horizontal = m), trailing = model.recommendedNote)
                Channels(model, PaddingValues(start = m, end = m, top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("排行榜", trailing = "全部 ›")
                DataPanel(model, Modifier.padding(top = 12.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                GlowRow(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)

/** A system line over the greeting, and the date and signal under it. */
@Composable
private fun Readout(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberNow()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SYS//LANYIN  %02d:%02d".format(now.hour, now.minute), style = mono.copy(fontSize = fixedSp(11f)), color = c.accent)
            Text(greeting(now.hour), style = style.type.display)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%d.%02d.%02d  SIGNAL ".format(now.year, now.monthValue, now.dayOfMonth), style = mono.copy(fontSize = fixedSp(10f)), color = c.muted)
                Canvas(Modifier.size(width = 26.dp, height = 10.dp)) {
                    val w = size.width / 4f
                    for (k in 0 until 4) {
                        val h = size.height * (k + 1) / 4f
                        drawRect(if (k < 3) c.accent else c.muted.copy(alpha = 0.4f), Offset(k * w + 1f, size.height - h), androidx.compose.ui.geometry.Size(w - 3f, h))
                    }
                }
            }
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** A monitor: a label over [content], with targeting brackets at its corners. */
@Composable
private fun Monitor(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    val c = Lanyin.colors
    Column(modifier) {
        Text(label, style = mono.copy(fontSize = fixedSp(10f)), color = c.accent, modifier = Modifier.padding(start = 2.dp, bottom = 11.dp))
        Box(Modifier.brackets(c.accent)) { content() }
    }
}

/** L-shaped brackets just outside each corner, as a targeting display frames what it's locked on. */
private fun Modifier.brackets(color: Color): Modifier = drawWithContent {
    drawContent()
    val out = 5.dp.toPx()
    val len = 14.dp.toPx()
    val stroke = 2.dp.toPx()
    val l = -out
    val t = -out
    val r = size.width + out
    val b = size.height + out
    for ((x, dx) in listOf(l to 1f, r to -1f)) {
        for ((y, dy) in listOf(t to 1f, b to -1f)) {
            drawLine(color, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Square)
            drawLine(color, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Square)
        }
    }
}

/** Your own music on a segmented bar: four cut-corner keys that glow. */
@Composable
private fun ControlBar(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.doors.drop(2).forEach { door ->
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .themedSurface(shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp), tone = SurfaceTone.Flat, onClick = door.onClick)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemedIcon(door.icon, null, tint = c.accent, size = 20.dp)
                Text(door.label, style = style.type.caption, color = c.content, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The recommended playlists as numbered channels. */
@Composable
private fun Channels(model: HomeModel, padding: PaddingValues) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(lists.size) { i ->
            val p = lists[i]
            Column(Modifier.width(132.dp)) {
                Text("CH.%02d".format(i + 3), style = mono.copy(fontSize = fixedSp(10f)), color = c.accentAlt, modifier = Modifier.padding(bottom = 6.dp))
                if (p != null) PlaylistTile(p, { model.openPlaylist(p) }, Modifier.fillMaxWidth())
                else ShelfCover(null, i, Modifier.size(132.dp))
            }
        }
    }
}

/** The charts as a live data panel: each chart's top three with signal bars falling off by rank. */
@Composable
private fun DataPanel(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(2).take(2)
    Column(modifier.fillMaxWidth().themedSurface().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CHART // LIVE", style = mono.copy(fontSize = fixedSp(11f)), color = c.accent, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(8.dp)) {
                drawCircle(c.like.copy(alpha = 0.35f), size.minDimension)
                drawCircle(c.like, size.minDimension / 2f)
            }
        }
        charts.forEachIndexed { i, chart ->
            Text(
                chart.nameOr(i),
                style = style.type.title,
                color = c.content,
                modifier = Modifier.padding(top = if (i == 0) 10.dp else 18.dp).pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }),
            )
            for (k in 0 until 3) {
                val song = chart?.top?.getOrNull(k)
                Row(Modifier.padding(top = 8.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(k + 1), style = mono.copy(fontSize = fixedSp(13f)), color = if (k == 0) c.accent else c.muted, modifier = Modifier.width(30.dp))
                    Text(song?.name ?: "-- -- --", style = style.type.body, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    // Signal: full for the top, falling off below.
                    Canvas(Modifier.width(64.dp).height(6.dp)) {
                        drawRect(c.accent.copy(alpha = 0.15f))
                        drawRect(if (k == 0) c.accent else c.accentAlt, size = size.copy(width = size.width * (1f - k * 0.22f)))
                    }
                }
            }
        }
    }
}

/** A song with a glowing number. */
@Composable
private fun GlowRow(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 38.dp, height = 28.dp).neonGlow(CutCornerShape(6.dp), if (playing) c.accent else c.accentAlt, intensity = if (playing) 1f else 0.5f), contentAlignment = Alignment.Center) {
            Text("%02d".format(index + 1), style = mono.copy(fontSize = fixedSp(12f)), color = if (playing) c.accent else c.content)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body, color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
