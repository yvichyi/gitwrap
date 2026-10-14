package io.lanyin.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.ComingSoonCard

@Composable
fun LibraryScreen(onLogin: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { FilledTonalButton(onClick = onLogin) { Text("登录网易云音乐") } }
        item { ComingSoonCard("我喜欢的音乐", "M3", "同步你在网易云的红心歌曲") }
        item { ComingSoonCard("创建的歌单", "M3", "") }
        item { ComingSoonCard("收藏的歌单", "M3", "") }
    }
}
