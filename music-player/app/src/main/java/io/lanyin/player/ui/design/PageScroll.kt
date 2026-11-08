package io.lanyin.player.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** Material's emphasized decelerate: quick off the mark, settling slowly. */
internal val SettleEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val REVEAL_MS = 820
/** Rows past this many come in at once: they are below the fold when the list first shows. */
private const val REVEAL_ROWS = 14
private const val REVEAL_STAGGER = 0.045f
private const val REVEAL_SPAN = 0.42f

/**
 * A 0→1 timeline that plays once when a list first has its rows (and again when [key] changes).
 * Rows read it in the draw phase through [revealed]; once it has played, rows composed later as
 * the list scrolls are simply there. With motion reduced it starts at the end.
 */
@Composable
fun rememberReveal(key: Any?): State<Float> {
    val reduced = LocalReduceMotion.current
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing)) }
    return progress.asState()
}

/** Rise and fade into place at this row's turn of [reveal]; rows far down the list don't wait. */
fun Modifier.revealed(reveal: State<Float>, index: Int): Modifier =
    if (index >= REVEAL_ROWS) {
        this
    } else {
        graphicsLayer {
            val local = ((reveal.value - index * REVEAL_STAGGER) / REVEAL_SPAN).coerceIn(0f, 1f)
            val p = SettleEasing.transform(local)
            alpha = p
            translationY = (1f - p) * 18.dp.toPx()
        }
    }

/**
 * For a page's hero, the item at [index]: as the page scrolls it drifts up slower than the list
 * and fades, so the rows slide over it. Draw phase only.
 */
fun Modifier.parallaxAway(state: LazyListState, index: Int): Modifier = graphicsLayer {
    val first = state.firstVisibleItemIndex
    val off = when {
        first < index -> 0f
        first == index -> state.firstVisibleItemScrollOffset.toFloat()
        else -> size.height
    }
    translationY = off * 0.42f
    alpha = (1f - off / (size.height * 0.85f)).coerceIn(0f, 1f)
}

/**
 * Once the page's big title or hero has scrolled away (the first visible item is [showAfter] or
 * later), a slim bar slides down over the top with the title in small, so the page still says
 * where it is: with the back button and [actions] when the page has them.
 */
@Composable
fun BoxScope.ScrolledTitleBar(
    state: LazyListState,
    title: String,
    showAfter: Int,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val shown by remember(state, showAfter) { derivedStateOf { state.firstVisibleItemIndex >= showAfter } }
    AnimatedVisibility(
        shown,
        Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        enter = fadeIn(tween(160)) + slideInVertically(tween(320, easing = SettleEasing)) { -it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 2 },
    ) {
        val style = Lanyin.style
        val c = style.colors
        Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = style.margin - 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconAction(LineIcon.Back, "返回", onBack) else Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = style.type.title,
                    color = if (style.edge == EdgeKind.Glow) c.accent else c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                actions()
            }
            Hairline()
        }
    }
}
