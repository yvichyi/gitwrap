package io.lanyin.player.ui.player

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.PlayerSkin
import kotlin.math.abs

@Composable
fun PlayerScreen(
    state: DemoPlayerState,
    skin: PlayerSkin,
    onCollapse: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val c = MaterialTheme.colorScheme
    // No Surface here (the background is a gradient), so set the content colour explicitly.
    CompositionLocalProvider(LocalContentColor provides c.onSurface) {
        Box(
            Modifier
                .fillMaxSize()
                // Stand-in for M4's cover-derived flowing background.
                .background(Brush.verticalGradient(listOf(c.primaryContainer, c.surface, c.surface)))
                .safeDrawingPadding(),
        ) {
            if (landscape) LandscapePlayer(state, skin, onCollapse) else PortraitPlayer(state, skin, onCollapse)
        }
    }
}

/**
 * QQ 音乐-style landscape page: artwork, title and transport on the left, a full-height lyric
 * column on the right, one slim seek bar along the bottom. Height is the scarce dimension here.
 */
@Composable
private fun LandscapePlayer(state: DemoPlayerState, skin: PlayerSkin, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        PlayerTopBar(onCollapse)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (skin == PlayerSkin.LYRICS) {
                Column(Modifier.fillMaxSize()) {
                    LyricsPanel(state, centered = true, modifier = Modifier.weight(1f).fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackTitle(state, Modifier.weight(1f))
                        TransportControls(state, playSize = 48.dp, Modifier.width(320.dp))
                    }
                }
            } else {
                Column(
                    Modifier.weight(0.42f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PlayerArtwork(skin, state.isPlaying, Modifier.fillMaxHeight())
                    }
                    TrackTitle(state, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    TransportControls(state, playSize = 48.dp, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(32.dp))
                LyricsPanel(state, centered = false, modifier = Modifier.weight(0.58f).fillMaxHeight())
            }
        }
        SeekBar(state, Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun PortraitPlayer(state: DemoPlayerState, skin: PlayerSkin, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        PlayerTopBar(onCollapse)
        if (skin == PlayerSkin.LYRICS) {
            LyricsPanel(state, centered = true, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayerArtwork(skin, state.isPlaying, Modifier.fillMaxWidth())
            }
            CurrentLyricLine(state, Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        TrackTitle(state)
        Spacer(Modifier.height(8.dp))
        SeekBar(state)
        TransportControls(state, playSize = 64.dp, Modifier.fillMaxWidth().padding(vertical = 12.dp))
    }
}

@Composable
private fun PlayerTopBar(onCollapse: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起") }
        Spacer(Modifier.weight(1f))
        Text(
            "无损 · 演示",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = {}) { Icon(Icons.Filled.MoreHoriz, contentDescription = "更多") }
    }
}

@Composable
private fun TrackTitle(state: DemoPlayerState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.artist} · ${state.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = {}) { Icon(Icons.Filled.FavoriteBorder, contentDescription = "喜欢") }
    }
}

@Composable
private fun CurrentLyricLine(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val (line, translation) = state.lyrics[state.currentLyricIndex]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Scrolling lyric list; the real word-by-word (YRC) renderer replaces this in M4. */
@Composable
private fun LyricsPanel(state: DemoPlayerState, centered: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val current = state.currentLyricIndex
    BoxWithConstraints(modifier) {
        val padding = maxHeight / 2
        // The active line rests 40% of the way down, leaving room for what's coming next.
        val anchorPx = with(LocalDensity.current) { (maxHeight * 0.4f).roundToPx() }
        LaunchedEffect(current, anchorPx) {
            listState.animateScrollToItem(index = current + 1, scrollOffset = -anchorPx)
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                // Fade lines out at the top and bottom edges instead of clipping them.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.18f to Color.Black,
                            0.82f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            state = listState,
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Spacer(Modifier.height(padding)) }
            itemsIndexed(state.lyrics) { index, (line, translation) ->
                LyricLine(
                    line = line,
                    translation = translation,
                    distance = abs(index - current),
                    centered = centered,
                    onClick = { state.seekTo(index * DemoPlayerState.LYRIC_STEP_MS) },
                )
            }
            item { Spacer(Modifier.height(padding)) }
        }
    }
}

@Composable
private fun LyricLine(line: String, translation: String, distance: Int, centered: Boolean, onClick: () -> Unit) {
    val active = distance == 0
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        label = "lyricColor",
    )
    val scale by animateFloatAsState(if (active) 1.08f else 1f, label = "lyricScale")
    // Neighbouring lines stay sharp; only lines two or more away soften (no-op below Android 12).
    val blur: Dp = ((distance - 1).coerceIn(0, 3) * 0.6f).dp
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
            }
            .blur(blur),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            line,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = color,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            translation,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = color.alpha * 0.75f),
        )
    }
}

/** Time · slim track · time, on one row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        Slider(
            value = state.positionMs.toFloat(),
            onValueChange = { state.seekTo(it.toLong()) },
            valueRange = 0f..state.durationMs.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            thumb = { Box(Modifier.size(12.dp).background(c.primary, CircleShape)) },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}

@Composable
private fun TransportControls(state: DemoPlayerState, playSize: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}) { Icon(Icons.Filled.Repeat, contentDescription = "播放模式") }
        IconButton(onClick = { state.seekTo(0) }) { Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首") }
        FilledIconButton(onClick = state::togglePlay, modifier = Modifier.size(playSize)) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                modifier = Modifier.size(playSize * 0.55f),
            )
        }
        IconButton(onClick = { state.seekTo(state.durationMs) }) { Icon(Icons.Filled.SkipNext, contentDescription = "下一首") }
        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列") }
    }
}
