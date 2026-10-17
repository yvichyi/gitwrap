package io.lanyin.player.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.HotSearch
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.ThemeMotif
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.music.RowNumber
import io.lanyin.player.ui.music.shortCount
import io.lanyin.player.ui.theme.Lanyin

/**
 * Search before anything is typed: the words searched here lately, then 热搜榜. Either picks its
 * words into the field. With neither (offline, nothing searched yet) the theme's motif asks what
 * to play.
 */
internal fun LazyListScope.searchStart(history: List<String>, hot: List<HotSearch>, onPick: (String) -> Unit, onClearHistory: () -> Unit) {
    if (history.isEmpty() && hot.isEmpty()) {
        item { Invitation() }
        return
    }
    if (history.isNotEmpty()) {
        item { HistoryHead(onClearHistory) }
        item { HistoryWords(history, onPick) }
    }
    if (hot.isNotEmpty()) {
        item {
            val m = Lanyin.style.margin
            SectionTitle(
                "热搜榜", Modifier.padding(start = m, end = m, top = 30.dp, bottom = 8.dp),
                trailing = "大家在搜", index = if (history.isEmpty()) 1 else 2,
            )
        }
        itemsIndexed(hot.take(20), key = { i, h -> "hot-$i-${h.word}" }) { i, h -> HotRow(i + 1, h) { onPick(h.word) } }
    }
}

@Composable
private fun Invitation() {
    val style = Lanyin.style
    val c = style.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = style.margin * 2, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemeMotif(c.content.copy(alpha = 0.22f), Modifier.size(120.dp))
        Text("想听点什么？", style = style.type.title, modifier = Modifier.padding(top = 20.dp))
        Text(
            "输入歌名、歌手或专辑，边打字边找。",
            style = style.type.caption, color = c.muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HistoryHead(onClear: () -> Unit) {
    val style = Lanyin.style
    val m = style.margin
    Row(Modifier.fillMaxWidth().padding(start = m, end = m, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("最近搜过", Modifier.weight(1f), index = 1)
        Spacer(Modifier.width(12.dp))
        Text(
            "清空", style = style.type.caption, color = style.colors.muted,
            modifier = Modifier.pressable(onClick = onClear).padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryWords(words: List<String>, onPick: (String) -> Unit) {
    val m = Lanyin.style.margin
    FlowRow(
        Modifier.fillMaxWidth().padding(start = m, end = m, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { w -> ThemedChip(if (w.length > 14) w.take(13) + "…" else w, false, { onPick(w) }) }
    }
}

/** One line of the chart: its place (the first three in the accent), the words, NetEase's note and how hot. */
@Composable
private fun HotRow(place: Int, hot: HotSearch, onClick: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val top = place <= 3
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = style.margin, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp)) { RowNumber(place, strong = top) }
        Column(Modifier.weight(1f)) {
            Text(
                hot.word, style = if (top) style.type.body.copy(fontWeight = FontWeight.Bold) else style.type.body,
                color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (hot.note != null) {
                Text(hot.note, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (hot.score > 0) {
            Spacer(Modifier.width(12.dp))
            Text(shortCount(hot.score.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), style = style.type.caption.copy(fontFamily = style.numeric), color = c.muted, maxLines = 1)
        }
    }
}
