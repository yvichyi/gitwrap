package io.lanyin.player.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import io.lanyin.player.ui.theme.BackdropKind
import kotlin.math.floor

/** How pages come and go: forward ([enter], [exit]) and back ([popEnter], [popExit]). */
internal class PageMotion(val enter: EnterTransition, val exit: ExitTransition, val popEnter: EnterTransition, val popExit: ExitTransition)

/** An easing that moves in [n] jumps, as a game's screen does. */
private fun stepped(n: Int) = Easing { f -> floor(f * n) / n }

/**
 * Each theme's way between pages. 墨: the page appears as ink soaks in, slowly. 霓: it switches
 * on like a sign, with a stutter. 暮: set down from a little below, like a postcard. 格: pushed in
 * hard from the side. 影: the next frame pulled up through the gate. 点: a quick fade. 机: the
 * screen switching over. 报: the page turned. 游: a stepped cut. 酸: popped in with a bounce.
 */
internal fun pageMotion(backdrop: BackdropKind): PageMotion = when (backdrop) {
    BackdropKind.Ink -> PageMotion(
        fadeIn(tween(420, easing = LinearEasing)), fadeOut(tween(220)),
        fadeIn(tween(420, easing = LinearEasing)), fadeOut(tween(220)),
    )
    BackdropKind.Neon -> {
        val flicker = fadeIn(
            keyframes {
                durationMillis = 360
                0f at 0
                0.85f at 50
                0.15f at 110
                0.9f at 170
                0.5f at 220
                1f at 300
            },
        )
        PageMotion(flicker, fadeOut(tween(120)), flicker, fadeOut(tween(120)))
    }
    BackdropKind.Dusk -> PageMotion(
        fadeIn(tween(320)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 }, fadeOut(tween(160)),
        fadeIn(tween(320)), fadeOut(tween(160)) + slideOutVertically(tween(260)) { it / 14 },
    )
    BackdropKind.Grid -> PageMotion(
        slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it }, slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 },
        slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 }, slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it },
    )
    BackdropKind.Film -> PageMotion(
        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }, slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -it },
        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it }, slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it },
    )
    BackdropKind.Dot -> PageMotion(fadeIn(tween(160)), fadeOut(tween(100)), fadeIn(tween(160)), fadeOut(tween(100)))
    BackdropKind.Device -> PageMotion(
        fadeIn(tween(90)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 0.94f), fadeOut(tween(80)),
        fadeIn(tween(90)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 0.94f), fadeOut(tween(80)),
    )
    BackdropKind.Paper -> PageMotion(
        slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)), slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(300)),
        slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(200)), slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(300)),
    )
    BackdropKind.Game -> PageMotion(
        fadeIn(tween(200, easing = stepped(4))), fadeOut(tween(120, easing = stepped(3))),
        fadeIn(tween(200, easing = stepped(4))), fadeOut(tween(120, easing = stepped(3))),
    )
    BackdropKind.Acid -> PageMotion(
        fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 520f), initialScale = 0.86f), fadeOut(tween(120)),
        fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 520f), initialScale = 0.86f), fadeOut(tween(120)),
    )
}
