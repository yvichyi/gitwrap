package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.BoxSign
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.HangingSign
import io.lanyin.player.ui.design.NeonText
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.drawTubeLine
import io.lanyin.player.ui.design.neonCore
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberNow
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.music.PlaylistTile
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.TiltNeonFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** A third colour of tube for 霓, beside the pink and the cyan: amber. */
private val Amber = Color(0xFFFFC46B)

/**
 * 霓: the home page as a street at night. Up top the app's own sign, LANYIN bent in pink tube with
 * an OPEN sign in cyan beside it, the greeting under it; today's picks and FM as two lit box
 * signs; your own music as small neon pictograms on the wall; the recommended playlists as
 * posters, each under its number in tube; the charts as signs hung down the side of a building,
 * switching on one after another, the top song under each; today's songs numbered in tube, the
 * one playing lit.
 */
@Composable
internal fun NeonHome(model: HomeModel) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { StreetHeader(model, Modifier.padding(horizontal = m)) }
        item {
            Row(Modifier.padding(start = m, end = m, top = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Lightbox(
                    "每日推荐", model.dailyNote, c.accent, model.openDaily, model.playDaily, Modifier.weight(1f),
                    art = { DailyArt(model.pickSongs) },
                    mark = { DayMark(model.now) },
                )
                Lightbox(
                    "私人 FM", model.fmNote, c.accentAlt, model.playFm, model.playFm, Modifier.weight(1f),
                    art = { CoverArt(77, Modifier.fillMaxSize()) },
                    mark = { BoxSign("ON AIR", c.accentAlt, Modifier.align(Alignment.TopStart).padding(8.dp), size = 10f, delayMillis = 300) },
                )
            }
        }
        item { Pictograms(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionTitle("推荐歌单", Modifier.padding(horizontal = m), trailing = model.recommendedNote)
                Posters(model, PaddingValues(start = m, end = m, top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("排行榜", trailing = "全部 ›")
                HangingCharts(model, Modifier.padding(top = 14.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                TubeRow(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

/** The app's sign, LANYIN in pink tube with OPEN lit in cyan beside it, and the greeting under them. */
@Composable
private fun StreetHeader(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberNow()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonText("LANYIN", TextStyle(fontFamily = TiltNeonFamily, fontSize = fixedSp(38f), letterSpacing = 2.sp), c.accent)
                Spacer(Modifier.width(12.dp))
                BoxSign("OPEN", c.accentAlt, size = 11f, delayMillis = 450)
            }
            Text(greeting(now.hour), style = style.type.title, color = c.content, modifier = Modifier.padding(top = 4.dp))
            Text(
                "${now.monthValue}月${now.dayOfMonth}日 · ${now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)}",
                style = style.type.caption, color = c.muted,
            )
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/**
 * A lit box sign: a tube of [color] along its top, the picture inside, the name glowing under it,
 * and the sign's light falling over the top of the box.
 */
@Composable
private fun Lightbox(
    title: String,
    note: String,
    color: Color,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
    art: @Composable () -> Unit,
    mark: @Composable BoxScope.() -> Unit,
) {
    val c = Lanyin.colors
    Column(
        modifier
            .themedSurface(onClick = onOpen)
            .drawBehind { drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.5f)) },
    ) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawTubeLine(Offset(16.dp.toPx(), 7.dp.toPx()), Offset(size.width - 16.dp.toPx(), 7.dp.toPx()), color, 2.dp.toPx())
        }
        Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(10.dp))) {
            art()
            mark()
        }
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NeonText(title, Lanyin.style.type.title.copy(fontSize = 17.sp), color, delayMillis = 200)
                Text(note, style = Lanyin.style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TilePlay(onPlay)
        }
    }
}

/** Your own music as small neon pictograms on the wall, each in its own colour of tube. */
@Composable
private fun Pictograms(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val tubes = listOf(c.accent, c.accentAlt, Amber, lerp(c.accent, c.accentAlt, 0.5f))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        model.doors.drop(2).forEachIndexed { i, door ->
            val tube = tubes[i % tubes.size]
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 76.dp)
                    .themedSurface(tone = SurfaceTone.Flat, onClick = door.onClick)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The pictogram's light on the wall round it, then the tube itself.
                Box(
                    Modifier.size(36.dp).drawBehind {
                        drawCircle(Brush.radialGradient(listOf(tube.copy(alpha = 0.32f), Color.Transparent)), size.minDimension * 0.75f)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    ThemedIcon(door.icon, null, tint = neonCore(tube), size = 22.dp)
                }
                Text(door.label, style = Lanyin.style.type.caption, color = c.content, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The recommended playlists as posters along the wall, each under its number in cyan tube. */
@Composable
private fun Posters(model: HomeModel, padding: PaddingValues) {
    val c = Lanyin.colors
    val lists = model.recommended.slots(6)
    LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(lists.size) { i ->
            val p = lists[i]
            Column(Modifier.width(132.dp)) {
                NeonText(
                    "%02d".format(i + 1), TextStyle(fontFamily = TiltNeonFamily, fontSize = fixedSp(20f)), c.accentAlt,
                    Modifier.padding(start = 2.dp, bottom = 4.dp), delayMillis = 120 * i,
                )
                if (p != null) PlaylistTile(p, { model.openPlaylist(p) }, Modifier.fillMaxWidth())
                else ShelfCover(null, i, Modifier.size(132.dp))
            }
        }
    }
}

/**
 * The charts as signs hung down the side of a building, each its own colour, switching on one
 * after another; under each, the song at the top of it.
 */
@Composable
private fun HangingCharts(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val tubes = listOf(c.accent, c.accentAlt, Amber, lerp(c.accent, c.accentAlt, 0.5f))
    val charts = model.charts.slots(4)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        charts.forEachIndexed { i, chart ->
            Column(
                Modifier.weight(1f).pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HangingSign(chart.nameOr(i).removeSuffix("（官方）"), tubes[i % tubes.size], Modifier.fillMaxWidth(), letterSize = 21f, delayMillis = 200 + 180 * i)
                Text(
                    chart?.top?.firstOrNull()?.name ?: "· · ·",
                    style = style.type.caption.copy(textAlign = TextAlign.Center),
                    color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** A song under its number in tube: lit pink for the one playing, cold glass for the rest. */
@Composable
private fun TubeRow(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val number = TextStyle(fontFamily = TiltNeonFamily, fontSize = fixedSp(22f))
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp)) {
            if (playing) NeonText("%02d".format(index + 1), number, c.accent, ignite = false)
            else Text("%02d".format(index + 1), style = number, color = c.content.copy(alpha = 0.28f))
        }
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body, color = if (playing) neonCore(c.accent) else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
