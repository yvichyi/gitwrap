package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin

/**
 * Pull down to fetch again, with the theme's own loading mark as the indicator (see
 * [StatusMark]): it comes down with the finger, filling in as the pull nears enough, and moves
 * while [refreshing].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedPullRefresh(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    val style = Lanyin.style
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val clock by rememberFrameClock(refreshing)
            val pulled = state.distanceFraction.coerceIn(0f, 1.4f)
            val shown = if (refreshing) 1f else pulled
            if (shown > 0.01f) {
                Canvas(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .offset(y = (-40).dp + 76.dp * shown.coerceAtMost(1f))
                        .size(48.dp)
                        .graphicsLayer { alpha = shown.coerceAtMost(1f) },
                ) {
                    // Pulling winds the mark on; once let go it runs by itself.
                    drawLoading(style, if (refreshing) clock else pulled * 1.2f)
                }
            }
        },
        content = content,
    )
}
