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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.AcidTicker
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 酸: the home page as a Y2K collage. The name in huge chrome, the date in lime mono; the sticker
 * ticker; today's picks and FM as two cards thrown down at angles over each other, a starburst
 * slapped on top; your own music as chunky pills; the recommended playlists as CDs; the first
 * chart's top three with chrome numerals and the other charts as stickers; the picks starred.
 */
@Composable
internal fun AcidHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Wordmark(model, Modifier.padding(horizontal = m)) }
        item { AcidTicker(model.player, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item { Collage(model, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item { Pills(model, Modifier.padding(start = m, end = m, top = 8.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionTitle("推荐歌单", Modifier.padding(horizontal = m), trailing = model.recommendedNote)
                Discs(model, PaddingValues(start = m, end = m, top = 14.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("TOP 3 · ${model.charts.firstOrNull().nameOr(0)}", trailing = "全部 ✦")
                TopThree(model, Modifier.padding(top = 12.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                StarRow(picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold)

/** 「LANYIN」 in huge chrome with the greeting and date under it in lime mono. */
@Composable
private fun Wordmark(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val now = model.now
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "LANYIN",
                style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(54f), lineHeight = fixedSp(56f), letterSpacing = (-0.03).em, brush = ChromeBrush),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(now.hour), style = Lanyin.style.type.title.copy(fontWeight = FontWeight.Bold), color = c.accent)
                Text(
                    "  ✦  %02d.%02d %s".format(now.monthValue, now.dayOfMonth, now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase()),
                    style = mono.copy(fontSize = fixedSp(12f)),
                    color = c.content,
                    maxLines = 1,
                )
            }
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** Today's picks and FM thrown down at angles, overlapping, with a starburst on top. */
@Composable
private fun Collage(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    Box(modifier.fillMaxWidth().height(262.dp)) {
        FeatureTile(
            "每日推荐", model.dailyNote, model.openDaily, model.playDaily,
            Modifier.fillMaxWidth(0.58f).align(Alignment.TopStart).padding(top = 12.dp).graphicsLayer { rotationZ = -4f },
            art = { DailyArt(model.pickSongs) }, mark = { DayMark(model.now) }, ratio = 0.84f,
        )
        FeatureTile(
            "私人 FM", model.fmNote, model.playFm, model.playFm,
            Modifier.fillMaxWidth(0.44f).align(Alignment.TopEnd).padding(top = 34.dp).graphicsLayer { rotationZ = 5f },
            art = { CoverArt(77, Modifier.fillMaxSize()) },
            mark = { ThemedIcon(LineIcon.Radio, null, tint = Color.White, size = 24.dp) }, ratio = 0.84f,
        )
        Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(64.dp).graphicsLayer { rotationZ = -12f }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawPath(starPath(center + Offset(2.dp.toPx(), 2.dp.toPx()), size.minDimension / 2f, size.minDimension * 0.34f, 16), Color.Black.copy(alpha = 0.5f))
                drawPath(starPath(center, size.minDimension / 2f, size.minDimension * 0.34f, 16), c.accent)
            }
            Text("HOT\n#${model.now.dayOfMonth}", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(12f), lineHeight = fixedSp(13f), textAlign = TextAlign.Center), color = c.onAccent)
        }
    }
}

/** Your own music as chunky pills, two by two, one of them hot pink. */
@Composable
private fun Pills(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val doors = model.doors.drop(2)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    val door = doors[i]
                    val hot = i == 0
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (hot) c.accentAlt else c.bg)
                            .border(1.5.dp, if (hot) c.accentAlt else c.accent, RoundedCornerShape(50))
                            .pressable(onClick = door.onClick)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemedIcon(door.icon, null, tint = if (hot) Color.White else c.accent, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(door.label, style = Lanyin.style.type.body.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
                            Text(door.code, style = mono.copy(fontSize = fixedSp(9f), letterSpacing = 0.1.em), color = if (hot) Color.White.copy(alpha = 0.8f) else c.accent, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The recommended playlists as CDs: the cover on a disc with a chrome rim, a rainbow sheen and the hole. */
@Composable
private fun Discs(model: HomeModel, padding: PaddingValues) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(lists.size) { i ->
            val p = lists[i]
            Column(Modifier.width(132.dp).pressable(onClick = { if (p != null) model.openPlaylist(p) }), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    ShelfCover(p, i, Modifier.size(132.dp).clip(CircleShape))
                    Canvas(Modifier.size(132.dp)) {
                        val r = size.minDimension / 2f
                        val center = Offset(r, r)
                        // The disc's rainbow where the light catches the data side.
                        drawCircle(
                            Brush.sweepGradient(listOf(Color(0x00FFFFFF), Color(0x33FF3EA5), Color(0x33C6FF00), Color(0x3300E5FF), Color(0x00FFFFFF), Color(0x26FFFFFF), Color(0x00FFFFFF)), center),
                            r, center,
                        )
                        drawCircle(ChromeBrush, r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.24f, center, style = Stroke(1.dp.toPx()))
                        drawCircle(c.bg, r * 0.12f, center)
                        drawCircle(ChromeBrush, r * 0.12f, center, style = Stroke(1.5.dp.toPx()))
                    }
                }
                Text(p?.name ?: "BLANK CD", style = style.type.caption, color = c.content, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** The first chart's top three with big chrome numerals; the other charts as stickers under them. */
@Composable
private fun TopThree(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(4)
    val first = charts.firstOrNull()
    Column(modifier) {
        for (i in 0 until 3) {
            val song = first?.top?.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().pressable(onClick = { if (first != null) model.openPlaylist(first.playlist) else model.openCharts() }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(46f), lineHeight = fixedSp(50f), brush = ChromeBrush), modifier = Modifier.width(46.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.name ?: "✦ ✦ ✦", style = style.type.title.copy(fontWeight = FontWeight.Bold), color = if (i == 0) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artistLine ?: "", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            charts.drop(1).forEachIndexed { k, chart ->
                Text(
                    chart.nameOr(k + 1),
                    style = style.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (k == 1) c.onAccent else c.content,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = if (k % 2 == 0) -3f else 3f }
                        .clip(RoundedCornerShape(50))
                        .background(if (k == 1) c.accent else c.bgDeep)
                        .border(1.dp, if (k == 1) c.accent else c.accentAlt, RoundedCornerShape(50))
                        .pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** A song with a lime star before it, the one playing in pink. */
@Composable
private fun StarRow(song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp)) {
            drawPath(starPath(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, size.minDimension * 0.14f, 4), if (playing) c.accentAlt else c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = if (playing) c.accentAlt else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
