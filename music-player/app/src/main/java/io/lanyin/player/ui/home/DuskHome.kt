package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.accentBrush
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin

/**
 * 暮: the home page as a travel magazine at golden hour. A cover story across the full width
 * (today's picks over their artwork, the title set large), FM as a postcard beside its picture,
 * your own music as soft pills, the recommended playlists in a loose two-column spread of tall
 * and square pictures, the charts as a stack of cards, the picks.
 */
@Composable
internal fun DuskHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Header(model.now, model.account, model.openSettings, model.openAccount, Modifier.padding(horizontal = m)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item { CoverStory(model, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item { Postcard(model, Modifier.padding(start = m, end = m, top = 14.dp)) }
        item { SoftPills(model, Modifier.padding(start = m, end = m, top = 14.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 32.dp)) {
                SectionTitle("推荐歌单", trailing = model.recommendedNote)
                Spread(model, Modifier.padding(top = 14.dp))
            }
        }
        item {
            Column(Modifier.padding(top = 32.dp)) {
                SectionTitle("排行榜", Modifier.padding(horizontal = m), trailing = "全部")
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val charts = model.charts.slots(4)
                    items(charts.size) { i ->
                        val chart = charts[i]
                        ChartCard(chart, { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }, Modifier.width(264.dp), seed = 30 + i)
                    }
                }
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 32.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                val song = picks.songs[i]
                PickRow(song, playing = song.id == model.player.playingSongId, onClick = { model.playPick(i) }, modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 10.dp else 0.dp))
            }
        }
    }
}

/** Today's picks as the cover story: the artwork across the width, the title large over its foot. */
@Composable
private fun CoverStory(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    Box(modifier.fillMaxWidth().aspectRatio(1.25f).themedSurface(onClick = model.openDaily)) {
        DailyArt(model.pickSongs)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))))
        Column(Modifier.align(Alignment.TopStart).padding(18.dp)) {
            Text("COVER STORY · ${model.now.monthValue}月${model.now.dayOfMonth}日", style = style.type.label.copy(fontSize = fixedSp(11f)), color = Color.White.copy(alpha = 0.9f))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("每日推荐", style = style.type.display.copy(fontSize = 34.sp, lineHeight = 40.sp, color = Color.White))
                Text(
                    model.pickSongs.firstOrNull()?.let { "今天为你挑了 ${model.pickSongs.size} 首，从《${it.name}》开始" } ?: model.dailyNote,
                    style = style.type.body,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(52.dp).clip(CircleShape).background(style.accentBrush()).pressable(label = "播放每日推荐", onClick = model.playDaily), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) { drawPlayPauseGlyph(0f, style.colors.onAccent) }
            }
        }
    }
}

/** FM as a postcard: its picture on the left, the words on the right. */
@Composable
private fun Postcard(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().height(96.dp).themedSurface(onClick = model.playFm), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(77, Modifier.fillMaxHeight().aspectRatio(1f))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("私人 FM", style = style.type.title, color = c.content)
            Text(model.fmNote, style = style.type.caption, color = c.muted, maxLines = 1)
        }
        Box(Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(13.dp)) { drawPlayPauseGlyph(0f, c.accent) }
        }
    }
}

/** Your own music as soft pills, two by two, each with its note. */
@Composable
private fun SoftPills(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val doors = model.doors.drop(2)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) {
                    val door = doors[row * 2 + col]
                    Row(
                        Modifier.weight(1f).heightIn(min = 56.dp).themedSurface(shape = RoundedCornerShape(18.dp), tone = SurfaceTone.Flat, onClick = door.onClick).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            ThemedIcon(door.icon, null, tint = c.accent, size = 18.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(door.label, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1)
                            Text(door.note, style = style.type.caption.copy(fontSize = 11.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The recommended playlists as a loose spread: two columns, tall and square pictures alternating. */
@Composable
private fun Spread(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(4).take(4)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                lists.indices.filter { it % 2 == col }.forEachIndexed { k, slot ->
                    val p = lists[slot]
                    val tall = (k + col) % 2 == 0
                    Column(Modifier.pressable(onClick = { if (p != null) model.openPlaylist(p) })) {
                        ShelfCover(p, slot, Modifier.fillMaxWidth().aspectRatio(if (tall) 0.78f else 1f).clip(style.artShape))
                        Text(p?.name ?: "", style = style.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        if (p != null) Text("${p.trackCount} 首 · ${p.creatorName ?: "澜音"}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
