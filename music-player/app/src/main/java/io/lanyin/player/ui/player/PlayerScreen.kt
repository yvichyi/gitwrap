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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LikeButton
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.home.chineseNumber
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PlayerSkin
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
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
    lyricScale: Float = 1f,
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
                LandscapePlayer(state, skin, spin, chromeVisible, dismissDrag, onCollapse, onRotate, lyricScale)
            } else {
                PortraitPlayer(state, skin, spin, dismissDrag, onCollapse, onRotate, lyricScale)
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
    lyricScale: Float,
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
            onCollapse = onCollapse, onRotate = onRotate,
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
                    onSeek = state::seekTo, textStyle = lyricTextStyle(LyricSize.BASE_SP, lyricScale),
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

/**
 * Landscape is a stage plus one control bar. The artwork sits in a spectrum halo on the left; the
 * right column carries the track header, the lyrics (active line level with the artwork's centre)
 * and what plays next. When the chrome hides, the stage takes the room, the header trades its
 * tags for a desk clock, and only a hairline of progress remains.
 */
@Composable
private fun LandscapePlayer(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    chromeVisible: Boolean,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    lyricScale: Float,
) {
    val style = Lanyin.style
    Column(
        Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeTopBar(state, onCollapse, onRotate, modifier = dismissDrag)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (skin == PlayerSkin.LYRICS) {
                Box(Modifier.fillMaxSize()) {
                    // No artwork to ring, so the spectrum rises faintly from the floor instead.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.56f)
                            .height(56.dp)
                            .spectrumFloor(state.isPlaying, alpha = 0.4f),
                    )
                    StageLyrics(
                        state, chromeVisible,
                        textStyle = lyricTextStyle(LyricSize.BASE_SP * 1.2f, lyricScale),
                        centered = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                BoxWithConstraints(
                    Modifier.weight(0.42f).fillMaxHeight().then(dismissDrag),
                    contentAlignment = Alignment.Center,
                ) {
                    // Leave room around the artwork for the spectrum halo, whichever side is short.
                    val halo = 20.dp
                    val side = minOf(maxHeight * 0.82f, maxWidth - halo * 2)
                    val artSize = if (skin == PlayerSkin.CASSETTE) {
                        Modifier.width(minOf(maxWidth - halo * 2, maxHeight * 0.82f * 1.58f))
                    } else {
                        Modifier.size(side)
                    }
                    PlayerArtwork(
                        skin, state.isPlaying, spin, state.coverSeed,
                        artSize
                            .spectrumHalo(skin, state.isPlaying)
                            .sharedPlayer("artwork", zIndex = 1f),
                    )
                }
                Spacer(Modifier.width(36.dp))
                // Left-aligned beside the artwork, so header, lyrics and next-up share one edge.
                StageLyrics(
                    state, chromeVisible,
                    textStyle = lyricTextStyle(LyricSize.BASE_SP, lyricScale),
                    centered = false,
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
        }
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeControlBar(state, Modifier.padding(top = 8.dp))
        }
        AnimatedVisibility(!chromeVisible, enter = fadeIn(tween(600)), exit = fadeOut(tween(150))) {
            val c = style.colors
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                drawRect(c.content.copy(alpha = 0.12f))
                drawRect(c.accent, size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
            }
        }
    }
}

/**
 * Track header, lyrics and the next-up line in one column. The lyrics' anchor is worked out from
 * the measured header and footer so the active line stays at the stage's vertical centre, level
 * with the artwork, while the chrome folds in and out.
 */
@Composable
private fun StageLyrics(
    state: DemoPlayerState,
    chromeVisible: Boolean,
    textStyle: TextStyle,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    var headerPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val total = constraints.maxHeight
        val lyricsPx = (total - headerPx - footerPx).coerceAtLeast(1)
        val anchor = ((total / 2f - headerPx) / lyricsPx).coerceIn(0.25f, 0.75f)
        Column(Modifier.fillMaxSize()) {
            TrackHeader(state, chromeVisible, Modifier.onSizeChanged { headerPx = it.height }.padding(bottom = 6.dp))
            LyricsView(
                state.lyrics, state.currentLyricIndex, state::lineProgress, state::seekTo,
                textStyle = textStyle, modifier = Modifier.weight(1f).fillMaxWidth(),
                centered = centered, anchorFraction = anchor,
            )
            NextUp(state, Modifier.align(Alignment.End).onSizeChanged { footerPx = it.height })
        }
    }
}

/** Title and artist; on the right, quality tags and the heart, or a desk clock when idle. */
@Composable
private fun TrackHeader(state: DemoPlayerState, chromeVisible: Boolean, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title, style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true),
            )
            Text(
                "${state.artist} · ${state.album}", style = style.type.caption, color = c.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        AnimatedContent(
            targetState = chromeVisible,
            transitionSpec = { fadeIn(tween(500, delayMillis = 150)) togetherWith fadeOut(tween(200)) },
            contentAlignment = Alignment.CenterEnd,
            label = "headerTrailing",
        ) { visible ->
            if (visible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityTags(state)
                    LikeButton(state.liked, onToggle = { state.liked = !state.liked })
                }
            } else {
                DeskClock()
            }
        }
    }
}

/** 「无损」 plus the stream format: a chip in most themes, a quiet tracked label in 墨. */
@Composable
private fun QualityTags(state: DemoPlayerState) {
    val style = Lanyin.style
    if (style.quietSections) {
        Text("${state.quality} · ${state.format}", style = style.type.label, color = style.colors.muted)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.format, style = style.type.caption, color = style.colors.muted)
            Spacer(Modifier.width(8.dp))
            QualityChip(state.quality)
        }
    }
}

@Composable
private fun QualityChip(text: String) {
    val style = Lanyin.style
    val c = style.colors
    Text(
        text,
        style = style.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = c.accent,
        modifier = Modifier
            .clip(style.chipShape)
            .border(1.dp, c.accent.copy(alpha = 0.6f), style.chipShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Shown while the landscape stage is idle, so a phone on a stand doubles as a bedside clock. */
@Composable
private fun DeskClock(modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberMinuteClock()
    val weekday = now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.CHINA)
    val date = if (style.quietSections) {
        "${chineseNumber(now.monthValue)}月${chineseNumber(now.dayOfMonth)}日 $weekday"
    } else {
        "${now.monthValue}月${now.dayOfMonth}日 $weekday"
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            "%02d:%02d".format(now.hour, now.minute),
            style = style.type.display.copy(fontFamily = style.numeric, fontSize = 28.sp, lineHeight = 30.sp),
            color = c.content.copy(alpha = 0.86f),
        )
        Text(date, style = style.type.caption, color = c.muted)
    }
}

/** The current time, updated on the minute. */
@Composable
private fun rememberMinuteClock(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(200L))
    }
}

@Composable
private fun NextUp(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("下一首", style = style.type.label, color = c.muted)
        Spacer(Modifier.width(10.dp))
        Text(
            "${state.nextTitle} · ${state.nextArtist}", style = style.type.caption,
            color = c.content.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Collapse, where the track is playing from, and the screen actions. */
@Composable
private fun LandscapeTopBar(state: DemoPlayerState, onCollapse: () -> Unit, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, iconSize = 30.dp)
        Spacer(Modifier.width(4.dp))
        Row(
            if (style.quietSections) Modifier
            else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Flat).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemedIcon(LineIcon.Note, null, tint = c.muted, size = 15.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                buildAnnotatedString {
                    append(state.playlistName)
                    withStyle(SpanStyle(color = c.muted, fontFamily = style.numeric)) {
                        append("   ${state.trackNumber} / ${state.trackCount}")
                    }
                },
                style = if (style.quietSections) style.type.label else style.type.caption,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        IconAction(LineIcon.Rotate, "切换到竖屏", onRotate)
        IconAction(LineIcon.More, "更多", {})
    }
}

@Composable
private fun LandscapeControlBar(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val docked = style.dock == DockKind.Docked
    Column(modifier) {
        if (docked) Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .then(if (docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
                .padding(start = if (docked) 0.dp else 18.dp, end = if (docked) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.weight(1f).padding(end = 12.dp))
            TransportControls(state, playSize = 46.dp, Modifier.width(264.dp))
        }
    }
}

/** Lyric face at [baseSp] (scaled by the user's 歌词大小), line height 1.36×. */
@Composable
private fun lyricTextStyle(baseSp: Float, scale: Float) = MaterialTheme.typography.headlineSmall.copy(
    fontSize = (baseSp * scale).sp,
    lineHeight = (baseSp * scale * 1.36f).sp,
)

@Composable
private fun PlayerTopBar(
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    onToggleLyrics: (() -> Unit)?,
    lyricsShown: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, iconSize = 30.dp)
        Spacer(Modifier.weight(1f))
        if (style.quietSections) {
            Text("无损", style = style.type.label, color = c.muted)
        } else {
            QualityChip("无损")
        }
        Spacer(Modifier.width(4.dp))
        if (onToggleLyrics != null) {
            IconAction(LineIcon.Lyrics, if (lyricsShown) "显示封面" else "显示歌词", onToggleLyrics, tint = if (lyricsShown) c.accent else c.content)
        }
        IconAction(LineIcon.Rotate, "切换到横屏", onRotate)
        IconAction(LineIcon.More, "更多", {})
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
                fontWeight = if (style.lyric.serif) FontWeight.Medium else FontWeight.SemiBold,
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
                    PlayMode.REPEAT_ALL -> LineIcon.Repeat
                    PlayMode.REPEAT_ONE -> LineIcon.RepeatOne
                    PlayMode.SHUFFLE -> LineIcon.Shuffle
                },
                mode.label, state::cyclePlayMode, tint = c.muted,
            )
        }
        IconAction(LineIcon.Previous, "上一首", { state.seekTo(0) }, iconSize = 34.dp)
        PlayPauseButton(state.isPlaying, state::togglePlay, playSize)
        IconAction(LineIcon.Next, "下一首", { state.seekTo(state.durationMs) }, iconSize = 34.dp)
        IconAction(LineIcon.Queue, "播放队列", {}, tint = c.muted)
    }
}
