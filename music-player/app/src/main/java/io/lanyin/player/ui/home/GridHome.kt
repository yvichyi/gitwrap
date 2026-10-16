package io.lanyin.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.Marquee
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 格: the home page as a Swiss poster. The day of the month set huge, with the month, weekday and
 * greeting stacked beside it, ruled off heavily; the ribbon across; today's picks as a tall block
 * beside FM and 我喜欢 in blue and yellow, then a row of red, white and black blocks for the rest;
 * the recommended playlists as a ruled table; the charts as a two-by-two grid of boxes; the picks
 * with heavy numbers.
 */
@Composable
internal fun GridHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    val player = model.player
    val now = model.now
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Poster(model, Modifier.padding(horizontal = m)) }
        item {
            Marquee(
                listOfNotNull(
                    "澜音 LANYIN",
                    "正在播放 ${player.title} — ${player.artist}".takeIf { !player.isEmpty },
                    "每日推荐 №%02d".format(now.dayOfMonth),
                    model.profile?.let { "欢迎回来 ${it.nickname}" } ?: "扫码登录网易云",
                ),
                Modifier
                    .padding(top = 18.dp, bottom = 4.dp)
                    .graphicsLayer {
                        rotationZ = -1.6f
                        scaleX = 1.06f
                    },
                height = 32.dp,
            )
        }
        item { Blocks(model, Modifier.padding(start = m, end = m, top = 22.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 34.dp)) {
                SectionTitle("推荐歌单", index = 1, trailing = model.recommendedNote)
                PlaylistTable(model, Modifier.padding(top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 34.dp)) {
                SectionTitle("排行榜", index = 2, trailing = "ALL →")
                ChartBoxes(model, Modifier.padding(top = 14.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 34.dp), trailing = picks.note, index = 3) }
            items(picks.songs.size) { i ->
                HeavyRow(i, picks.songs[i], picks.songs[i].id == player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

/** The date as a poster's headline: the day huge, the rest stacked beside it, a heavy rule under. */
@Composable
private fun Poster(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "${now.dayOfMonth}",
                style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(104f), lineHeight = fixedSp(96f), letterSpacing = (-0.06).em),
                color = c.content,
            )
            Column(Modifier.padding(start = 10.dp, top = 12.dp).weight(1f)) {
                Text(now.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase() + ".", style = mono.copy(fontSize = 14.sp), color = c.accent)
                Text(now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA), style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content)
                Text(greeting(now.hour), style = style.type.display.copy(fontSize = 26.sp, lineHeight = 30.sp), color = c.content, maxLines = 1)
            }
            HeaderActions(model.account, model.openSettings, model.openAccount)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.content))
        Row(Modifier.padding(top = 6.dp)) {
            Text("№ %d.%02d.%02d".format(now.year, now.monthValue, now.dayOfMonth), style = mono.copy(fontSize = 12.sp), color = c.content, modifier = Modifier.weight(1f))
            Text("LANYIN / 澜音", style = mono.copy(fontSize = 12.sp), color = c.content)
        }
    }
}

/** Today's picks tall on the left, FM and 我喜欢 stacked beside it, then a row of three blocks. */
@Composable
private fun Blocks(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val doors = model.doors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureTile(
                "每日推荐", model.dailyNote, model.openDaily, model.playDaily, Modifier.weight(0.58f),
                art = { DailyArt(model.pickSongs) }, mark = { DayMark(model.now) }, ratio = 0.7f,
            )
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorBlock(doors[1], 2, c.accentAlt, Color.White, Modifier.weight(1f).fillMaxWidth())
                ColorBlock(doors[2], 3, GridYellow, c.content, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ColorBlock(doors[3], 4, c.accent, Color.White, Modifier.weight(1f).aspectRatio(1f))
            ColorBlock(doors[4], 5, c.surface, c.content, Modifier.weight(1f).aspectRatio(1f))
            ColorBlock(doors[5], 6, c.content, c.bg, Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

/** A block of flat colour: icon, number, and the name set heavy at its foot. */
@Composable
private fun ColorBlock(door: Door, number: Int, fill: Color, ink: Color, modifier: Modifier) {
    val style = Lanyin.style
    Box(modifier.themedSurface(onClick = door.onClick).background(fill).padding(10.dp)) {
        ThemedIcon(door.icon, null, tint = ink, size = 22.dp, modifier = Modifier.align(Alignment.TopStart))
        Text("%02d".format(number), style = mono.copy(fontSize = fixedSp(11f)), color = ink, modifier = Modifier.align(Alignment.TopEnd))
        Text(
            door.label,
            style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = cappedSp(17f, maxScale = 1.08f), lineHeight = cappedSp(20f, maxScale = 1.08f)),
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** The recommended playlists as a table: number, cover, name, length, ruled heavily top and bottom. */
@Composable
private fun PlaylistTable(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(5).take(5)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.content))
        lists.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.content))
            Row(
                Modifier.fillMaxWidth().pressable(onClick = { if (p != null) model.openPlaylist(p) }).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(i + 1), style = mono.copy(fontSize = 13.sp), color = c.accent, modifier = Modifier.width(30.dp))
                ShelfCover(p, i, Modifier.size(48.dp).border(2.dp, c.outline))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.name ?: "——", style = style.type.body.copy(fontWeight = FontWeight.Black), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (p != null) "${p.trackCount} TRACKS · ${p.creatorName ?: "LANYIN"}" else "LOADING",
                        style = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("→", style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.content))
    }
}

/** The charts in a two-by-two grid of boxes, each with its top three. */
@Composable
private fun ChartBoxes(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(4)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until (charts.size + 1) / 2) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (k in 0..1) {
                    val slot = row * 2 + k
                    if (slot >= charts.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val chart = charts[slot]
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .themedSurface(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
                            .padding(12.dp),
                    ) {
                        Text(chart.nameOr(slot), style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 17.sp), color = c.content, maxLines = 1)
                        Text((chart?.playlist?.updateNote ?: "LOADING").uppercase(), style = mono.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        for (i in 0 until 3) {
                            val song = chart?.top?.getOrNull(i)
                            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = mono.copy(fontSize = 12.sp), color = if (i == 0) c.accent else c.content, modifier = Modifier.width(16.dp))
                                Text(song?.name ?: "——", style = style.type.caption, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A song with a heavy number, ruled under. */
@Composable
private fun HeavyRow(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(index + 1), style = TextStyle(fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.04).em), color = if (playing) c.accent else c.content, modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistLine.uppercase(), style = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (playing) ThemedIcon(LineIcon.Note, null, tint = c.accent, size = 18.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.content))
    }
}
