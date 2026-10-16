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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.Airmail
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.LuggageTag
import io.lanyin.player.ui.design.StampInks
import io.lanyin.player.ui.design.Postmark
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.accentBrush
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.drawStamp
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PostcardSerifFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 暮: the home page as the things you bring back from a trip. A postmark with today's date by
 * the greeting; today's picks as a postcard (「Greetings from」 over the picture, a stamp and its
 * postmark in the corner); FM as a boarding pass with its stub; your own music as luggage tags;
 * the recommended playlists as snapshots, white-bordered and set down at angles; each chart as a
 * route with its first three stops; the picks.
 */
@Composable
internal fun DuskHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { DuskHeader(model, Modifier.padding(horizontal = m)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item { CoverPostcard(model, Modifier.padding(start = m, end = m, top = 20.dp)) }
        item { BoardingPass(model, Modifier.padding(start = m, end = m, top = 18.dp)) }
        item { LuggageTags(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 32.dp)) {
                SectionTitle("推荐歌单", index = 1, trailing = model.recommendedNote)
                Snapshots(model, Modifier.padding(top = 16.dp))
            }
        }
        item {
            Column(Modifier.padding(top = 32.dp)) {
                SectionTitle("排行榜", Modifier.padding(horizontal = m), index = 2, trailing = "全部")
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val charts = model.charts.slots(4)
                    items(charts.size) { i ->
                        val chart = charts[i]
                        RouteCard(chart, i, { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }, Modifier.width(252.dp))
                    }
                }
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 32.dp), index = 3, trailing = picks.note) }
            items(picks.songs.size) { i ->
                val song = picks.songs[i]
                PickRow(song, playing = song.id == model.player.playingSongId, onClick = { model.playPick(i) }, modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 10.dp else 0.dp))
            }
        }
    }
}

/** The paper a postcard or a print is on: white by day, a dim card at night. */
@Composable
private fun printPaper(): Color = if (Lanyin.style.dark) Color(0xFF2A2F48) else Color(0xFFFFFCF6)

/** The greeting in serif, the date under it, and today struck as a postmark beside them. */
@Composable
private fun DuskHeader(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = style.type.display)
            Text("${now.monthValue}月${now.dayOfMonth}日 · ${now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)}", style = style.type.caption, color = c.muted)
        }
        Postmark(
            "澜音", "${now.dayOfMonth}", now.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(),
            (if (style.dark) c.accent else Airmail.Red).copy(alpha = 0.75f), Modifier.padding(end = 8.dp), diameter = 46.dp, waves = false, angle = -10f,
        )
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/**
 * Today's picks as a postcard: the picture with a white border, 「Greetings from」 and the
 * title set over it, a stamp in its corner with the postmark struck half over it.
 */
@Composable
private fun CoverPostcard(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val paper = printPaper()
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .graphicsLayer { rotationZ = -1.2f }
                .drawBehind {
                    drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.35f else 0.1f), Offset(2.dp.toPx(), 5.dp.toPx()), size, CornerRadius(4.dp.toPx()))
                    drawRoundRect(paper, cornerRadius = CornerRadius(4.dp.toPx()))
                }
                .pressable(onClick = model.openDaily)
                .padding(9.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(style.artShape)) {
                DailyArt(model.pickSongs)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.32f), 0.32f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                Text(
                    "Greetings from",
                    style = TextStyle(fontFamily = PostcardSerifFamily, fontStyle = FontStyle.Italic, fontSize = fixedSp(17f), shadow = TextShade),
                    color = Color.White, modifier = Modifier.padding(14.dp),
                )
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("每日推荐", style = style.type.display.copy(fontSize = cappedSp(32f), lineHeight = cappedSp(38f), shadow = TextShade), color = Color.White, maxLines = 1)
                        Text(
                            model.pickSongs.firstOrNull()?.let { "今天为你挑了 ${model.pickSongs.size} 首，从《${it.name}》开始" } ?: model.dailyNote,
                            style = style.type.caption.copy(shadow = TextShade), color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PlayBlob(model.playDaily, "播放每日推荐", 48.dp)
                }
            }
        }
        // The stamp in the corner, and the postmark struck over its edge.
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 16.dp).size(34.dp, 40.dp).graphicsLayer { rotationZ = 5f }) {
            drawStamp(Rect(Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())), Airmail.Red, paper, Color.Transparent)
        }
        Postmark(
            "每日", "%02d".format(model.now.dayOfMonth), "${model.now.monthValue}月",
            Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 34.dp), diameter = 44.dp, angle = -14f,
        )
    }
}

/** A round play button in the sunset's colours. */
@Composable
private fun PlayBlob(onClick: () -> Unit, label: String, size: Dp) {
    val style = Lanyin.style
    val ink = style.colors.onAccent
    Box(Modifier.size(size).clip(CircleShape).background(style.accentBrush()).pressable(label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.3f)) { drawPlayPauseGlyph(0f, ink) }
    }
}

/**
 * FM as a boarding pass: where from and where to, a gate and a seat, its picture; then the
 * stub past a perforated line, notched top and bottom, with the button to board.
 */
@Composable
private fun BoardingPass(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val paper = if (style.dark) c.surface else Color(0xFFFFFCF6)
    val stub = 78.dp
    val field = TextStyle(fontWeight = FontWeight.Medium, fontSize = fixedSp(8.5f), letterSpacing = 0.14.em)
    val value = TextStyle(fontFamily = PostcardSerifFamily, fontSize = fixedSp(15f))
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .drawBehind {
                val notch = 9.dp.toPx()
                val cut = size.width - stub.toPx()
                val ticket = Path.combine(
                    PathOperation.Difference,
                    Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(10.dp.toPx()))) },
                    Path().apply {
                        addOval(Rect(Offset(cut, 0f), notch))
                        addOval(Rect(Offset(cut, size.height), notch))
                    },
                )
                translate(0f, 3.dp.toPx()) { drawPath(ticket, Color.Black.copy(alpha = if (style.dark) 0.3f else 0.07f)) }
                drawPath(ticket, paper)
                // The airmail stripe along the top of the pass, and the perforation to the stub.
                drawRect(Airmail.Blue.copy(alpha = if (style.dark) 0.6f else 0.85f), Offset(14.dp.toPx(), 0f), Size(cut - 28.dp.toPx(), 3.dp.toPx()))
                drawLine(c.muted.copy(alpha = 0.5f), Offset(cut, notch + 3.dp.toPx()), Offset(cut, size.height - notch - 3.dp.toPx()), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            .pressable(onClick = model.playFm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(77, Modifier.padding(start = 12.dp).size(64.dp).clip(style.artShape))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text("BOARDING PASS", style = field, color = Airmail.Blue.copy(alpha = if (style.dark) 1f else 0.9f).let { if (style.dark) lerp(it, Color.White, 0.45f) else it })
            Text("私人 FM", style = style.type.title, color = c.content, maxLines = 1)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for ((k, v) in listOf("FROM" to "澜音", "TO" to "你", "GATE" to "FM")) {
                    Column {
                        Text(k, style = field, color = c.muted, maxLines = 1)
                        Text(v, style = value, color = c.content, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.width(stub).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PlayBlob(model.playFm, "播放私人 FM", 40.dp)
            Text("SEAT ∞", style = field, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            Text(model.fmNote, style = style.type.caption.copy(fontSize = fixedSp(9f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** Your own music as luggage tags, two by two, each with its band of colour. */
@Composable
private fun LuggageTags(model: HomeModel, modifier: Modifier) {
    val doors = model.doors.drop(2)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    val door = doors[i]
                    LuggageTag(door.icon, door.label, door.note, StampInks[i % StampInks.size], door.onClick, Modifier.weight(1f), tilt = if (i % 2 == 0) -0.8f else 0.8f)
                }
            }
        }
    }
}

/** The recommended playlists as snapshots: white-bordered prints set down at angles, the name written under each. */
@Composable
private fun Snapshots(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val paper = printPaper()
    val pen = if (style.dark) c.content else Color(0xFF3B2A24)
    val lists = model.recommended.slots(4).take(4)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f).padding(top = if (col == 1) 18.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                lists.indices.filter { it % 2 == col }.forEach { slot ->
                    val p = lists[slot]
                    val tilt = listOf(-2.2f, 1.6f, 1.2f, -1.8f)[slot % 4]
                    Column(
                        Modifier
                            .graphicsLayer { rotationZ = tilt }
                            .drawBehind {
                                drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.35f else 0.09f), Offset(1.5.dp.toPx(), 4.dp.toPx()), size, CornerRadius(2.dp.toPx()))
                                drawRoundRect(paper, cornerRadius = CornerRadius(2.dp.toPx()))
                            }
                            .pressable(onClick = { if (p != null) model.openPlaylist(p) })
                            .padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 6.dp),
                    ) {
                        ShelfCover(p, slot, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(
                            p?.name ?: "", style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = cappedSp(13f), lineHeight = cappedSp(18f)),
                            color = pen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                        Text(
                            p?.let { "${it.trackCount} 首 · ${it.creatorName ?: "澜音"}" } ?: "",
                            style = style.type.caption.copy(fontSize = cappedSp(10.5f), lineHeight = cappedSp(14f)), color = pen.copy(alpha = 0.55f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A chart as a route: its name, then its first three songs as stops along a dotted line. */
@Composable
private fun RouteCard(chart: ChartPreview?, slot: Int, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val ink = StampInks[slot.mod(StampInks.size)]
    val stops = chart?.top.orEmpty().take(3)
    Column(
        modifier
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.3f else 0.06f), Offset(0f, 3.dp.toPx()), size, CornerRadius(12.dp.toPx()))
                drawRoundRect(if (style.dark) c.surface else Color(0xFFFFFCF6), cornerRadius = CornerRadius(12.dp.toPx()))
            }
            .pressable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ROUTE %02d".format(slot + 1), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = fixedSp(9f), letterSpacing = 0.16.em), color = ink.let { if (style.dark) lerp(it, Color.White, 0.4f) else it })
            Spacer(Modifier.weight(1f))
            Text("${stops.size.coerceAtLeast(3)} 站", style = style.type.caption, color = c.muted)
        }
        Text(chart.nameOr(slot), style = style.type.title, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        for (k in 0 until 3) {
            val song = stops.getOrNull(k)
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                // The line through the stops, and the stop: filled for the first, open after.
                Canvas(Modifier.width(18.dp).fillMaxHeight()) {
                    val mid = Offset(size.width / 2f, size.height / 2f)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                    if (k > 0) drawLine(c.muted.copy(alpha = 0.6f), Offset(mid.x, 0f), Offset(mid.x, mid.y - 6.dp.toPx()), 1.5.dp.toPx(), pathEffect = dash)
                    if (k < 2) drawLine(c.muted.copy(alpha = 0.6f), Offset(mid.x, mid.y + 6.dp.toPx()), Offset(mid.x, size.height), 1.5.dp.toPx(), pathEffect = dash)
                    if (k == 0) drawCircle(ink, 5.dp.toPx(), mid) else drawCircle(ink, 4.5.dp.toPx(), mid, style = Stroke(1.6.dp.toPx()))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.name ?: "· · ·", style = style.type.body.copy(fontWeight = if (k == 0) FontWeight.SemiBold else FontWeight.Normal), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (song != null) Text(song.artistLine, style = style.type.caption.copy(fontSize = fixedSp(10.5f), lineHeight = fixedSp(13f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
