package io.lanyin.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.DotWidget
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.Lanyin

/**
 * 点: the home page as a phone's home screen of widgets, in Nothing's manner. Tiles on a two-column
 * grid: the dot-matrix clock across the top; today's picks and FM as square tiles; your own music
 * as four round buttons sharing a tile with the top three of the charts; the recommended
 * playlists two by two; the picks with dot-matrix numbers.
 */
@Composable
internal fun DotHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    val gap = 10.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { DotHeader(model, Modifier.padding(horizontal = m)) }
        item { DotWidget(model.player, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Row(Modifier.padding(start = m, end = m, top = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                FeatureTile(
                    "每日推荐", model.dailyNote, model.openDaily, model.playDaily, Modifier.weight(1f),
                    art = { DailyArt(model.pickSongs) }, mark = { DayMark(model.now) }, ratio = 1f,
                )
                FeatureTile(
                    "私人 FM", model.fmNote, model.playFm, model.playFm, Modifier.weight(1f),
                    art = { CoverArt(77, Modifier.fillMaxSize()) },
                    mark = { ThemedIcon(LineIcon.Radio, null, tint = Color.White, size = 24.dp) }, ratio = 1f,
                )
            }
        }
        item {
            Row(Modifier.padding(start = m, end = m, top = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                RoundButtons(model.doors.drop(2), Modifier.weight(1f))
                TopThree(model, Modifier.weight(1f))
            }
        }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = gap)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("推荐歌单", index = 1, trailing = model.recommendedNote)
                PlaylistGrid(model, Modifier.padding(top = 14.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note, index = 2) }
            items(picks.songs.size) { i ->
                DotSong(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val doto: TextStyle get() = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black)

@Composable
private fun DotHeader(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = style.type.display)
            Text(
                "LANYIN · %02d.%02d".format(now.monthValue, now.dayOfMonth),
                style = doto.copy(fontSize = fixedSp(14f), letterSpacing = 0.14.em),
                color = c.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** Four round buttons on a tile, two by two, each named beneath. */
@Composable
private fun RoundButtons(doors: List<Door>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(
        modifier.aspectRatio(1f).themedSurface(tone = SurfaceTone.Flat).padding(10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0..1) {
                    val door = doors[row * 2 + col]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable(onClick = door.onClick)) {
                        Box(Modifier.size(46.dp).background(c.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) {
                            ThemedIcon(door.icon, door.label, tint = c.content, size = 20.dp)
                        }
                        Text(door.label, style = style.type.caption.copy(fontSize = fixedSp(11f)), color = c.muted, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** The first chart's top three, with dot-matrix numbers, the first in red. */
@Composable
private fun TopThree(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val chart = model.charts.firstOrNull()
    Column(
        modifier
            .aspectRatio(1f)
            .themedSurface(tone = SurfaceTone.Flat, onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TOP", style = doto.copy(fontSize = fixedSp(15f), letterSpacing = 0.1.em), color = c.content)
            Spacer(Modifier.weight(1f))
            Text(chart.nameOr(0), style = style.type.caption.copy(fontSize = fixedSp(11f)), color = c.muted, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        for (i in 0 until 3) {
            val song = chart?.top?.getOrNull(i)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = doto.copy(fontSize = fixedSp(20f)), color = if (i == 0) c.accentAlt else c.content, modifier = Modifier.width(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(song?.name ?: "· · ·", style = style.type.caption.copy(fontSize = fixedSp(12f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The recommended playlists as tiles, two across. */
@Composable
private fun PlaylistGrid(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(4).take(4)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until (lists.size + 1) / 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in 0..1) {
                    val slot = row * 2 + k
                    if (slot >= lists.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val p = lists[slot]
                    Column(Modifier.weight(1f).pressable(onClick = { if (p != null) model.openPlaylist(p) })) {
                        ShelfCover(p, slot, Modifier.fillMaxWidth().aspectRatio(1f).clip(style.artShape))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p?.name ?: "· · ·", style = style.type.body.copy(fontSize = fixedSp(14f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (p != null) {
                                Spacer(Modifier.width(6.dp))
                                Text("${p.trackCount}", style = doto.copy(fontSize = fixedSp(13f)), color = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A song with its number in dot-matrix figures. */
@Composable
private fun DotSong(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(index + 1), style = doto.copy(fontSize = fixedSp(20f)), color = if (playing) c.accentAlt else c.muted, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Box(Modifier.size(7.dp).background(c.accentAlt, CircleShape))
    }
}
