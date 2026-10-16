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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.GelButton
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 酸: the home page as the millennium's gadgets. The name in huge chrome; today's picks and FM
 * each in a see-through plastic case, Bondi blue and tangerine, set down at angles, one starburst
 * stuck on; your own music as round gel buttons; the recommended playlists as CDs; the first
 * chart's top three with chrome numerals and the other charts as gel capsules. Lime is kept for
 * what to press and what's playing.
 */
@Composable
internal fun AcidHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Wordmark(model, Modifier.padding(horizontal = m)) }
        item { Cases(model, Modifier.padding(start = m, end = m, top = 14.dp)) }
        item { GelDoors(model, Modifier.padding(start = m, end = m, top = 10.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionTitle("推荐歌单", Modifier.padding(horizontal = m), index = 1, trailing = model.recommendedNote)
                Discs(model, PaddingValues(start = m, end = m, top = 14.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("TOP 3 · ${model.charts.firstOrNull().nameOr(0)}", index = 2, trailing = "全部 ✦")
                TopThree(model, Modifier.padding(top = 12.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), index = 3, trailing = picks.note) }
            items(picks.songs.size) { i ->
                StarRow(picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold)

/** 「LANYIN」 in huge chrome with the greeting and date under it. */
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
                Text(greeting(now.hour), style = Lanyin.style.type.title.copy(fontWeight = FontWeight.Bold), color = c.content)
                Text(
                    "  ✦  %02d.%02d %s".format(now.monthValue, now.dayOfMonth, now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase()),
                    style = mono.copy(fontSize = fixedSp(12f)),
                    color = c.muted,
                    maxLines = 1,
                )
            }
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** Today's picks and FM, each in a see-through plastic case, set down at angles; one starburst stuck over a corner. */
@Composable
private fun Cases(model: HomeModel, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(300.dp)) {
        PlasticCase(
            "每日推荐", model.dailyNote, Flavor.Bondi, model.openDaily, model.playDaily,
            Modifier.fillMaxWidth(0.57f).align(Alignment.TopStart).padding(top = 10.dp).graphicsLayer { rotationZ = -3f },
        ) {
            DailyArt(model.pickSongs)
            Box(Modifier.padding(10.dp)) { DayMark(model.now) }
        }
        PlasticCase(
            "私人 FM", model.fmNote, Flavor.Tangerine, model.playFm, model.playFm,
            Modifier.fillMaxWidth(0.46f).align(Alignment.TopEnd).padding(top = 44.dp).graphicsLayer { rotationZ = 4f },
        ) {
            CoverArt(77, Modifier.fillMaxSize())
            Box(Modifier.padding(10.dp)) { ThemedIcon(LineIcon.Radio, null, tint = Color.White, size = 24.dp) }
        }
        Starburst(model.now.dayOfMonth, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 2.dp))
    }
}

/**
 * Something in a case of [tint] plastic, as a Discman or an iMac came: the art seen through a
 * window in the shell, the title on the plastic under it, a lime gel button to play.
 */
@Composable
private fun PlasticCase(
    title: String,
    note: String,
    tint: Color,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
    art: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(14.dp, shape, ambientColor = tint, spotColor = tint)
            .candyPlastic(tint, shape)
            .pressable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(19.dp)),
        ) { art() }
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = cappedSp(16f), lineHeight = cappedSp(20f)), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(note, style = Lanyin.style.type.caption.copy(fontSize = cappedSp(11.5f), lineHeight = cappedSp(16f)), color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GelPlay(onPlay)
        }
    }
}

/** A lime gel button to play, the glyph dark under the gloss. */
@Composable
private fun GelPlay(onClick: () -> Unit) {
    val ink = Lanyin.colors.onAccent
    Box(
        Modifier
            .size(36.dp)
            .shadow(6.dp, CircleShape, ambientColor = Lanyin.colors.accent, spotColor = Lanyin.colors.accent)
            .candyPlastic(Color(0xFFB8F000), CircleShape, rim = false, glossOver = true)
            .pressable(label = "播放", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) { drawPlayPauseGlyph(0f, ink) }
    }
}

/** The one sticker: a strawberry starburst, 「HOT」 and the day. */
@Composable
private fun Starburst(day: Int, modifier: Modifier) {
    Box(modifier.size(62.dp).graphicsLayer { rotationZ = -12f }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(62.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawPath(starPath(center + Offset(2.dp.toPx(), 2.dp.toPx()), r, r * 0.68f, 16), Color.Black.copy(alpha = 0.4f))
            drawPath(starPath(center, r, r * 0.68f, 16), Brush.verticalGradient(listOf(Color(0xFFFF8FAF), Flavor.Strawberry, Color(0xFFD62F5B)), 0f, size.height))
            drawPath(starPath(center, r, r * 0.68f, 16), ChromeBrush, style = Stroke(1.dp.toPx()))
        }
        Text("HOT\n#$day", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(12f), lineHeight = fixedSp(13f), textAlign = TextAlign.Center), color = Color.White)
    }
}

/** Your own music as a row of round gel buttons, a flavour each, named under them. */
@Composable
private fun GelDoors(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val doors = model.doors.drop(2)
    val flavors = listOf(Flavor.Strawberry, Flavor.Grape, Flavor.Blueberry, Flavor.Bondi)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        doors.forEachIndexed { i, door ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GelButton(door.icon, flavors[i % flavors.size], door.label, door.onClick, size = 58.dp)
                Text(door.label, style = Lanyin.style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(door.code, style = mono.copy(fontSize = fixedSp(9f), letterSpacing = 0.1.em), color = c.muted, maxLines = 1)
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
                            Brush.sweepGradient(listOf(Color(0x00FFFFFF), Color(0x33FF4F7B), Color(0x338B5CF6), Color(0x3300A9C9), Color(0x00FFFFFF), Color(0x26FFFFFF), Color(0x00FFFFFF)), center),
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

/** The first chart's top three with big chrome numerals; the other charts as gel capsules under them. */
@Composable
private fun TopThree(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(4)
    val first = charts.firstOrNull()
    Column(modifier) {
        for (i in 0 until 3) {
            val song = first?.top?.getOrNull(i)
            val playing = song != null && song.id == model.player.playingSongId
            Row(
                Modifier.fillMaxWidth().pressable(onClick = { if (first != null) model.openPlaylist(first.playlist) else model.openCharts() }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(46f), lineHeight = fixedSp(50f), brush = ChromeBrush), modifier = Modifier.width(46.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.name ?: "✦ ✦ ✦", style = style.type.title.copy(fontWeight = FontWeight.Bold), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artistLine ?: "", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val flavors = listOf(Flavor.Grape, Flavor.Bondi, Flavor.Strawberry)
            charts.drop(1).forEachIndexed { k, chart ->
                GelCapsule(chart.nameOr(k + 1), flavors[k % flavors.size], { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
            }
        }
    }
}

/** A word on a capsule of [tint] gel. */
@Composable
private fun GelCapsule(text: String, tint: Color, onClick: () -> Unit, height: Dp = 34.dp) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(height)
            .candyPlastic(tint, shape, glossOver = true)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Lanyin.style.type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}

/** A song with a white sparkle before it; the one playing in lime. */
@Composable
private fun StarRow(song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp)) {
            drawPath(starPath(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, size.minDimension * 0.14f, 4), if (playing) c.accent else Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
