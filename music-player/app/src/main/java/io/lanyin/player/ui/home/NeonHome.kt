package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.NightWindshield
import io.lanyin.player.ui.design.PixelCover
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily

/**
 * 霓: the home page is the drive home. Up top the windscreen on a rainy night on the expressway,
 * the head-up display projecting tonight's route (today's picks: how many, and when you'll be
 * there); below it the console's screen: tonight's route with its picture and a key to set off,
 * the radio's keys for your own music (FM lit while it's on), the recommended playlists as
 * pictures, the charts, and today's songs.
 */
@Composable
internal fun NeonHome(model: HomeModel) {
    val m = Lanyin.style.margin
    val padding = pagePadding()
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
        item { Windscreen(model) }
        item { ConsoleHeader(model, Modifier.padding(start = m, end = m, top = 14.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item { Route(model, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item { Keys(model, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item {
            Column(Modifier.padding(top = 22.dp)) {
                ConsoleHeading("推荐歌单", model.recommendedNote, Modifier.padding(horizontal = m))
                val lists = model.recommended.slots(6).take(8)
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(lists.size) { i ->
                        val p = lists[i]
                        Column(
                            Modifier
                                .width(92.dp)
                                .themedSurface(tone = SurfaceTone.Flat, onClick = { if (p != null) model.openPlaylist(p) })
                                .padding(6.dp),
                        ) {
                            PixelCover(p?.coverUrl, p?.let { (it.id % 97).toInt() } ?: (10 + i), Modifier.fillMaxWidth().height(80.dp))
                            Text(p?.name ?: "· · ·", style = Lanyin.type.caption.copy(fontSize = fixedSp(11f), lineHeight = fixedSp(14f)), color = Lanyin.colors.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 22.dp)) {
                ConsoleHeading("排行榜", "全部", onTrailing = model.openCharts)
                val charts = model.charts.slots(4)
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    charts.forEachIndexed { i, chart -> ChartRow(chart, i, { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }) }
                }
            }
        }
        model.picks?.let { picks ->
            item { ConsoleHeading(picks.title, picks.note, Modifier.padding(start = m, end = m, top = 22.dp)) }
            items(picks.songs.size) { i ->
                val song = picks.songs[i]
                PickRow(song, playing = song.id == model.player.playingSongId, onClick = { model.playPick(i) }, modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 8.dp else 0.dp))
            }
        }
    }
}

/** How the head-up display sets its words: white light on the glass, a soft halo. */
@Composable
private fun hud(size: Float) = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(size), lineHeight = fixedSp(size * 1.3f), color = Color(0xFFD8E4EE), shadow = Shadow(Color(0xFF6FE0C8).copy(alpha = 0.6f), Offset.Zero, 8f))

/** The windscreen, under the status bar, with tonight's route projected on it. */
@Composable
private fun Windscreen(model: HomeModel) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val songs = model.pickSongs
    val minutes = songs.sumOf { it.durationMs } / 60_000
    val arrive = if (songs.isEmpty()) "--:--" else "%02d:%02d".format(minutes / 60, minutes % 60)
    NightWindshield(Modifier.fillMaxWidth().height(top + 250.dp).pressable(label = "每日推荐", onClick = model.openDaily), seed = 7) {
        Box(Modifier.fillMaxSize().padding(top = top)) {
            Row(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 2.dp)) {
                HeaderActions(model.account, model.openSettings, model.openAccount)
            }
            // The route, projected low on the glass.
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↑ 每日推荐", style = hud(13f))
                Text("  ·  ${if (songs.isEmpty()) "今晚" else "${songs.size} 首"}", style = hud(11f))
                Spacer(Modifier.width(18.dp))
                Text("$arrive 到达", style = hud(11f).copy(color = Color(0xFFFFC874)))
            }
        }
    }
}

/** The screen's status line: what it's showing, and the time. */
@Composable
private fun ConsoleHeader(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(c.accent))
        Text("  媒体 · 网易云音乐", style = Lanyin.type.caption.copy(fontSize = fixedSp(12f)), color = c.content, modifier = Modifier.weight(1f))
        Text("%02d:%02d".format(model.now.hour, model.now.minute), style = Lanyin.type.caption.copy(fontSize = fixedSp(12f)), color = c.muted)
    }
}

/** A heading on the screen, two little arrows after it. */
@Composable
private fun ConsoleHeading(text: String, trailing: String?, modifier: Modifier = Modifier, onTrailing: (() -> Unit)? = null) {
    val c = Lanyin.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$text ▸▸", style = Lanyin.type.title.copy(fontSize = fixedSp(15f)), color = c.content)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Lanyin.type.caption.copy(fontSize = fixedSp(11f)), color = if (onTrailing != null) c.accent else c.muted, modifier = if (onTrailing != null) Modifier.pressable(onClick = onTrailing) else Modifier)
    }
}

/** Tonight's route: today's picks with their picture, how long they run, and the key to set off. */
@Composable
private fun Route(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val songs = model.pickSongs
    val minutes = songs.sumOf { it.durationMs } / 60_000
    Row(modifier.fillMaxWidth().themedSurface(onClick = model.openDaily).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val first = songs.firstOrNull()
        PixelCover(first?.album?.coverUrl, first?.let { (it.id % 97).toInt() } ?: 3, Modifier.size(104.dp).border(3.dp, Color(0xFF05060A)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("今晚的路线 ▸ DAILY", style = Lanyin.type.caption.copy(fontSize = fixedSp(11f)), color = c.accent)
            Text("每日推荐", style = Lanyin.type.display.copy(fontSize = fixedSp(24f), lineHeight = fixedSp(30f)), color = c.content, modifier = Modifier.padding(top = 2.dp))
            Text(
                if (songs.isEmpty()) model.dailyNote else "${songs.size} 首 · ${minutes / 60} 小时 ${minutes % 60} 分",
                style = Lanyin.type.caption.copy(fontSize = fixedSp(11f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .themedSurface(shape = CutCornerShape(3.dp), tone = SurfaceTone.Accent, onClick = model.playDaily)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(9.dp, 12.dp)) {
                    drawRect(c.onAccent, size = Size(2.dp.toPx(), size.height))
                    drawRect(c.onAccent, Offset(2.dp.toPx(), 0f), Size(size.width - 2.dp.toPx(), size.height * 0.55f))
                }
                Text("  出发", style = Lanyin.type.body.copy(fontSize = fixedSp(13f)), color = c.onAccent)
            }
        }
    }
}

/**
 * The radio's keys for your own music: FM, liked, charts, recent, downloads; FM's key lit with
 * ON beside its picture while it plays.
 */
@Composable
private fun Keys(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val doors = model.doors.drop(1)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        doors.forEachIndexed { i, door ->
            val lit = i == 0
            Column(
                Modifier
                    .weight(1f)
                    .themedSurface(tone = SurfaceTone.Flat, onClick = door.onClick)
                    .then(if (lit) Modifier.border(2.dp, c.accent, CutCornerShape(3.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ThemedIcon(door.icon, null, tint = if (door.icon == LineIcon.Heart) c.like else if (lit) c.accent else c.content, size = 20.dp)
                    Spacer(Modifier.weight(1f))
                    if (lit) Text("ON", style = Lanyin.type.caption.copy(fontSize = fixedSp(9f)), color = c.accentAlt)
                }
                Text(door.label, style = Lanyin.type.caption.copy(fontSize = fixedSp(11f)), color = if (lit) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** A chart on the screen: its number, name, and the song at the top of it, with that song's picture. */
@Composable
private fun ChartRow(chart: ChartPreview?, slot: Int, onClick: () -> Unit) {
    val c = Lanyin.colors
    val top = chart?.top?.firstOrNull()
    Row(Modifier.fillMaxWidth().themedSurface(tone = SurfaceTone.Flat, onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(slot + 1), style = Lanyin.type.title.copy(fontSize = fixedSp(18f)), color = c.accent, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(chart.nameOr(slot), style = Lanyin.type.body.copy(fontSize = fixedSp(14f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(top?.let { "▸ ${it.name} · ${it.artistLine}" } ?: "· · ·", style = Lanyin.type.caption.copy(fontSize = fixedSp(11f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PixelCover(chart?.playlist?.coverUrl, 20 + slot, Modifier.size(40.dp).drawBehind { drawRect(Color(0xFF05060A), Offset(-2.dp.toPx(), -2.dp.toPx()), Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx())) }, cells = 20)
    }
}
