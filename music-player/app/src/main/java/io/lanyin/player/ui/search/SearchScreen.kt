package io.lanyin.player.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.PageTitle
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.sin

private val categories = listOf("单曲", "歌手", "专辑", "歌单", "歌词")

@Composable
fun SearchScreen() {
    val style = Lanyin.style
    val c = style.colors
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = pagePadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { PageTitle("搜索", Modifier.padding(horizontal = 20.dp), subtitle = "歌曲、歌手、专辑、歌单，还有一句记得的歌词") }
            item {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .themedSurface(shape = style.chipShape)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.content),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("想听什么？", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                                field()
                            }
                        },
                    )
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(categories) { cat -> ThemedChip(cat, selected = cat == category, onClick = { category = cat }) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SoundBars(Modifier.size(180.dp, 96.dp))
                    Text("想听点什么？", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                    Text(
                        "登录后，这里会出现热门搜索和你的搜索记录。",
                        style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** An equaliser-like row of bars in the theme's accents. */
@Composable
private fun SoundBars(modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Canvas(modifier) {
        val n = 11
        val gap = size.width / (n * 2f)
        val barW = gap
        for (i in 0 until n) {
            val f = 0.25f + 0.75f * (0.5f + 0.5f * sin(i * 1.3f + 0.6f)) * (1f - kotlin.math.abs(i - n / 2) / n.toFloat())
            val h = size.height * f
            val x = i * (barW + gap) + gap / 2
            drawRoundRect(
                Brush.verticalGradient(listOf(c.accent, c.accentAlt), startY = size.height - h, endY = size.height),
                Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2),
            )
        }
    }
}
