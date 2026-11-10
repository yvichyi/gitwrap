package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.Acid
import io.lanyin.player.ui.design.PhotocopyCover
import io.lanyin.player.ui.design.PlaySticker
import io.lanyin.player.ui.design.drawAsterisk
import io.lanyin.player.ui.design.drawBarcode
import io.lanyin.player.ui.design.drawCrosshair
import io.lanyin.player.ui.design.drawFlyer
import io.lanyin.player.ui.design.drawPerforation
import io.lanyin.player.ui.design.drawSmiley
import io.lanyin.player.ui.design.drawTapeStrip
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 酸: the home page is a wall of rave flyers on grey concrete. Tonight's headliner is today's
 * picks: a lime flyer with the count huge in outline, DAILY MIX, the line-up, the doors and the
 * venue (your headphones), a PLAY sticker slapped on it and, along its foot, tear-off tabs that
 * are the ways in. Beside it, photocopied flyers for the recommended playlists, taped up; a
 * smiley and a 303 sticker. Further down, the charts on a black flyer and the rest of the night's
 * line-up.
 */
@Composable
internal fun AcidHome(model: HomeModel) {
    val m = 14.dp
    val lists = model.recommended.slots(6)
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(extraTop = 6.dp)) {
        item { Header(model, Modifier.padding(horizontal = m)) }
        item { Wall(model, lists.take(3), Modifier.padding(start = m - 4.dp, end = m - 4.dp, top = 10.dp)) }
        if (!model.signedIn) item { MembersOnly(model.login, Modifier.padding(start = m, end = m, top = 14.dp)) }
        item { ChartFlyer(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        if (lists.size > 3) item {
            LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val more = lists.drop(3)
                items(more.size) { i -> Photocopy(more[i], i + 3, Modifier.width(132.dp), tilt = if (i % 2 == 0) 1.5f else -1.5f) { more[i]?.let(model.openPlaylist) } }
            }
        }
        model.picks?.let { picks ->
            if (picks.songs.size > 4) {
                item { LineUpHead(picks.title, picks.songs.size, Modifier.padding(start = m, end = m, top = 22.dp)) }
                items(picks.songs.size) { i ->
                    LineUpRow(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
                }
            }
        }
    }
}

private val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold)

/** Who's putting it on, and two stickers for the settings and the account. */
@Composable
private fun Header(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("LANYIN SOUND SYSTEM", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(20f), letterSpacing = (-0.01).em), color = c.content, maxLines = 1)
            Text("${greeting(model.now.hour)} ✱ WALL Nº%02d".format(model.now.dayOfMonth), style = mono.copy(fontSize = fixedSp(10f)), color = c.muted, maxLines = 1)
        }
        Sticker("SET", "设置", model.openSettings)
        Spacer(Modifier.width(8.dp))
        Sticker(if (model.signedIn) "ME" else "IN", if (model.signedIn) "我的账号" else "登录", model.openAccount)
    }
}

/** A round sticker with a word on it, a tap does [onClick]. */
@Composable
private fun Sticker(word: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { rotationZ = -8f }
            .drawBehind {
                drawCircle(Color.Black.copy(alpha = 0.35f), size.minDimension / 2f, center + Offset(0f, 2.dp.toPx()))
                drawCircle(Acid.Paper)
                drawCircle(Acid.Black, size.minDimension / 2f - 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
            .pressable(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(word, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(13f)), color = Acid.Black)
    }
}

/**
 * The wall itself: the headliner flyer on the left, three photocopies down the right, the stickers
 * and a strip of tape over the lot.
 */
@Composable
private fun Wall(model: HomeModel, lists: List<Playlist?>, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Headliner(model, Modifier.weight(0.63f).padding(top = 4.dp).graphicsLayer { rotationZ = -1.2f })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(0.37f).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                lists.forEachIndexed { i, p ->
                    Photocopy(p, i, Modifier.fillMaxWidth(), tilt = listOf(2f, -2.5f, 1.5f)[i]) { p?.let(model.openPlaylist) }
                }
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    drawSmiley(Offset(size.width * 0.55f, size.height * 0.5f), size.height * 0.4f, Acid.Lime, Acid.Black)
                }
            }
        }
        // The 303 sticker, half on the headliner.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 6.dp, y = 14.dp)
                .graphicsLayer { rotationZ = -4f }
                .background(Acid.Black)
                .border(2.dp, Acid.Lime)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("303 ✱ ACID", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(13f)), color = Acid.Lime)
        }
    }
}

/**
 * Tonight's headliner, today's picks: the lime flyer. The count of songs huge in outline, DAILY
 * MIX 每日推荐, the date and doors, the line-up (the first four), how many more and how long,
 * the venue, a barcode, the PLAY sticker, and the tear-off tabs for the other ways in.
 */
@Composable
private fun Headliner(model: HomeModel, modifier: Modifier) {
    val ink = Acid.Black
    val songs = model.pickSongs
    val now = model.now
    val small = mono.copy(fontSize = fixedSp(8f), letterSpacing = 0.06.em)
    val outline = with(androidx.compose.ui.platform.LocalDensity.current) { 2.5.dp.toPx() }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawFlyer(Acid.Lime, seed = 7, torn = true) }
                .pressable(label = "每日推荐", onClick = model.openDaily)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LANYIN SOUND SYSTEM PRESENTS", style = small, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                Canvas(Modifier.size(12.dp)) { drawCrosshair(center, size.minDimension / 2f, ink) }
            }
            Text(
                if (songs.isEmpty()) "00" else "%02d".format(songs.size.coerceAtMost(99)),
                style = TextStyle(
                    fontFamily = ArchivoBlackFamily, fontSize = fixedSp(104f), lineHeight = fixedSp(104f), letterSpacing = (-0.04).em,
                    drawStyle = Stroke(outline),
                ),
                color = ink, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("DAILY\nMIX", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(26f), lineHeight = fixedSp(26f)), color = ink)
                Spacer(Modifier.width(10.dp))
                Text("每日\n推荐", style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = fixedSp(22f), lineHeight = fixedSp(24f)), color = ink)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(ink))
            Row(Modifier.padding(top = 5.dp)) {
                Text(
                    "%s %02d.%02d.%d".format(now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(), now.monthValue, now.dayOfMonth, now.year),
                    style = small, color = ink, modifier = Modifier.weight(1f), maxLines = 1,
                )
                Text("DOORS 21:00 — LATE", style = small, color = ink, maxLines = 1)
            }
            Text("LINE-UP · 阵容", style = small, color = ink, modifier = Modifier.padding(top = 6.dp))
            if (songs.isEmpty()) {
                Text(model.dailyNote, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(13f)), color = ink, modifier = Modifier.padding(top = 2.dp))
            }
            songs.take(4).forEachIndexed { i, s ->
                Text(
                    "%02d %s / %s".format(i + 1, s.name, s.artistLine),
                    style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(12.5f), lineHeight = fixedSp(17f)), color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (songs.size > 4) {
                Text("+ ${songs.size - 4} MORE · ${hms(songs.sumOf { it.durationMs })}", style = small.copy(fontSize = fixedSp(9f)), color = ink, modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("VENUE: 你的耳机", style = small, color = ink, maxLines = 1)
                    Text("FREE ENTRY · NO PHOTOS", style = small, color = ink, maxLines = 1)
                }
                Canvas(Modifier.size(56.dp, 20.dp)) { drawBarcode(ink, 41) }
            }
            TearTabs(model, Modifier.padding(top = 8.dp).fillMaxWidth().height(66.dp))
        }
        PlaySticker(model.playDaily, Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = 132.dp), size = 72.dp, label = "播放每日推荐")
    }
}

/** h:mm:ss for a night's length. */
private fun hms(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

/**
 * The tear-off tabs along the foot of the headliner, each a way in, its name running down it;
 * one already torn off.
 */
@Composable
private fun TearTabs(model: HomeModel, modifier: Modifier) {
    val ink = Acid.Black
    val doors = model.doors
    // FM, the charts, (one torn away), recent, downloads, liked.
    val order = listOf(1, 3, -1, 4, 5, 2)
    Row(modifier.drawBehind { drawPerforation(ink) }) {
        order.forEachIndexed { k, d ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .drawBehind { if (k > 0) drawLine(ink, Offset(0f, 0f), Offset(0f, size.height), 1f * density) }
                    .then(if (d >= 0) Modifier.pressable(label = doors[d].label, onClick = doors[d].onClick) else Modifier)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (d >= 0) {
                    Text(
                        doors[d].label.replace(" ", "").toCharArray().joinToString("\n"),
                        style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(10f), lineHeight = fixedSp(11.5f)), color = ink,
                    )
                } else {
                    // Torn off: a ragged stub, and the wall showing where the tab was.
                    val wall = Lanyin.colors.bg
                    Canvas(Modifier.fillMaxSize()) {
                        val gap = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height)
                            for (j in 0..6) lineTo(size.width * j / 6f, (if (j % 2 == 0) 6 else 10).dp.toPx())
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(gap, wall)
                    }
                }
            }
        }
    }
}

/**
 * A photocopied flyer for a playlist: copier paper taped to the wall, the cover in coarse
 * halftone, its name and track count, a volume number and a scrap of barcode.
 */
@Composable
private fun Photocopy(p: Playlist?, slot: Int, modifier: Modifier, tilt: Float, onClick: () -> Unit) {
    val ink = Acid.Black
    Box(modifier.graphicsLayer { rotationZ = tilt }) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawFlyer(Acid.Paper, seed = 30 + slot, torn = slot % 2 == 1) }
                .pressable(label = p?.name ?: "推荐歌单", onClick = onClick)
                .padding(6.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(Acid.Black)) {
                PhotocopyCover(p?.coverUrl, p?.let { (it.id % 97).toInt() } ?: (10 + slot), Modifier.fillMaxSize(), lines = 30f)
            }
            Text(p?.name ?: "· · ·", style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(13f), lineHeight = fixedSp(16f)), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%d TRK · VOL.%02d".format(p?.trackCount ?: 0, slot + 2), style = mono.copy(fontSize = fixedSp(8f)), color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                Canvas(Modifier.size(24.dp, 10.dp)) { drawBarcode(ink, 70 + slot) }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp).offset(y = (-5).dp)) {
            drawTapeStrip(Offset(size.width / 2f, size.height / 2f), size.width * 0.42f, 12.dp.toPx(), -3f + slot * 2.5f, seed = slot)
        }
    }
}

/** Not signed in: a strip of copier paper, MEMBERS ONLY, and a way in. */
@Composable
private fun MembersOnly(onLogin: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = 0.8f }
            .drawBehind { drawFlyer(Acid.Paper, seed = 90, torn = false) }
            .pressable(label = "登录", onClick = onLogin)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MEMBERS ONLY", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(15f)), color = Acid.Black)
        Text("  登录网易云，拿到你的入场章", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixedSp(12.5f)), color = Acid.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Canvas(Modifier.size(16.dp)) { drawAsterisk(center, size.minDimension / 2f, Acid.Black) }
    }
}

/** The charts on a black flyer: TOP 4, each with its number in lime and the song at its top. */
@Composable
private fun ChartFlyer(model: HomeModel, modifier: Modifier) {
    val charts = model.charts.slots(4)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = -0.6f }
            .drawBehind { drawFlyer(Acid.Black, seed = 55, torn = true, stapled = true) }
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("TOP 4", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(34f), lineHeight = fixedSp(36f)), color = Acid.Lime)
            Text("  排行榜 · CHARTS", style = mono.copy(fontSize = fixedSp(10f)), color = Acid.Paper, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
            Text(
                "ALL ✱", style = mono.copy(fontSize = fixedSp(10f)), color = Acid.Lime,
                modifier = Modifier.pressable(label = "全部排行榜", onClick = model.openCharts).padding(6.dp),
            )
        }
        charts.forEachIndexed { i, chart ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .pressable(label = chart.nameOr(i)) { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(i + 1), style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(24f)), color = Acid.Lime, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(chart.nameOr(i), style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(15f)), color = Acid.Paper, maxLines = 1)
                    val top: Song? = chart?.top?.firstOrNull()
                    Text(if (top != null) "${top.name} / ${top.artistLine}" else "LOADING…", style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Normal), color = Lanyin.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The rest of the night's line-up: a strip of tape with its title, then each act. */
@Composable
private fun LineUpHead(title: String, count: Int, modifier: Modifier) {
    Row(
        modifier
            .graphicsLayer { rotationZ = -1.5f }
            .background(Acid.Lime)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FULL LINE-UP", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(15f)), color = Acid.Black)
        Text("  $title · $count", style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(12f)), color = Acid.Black)
    }
}

/** One act: its slot number, name and artist, its length; the one playing in lime with an asterisk. */
@Composable
private fun LineUpRow(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(label = song.name, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing) {
            Canvas(Modifier.width(34.dp).height(14.dp)) { drawAsterisk(Offset(7.dp.toPx(), center.y), 6.dp.toPx(), Acid.Lime) }
        } else {
            Text("%02d".format(index + 1), style = mono.copy(fontSize = fixedSp(12f)), color = c.muted, modifier = Modifier.width(34.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(song.name, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(15f)), color = if (playing) Acid.Lime else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), style = mono.copy(fontSize = fixedSp(11f)), color = c.muted, modifier = Modifier.padding(start = 8.dp))
    }
}
