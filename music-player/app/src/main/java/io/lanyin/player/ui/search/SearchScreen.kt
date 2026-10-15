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
import androidx.compose.ui.draw.clip
import io.lanyin.player.ui.design.pressable

/**
 * Search songs as you type (after a short pause). Tap a result to play the results from there.
 * [initialResults] stands in for the network in previews.
 */
@Composable
fun SearchScreen(
    player: PlayerState? = null,
    initialQuery: String = "",
    initialResults: List<Song>? = null,
    onOpenArtist: (Long, String) -> Unit = { _, _ -> },
    onOpenAlbum: (Long, String) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    val library = LocalAppServices.current?.library
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var kind by rememberSaveable { mutableStateOf(SearchKind.Songs) }
    var results by remember { mutableStateOf<Load<SearchResults>?>(initialResults?.let { Load.Ready(SearchResults.Songs(it)) }) }
    var attempt by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query, kind, attempt) {
        val q = query.trim()
        if (q.isEmpty() || library == null) {
            if (library != null) results = null
            return@LaunchedEffect
        }
        delay(400)
        results = Load.Loading
        results = try {
            Load.Ready(
                when (kind) {
                    SearchKind.Songs -> SearchResults.Songs(library.search(q))
                    SearchKind.Artists -> SearchResults.Artists(library.searchArtists(q))
                    SearchKind.Albums -> SearchResults.Albums(library.searchAlbums(q))
                    SearchKind.Playlists -> SearchResults.Playlists(library.searchPlaylists(q))
                },
            )
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
            item {
                ChoiceRow(
                    SearchKind.entries.map { it.label }, kind.label, { label -> kind = SearchKind.entries.first { it.label == label } },
                    Modifier.padding(start = m, end = m, top = 6.dp, bottom = 6.dp),
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
                    val value = r.value
                    if (value.isEmpty) item { Placeholder("没有找到「${query.trim()}」") }
                    when (value) {
                        is SearchResults.Songs -> itemsIndexed(value.items, key = { i, s -> "${s.id}-$i" }) { i, song ->
                            SongRow(
                                song, i + 1,
                                playing = song.id == player?.playingSongId,
                                isPlaying = player?.isPlaying == true,
                                onClick = {
                                    keyboard?.hide()
                                    player?.play(value.items, i, "搜索「${query.trim()}」")
                                },
                                modifier = Modifier.padding(horizontal = m),
                            )
                        }
                        is SearchResults.Artists -> itemsIndexed(value.items, key = { _, a -> a.id }) { _, a ->
                            ResultRow(a.pictureUrl, a.name, "${a.albumCount} 张专辑", round = true, seed = (a.id % 97).toInt()) { onOpenArtist(a.id, a.name) }
                        }
                        is SearchResults.Albums -> itemsIndexed(value.items, key = { _, a -> a.id }) { _, a ->
                            ResultRow(a.coverUrl, a.name, listOf(a.artistName, "${a.size} 首").filter { it.isNotEmpty() }.joinToString(" · "), round = false, seed = (a.id % 97).toInt()) { onOpenAlbum(a.id, a.name) }
                        }
                        is SearchResults.Playlists -> itemsIndexed(value.items, key = { _, p -> p.id }) { _, p ->
                            io.lanyin.player.ui.music.PlaylistRow(p, { onOpenPlaylist(p.id, p.name) }, Modifier.padding(horizontal = m))
                        }
                    }
                }
            }
        }
    }
}

private enum class SearchKind(val label: String) { Songs("单曲"), Artists("歌手"), Albums("专辑"), Playlists("歌单") }

private sealed interface SearchResults {
    val isEmpty: Boolean
    data class Songs(val items: List<Song>) : SearchResults { override val isEmpty get() = items.isEmpty() }
    data class Artists(val items: List<io.lanyin.player.core.model.ArtistHit>) : SearchResults { override val isEmpty get() = items.isEmpty() }
    data class Albums(val items: List<io.lanyin.player.core.model.AlbumHit>) : SearchResults { override val isEmpty get() = items.isEmpty() }
    data class Playlists(val items: List<io.lanyin.player.core.model.Playlist>) : SearchResults { override val isEmpty get() = items.isEmpty() }
}

/** An artist or album result: picture, name, a line about it. */
@Composable
private fun ResultRow(imageUrl: String?, title: String, subtitle: String, round: Boolean, seed: Int, onClick: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = style.margin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        io.lanyin.player.ui.design.CoverImage(imageUrl, seed, 56.dp, Modifier.size(56.dp).clip(if (round) style.roundShape else style.artShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = style.type.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp)
    }
}
