package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.DeviceScreen
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.VuMeter
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberNow
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily

/**
 * 机: the home page as the machine's face. The model plate and power light across the top; the
 * big screen showing what's on the deck, with its VU meter and a block counter for the tape's
 * progress; a keypad of six raised keys numbered and labelled on the case beneath (the orange one
 * plays today's picks); the recommended playlists as a rack of tapes; the charts as channels on a
 * second screen; the picks as a sequencer's track list.
 */
@Composable
internal fun DeviceHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Faceplate(model, Modifier.padding(horizontal = m)) }
        item { MainDisplay(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        item { Keypad(model, Modifier.padding(start = m, end = m, top = 22.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionTitle("磁带架", Modifier.padding(horizontal = m), trailing = model.recommendedNote.uppercase(), index = 1)
                TapeRack(model, PaddingValues(start = m, end = m, top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("频道 · 排行榜", trailing = "ALL CH", index = 2)
                ChannelScreen(model, Modifier.padding(top = 12.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = "${picks.songs.size} TRK", index = 3) }
            items(picks.songs.size) { i ->
                Step(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val mono: TextStyle get() = TextStyle(fontFamily = SpaceMonoFamily)

/** The model plate and power light over the greeting, the actions at the side. */
@Composable
private fun Faceplate(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LANYIN LY-01", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold, letterSpacing = 0.12.em), color = c.muted)
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.size(8.dp)) {
                    if (model.player.isPlaying) drawCircle(c.accent.copy(alpha = 0.35f), size.minDimension)
                    drawCircle(if (model.player.isPlaying) c.accent else c.content.copy(alpha = 0.2f), size.minDimension / 2f)
                }
            }
            Text(greeting(model.now.hour), style = style.type.display, modifier = Modifier.padding(top = 2.dp))
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** The big screen: the clock and channel, what's on the deck, the VU meter and a block counter. */
@Composable
private fun MainDisplay(model: HomeModel, modifier: Modifier) {
    val player = model.player
    val now by rememberNow()
    val accent = Lanyin.colors.accent
    DeviceScreen(modifier.fillMaxWidth().pressable(onClick = if (player.isEmpty) model.playDaily else model.openPlayer)) {
        val c = Lanyin.colors
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d:%02d".format(now.hour, now.minute), style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold), color = c.muted)
            Spacer(Modifier.width(12.dp))
            Text("CH %02d".format(now.dayOfMonth), style = mono.copy(fontSize = fixedSp(11f)), color = c.muted)
            Spacer(Modifier.weight(1f))
            Text(if (player.isPlaying) "▶ PLAY" else "■ STOP", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold), color = if (player.isPlaying) accent else c.muted)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (player.isEmpty) {
                    Text("NO TAPE", style = mono.copy(fontSize = fixedSp(26f), fontWeight = FontWeight.Bold), color = c.content)
                    Text("按 1 号键，翻开今天的推荐", style = Lanyin.style.type.caption, color = c.muted)
                } else {
                    Text(player.title, style = Lanyin.style.type.title.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(player.artist, style = Lanyin.style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            VuMeter(player.audioFeed, player.isPlaying, Modifier.size(72.dp, 40.dp))
        }
        // The position changes every frame: read it while drawing, and the time only by the second.
        val seconds by remember(player) { derivedStateOf { player.positionMs / 1000 } }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.weight(1f).height(10.dp)) {
                val f = if (player.isEmpty) 0f else (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
                val blocks = 24
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (blocks - 1)) / blocks
                val lit = (f * blocks).toInt()
                for (k in 0 until blocks) {
                    drawRoundRect(
                        when {
                            k < lit -> c.content
                            k == lit && !player.isEmpty -> accent
                            else -> c.content.copy(alpha = 0.14f)
                        },
                        Offset(k * (w + gap), 0f), Size(w, size.height), CornerRadius(1.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (player.isEmpty) "--:--" else "${formatTime(seconds * 1000)} / ${formatTime(player.durationMs)}",
                style = mono.copy(fontSize = fixedSp(11f)),
                color = c.muted,
            )
        }
    }
}

/** Six raised keys, numbered on their caps and named on the case beneath; the first is orange. */
@Composable
private fun Keypad(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val doors = model.doors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0..2) {
                    val i = row * 3 + col
                    val door = doors[i]
                    val lead = i == 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .themedSurface(shape = RoundedCornerShape(12.dp), tone = if (lead) SurfaceTone.Accent else SurfaceTone.Chrome, onClick = door.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            ThemedIcon(door.icon, door.label, tint = if (lead) c.onAccent else c.content, size = 22.dp)
                            Text(
                                "${i + 1}",
                                style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold),
                                color = if (lead) c.onAccent.copy(alpha = 0.8f) else c.muted,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 5.dp),
                            )
                        }
                        Text(door.code, style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Bold, letterSpacing = 0.08.em), color = c.content, modifier = Modifier.padding(top = 8.dp))
                        Text(door.label, style = style.type.caption, color = c.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The recommended playlists as tapes in a rack: the cover, a printed label with its number and length. */
@Composable
private fun TapeRack(model: HomeModel, padding: PaddingValues) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(lists.size) { i ->
            val p = lists[i]
            Column(Modifier.width(128.dp).themedSurface(onClick = { if (p != null) model.openPlaylist(p) }).padding(8.dp)) {
                ShelfCover(p, i, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("T%02d".format(i + 1), style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Bold), color = c.accent)
                    Spacer(Modifier.weight(1f))
                    Text(if (p != null) "${p.trackCount} TRK" else "-- TRK", style = mono.copy(fontSize = fixedSp(9f)), color = c.muted)
                }
                Text(p?.name ?: "空白磁带", style = style.type.caption, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The charts as channels on a second screen: number, name, and what's at the top. */
@Composable
private fun ChannelScreen(model: HomeModel, modifier: Modifier) {
    val accent = Lanyin.colors.accent
    val charts = model.charts.slots(4)
    DeviceScreen(modifier.fillMaxWidth()) {
        val c = Lanyin.colors
        charts.forEachIndexed { i, chart ->
            if (i > 0) Rule(thickness = 1.dp, color = c.outline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CH${i + 1}", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold), color = accent, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(chart.nameOr(i), style = Lanyin.style.type.body.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1)
                    val top: Song? = chart?.top?.firstOrNull()
                    Text(
                        if (top != null) "▸ ${top.name} — ${top.artistLine}" else "▸ 调频中…",
                        style = Lanyin.style.type.caption,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(chart?.playlist?.updateNote ?: "", style = mono.copy(fontSize = fixedSp(9f)), color = c.muted, maxLines = 1)
            }
        }
    }
}

/** A step in the sequencer: number, title and artist, and the length in mono. */
@Composable
private fun Step(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (playing) "▶" else "%02d".format(index + 1),
                style = mono.copy(fontSize = fixedSp(12f), fontWeight = FontWeight.Bold),
                color = if (playing) c.accent else c.muted,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(song.name, style = style.type.body, color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatTime(song.durationMs), style = mono.copy(fontSize = fixedSp(11f)), color = c.muted, modifier = Modifier.padding(start = 10.dp))
        }
        Rule(thickness = 1.dp, color = c.outline)
    }
}
