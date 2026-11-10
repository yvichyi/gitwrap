package io.lanyin.player.ui.home

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.Lanyin
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 墨: the home page as a scholar's desk. A large greeting with the name written down the margin
 * and stamped with its seal; today's picks as a hanging scroll of a landscape in ink, inscribed,
 * its 奏 seal to play them; the other ways in as a row of seals; the recommended playlists as
 * leaves of an album; the charts as plain text; the picks.
 */
@Composable
internal fun InkHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Title(model, Modifier.padding(horizontal = m)) }
        item { ScrollHero(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item { Seals(model, Modifier.padding(start = m, end = m, top = 26.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 24.dp)) }
        item {
            Column(Modifier.padding(top = 36.dp)) {
                SectionTitle("卷一 · 推荐歌单", Modifier.padding(horizontal = m), trailing = model.recommendedNote)
                AlbumLeaves(model, androidx.compose.foundation.layout.PaddingValues(start = m, end = m, top = 16.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                SectionTitle("卷二 · 排行榜", trailing = "全部 ›")
                TextCharts(model, Modifier.padding(top = 10.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle("卷三 · ${picks.title}", Modifier.padding(start = m, end = m, top = 36.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                val song = picks.songs[i]
                PickRow(song, playing = song.id == model.player.playingSongId, onClick = { model.playPick(i) }, modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 10.dp else 0.dp))
            }
        }
    }
}

/** The date in characters, the actions, then the greeting large with the name written down the margin and the seal. */
@Composable
private fun Title(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${chineseNumber(now.monthValue)}月${chineseNumber(now.dayOfMonth)}日 · ${now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)}",
                style = style.type.label,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            HeaderActions(model.account, model.openSettings, model.openAccount)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                greeting(now.hour),
                style = style.type.display.copy(fontSize = 46.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, letterSpacing = 0.08.em),
                color = c.content,
                modifier = Modifier.weight(1f),
            )
            // 澜音, written top to bottom, and its seal.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (ch in "澜音") Text("$ch", style = style.type.body.copy(fontFamily = style.display, fontSize = 15.sp, lineHeight = 19.sp), color = c.content)
                io.lanyin.player.ui.design.InkSeal("澜", 22.dp, Modifier.padding(top = 6.dp))
            }
        }
        Hairline(Modifier.padding(top = 14.dp))
    }
}

/** The charts as text: each name, then its top three with light numerals, ruled apart. */
@Composable
private fun TextCharts(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(3).take(3)
    Column(modifier) {
        charts.forEachIndexed { i, chart ->
            Column(Modifier.fillMaxWidth().pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }).padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(chart.nameOr(i), style = style.type.title, color = c.content, modifier = Modifier.weight(1f))
                    Text(chart?.playlist?.updateNote ?: "", style = style.type.caption, color = c.muted)
                }
                for (k in 0 until 3) {
                    val song = chart?.top?.getOrNull(k)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${k + 1}", style = style.type.title.copy(fontWeight = FontWeight.Light, fontSize = 20.sp), color = if (k == 0) c.accentAlt else c.muted, modifier = Modifier.width(28.dp))
                        Text(song?.name ?: "　", style = style.type.body, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (song != null) Text("　${song.artistLine}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Hairline()
        }
    }
}


/**
 * Today's picks as a hanging scroll: a landscape in ink, painted for the hour (the sun in
 * cinnabar by day, the moon by night), with the inscription down its right edge, today's count
 * of songs in characters and the seals; the 奏 seal at its foot plays them. A tap on the painting
 * opens them.
 */
@Composable
private fun ScrollHero(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val songs = model.pickSongs
    io.lanyin.player.ui.design.HangingScroll(modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.86f).pressable(label = "每日推荐", onClick = model.openDaily)) {
            io.lanyin.player.ui.design.ShanShui(Modifier.fillMaxSize(), hour = model.now.hour + model.now.minute / 60f, seed = model.now.dayOfMonth)
            // The inscription, top to bottom down the right edge.
            Row(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 6.dp)) {
                    val note = if (songs.isEmpty()) model.dailyNote.take(6) else "${chineseNumber(songs.size)}首 · ${chineseNumber(model.now.monthValue)}月${chineseNumber(model.now.dayOfMonth)}日"
                    for (ch in note.filter { it != ' ' }) Text("$ch", style = style.type.caption.copy(fontFamily = style.display, fontSize = 11.sp, lineHeight = 13.sp), color = c.muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (ch in "今日推荐") Text("$ch", style = style.type.title.copy(fontFamily = style.display, fontSize = 22.sp, lineHeight = 26.sp), color = c.content)
                    io.lanyin.player.ui.design.InkSeal("澜音", 24.dp, Modifier.padding(top = 6.dp))
                }
            }
            // 奏: press to play today's picks.
            io.lanyin.player.ui.design.InkSeal(
                "奏", 44.dp,
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp).graphicsLayer { rotationZ = -4f }
                    .pressable(label = "播放每日推荐", onClick = model.playDaily),
            )
        }
    }
}

/**
 * The other ways in as a row of seals, each with a character cut for it: 声 for the radio, 心
 * for the liked, 榜 the charts, 近 recent, 藏 the downloads; cut alternately in white and in red,
 * their names under them.
 */
@Composable
private fun Seals(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val marks = listOf("声", "心", "榜", "近", "藏")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        model.doors.drop(1).forEachIndexed { i, door ->
            Column(
                Modifier.pressable(label = door.label, onClick = door.onClick).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                io.lanyin.player.ui.design.InkSeal(marks[i], 46.dp, Modifier.graphicsLayer { rotationZ = (i % 3 - 1) * 2.5f }, carved = i % 2 == 0)
                Text(door.label, style = style.type.caption.copy(fontFamily = style.display), color = c.content, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * The recommended playlists as leaves of an album (册页), side by side to turn through: each
 * cover painted in ink on its leaf, its number and name beside it in a column, a small seal.
 */
@Composable
private fun AlbumLeaves(model: HomeModel, padding: androidx.compose.foundation.layout.PaddingValues) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    androidx.compose.foundation.lazy.LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(lists.size) { slot ->
            val p = lists[slot]
            Row(
                Modifier
                    .width(176.dp)
                    .height(214.dp)
                    .drawBehind {
                        drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f), Offset(2.dp.toPx(), 3.dp.toPx()), size)
                        drawRect(if (style.dark) io.lanyin.player.ui.design.InkDesk.SilkNight else io.lanyin.player.ui.design.InkDesk.Silk)
                    }
                    .pressable(label = p?.name ?: "推荐歌单") { if (p != null) model.openPlaylist(p) }
                    .padding(8.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight().drawBehind { io.lanyin.player.ui.design.drawXuanPage(this, style) }) {
                    io.lanyin.player.ui.design.InkWashCover(p?.coverUrl, p?.let { (it.id % 97).toInt() } ?: (10 + slot), Modifier.fillMaxSize().padding(6.dp))
                }
                Column(Modifier.padding(start = 8.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("其${chineseNumber(slot + 1)}", style = style.type.caption.copy(fontFamily = style.display, fontSize = 10.sp), color = c.muted)
                    val name = (p?.name ?: "　").take(9)
                    Column(Modifier.weight(1f).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        for (ch in name) Text("$ch", style = style.type.body.copy(fontFamily = style.display, fontSize = 14.sp, lineHeight = 16.sp), color = c.content)
                    }
                    io.lanyin.player.ui.design.InkSeal("藏", 16.dp)
                }
            }
        }
    }
}
