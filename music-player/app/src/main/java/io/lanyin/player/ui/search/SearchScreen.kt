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
import io.lanyin.player.ui.design.ThemeMotif
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

private val categories = listOf("单曲", "歌手", "专辑", "歌单", "歌词")

@Composable
fun SearchScreen() {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { PageTitle("搜索", Modifier.padding(horizontal = m), subtitle = "歌曲、歌手、专辑、歌单，或一句记得的歌词") }
            item { SearchField(query, { query = it }, Modifier.padding(start = m, end = m, top = 24.dp)) }
            item { ChoiceRow(categories, category, { category = it }, Modifier.padding(start = m, end = m, top = 20.dp)) }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = m * 2, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ThemeMotif(c.content.copy(alpha = 0.22f), Modifier.size(120.dp))
                    Text("想听点什么？", style = style.type.title, modifier = Modifier.padding(top = 20.dp))
                    Text(
                        "登录后，这里会出现热门搜索和你的搜索记录。",
                        style = style.type.caption, color = c.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** An underlined line in hairline themes, a field in the theme's chip shape elsewhere. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(if (hairline) Modifier else Modifier.themedSurface(shape = style.chipShape).padding(horizontal = 18.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemedIcon(LineIcon.Search, null, tint = c.muted, size = 22.dp)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = style.type.body.copy(color = c.content),
                cursorBrush = SolidColor(c.content),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("想听什么？", style = style.type.body, color = c.muted)
                        field()
                    }
                },
            )
        }
        if (hairline) Hairline()
    }
}
