package io.lanyin.player.ui.player

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LikeButton
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    keepScreenOnInLandscape: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val style = Lanyin.style
    val c = style.colors
    val orientation = LocalOrientationController.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 0 = resting, 1 = fully pulled away; driven by drag-down and predictive back.
    var dismiss by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { dismiss = it.progress * 0.5f }
            onCollapse()
        } catch (e: CancellationException) {
            scope.launch { animate(dismiss, 0f, animationSpec = style.motion.spec()) { v, _ -> dismiss = v } }
            throw e
        }
    }
    val dismissDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dismiss > 0.22f) onCollapse()
                else scope.launch { animate(dismiss, 0f, animationSpec = style.motion.spec()) { v, _ -> dismiss = v } }
            },
        ) { change, dy ->
            change.consume()
            dismiss = (dismiss + dy / (size.height * 1.2f)).coerceIn(0f, 1f)
        }
    }

    // Landscape is a stage: hide system bars, keep the screen on, fade the chrome when idle.
    DisposableEffect(landscape) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(landscape, keepScreenOnInLandscape) {
        view.keepScreenOn = landscape && keepScreenOnInLandscape
        onDispose { view.keepScreenOn = false }
    }
    var touches by remember { mutableIntStateOf(0) }
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(landscape, touches) {
        chromeVisible = true
        if (landscape) {
            delay(5_000)
            chromeVisible = false
        }
    }

    val onRotate = { if (landscape) orientation.lockPortrait() else orientation.lockLandscape() }

    CompositionLocalProvider(LocalContentColor provides c.content) {
        Box(
            modifier
                .fillMaxSize()
                .sharedPlayer("player-container", container = true)
                .graphicsLayer {
                    val p = dismiss
                    val s = 1f - 0.1f * p
                    scaleX = s
                    scaleY = s
                    translationY = p * 96.dp.toPx()
                    shape = RoundedCornerShape(36.dp * p)
                    clip = p > 0f
                }
                .background(c.bg)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) touches++
                        }
                    }
                },
        ) {
            Backdrop(Modifier.matchParentSize(), live = true, playing = state.isPlaying)
            if (landscape) {
                LandscapePlayer(state, skin, spin, chromeVisible, dismissDrag, onCollapse, onRotate)
            } else {
                PortraitPlayer(state, skin, spin, dismissDrag, onCollapse, onRotate)
            }
        }
    }
}

@Composable
private fun PortraitPlayer(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyricsOnly = skin == PlayerSkin.LYRICS || showLyrics
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        PlayerTopBar(
            landscape = false, onCollapse = onCollapse, onRotate = onRotate,
            onToggleLyrics = if (skin == PlayerSkin.LYRICS) null else ({ showLyrics = !showLyrics }),
            lyricsShown = lyricsOnly, modifier = dismissDrag,
        )
        AnimatedContent(
            targetState = lyricsOnly,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "artOrLyrics",
        ) { lyrics ->
            if (lyrics) {
                LyricsView(
                    lines = state.lyrics, currentIndex = state.currentLyricIndex, progress = state::lineProgress,
                    onSeek = state::seekTo, textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp),
                    modifier = Modifier.fillMaxSize(), anchorFraction = 0.4f,
                )
            } else {
                Box(Modifier.fillMaxSize().then(dismissDrag), contentAlignment = Alignment.Center) {
                    PlayerArtwork(
                        skin, state.isPlaying, spin, state.coverSeed,
                        Modifier
                            .fillMaxWidth()
                            .sharedPlayer("artwork", zIndex = 1f)
                            .pressable { showLyrics = true },
                    )
                }
            }
        }
        if (!lyricsOnly) CurrentLine(state, Modifier.fillMaxWidth().padding(vertical = 12.dp))
        TrackTitle(state, Modifier.padding(top = 8.dp))
        SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.padding(top = 10.dp))
        TransportControls(state, playSize = 72.dp, Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp))
    }
}

@Composable
private fun LandscapePlayer(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    chromeVisible: Boolean,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
) {
    val lyricStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp)
    Column(
        Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            PlayerTopBar(landscape = true, onCollapse = onCollapse, onRotate = onRotate, onToggleLyrics = null, lyricsShown = true, modifier = dismissDrag)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (skin == PlayerSkin.LYRICS) {
                Column(Modifier.fillMaxSize()) {
                    LyricsView(
                        state.lyrics, state.currentLyricIndex, state::lineProgress, state::seekTo,
                        textStyle = lyricStyle.copy(fontSize = 32.sp, lineHeight = 42.sp),
                        modifier = Modifier.weight(1f).fillMaxWidth(), centered = true, anchorFraction = 0.42f,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackTitle(state, Modifier.weight(1f))
                        AnimatedVisibility(chromeVisible) { TransportControls(state, playSize = 52.dp, Modifier.widthIn(max = 340.dp)) }
                    }
                }
            } else {
                Column(Modifier.weight(0.42f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth().then(dismissDrag), contentAlignment = Alignment.Center) {
                        PlayerArtwork(skin, state.isPlaying, spin, state.coverSeed, Modifier.fillMaxHeight().sharedPlayer("artwork", zIndex = 1f))
                    }
                    TrackTitle(state, Modifier.fillMaxWidth())
                    AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        TransportControls(state, playSize = 52.dp, Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.width(36.dp))
                LyricsView(
                    state.lyrics, state.currentLyricIndex, state::lineProgress, state::seekTo,
                    textStyle = lyricStyle, modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
        }
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo)
        }
    }
}

@Composable
private fun PlayerTopBar(
    landscape: Boolean,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    onToggleLyrics: (() -> Unit)?,
    lyricsShown: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.Filled.KeyboardArrowDown, "收起", onCollapse, iconSize = 30.dp)
        Spacer(Modifier.weight(1f))
        Text(
            "无损",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = style.numeric, fontWeight = FontWeight.SemiBold),
            color = c.accent,
            modifier = Modifier
                .clip(style.chipShape)
                .border(1.dp, c.accent.copy(alpha = 0.6f), style.chipShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(4.dp))
        if (onToggleLyrics != null) {
            IconAction(Icons.Filled.Lyrics, if (lyricsShown) "显示封面" else "显示歌词", onToggleLyrics, tint = if (lyricsShown) c.accent else c.content)
        }
        IconAction(Icons.Filled.ScreenRotation, if (landscape) "切换到竖屏" else "切换到横屏", onRotate)
        IconAction(Icons.Filled.MoreHoriz, "更多", {})
    }
}

@Composable
private fun TrackTitle(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true),
            )
            Text(
                "${state.artist} · ${state.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LikeButton(state.liked, onToggle = { state.liked = !state.liked })
    }
}

@Composable
private fun CurrentLine(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val line = state.lyrics[state.currentLyricIndex]
    AnimatedContent(
        targetState = line,
        transitionSpec = {
            (fadeIn(tween(220)) + androidx.compose.animation.slideInVertically { it / 2 }) togetherWith
                (fadeOut(tween(140)) + androidx.compose.animation.slideOutVertically { -it / 2 })
        },
        modifier = modifier.height(48.dp),
        contentAlignment = Alignment.Center,
        label = "currentLine",
    ) { l ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SweepText(
            text = l.text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = if (style.lyric.serif) style.display else MaterialTheme.typography.titleMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
            baseColor = c.content.copy(alpha = 0.45f),
            sweepColor = c.accent,
            progress = if (l == state.lyrics[state.currentLyricIndex]) state::lineProgress else null,
            glow = if (style.lyric.glow) c.accent else null,
        ) }
    }
}

@Composable
private fun TransportControls(state: DemoPlayerState, playSize: Dp, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(state.playMode, label = "playMode", transitionSpec = { fadeIn() togetherWith fadeOut() }) { mode ->
            IconAction(
                when (mode) {
                    PlayMode.REPEAT_ALL -> Icons.Filled.Repeat
                    PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                },
                mode.label, state::cyclePlayMode, tint = c.muted,
            )
        }
        IconAction(Icons.Filled.SkipPrevious, "上一首", { state.seekTo(0) }, iconSize = 34.dp)
        PlayPauseButton(state.isPlaying, state::togglePlay, playSize)
        IconAction(Icons.Filled.SkipNext, "下一首", { state.seekTo(state.durationMs) }, iconSize = 34.dp)
        IconAction(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", {}, tint = c.muted)
    }
}
