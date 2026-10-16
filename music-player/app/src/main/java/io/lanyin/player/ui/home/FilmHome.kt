package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.Lanyin

private val FilmBase = Color(0xFF0B0A09)
private val SheetWhite = Color(0xFFE9E1D3)

/**
 * 影: the home page as a day on set. A clapperboard for the header (production, roll, the
 * greeting as the scene, the date in the camera's orange); today's picks and FM as two frames on a
 * strip of film; your own music on a row of labels; the recommended playlists as a contact sheet,
 * the first frame ringed in red grease pencil as the keeper; the charts as rolling credits; the
 * picks as a cue sheet with timecodes.
 */
@Composable
internal fun FilmHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item {
            Row(Modifier.padding(horizontal = m), verticalAlignment = Alignment.CenterVertically) {
                Text("LANYIN PICTURES", style = edge.copy(fontSize = fixedSp(10f)), color = style.colors.muted, modifier = Modifier.weight(1f))
                HeaderActions(model.account, model.openSettings, model.openAccount)
            }
        }
        item { Slate(model, Modifier.padding(start = m, end = m, top = 10.dp)) }
        item { FeatureStrip(model, Modifier.padding(top = 22.dp)) }
        item { Labels(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("印样 · 推荐歌单", index = 1, trailing = model.recommendedNote)
                ContactSheet(model, Modifier.padding(top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("字幕 · 排行榜", index = 2, trailing = "全部 ›")
                Credits(model, Modifier.padding(top = 18.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle("场记 · ${picks.title}", Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note, index = 3) }
            items(picks.songs.size) { i ->
                Cue(picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val edge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)

/** The clapperboard: striped sticks over a grid of chalked fields. */
@Composable
private fun Slate(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    val line = c.content.copy(alpha = 0.45f)
    Column(modifier.fillMaxWidth().border(1.dp, line)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            drawRect(c.content)
            // The sticks' diagonal bands.
            val band = 16.dp.toPx()
            var x = -size.height
            var k = 0
            while (x < size.width + size.height) {
                if (k % 2 == 0) {
                    val path = Path().apply {
                        moveTo(x, size.height)
                        lineTo(x + band, size.height)
                        lineTo(x + band + size.height, 0f)
                        lineTo(x + size.height, 0f)
                        close()
                    }
                    drawPath(path, FilmBase)
                }
                x += band
                k++
            }
        }
        SlateRow(line, listOf("PROD." to "澜音 LANYIN", "ROLL" to "%02d".format(now.dayOfMonth)))
        SlateRow(line, listOf("SCENE" to greeting(now.hour), "TAKE" to "01"), big = true)
        SlateRow(line, listOf("DATE" to "%d.%02d.%02d".format(now.year, now.monthValue, now.dayOfMonth), "DIRECTOR" to (model.profile?.nickname ?: "你")), stamp = true)
    }
}

@Composable
private fun SlateRow(line: Color, cells: List<Pair<String, String>>, big: Boolean = false, stamp: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(line))
    Row(Modifier.height(IntrinsicSize.Min)) {
        cells.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            Column(Modifier.weight(if (i == 0) 1.6f else 1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, style = edge.copy(fontSize = fixedSp(9f)), color = c.muted)
                Text(
                    value,
                    style = when {
                        big -> style.type.display.copy(fontSize = 28.sp, lineHeight = 34.sp)
                        stamp && i == 0 -> edge.copy(fontSize = 15.sp, letterSpacing = 0.06.em)
                        else -> style.type.title
                    },
                    color = if (stamp && i == 0) FilmStamp else c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Today's picks and FM as two frames on a strip of film running edge to edge. */
@Composable
private fun FeatureStrip(model: HomeModel, modifier: Modifier) {
    val now = model.now
    Column(
        modifier
            .fillMaxWidth()
            .background(FilmBase)
            .drawBehind { sprockets(4.dp.toPx(), size.height - 9.dp.toPx()) }
            .padding(top = 13.dp, bottom = 13.dp),
    ) {
        Text(
            "LANYIN 400  ·  SAFETY FILM  ·  ▸ %02d".format(now.dayOfMonth),
            style = edge.copy(fontSize = fixedSp(8f)), color = FilmStamp, maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Frame("每日推荐", model.dailyNote, model.openDaily, model.playDaily, Modifier.weight(1f)) { DailyArt(model.pickSongs) }
            Frame("私人 FM", model.fmNote, model.playFm, model.playFm, Modifier.weight(1f)) { CoverArt(77, Modifier.fillMaxSize()) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("▸ %d".format(now.dayOfMonth), style = edge.copy(fontSize = fixedSp(8f)), color = FilmStamp, modifier = Modifier.weight(1f))
            Text("▸ %dA".format(now.dayOfMonth), style = edge.copy(fontSize = fixedSp(8f)), color = FilmStamp, modifier = Modifier.weight(1f))
        }
    }
}

/** Sprocket holes along both edges, the light of the sheet showing through. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.sprockets(top: Float, bottom: Float) {
    val hole = Size(7.dp.toPx(), 5.dp.toPx())
    val pitch = 14.dp.toPx()
    val radius = CornerRadius(1.5.dp.toPx())
    var x = 5.dp.toPx()
    while (x < size.width - hole.width) {
        drawRoundRect(SheetWhite, Offset(x, top), hole, radius)
        drawRoundRect(SheetWhite, Offset(x, bottom), hole, radius)
        x += pitch
    }
}

@Composable
private fun Frame(title: String, note: String, onClick: () -> Unit, onPlay: () -> Unit, modifier: Modifier, art: @Composable () -> Unit) {
    val style = Lanyin.style
    Box(modifier.aspectRatio(0.92f).pressable(onClick = onClick)) {
        Box(Modifier.fillMaxSize()) { art() }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = style.type.title.copy(fontFamily = style.display), color = Color.White, maxLines = 1)
                Text(note, style = style.type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.92f), CircleShape).pressable(onClick = onPlay), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(11.dp)) { drawPlayPauseGlyph(0f, Color(0xFF111111)) }
            }
        }
    }
}

/** Your own music on a row of labels, ruled above and below. */
@Composable
private fun Labels(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Rule(thickness = 1.dp, color = c.outline)
        Row(Modifier.height(IntrinsicSize.Min)) {
            model.doors.drop(2).forEachIndexed { i, door ->
                if (i > 0) VRule(Modifier.padding(vertical = 10.dp))
                Column(
                    Modifier.weight(1f).pressable(onClick = door.onClick).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ThemedIcon(door.icon, null, tint = c.content, size = 20.dp)
                    Text(door.label, style = style.type.caption, color = c.content, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Rule(thickness = 1.dp, color = c.outline)
    }
}

/** The recommended playlists as a contact sheet: rows of frames between sprocket bands, numbered in orange. */
@Composable
private fun ContactSheet(model: HomeModel, modifier: Modifier) {
    val accent = Lanyin.colors.accentAlt
    val lists = model.recommended.slots(6)
    val first = model.now.dayOfMonth
    Column(modifier.fillMaxWidth().background(FilmBase).padding(horizontal = 8.dp)) {
        for (row in 0 until (lists.size + 2) / 3) {
            Box(Modifier.fillMaxWidth().height(14.dp).drawBehind { sprockets(4.5.dp.toPx(), 4.5.dp.toPx()) })
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (k in 0..2) {
                    val slot = row * 3 + k
                    if (slot >= lists.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val p = lists[slot]
                    Column(Modifier.weight(1f).pressable(onClick = { if (p != null) model.openPlaylist(p) })) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .then(if (slot == 0) Modifier.greasePencil(accent) else Modifier),
                        ) {
                            ShelfCover(p, slot, Modifier.fillMaxSize())
                        }
                        Text(
                            "▸ ${first + slot / 2}${if (slot % 2 == 1) "A" else ""}",
                            style = edge.copy(fontSize = fixedSp(8f)), color = FilmStamp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            p?.name ?: "未冲洗",
                            style = Lanyin.style.type.caption.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            color = SheetWhite.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(14.dp).drawBehind { sprockets(4.5.dp.toPx(), 4.5.dp.toPx()) })
    }
}

/** A loose ring in red grease pencil round the frame, as a photographer marks the keeper. */
private fun Modifier.greasePencil(color: Color): Modifier = drawWithContent {
    drawContent()
    rotate(-7f) {
        val grow = 6.dp.toPx()
        drawOval(
            color.copy(alpha = 0.9f),
            Offset(-grow, -grow * 0.6f),
            Size(size.width + grow * 2, size.height + grow * 1.2f),
            style = Stroke(2.5.dp.toPx()),
        )
    }
}

/** The charts as end credits: each one's name spaced out, its top three centred beneath. */
@Composable
private fun Credits(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val charts = model.charts.slots(3).take(3)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        charts.forEachIndexed { i, chart ->
            Column(
                Modifier.fillMaxWidth().pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(chart.nameOr(i), style = style.type.title.copy(letterSpacing = 0.4.em), color = c.content)
                Text(chart?.playlist?.updateNote ?: "", style = edge.copy(fontSize = fixedSp(9f)), color = FilmStamp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                for (k in 0 until 3) {
                    val song = chart?.top?.getOrNull(k)
                    Text(
                        if (song != null) "${song.name}　·　${song.artistLine}" else "· · ·",
                        style = style.type.caption,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
        Text("全部排行榜 ›", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, modifier = Modifier.pressable(onClick = model.openCharts).padding(6.dp))
    }
}

/** A song on the cue sheet: its length as a timecode in the camera's orange, then title and artist. */
@Composable
private fun Cue(song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(song.durationMs).padStart(5, '0'), style = edge.copy(fontSize = fixedSp(11f)), color = if (playing) c.accent else FilmStamp, modifier = Modifier.width(54.dp))
            Column(Modifier.weight(1f)) {
                Text(song.name, style = style.type.body.copy(fontFamily = style.display), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Rule(thickness = 1.dp, color = c.outline)
    }
}
