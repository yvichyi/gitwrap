package io.lanyin.player.ui.home

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
 * 报: the home page as a front page. Under the masthead the lead story is today's picks, with a
 * big halftone photograph and the day's running order beside it; then a column of briefs (what's
 * playing, the other ways in) next to the almanac's 宜 and 忌; the charts set as tables in two
 * columns; the recommended playlists as a page of captioned photographs; and the picks as a
 * numbered list. Signing in is advertised in a boxed notice, as a paper takes subscriptions.
 */
@Composable
internal fun PaperHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Header(model.now, model.account, model.openSettings, model.openAccount, Modifier.padding(horizontal = m)) }
        item { LeadStory(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        if (!model.signedIn) item { SubscribeNotice(model.login, Modifier.padding(start = m, end = m, top = 22.dp)) }
        item { Briefs(model, Modifier.padding(start = m, end = m, top = 24.dp)) }
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
            style = style.type.display.copy(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, letterSpacing = 0.01.em),
            color = c.content,
            modifier = Modifier.padding(top = 4.dp),
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

/** A column of briefs (what's playing, the other ways in) beside the almanac's box. */
@Composable
private fun Briefs(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val player = model.player
    val day = model.now.dayOfYear + model.now.year
    Column(modifier) {
        DoubleRule()
        Row(Modifier.padding(top = 8.dp).height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f)) {
                Text("简讯", style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content)
                if (!player.isEmpty) {
                    Brief("正在播放", "《${player.title}》${player.artist}", model.openPlayer, lead = true)
                }
                model.doors.drop(1).forEach { door -> Brief(door.label, door.note, door.onClick) }
            }
            VRule(Modifier.padding(horizontal = 12.dp))
            Column(Modifier.width(104.dp)) {
                Text("今日宜忌", style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content)
                Spacer(Modifier.height(6.dp))
                Almanac("宜", Fitting[day % Fitting.size], lucky = true)
                Spacer(Modifier.height(6.dp))
                Almanac("忌", Avoid[(day * 3) % Avoid.size], lucky = false)
                Spacer(Modifier.weight(1f))
                Text(
                    "每日一换，\n仅供一笑。",
                    style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 11.sp, lineHeight = 15.sp),
                    color = c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Brief(label: String, note: String, onClick: () -> Unit, lead: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).pressable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 8.dp).width(5.dp).height(5.dp).background(if (lead) c.accent else c.content))
        Text(
            buildBrief(label, note),
            style = style.type.caption.copy(fontSize = 13.sp),
            color = c.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = style.type.caption, color = c.muted)
    }
}

@Composable
private fun buildBrief(label: String, note: String) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    append(label)
    pop()
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Lanyin.colors.muted))
    append("　$note")
    pop()
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
