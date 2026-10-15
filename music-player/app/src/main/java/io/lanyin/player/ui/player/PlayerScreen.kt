package io.lanyin.player.ui.player

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.BiasAlignment
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
import androidx.compose.ui.text.style.TextAlign
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
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PlayerSkin
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the landscape chrome stays up without a touch. */
private const val CHROME_TIMEOUT_MS = 5_000L

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

    // Landscape is a stage: hide system bars, keep the screen on, fold the chrome away when idle.
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
    // Any touch restarts the idle timer; a tap on the empty stage toggles full screen.
    var touches by remember { mutableIntStateOf(0) }
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(landscape) { if (!landscape) chromeVisible = true }
    LaunchedEffect(landscape, touches, chromeVisible) {
        if (landscape && chromeVisible) {
            delay(CHROME_TIMEOUT_MS)
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
                LandscapePlayer(
                    state, skin, spin, chromeVisible, onToggleChrome = { chromeVisible = !chromeVisible },
                    dismissDrag, onCollapse, onRotate, lyricScale,
                )
            } else {
                PortraitPlayer(state, skin, spin, dismissDrag, onCollapse, onRotate, lyricScale)
            }
        }
    }
}

/**
 * Top to bottom: where the music is playing from, the artwork with a few lines of lyrics under it,
 * title and artist, the progress line with its times beneath, transport, and a quiet row of
 * secondary actions. Everything rises into place in turn when the player opens; the artwork
 * settles back when paused and throws a ripple on every beat while playing. Swipe the artwork to
 * change track; tap it (or the lyrics) for the full lyrics. Dragging the progress line scrubs the
 * lyrics along with it.
 */
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
    val style = Lanyin.style
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyricsOnly = skin == PlayerSkin.LYRICS || showLyrics
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        PlayerTopBar(state, onCollapse, modifier = dismissDrag.enter(entrance, 0))
        AnimatedContent(
            targetState = lyricsOnly,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "artOrLyrics",
        ) { lyrics ->
            val textStyle = lyricTextStyle(LyricSize.BASE_SP, lyricScale)
            if (lyrics) {
                StateLyrics(state, textStyle, centered = style.lyric.centered, anchorFraction = 0.4f, scrubMs = scrubMs, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().then(dismissDrag),
                        // Sit a little low so artwork and lyrics read as one group.
                        contentAlignment = BiasAlignment(0f, 0.45f),
                    ) {
                        // A record or tape settles back a little when paused; covers already do.
                        val settle by animateFloatAsState(
                            if (state.isPlaying || skin == PlayerSkin.COVER) 1f else 0.95f,
                            style.bouncy.spec(), label = "artSettle",
                        )
                        Box(
                            artSize(skin, maxWidth, maxHeight)
                                .beatRipples(skin, state.isPlaying)
                                .artworkBackdrop(skin)
                                .graphicsLayer {
                                    scaleX = settle
                                    scaleY = settle
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            SwipeToSkip(state) {
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
                    // A short window onto the lyrics: the line being sung with its neighbours.
                    StateLyrics(
                        state,
                        textStyle = lyricTextStyle(16f, lyricScale),
                        centered = true,
                        anchorFraction = 0.5f,
                        interactive = false,
                        showTranslation = false,
                        scrubMs = scrubMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .enter(entrance, 2)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { showLyrics = true },
                    )
                }
            }
        }
        TrackTitle(state, Modifier.padding(top = 14.dp).enter(entrance, 3))
        SeekBar(
            state::positionMs, state.durationMs, state.isPlaying, state::seekTo,
            Modifier.padding(top = 10.dp).enter(entrance, 4),
            timesBelow = true, onScrub = { scrubMs = it },
        )
        TransportControls(state, playSize = 72.dp, Modifier.fillMaxWidth().padding(top = 4.dp).enter(entrance, 5))
        UtilityRow(
            state,
            onToggleLyrics = if (skin == PlayerSkin.LYRICS) null else ({ showLyrics = !showLyrics }),
            lyricsShown = lyricsOnly,
            onRotate = onRotate,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).enter(entrance, 6),
        )
    }
}

/** A square for records and covers, the tape's own proportions for the cassette, fitted to the box. */
private fun artSize(skin: PlayerSkin, maxWidth: Dp, maxHeight: Dp): Modifier =
    if (skin == PlayerSkin.CASSETTE) {
        Modifier.width(minOf(maxWidth, maxHeight * CASSETTE_RATIO))
    } else {
        Modifier.size(minOf(maxWidth, maxHeight))
    }

private const val CASSETTE_RATIO = 1.58f

/**
 * Landscape is a stage between a top bar and a control bar. The top bar carries the collapse
 * button with the title beside it, as a window title; the stage holds the artwork in its
 * spectrum halo and the lyrics, the active line level with the artwork's centre. Tap the empty
 * stage (or wait five seconds) for full screen: the bars fold away and a quiet status line with
 * the title and the time takes their place.
 */
@Composable
private fun LandscapePlayer(
    state: DemoPlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    lyricScale: Float,
) {
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeTopBar(state, onCollapse, onRotate, modifier = dismissDrag.enter(entrance, 0))
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { onToggleChrome() } },
        ) {
            if (skin == PlayerSkin.LYRICS) {
                StageLyrics(
                    state, chromeVisible,
                    textStyle = lyricTextStyle(LyricSize.BASE_SP * 1.2f, lyricScale),
                    centered = true,
                    scrubMs = scrubMs,
                    modifier = Modifier.fillMaxSize().enter(entrance, 1),
                )
            } else {
                BoxWithConstraints(
                    Modifier.weight(0.42f).fillMaxHeight().then(dismissDrag),
                    contentAlignment = Alignment.Center,
                ) {
                    // Leave room around the artwork for the spectrum halo, whichever side is short.
                    val halo = 20.dp
                    Box(
                        artSize(skin, maxWidth - halo * 2, maxHeight * 0.82f).spectrumHalo(skin, state.isPlaying),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The halo stays put while the record is swiped away and the next slides in.
                        SwipeToSkip(state) {
                            PlayerArtwork(skin, state.isPlaying, spin, state.coverSeed, Modifier.fillMaxWidth().sharedPlayer("artwork", zIndex = 1f))
                        }
                    }
                }
                Spacer(Modifier.width(36.dp))
                val inscribed = Lanyin.style.edge == EdgeKind.Hairline && !chromeVisible
                val inscriptionRoom by animateDpAsState(if (inscribed) 76.dp else 0.dp, tween(400), label = "inscriptionRoom")
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    // Left-aligned beside the artwork so the lyrics read as one column.
                    StageLyrics(
                        state, chromeVisible,
                        textStyle = lyricTextStyle(LyricSize.BASE_SP, lyricScale),
                        centered = false,
                        scrubMs = scrubMs,
                        modifier = Modifier.fillMaxSize().padding(end = inscriptionRoom).enter(entrance, 1),
                    )
                    InscriptionOverlay(inscribed, state, Modifier.align(Alignment.TopEnd).padding(top = 12.dp))
                }
            }
        }
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeControlBar(state, onScrub = { scrubMs = it }, Modifier.padding(top = 8.dp).enter(entrance, 2))
        }
        AnimatedVisibility(!chromeVisible, enter = fadeIn(tween(600)), exit = fadeOut(tween(150))) {
            // 墨 writes the title in its inscription instead.
            ImmersiveStatus(state, showTitle = !(Lanyin.style.edge == EdgeKind.Hairline && skin != PlayerSkin.LYRICS))
        }
    }
}

/** 墨's inscription, fading in a moment after the stage goes full screen. */
@Composable
private fun InscriptionOverlay(visible: Boolean, state: DemoPlayerState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(900, delayMillis = 300)), exit = fadeOut(tween(200))) {
        Inscription(state)
    }
}

/**
 * Lyrics with a footer of where the track is from and what plays next. The anchor is worked out
 * from the measured footer so the active line stays at the stage's vertical centre, level with
 * the artwork, while the footer folds in and out.
 */
@Composable
private fun StageLyrics(
    state: DemoPlayerState,
    chromeVisible: Boolean,
    textStyle: TextStyle,
    centered: Boolean,
    modifier: Modifier = Modifier,
    scrubMs: Long? = null,
) {
    var footerPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val total = constraints.maxHeight
        val lyricsPx = (total - footerPx).coerceAtLeast(1)
        val anchor = (total / 2f / lyricsPx).coerceIn(0.25f, 0.75f)
        Column(Modifier.fillMaxSize()) {
            StateLyrics(state, textStyle, centered, anchor, scrubMs = scrubMs, modifier = Modifier.weight(1f).fillMaxWidth())
            AnimatedVisibility(
                chromeVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.onSizeChanged { footerPx = it.height },
            ) {
                StageFooter(state, centered)
            }
        }
    }
}

/** The track's lyrics, or a quiet note for an instrumental. */
@Composable
private fun StateLyrics(
    state: DemoPlayerState,
    textStyle: TextStyle,
    centered: Boolean,
    anchorFraction: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showTranslation: Boolean = true,
    /** Where the progress line is being dragged: the lyrics follow it before the seek lands. */
    scrubMs: Long? = null,
) {
    if (state.lyrics.isEmpty()) {
        Instrumental(textStyle, centered, modifier)
    } else {
        val index = scrubMs?.let { lyricIndexAt(state.lyrics, it) } ?: state.currentLyricIndex
        val scrubbing = scrubMs != null
        LyricsView(
            state.lyrics, index, { if (scrubbing) 0f else state.lineProgress() }, state::seekTo,
            textStyle = textStyle, modifier = modifier, centered = centered, anchorFraction = anchorFraction,
            interactive = interactive, showTranslation = showTranslation,
        )
    }
}

@Composable
private fun Instrumental(textStyle: TextStyle, centered: Boolean, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                "纯音乐，请欣赏",
                style = textStyle.copy(
                    fontFamily = if (style.lyric.serif) style.display else textStyle.fontFamily,
                    fontWeight = if (style.lyric.serif) FontWeight.Medium else FontWeight.Bold,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                ),
                color = c.content.copy(alpha = 0.7f),
            )
            Text("这首歌没有歌词", style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Where the track is playing from, and what comes next. */
@Composable
private fun StageFooter(state: DemoPlayerState, centered: Boolean) {
    val style = Lanyin.style
    val c = style.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        ThemedIcon(LineIcon.Note, null, tint = c.muted, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            buildAnnotatedString {
                append(state.playlistName)
                withStyle(SpanStyle(fontFamily = style.numeric)) { append("  ${state.trackNumber} / ${state.trackCount}") }
            },
            style = style.type.caption, color = c.muted, maxLines = 1,
        )
        if (centered) Spacer(Modifier.width(28.dp)) else Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
        Text("下一首", style = style.type.label, color = c.muted)
        Spacer(Modifier.width(8.dp))
        Text(
            state.next.title, style = style.type.caption, color = c.content.copy(alpha = 0.72f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

/** What is playing and the time, in place of the bars while the stage is full screen. */
@Composable
private fun ImmersiveStatus(state: DemoPlayerState, showTitle: Boolean = true) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberMinuteClock()
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showTitle) {
                    buildAnnotatedString {
                        append(state.title)
                        withStyle(SpanStyle(color = c.muted)) { append("  ·  ${state.artist}") }
                    }
                } else {
                    buildAnnotatedString { withStyle(SpanStyle(color = c.muted)) { append("${state.playlistName}  ${state.trackNumber} / ${state.trackCount}") } }
                },
                style = style.type.caption,
                color = c.content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "%02d:%02d".format(now.hour, now.minute),
                style = style.type.caption.copy(fontFamily = style.numeric, fontWeight = FontWeight.Medium),
                color = c.muted,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            drawRect(c.content.copy(alpha = 0.12f))
            drawRect(c.accent, size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
        }
    }
}

/** The current time, updated on the minute. */
@Composable
private fun rememberMinuteClock(): State<LocalTime> = produceState(LocalTime.now()) {
    while (true) {
        val now = LocalTime.now()
        value = now
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(200L))
    }
}

/** Collapse, the title beside it like a window title, and the track's tags and actions. */
@Composable
private fun LandscapeTopBar(state: DemoPlayerState, onCollapse: () -> Unit, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, iconSize = 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            TrackSwitch(state, Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true)) { track ->
                Text(track.title, style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TrackSwitch(state) { track ->
                Text("${track.artist} · ${track.album}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        QualityTags(state)
        LikeButton(state.liked, onToggle = { state.liked = !state.liked })
        IconAction(LineIcon.Rotate, "切换到竖屏", onRotate)
        IconAction(LineIcon.More, "更多", {})
    }
}

/** The quality tag plus the stream format: a chip in most themes, a quiet tracked label in 墨. */
@Composable
private fun QualityTags(state: DemoPlayerState, chipFirst: Boolean = false) {
    val style = Lanyin.style
    if (style.quietSections) {
        Text("${state.quality} · ${state.format}", style = style.type.label, color = style.colors.muted, maxLines = 1)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chipFirst) {
                QualityChip(state.quality)
                Spacer(Modifier.width(8.dp))
                Text(state.format, style = style.type.caption, color = style.colors.muted, maxLines = 1)
            } else {
                Text(state.format, style = style.type.caption, color = style.colors.muted, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                QualityChip(state.quality)
            }
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
        maxLines = 1,
        modifier = Modifier
            .clip(style.chipShape)
            .border(1.dp, c.accent.copy(alpha = 0.6f), style.chipShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun LandscapeControlBar(state: DemoPlayerState, onScrub: (Long?) -> Unit, modifier: Modifier = Modifier) {
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
            SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.weight(1f).padding(end = 12.dp), onScrub = onScrub)
            TransportControls(state, playSize = 46.dp, Modifier.width(264.dp))
        }
    }
}

/**
 * Lyric face at [baseSp] scaled by the user's 歌词大小, line height 1.36×. It deliberately ignores
 * the system font scale: many phones ship with a large default font, which made lyrics far bigger
 * than intended, and 歌词大小 already lets anyone who wants them bigger say so.
 */
@Composable
private fun lyricTextStyle(baseSp: Float, scale: Float): TextStyle {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val size = baseSp * scale / fontScale
    return MaterialTheme.typography.headlineSmall.copy(fontSize = size.sp, lineHeight = (size * 1.36f).sp)
}

/** Collapse, where the music is playing from (centred, like a page title), and more. */
@Composable
private fun PlayerTopBar(state: DemoPlayerState, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, Modifier.align(Alignment.CenterStart), iconSize = 30.dp)
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("正在播放", style = style.type.label, color = c.muted, maxLines = 1)
            Text(
                state.playlistName, style = style.type.caption.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconAction(LineIcon.More, "更多", {}, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun TrackTitle(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TrackSwitch(state, Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true)) { track ->
                Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TrackSwitch(state, Modifier.padding(top = 2.dp)) { track ->
                Text(
                    "${track.artist} · ${track.album}", style = MaterialTheme.typography.bodyMedium, color = c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LikeButton(state.liked, onToggle = { state.liked = !state.liked })
    }
}

/**
 * Per-track text that slides out the way you skipped while the next slides in after it. The
 * wrapper keeps its place (and any shared-element key), only the content changes.
 */
@Composable
private fun TrackSwitch(state: DemoPlayerState, modifier: Modifier = Modifier, content: @Composable (DemoTrack) -> Unit) {
    AnimatedContent(
        targetState = state.track,
        modifier = modifier,
        transitionSpec = {
            val dir = if (state.skipDirection < 0) -1 else 1
            (slideInHorizontally(tween(340)) { it / 5 * dir } + fadeIn(tween(300, delayMillis = 60))) togetherWith
                (slideOutHorizontally(tween(220)) { -it / 5 * dir } + fadeOut(tween(160))) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.CenterStart,
        label = "trackText",
    ) { track -> content(track) }
}

/** The quieter actions under the transport: quality on the left, lyrics and rotation on the right. */
@Composable
private fun UtilityRow(
    state: DemoPlayerState,
    onToggleLyrics: (() -> Unit)?,
    lyricsShown: Boolean,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lanyin.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QualityTags(state, chipFirst = true)
        Spacer(Modifier.weight(1f))
        if (onToggleLyrics != null) {
            IconAction(LineIcon.Lyrics, if (lyricsShown) "显示封面" else "显示歌词", onToggleLyrics, tint = if (lyricsShown) c.accent else c.muted)
        }
        IconAction(LineIcon.Rotate, "切换到横屏", onRotate, tint = c.muted)
    }
}

/** Transport; skip buttons give a little push in their direction, the mode icon pops as it changes. */
@Composable
private fun TransportControls(state: DemoPlayerState, playSize: Dp, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(0f) }
    val forward = remember { Animatable(0f) }
    fun push(a: Animatable<Float, *>, dir: Float) {
        scope.launch {
            a.animateTo(dir, tween(80))
            a.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            state.playMode, label = "playMode",
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.5f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.5f)) },
        ) { mode ->
            IconAction(
                when (mode) {
                    PlayMode.REPEAT_ALL -> LineIcon.Repeat
                    PlayMode.REPEAT_ONE -> LineIcon.RepeatOne
                    PlayMode.SHUFFLE -> LineIcon.Shuffle
                },
                mode.label, state::cyclePlayMode, tint = c.muted,
            )
        }
        IconAction(
            LineIcon.Previous, "上一首", { state.skipPrevious(); push(back, -1f) },
            Modifier.graphicsLayer { translationX = back.value * 7.dp.toPx() }, iconSize = 34.dp,
        )
        PlayPauseButton(state.isPlaying, state::togglePlay, playSize)
        IconAction(
            LineIcon.Next, "下一首", { state.skipNext(); push(forward, 1f) },
            Modifier.graphicsLayer { translationX = forward.value * 7.dp.toPx() }, iconSize = 34.dp,
        )
        IconAction(LineIcon.Queue, "播放队列", {}, tint = c.muted)
    }
}
