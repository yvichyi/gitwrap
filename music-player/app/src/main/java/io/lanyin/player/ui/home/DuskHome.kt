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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.Overprint
import io.lanyin.player.ui.design.Pencil
import io.lanyin.player.ui.design.PencilNote
import io.lanyin.player.ui.design.PrintFrame
import io.lanyin.player.ui.design.Riso
import io.lanyin.player.ui.design.RisoCover
import io.lanyin.player.ui.design.RisoPicture
import io.lanyin.player.ui.design.RisoScenes
import io.lanyin.player.ui.design.RisoSheet
import io.lanyin.player.ui.design.TapeAt
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.drawInkBar
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.drawRegMark
import io.lanyin.player.ui.design.drawRisoStamp
import io.lanyin.player.ui.design.grainBrush
import io.lanyin.player.ui.design.overprint
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberHour
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PostcardSerifFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 暮: the home page is a riso printer's desk at the end of the day. Today's print is taped to the
 * cutting mat, the sun on it where it stands in the sky now (half sunk at sunset, a moon after
 * dark), the greeting overprinted in pink and blue and the margin written up in pencil; beside
 * it today's picks as a slip with a stamp to play them, and the print's three separations. The
 * ways in are a fan of ink swatches on a rivet, the recommended playlists are proofs of their
 * covers, each chart is a proof sheet, and a 2B pencil lies across the desk.
 */
@Composable
internal fun DuskHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { StudioHeader(model, Modifier.padding(horizontal = m)) }
        if (!model.signedIn) item { ConnectLine(model.login, Modifier.padding(start = m, end = m, top = 12.dp)) }
        item { TodaysPrint(model, Modifier.padding(start = m + 2.dp, end = m + 2.dp, top = 16.dp)) }
        item { DailySlip(model, Modifier.padding(start = m, end = m, top = 26.dp)) }
        item { SwatchFan(model, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item {
            Column(Modifier.padding(top = 26.dp)) {
                MatHeading("推荐歌单", if (model.recommended.isEmpty()) model.recommendedNote else "${model.recommended.size.coerceAtMost(8)} 张", Modifier.padding(horizontal = m))
                val lists = model.recommended.slots(6).take(8)
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(lists.size) { i ->
                        val p = lists[i]
                        PrintFrame(
                            Modifier.width(132.dp),
                            tilt = if (i % 2 == 0) -1.4f else 1.1f,
                            tapes = listOf(TapeAt.Top),
                            margin = 7.dp,
                            onClick = { if (p != null) model.openPlaylist(p) },
                            label = p?.name,
                            picture = { RisoCover(p?.coverUrl, p?.let { (it.id % 97).toInt() } ?: (10 + i), Modifier.fillMaxWidth().aspectRatio(1f)) },
                            caption = {
                                Column {
                                    PencilNote(p?.name ?: "· · ·", size = 14.sp, maxLines = 1)
                                    Text(p?.let { "${it.trackCount} 首" } ?: "", style = Lanyin.type.caption.copy(fontSize = 10.sp, lineHeight = 13.sp), color = Lanyin.colors.muted)
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 22.dp)) {
                MatHeading("排行榜", "全部", Modifier.padding(horizontal = m), onTrailing = model.openCharts)
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val charts = model.charts.slots(4)
                    items(charts.size) { i ->
                        val chart = charts[i]
                        ProofSheet(chart, i, { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }, Modifier.width(244.dp))
                    }
                }
            }
        }
        model.picks?.let { picks ->
            item { MatHeading(picks.title, picks.note, Modifier.padding(start = m, end = m, top = 26.dp)) }
            items(picks.songs.size) { i ->
                val song = picks.songs[i]
                PickRow(song, playing = song.id == model.player.playingSongId, onClick = { model.playPick(i) }, modifier = Modifier.padding(start = m, end = m - 10.dp, top = if (i == 0) 10.dp else 0.dp))
            }
        }
    }
}

/** A heading written on the mat in white pencil, with a note (or a way to see more) at its end. */
@Composable
private fun MatHeading(text: String, trailing: String?, modifier: Modifier, onTrailing: (() -> Unit)? = null) {
    val c = Lanyin.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        PencilNote(text, size = 22.sp, color = c.content)
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.padding(bottom = 8.dp).size(52.dp, 6.dp)) { drawInkBar(5.dp.toPx()) }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            PencilNote(trailing, size = 15.sp, color = c.muted, modifier = if (onTrailing != null) Modifier.pressable(onClick = onTrailing) else Modifier)
        }
    }
}

/** The date in pencil, the studio's name, and the settings and account. */
@Composable
private fun StudioHeader(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val now = model.now
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("RISO STUDIO · 暮", style = TextStyle(fontFamily = PostcardSerifFamily, fontStyle = FontStyle.Italic, fontSize = 12.sp, letterSpacing = 0.16.em), color = c.muted)
            PencilNote("${now.monthValue}月${now.dayOfMonth}日 · ${now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)}", size = 19.sp, color = c.content)
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/**
 * Today's print: the sea from the headland, the sun where it is now, the greeting overprinted
 * on the sky and the countdown to dark under it; the margin numbered, signed and annotated in
 * pencil, a registration mark and a note that the pink drum landed a little off.
 */
@Composable
private fun TodaysPrint(model: HomeModel, modifier: Modifier) {
    val hourState = rememberHour()
    val hour by hourState
    val now = model.now
    val sunset = RisoScenes.sunset(now.monthValue)
    val bucket = (hour * 6).toInt()
    val dark = sunset + 0.5f
    val countdown = when {
        hour < sunset - 3f -> "日落 ${clock(sunset)} · 还早，先听几首"
        hour < dark -> "日落 ${clock(sunset)} · 天黑前还有 ${((dark - hour) * 60).toInt().coerceAtLeast(1)} 分钟"
        else -> "日落 ${clock(sunset)} · 天黑了，开灯接着印"
    }
    val day = now.dayOfMonth
    val days = now.toLocalDate().lengthOfMonth()
    PrintFrame(
        modifier.fillMaxWidth(),
        tilt = -0.8f,
        margin = 12.dp,
        onClick = model.openDaily,
        label = "今天的印刷：每日推荐",
        picture = {
            Box(Modifier.fillMaxWidth().aspectRatio(1.06f)) {
                RisoPicture(listOf("evening", bucket, sunset), Modifier.fillMaxSize(), cell = 2.5.dp) { w, h -> RisoScenes.evening(w, h, bucket / 6f, sunset) }
                Column(Modifier.padding(14.dp)) {
                    // After dark the sky is violet: the greeting in yellow and blue, the note in yellow.
                    val night = hour >= sunset + 0.7f || hour < 5.5f
                    Overprint(greeting(hour.toInt()), TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = cappedSp(40f), lineHeight = cappedSp(46f)), under = if (night) Riso.Yellow else Riso.Pink)
                    Text(countdown, style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = cappedSp(12.5f)), color = if (night) Riso.Yellow else Riso.Blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "SUNSET  No.${now.dayOfYear}",
                    style = TextStyle(fontFamily = PostcardSerifFamily, fontSize = fixedSp(12f), letterSpacing = 0.22.em),
                    color = Riso.Paper.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        },
        caption = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PencilNote("暮 · 第 $day 号", size = 17.sp)
                    Spacer(Modifier.width(14.dp))
                    PencilNote("三色孔版", size = 15.sp)
                    Spacer(Modifier.weight(1f))
                    PencilNote("$day/$days", size = 18.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Canvas(Modifier.size(14.dp)) { drawRegMark(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, Riso.Graphite) }
                    Spacer(Modifier.weight(0.6f))
                    PencilNote("套准偏了一点 ↗", size = 14.sp, color = Riso.RedPencil)
                }
            }
        },
    )
}

private fun clock(hour: Float): String = "%d:%02d".format(hour.toInt(), ((hour - hour.toInt()) * 60).toInt())

/**
 * Today's picks as a slip of paper with a stamp to play them; beside it, on the mat, the three
 * separations of today's print, one drum each, fanned out under a pencil note.
 */
@Composable
private fun DailySlip(model: HomeModel, modifier: Modifier) {
    val hour by rememberHour()
    val sunset = RisoScenes.sunset(model.now.monthValue)
    val bucket = (hour * 6).toInt()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RisoSheet(Modifier.weight(1.35f), tilt = 0.8f, tapes = listOf(TapeAt.TopLeft), onClick = model.openDaily, label = "每日推荐", contentPadding = PaddingValues(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
            val c = Lanyin.colors
            Column {
                Text("TODAY'S PRINT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixedSp(9f), letterSpacing = 0.2.em), color = c.accent)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overprint("每日推荐", TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = cappedSp(23f), lineHeight = cappedSp(28f)))
                        Text(
                            model.pickSongs.firstOrNull()?.let { "${model.pickSongs.size} 首 · 从《${it.name}》开始" } ?: model.dailyNote,
                            style = Lanyin.type.caption.copy(fontSize = cappedSp(11f), lineHeight = cappedSp(15f)), color = c.muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(46.dp)
                            .drawBehind { drawRisoStamp(c.accent) }
                            .pressable(label = "播放每日推荐", onClick = model.playDaily),
                        contentAlignment = Alignment.Center,
                    ) { Canvas(Modifier.size(15.dp)) { drawPlayPauseGlyph(0f, c.onAccent) } }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            PencilNote("分色 ↓", size = 16.sp, color = Lanyin.colors.content, modifier = Modifier.padding(start = 4.dp))
            Box(Modifier.fillMaxWidth().height(96.dp).padding(top = 4.dp)) {
                for (k in 0 until 3) {
                    RisoSheet(
                        Modifier.offset(x = (k * 30).dp, y = (k * 5).dp).size(54.dp, 70.dp),
                        tilt = -7f + k * 7f,
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        RisoPicture(listOf("evening", bucket, sunset), Modifier.fillMaxSize(), cell = 2.dp, only = k) { w, h -> RisoScenes.evening(w, h, bucket / 6f, sunset) }
                    }
                }
            }
        }
    }
}

/**
 * The ways in as a fan of ink swatches riveted at the bottom: each a strip of card with its
 * name, the drum's code and a block of the ink (or two overprinted); the pencil lies beside it.
 */
@Composable
private fun SwatchFan(model: HomeModel, modifier: Modifier) {
    val doors = model.doors.drop(1)
    val inks = listOf(Riso.Pink, overprint(Riso.Pink, Riso.Yellow), Riso.Yellow, overprint(Riso.Yellow, Riso.Blue), Riso.Blue)
    val codes = listOf("FL.PINK", "ORANGE", "SUNFLOWER", "GREEN", "MED.BLUE")
    Box(modifier.fillMaxWidth().height(206.dp)) {
        val n = doors.size
        Box(Modifier.align(Alignment.BottomStart).padding(start = 46.dp).size(84.dp, 196.dp)) {
            doors.forEachIndexed { i, door ->
                val angle = -34f + 68f * i / (n - 1)
                RisoSheet(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(74.dp, 176.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.9f)
                            rotationZ = angle
                        },
                    onClick = door.onClick,
                    label = door.label,
                    contentPadding = PaddingValues(7.dp),
                ) {
                    val c = Lanyin.colors
                    Column {
                        Text(door.label, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(12.5f), lineHeight = fixedSp(15f)), color = c.content, maxLines = 1)
                        Text(codes[i % codes.size], style = TextStyle(fontFamily = PostcardSerifFamily, fontSize = fixedSp(7.5f), letterSpacing = 0.12.em), color = c.muted, maxLines = 1)
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .height(62.dp)
                                .drawBehind {
                                    drawRect(inks[i % inks.size])
                                    drawRect(grainBrush, alpha = 0.16f, blendMode = BlendMode.Multiply)
                                },
                            contentAlignment = Alignment.BottomEnd,
                        ) { ThemedIcon(door.icon, null, tint = Color.White.copy(alpha = 0.9f), size = 16.dp, modifier = Modifier.padding(5.dp)) }
                        Text(door.note, style = TextStyle(fontSize = fixedSp(8.5f), lineHeight = fixedSp(11f)), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            // The rivet the fan turns on.
            Canvas(Modifier.align(Alignment.BottomCenter).offset(y = (-14).dp).size(11.dp)) {
                drawCircle(Color.Black.copy(alpha = 0.35f), size.minDimension / 2f, Offset(size.width / 2f + 1f, size.height / 2f + 1.5f))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9AA0A6), Color(0xFF5D6268)), Offset(size.width * 0.35f, size.height * 0.3f), size.minDimension * 0.6f), size.minDimension / 2f)
            }
        }
        PencilNote("色票 · 点一张进去", size = 15.sp, color = Lanyin.colors.muted, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 4.dp))
        Pencil(Modifier.align(Alignment.BottomEnd).offset(x = 8.dp).size(224.dp, 64.dp), angle = -16f)
    }
}

/**
 * A chart as a proof sheet: its name overprinted, the first three songs listed with their
 * numbers in the blue, as pulled to check before the run.
 */
@Composable
private fun ProofSheet(chart: ChartPreview?, slot: Int, onClick: () -> Unit, modifier: Modifier) {
    RisoSheet(modifier, tilt = if (slot % 2 == 0) 0.7f else -0.6f, tapes = listOf(TapeAt.TopLeft), onClick = onClick, label = chart.nameOr(slot), contentPadding = PaddingValues(14.dp)) {
        val c = Lanyin.colors
        val stops = chart?.top.orEmpty().take(3)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROOF %02d".format(slot + 1), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixedSp(9f), letterSpacing = 0.18.em), color = c.accent)
                Spacer(Modifier.weight(1f))
                PencilNote("试印 ${slot + 1}", size = 13.sp)
            }
            Overprint(chart.nameOr(slot), TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = cappedSp(19f), lineHeight = cappedSp(24f)), Modifier.padding(top = 2.dp, bottom = 8.dp))
            for (k in 0 until 3) {
                val song = stops.getOrNull(k)
                Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${k + 1}", style = TextStyle(fontFamily = PostcardSerifFamily, fontSize = fixedSp(20f)), color = Riso.Blue, modifier = Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song?.name ?: "· · ·", style = Lanyin.type.body.copy(fontWeight = if (k == 0) FontWeight.SemiBold else FontWeight.Normal), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (song != null) Text(song.artistLine, style = Lanyin.type.caption.copy(fontSize = fixedSp(10.5f), lineHeight = fixedSp(13f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
