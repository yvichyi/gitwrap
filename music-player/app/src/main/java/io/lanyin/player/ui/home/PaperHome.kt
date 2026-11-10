package io.lanyin.player.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import io.lanyin.player.ui.design.drawBarcode
import io.lanyin.player.ui.design.letterpress
import io.lanyin.player.ui.design.inked
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.HalftoneCover
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.theme.Lanyin

private val Fitting = listOf("单曲循环", "听一张新专辑", "戴好耳机", "跟着唱", "慢慢听完一首", "收藏一个歌单", "翻翻老歌", "听现场版")
private val Avoid = listOf("切歌太快", "只听前奏", "外放", "开得太响", "错过副歌", "熬夜听歌", "跳过尾奏")

/**
 * 报: the home page as a front page. Under the masthead the lead story is today's picks, its
 * headline pressed by letterpress, a big halftone photograph and the day's running order beside
 * it; then the classifieds (the other ways in as small boxed ads, what's playing as a stop-press
 * line, the almanac's 宜 and 忌 in the last box); the charts set as tables in two columns; the
 * recommended playlists as a page of captioned photographs; the picks as a numbered list; and the
 * colophon at the foot. Signing in is advertised in a boxed notice, as a paper takes subscriptions.
 */
@Composable
internal fun PaperHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Header(model.now, model.account, model.openSettings, model.openAccount, Modifier.padding(horizontal = m)) }
        item { LeadStory(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        if (!model.signedIn) item { SubscribeNotice(model.login, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item { Classifieds(model, Modifier.padding(start = m, end = m, top = 24.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("排行榜", index = 2, trailing = "每日更新")
                ChartTables(model, Modifier.padding(top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("推荐歌单", index = 3, trailing = model.recommendedNote)
                PhotoPage(model, Modifier.padding(top = 14.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle(picks.title, Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note, index = 4) }
            items(picks.songs.size) { i ->
                NumberedSong(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
        item { Colophon(model, Modifier.padding(start = m, end = m, top = 26.dp)) }
    }
}

/** Today's picks as the lead: kicker, headline, deck, then the photograph with the running order beside it. */
@Composable
private fun LeadStory(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val songs = model.pickSongs
    Column(modifier) {
        Text("头版 · 每日推荐", style = style.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.16.em), color = c.accent)
        Text(
            if (model.signedIn) "今天的 ${songs.size.coerceAtLeast(1)} 首歌已经送达" else "每日推荐：每天清晨六点送达",
            style = style.type.display.copy(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, letterSpacing = 0.01.em).inked(c.content),
            color = c.content,
            modifier = Modifier.padding(top = 4.dp).letterpress(),
        )
        Text(
            if (model.signedIn) "本报讯　按你最近常听的挑选，从《${songs.firstOrNull()?.name ?: "第一首"}》开始。" else "本报讯　扫码登录网易云音乐，每天按你的口味挑一份歌单。今天先看看大家在听什么。",
            style = style.type.body.copy(fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 21.sp),
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.weight(0.58f).aspectRatio(1f).pressable(onClick = model.playDaily)) {
                val lead = songs.firstOrNull()
                if (lead != null) HalftoneCover(lead.album.coverUrl, (lead.id % 97).toInt(), Modifier.fillMaxSize())
                else CoverArt(3, Modifier.fillMaxSize())
                // A red stamp to press: 「播放」.
                Text(
                    "▶ 播放",
                    style = style.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.onAccent,
                    modifier = Modifier.align(Alignment.BottomEnd).background(c.accent).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(0.42f).fillMaxSize()) {
                Text("今日歌目", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.content)
                Rule(Modifier.padding(top = 3.dp, bottom = 4.dp), thickness = 0.75.dp)
                val shown = songs.take(5)
                if (shown.isEmpty()) {
                    Text("（待刊）", style = style.type.caption, color = c.muted)
                } else {
                    shown.forEachIndexed { i, song ->
                        Text(
                            "${chineseIndex(i + 1)}　${song.name}",
                            style = style.type.caption,
                            color = c.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "转第二版 ›",
                    style = style.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.accent,
                    modifier = Modifier.pressable(onClick = model.openDaily).padding(vertical = 4.dp),
                )
            }
        }
        Text(
            "图：每日推荐　本报记者 澜音",
            style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 11.sp),
            color = c.muted,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** 「订阅启事」: the login, as a boxed notice in the paper. */
@Composable
private fun SubscribeNotice(onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(
        modifier
            .fillMaxWidth()
            .border(2.dp, c.content)
            .padding(3.dp)
            .border(0.75.dp, c.content)
            .pressable(onClick = onLogin)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("订 阅 启 事", style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content)
        Text(
            "扫码登录网易云音乐，即可同步歌单、红心与每日推荐。",
            style = style.type.caption,
            color = c.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("扫码订阅 ›", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, modifier = Modifier.padding(top = 4.dp))
    }
}

/** What each way in advertises under, and the number to ring. */
private val AdHeads = listOf("电台" to "FM 0101", "寻物" to "♥ 5201", "行情" to "TOP 0100", "旧闻" to "☎ 0423", "存货" to "↓ 0808")

/**
 * The classifieds: what's playing as a stop-press line across the top, then the other ways in as
 * small boxed ads two to a row (a heading in a black bar, the name in heavy type, the note, a
 * number to ring), and in the last box the almanac's 宜 and 忌.
 */
@Composable
private fun Classifieds(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val player = model.player
    val day = model.now.dayOfYear + model.now.year
    Column(modifier) {
        DoubleRule()
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("分类广告", style = style.type.title.copy(fontWeight = FontWeight.Black).inked(c.content), color = c.content, modifier = Modifier.letterpress())
            Spacer(Modifier.weight(1f))
            Text("刊登热线　澜音广告部", style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 11.sp), color = c.muted, maxLines = 1)
        }
        if (!player.isEmpty) {
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().pressable(onClick = model.openPlayer), verticalAlignment = Alignment.CenterVertically) {
                Text("号外", style = style.type.caption.copy(fontWeight = FontWeight.Black), color = c.onAccent, modifier = Modifier.background(c.accent).padding(horizontal = 5.dp, vertical = 1.dp))
                Text(
                    "　《${player.title}》${player.artist}　正在播放",
                    style = style.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = c.content,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text("›", style = style.type.caption, color = c.muted)
            }
        }
        val doors = model.doors.drop(1)
        val cells = doors.size + 1
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until (cells + 1) / 2) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in 0..1) {
                        val i = row * 2 + k
                        val cell = Modifier.weight(1f).fillMaxHeight().border(0.75.dp, c.content)
                        when {
                            i < doors.size -> {
                                val door = doors[i]
                                val (head, phone) = AdHeads[i % AdHeads.size]
                                Column(cell.pressable(label = door.label, onClick = door.onClick).padding(bottom = 6.dp)) {
                                    Text(
                                        "【$head】", style = style.type.caption.copy(fontWeight = FontWeight.Black, fontSize = 11.sp),
                                        color = c.bg, maxLines = 1, modifier = Modifier.fillMaxWidth().background(c.content).padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                    Text(door.label, style = style.type.title.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp))
                                    Text(door.note, style = style.type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                                    Spacer(Modifier.weight(1f))
                                    Text("电话　$phone", style = style.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = if (i == 0) c.accent else c.content, maxLines = 1, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                                }
                            }
                            i == doors.size -> Column(cell.padding(6.dp)) {
                                Text("今日宜忌", style = style.type.caption.copy(fontWeight = FontWeight.Black, fontSize = 12.sp), color = c.content)
                                Spacer(Modifier.height(4.dp))
                                Almanac("宜", Fitting[day % Fitting.size], lucky = true)
                                Spacer(Modifier.height(4.dp))
                                Almanac("忌", Avoid[(day * 3) % Avoid.size], lucky = false)
                                Text("每日一换，仅供一笑。", style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 10.sp), color = c.muted, modifier = Modifier.padding(top = 4.dp))
                            }
                            else -> Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The colophon at the foot of the page, as every paper prints it: where it's published, the
 * postcode, the printer, the price and the issue's barcode.
 */
@Composable
private fun Colophon(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        DoubleRule()
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("本报地址：你的耳机里　邮编 000000", style = style.type.caption.copy(fontSize = 10.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("印刷：澜音印刷厂　本期 8 版　每份 0.00 元", style = style.type.caption.copy(fontSize = 10.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("第 ${model.now.dayOfYear} 期　欢迎投稿，欢迎订阅", style = style.type.caption.copy(fontSize = 10.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.Canvas(Modifier.padding(start = 10.dp).size(64.dp, 26.dp)) {
                drawBarcode(c.content, model.now.dayOfYear + model.now.year)
            }
        }
    }
}

@Composable
private fun Almanac(mark: String, text: String, lucky: Boolean) {
    val style = Lanyin.style
    val c = style.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            mark,
            style = style.type.caption.copy(fontWeight = FontWeight.Bold),
            color = c.bg,
            modifier = Modifier.background(if (lucky) c.accent else c.content).padding(horizontal = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = style.type.caption, color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The charts as tables, two to a row with a rule between, each its name and top three. */
@Composable
private fun ChartTables(model: HomeModel, modifier: Modifier) {
    val charts = model.charts.slots(4)
    Column(modifier) {
        for (row in 0 until (charts.size + 1) / 2) {
            if (row > 0) Rule(Modifier.padding(vertical = 12.dp), thickness = 0.75.dp, color = Lanyin.colors.outline)
            Row(Modifier.height(IntrinsicSize.Min)) {
                ChartTable(charts[row * 2], row * 2, model, Modifier.weight(1f))
                VRule(Modifier.padding(horizontal = 12.dp))
                if (row * 2 + 1 < charts.size) ChartTable(charts[row * 2 + 1], row * 2 + 1, model, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartTable(chart: ChartPreview?, slot: Int, model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })) {
        Text(chart.nameOr(slot), style = style.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Black), color = c.content, maxLines = 1)
        Text(chart?.playlist?.updateNote ?: "正在排版", style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 11.sp), color = c.muted, maxLines = 1)
        val top = chart?.top.orEmpty().take(3)
        for (i in 0 until 3) {
            val song = top.getOrNull(i)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = style.type.caption.copy(fontWeight = FontWeight.Black), color = if (i == 0) c.accent else c.content, modifier = Modifier.width(14.dp))
                Text(song?.name ?: "——", style = style.type.caption, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A page of photographs: the playlists' covers in halftone, three across, captioned underneath. */
@Composable
private fun PhotoPage(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until (lists.size + 2) / 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in 0..2) {
                    val slot = row * 3 + k
                    if (slot >= lists.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val p = lists[slot]
                    Column(Modifier.weight(1f).pressable(onClick = { if (p != null) model.openPlaylist(p) })) {
                        if (p != null) HalftoneCover(p.coverUrl, (p.id % 97).toInt(), Modifier.fillMaxWidth().aspectRatio(1f))
                        else CoverArt(10 + slot, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(
                            p?.name ?: "稿件待到",
                            style = style.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = c.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A song in a numbered list: its number large in the margin, then title and artist. */
@Composable
private fun NumberedSong(index: Int, song: Song, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 20.sp),
                color = if (playing) c.accent else c.content.copy(alpha = 0.85f),
                modifier = Modifier.width(40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(song.artistLine, song.album.name).filter { it.isNotEmpty() }.joinToString(" · "), style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Rule(thickness = 0.5.dp, color = c.outline)
    }
}
