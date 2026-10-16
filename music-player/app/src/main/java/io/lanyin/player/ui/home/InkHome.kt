package io.lanyin.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 墨: the home page as a literary magazine. A large greeting with the masthead written down the
 * margin and stamped with its seal; today's picks as the frontispiece, framed in a hairline with
 * its caption beneath; the other ways in as a table of contents with leaders; the recommended
 * playlists set in two staggered columns like plates; the charts as plain text; the picks.
 */
@Composable
internal fun InkHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Title(model, Modifier.padding(horizontal = m)) }
        item { Frontispiece(model, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item { Contents(model, Modifier.padding(start = m, end = m, top = 30.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 24.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 36.dp)) {
                SectionTitle("卷一 · 推荐歌单", trailing = model.recommendedNote)
                Plates(model, Modifier.padding(top = 16.dp))
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
                Box(Modifier.padding(top = 6.dp).size(22.dp).background(c.accentAlt), contentAlignment = Alignment.Center) {
                    Text("澜", style = style.type.caption.copy(fontFamily = style.display, fontSize = 13.sp, lineHeight = 14.sp), color = c.bg)
                }
            }
        }
        Hairline(Modifier.padding(top = 14.dp))
    }
}

/** Today's picks as the frontispiece: a framed picture, its caption and the day's number beneath. */
@Composable
private fun Frontispiece(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).border(1.dp, c.outline).padding(6.dp).pressable(onClick = model.openDaily)) {
            DailyArt(model.pickSongs)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("今日 · 每日推荐", style = style.type.title.copy(fontSize = 20.sp, lineHeight = 26.sp), color = c.content)
                Text(model.dailyNote, style = style.type.caption, color = c.muted)
            }
            Text(chineseNumber(model.now.dayOfMonth), style = style.type.display.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light), color = c.content, modifier = Modifier.padding(end = 14.dp))
            Box(Modifier.size(42.dp).clip(CircleShape).border(1.dp, c.content, CircleShape).pressable(onClick = model.playDaily), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(12.dp)) { drawPlayPauseGlyph(0f, c.content) }
            }
        }
    }
}

/** The other ways in as a table of contents: numbered in characters, a leader to the note. */
@Composable
private fun Contents(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Text("目　录", style = style.type.label, color = c.muted)
        Hairline(Modifier.padding(top = 8.dp))
        model.doors.drop(1).forEachIndexed { i, door ->
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).pressable(onClick = door.onClick), verticalAlignment = Alignment.CenterVertically) {
                Text(chineseNumber(i + 1), style = style.type.body.copy(fontFamily = style.display), color = c.muted, modifier = Modifier.width(30.dp))
                Text(door.label, style = style.type.body.copy(fontFamily = style.display, fontSize = 16.sp), color = c.content, maxLines = 1)
                DotLeader(Modifier.weight(1f).padding(horizontal = 10.dp), color = c.content.copy(alpha = 0.25f), pitch = 6.dp)
                Text(door.note, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            Hairline()
        }
    }
}

/** The recommended playlists in two staggered columns, each a plate with its number and name. */
@Composable
private fun Plates(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(4).take(4)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f).padding(top = if (col == 1) 40.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                for (slot in lists.indices.filter { it % 2 == col }) {
                    val p = lists[slot]
                    Column(Modifier.pressable(onClick = { if (p != null) model.openPlaylist(p) })) {
                        ShelfCover(p, slot, Modifier.fillMaxWidth().aspectRatio(if (slot % 3 == 0) 0.8f else 1f).border(1.dp, c.outline))
                        Text("其${chineseNumber(slot + 1)}", style = style.type.label, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                        Text(p?.name ?: "　", style = style.type.body.copy(fontFamily = style.display), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
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
