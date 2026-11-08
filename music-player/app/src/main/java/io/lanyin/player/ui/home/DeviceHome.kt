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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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

/**
 * Six keys set in a keybed, a recess in the case with its shadowed upper edge and a bright lower
 * lip: numbered on their caps, named on the case beneath in mono and in words; the first orange.
 */
@Composable
private fun Keypad(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val doors = model.doors
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val r = CornerRadius(18.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = if (style.dark) 0.05f else 0.7f), Offset(0f, 1.dp.toPx()), size, r)
                drawRoundRect(lerp(c.bgDeep, Color.Black, if (style.dark) 0.3f else 0.07f), cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (style.dark) 0.4f else 0.14f), Color.Transparent), endY = 12.dp.toPx()), cornerRadius = r)
            }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..2) {
                    val i = row * 3 + col
                    val door = doors[i]
                    val lead = i == 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .themedSurface(shape = RoundedCornerShape(10.dp), tone = if (lead) SurfaceTone.Accent else SurfaceTone.Chrome, onClick = door.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            ThemedIcon(door.icon, door.label, tint = if (lead) c.onAccent else c.content, size = 21.dp)
                            Text(
                                "${i + 1}",
                                style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold),
                                color = if (lead) c.onAccent.copy(alpha = 0.8f) else c.muted,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 5.dp),
                            )
                        }
                        Text(door.code, style = mono.copy(fontSize = fixedSp(9.5f), fontWeight = FontWeight.Bold, letterSpacing = 0.1.em), color = c.content, modifier = Modifier.padding(top = 9.dp))
                        Text(door.label, style = style.type.caption.copy(fontSize = 11.sp), color = c.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The recommended playlists as cassettes in a rack, each with its cover on the label. */
@Composable
private fun TapeRack(model: HomeModel, padding: PaddingValues) {
    val lists = model.recommended.slots(6)
    LazyRow(contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(lists.size) { i ->
            val p = lists[i]
            Tape(p, i, { if (p != null) model.openPlaylist(p) }, Modifier.width(184.dp))
        }
    }
}

/**
 * A cassette: a smoked shell with its four screws and the head opening at its foot, a paper label
 * printed with the playlist's cover, its side and number, its name over an orange stripe; and
 * under the label the window with the two reels.
 */
@Composable
private fun Tape(p: io.lanyin.player.core.model.Playlist?, i: Int, onClick: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    val shell = Color(0xFF2B2B29)
    val paper = Color(0xFFF4F1EA)
    val ink = Color(0xFF1C1C1A)
    Column(
        modifier
            .aspectRatio(1.56f)
            .pressable(onClick = onClick)
            .drawBehind {
                val w = size.width
                val h = size.height
                val corner = CornerRadius(8.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(0f, 3.dp.toPx()), size, corner)
                drawRoundRect(shell, cornerRadius = corner)
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), endY = h * 0.35f), cornerRadius = corner)
                for (x in listOf(5.dp.toPx(), w - 5.dp.toPx())) for (y in listOf(5.dp.toPx(), h - 5.dp.toPx())) drawCircle(Color.White.copy(alpha = 0.22f), 1.7.dp.toPx(), Offset(x, y))
                val head = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.2f, h); lineTo(w * 0.26f, h * 0.84f); lineTo(w * 0.74f, h * 0.84f); lineTo(w * 0.8f, h); close() }
                drawPath(head, Color.White.copy(alpha = 0.07f))
                for (x in listOf(w * 0.37f, w * 0.63f)) drawCircle(Color.Black.copy(alpha = 0.55f), 2.4.dp.toPx(), Offset(x, h * 0.93f))
            }
            .padding(start = 9.dp, end = 9.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().weight(0.66f).clip(RoundedCornerShape(4.dp)).background(paper)) {
            ShelfCover(p, i, Modifier.fillMaxHeight().aspectRatio(1f))
            Column(Modifier.weight(1f).padding(start = 7.dp, end = 6.dp, top = 4.dp, bottom = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Bold), color = c.accent)
                    Spacer(Modifier.weight(1f))
                    Text("T%02d".format(i + 1), style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold), color = ink.copy(alpha = 0.55f))
                }
                Spacer(Modifier.weight(1f))
                Text(p?.name ?: "空白磁带", style = TextStyle(fontSize = fixedSp(11f), lineHeight = fixedSp(14f), fontWeight = FontWeight.Medium), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(2.dp).background(c.accent))
            }
        }
        Canvas(Modifier.fillMaxWidth().weight(0.34f).padding(start = 30.dp, end = 30.dp, top = 6.dp)) {
            val wr = size.height / 2f
            drawRoundRect(Color(0xFF121211), cornerRadius = CornerRadius(wr))
            val r = size.height * 0.36f
            for (cx in listOf(wr, size.width - wr)) {
                val center = Offset(cx, size.height / 2f)
                drawCircle(Color(0xFFE9E6DE), r, center)
                for (k in 0 until 6) {
                    rotate(k * 60f, center) { drawRect(Color(0xFF121211), Offset(center.x - r * 0.12f, center.y - r * 0.82f), Size(r * 0.24f, r * 0.32f)) }
                }
            }
            // The tape wound between them.
            drawRect(Color(0xFF3A2A1E), Offset(wr + r, size.height * 0.3f), Size(size.width - 2 * (wr + r), size.height * 0.4f))
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
