package io.lanyin.player.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemeMotif
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.design.ThemedTextField
import io.lanyin.player.ui.music.Load
import io.lanyin.player.ui.music.Placeholder
import io.lanyin.player.ui.music.SongRow
import io.lanyin.player.ui.music.describe
import io.lanyin.player.ui.player.PlayerState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Search songs as you type (after a short pause). Tap a result to play the results from there.
 * [initialResults] stands in for the network in previews.
 */
@Composable
fun SearchScreen(player: PlayerState? = null, initialQuery: String = "", initialResults: List<Song>? = null) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    val library = LocalAppServices.current?.library
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<Load<List<Song>>?>(initialResults?.let { Load.Ready(it) }) }
    var attempt by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query, attempt) {
        val q = query.trim()
        if (q.isEmpty() || library == null) {
            if (library != null) results = null
            return@LaunchedEffect
        }
        delay(400)
        results = Load.Loading
        results = try {
            Load.Ready(library.search(q))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(describe(e))
        }
    }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { PageTitle("搜索", Modifier.padding(horizontal = m), subtitle = "歌名、歌手或专辑") }
            item {
                ThemedTextField(
                    query, { query = it }, "想听什么？",
                    Modifier.padding(start = m, end = m, top = 24.dp, bottom = 8.dp),
                    leading = LineIcon.Search,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }
            when (val r = results) {
                null -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = m * 2, vertical = 56.dp),
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
                Load.Loading -> item { Placeholder("正在找…") }
                is Load.Failed -> item { Placeholder(r.message, "重试") { attempt++ } }
                is Load.Ready -> {
                    if (r.value.isEmpty()) item { Placeholder("没有找到「${query.trim()}」") }
                    itemsIndexed(r.value, key = { i, s -> "${s.id}-$i" }) { i, song ->
                        SongRow(
                            song, i + 1,
                            playing = song.id == player?.playingSongId,
                            isPlaying = player?.isPlaying == true,
                            onClick = {
                                keyboard?.hide()
                                player?.play(r.value, i, "搜索「${query.trim()}」")
                            },
                            modifier = Modifier.padding(horizontal = m),
                        )
                    }
                }
            }
        }
    }
}
