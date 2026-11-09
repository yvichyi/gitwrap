package io.lanyin.player.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
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

/** How pages come and go: forward ([enter], [exit]) and back ([popEnter], [popExit]). */
internal class PageMotion(val enter: EnterTransition, val exit: ExitTransition, val popEnter: EnterTransition, val popExit: ExitTransition)

/** With motion reduced: a short fade, whatever the theme. */
internal val quietPageMotion = PageMotion(fadeIn(tween(150)), fadeOut(tween(100)), fadeIn(tween(150)), fadeOut(tween(100)))

/** Material's emphasized curves: a quick start that settles slowly, and the reverse for leaving. */
private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Leave = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Each theme's way between pages, all of them smooth: a stutter or a stepped cut reads as a dropped
 * frame however much it suits the theme, so the theme shows in the direction, distance and pace
 * instead. 墨: the page settles in as ink soaks in, rising a hair. 霓: it switches on, bright at
 * once and steadying. 暮: set down from a little below, like a postcard. 格: pushed in hard from
 * the side. 点: a quick fade. 机: the screen
 * switching over. 报: the page turned. 游: slid in like the next screen of a level. 酸: popped in
 * with a little bounce.
 */
internal fun pageMotion(backdrop: BackdropKind): PageMotion = when (backdrop) {
    BackdropKind.Ink -> PageMotion(
        fadeIn(tween(380, easing = Standard)) + slideInVertically(tween(460, easing = Settle)) { it / 40 }, fadeOut(tween(160, easing = Leave)),
        fadeIn(tween(380, easing = Standard)) + slideInVertically(tween(460, easing = Settle)) { -it / 60 }, fadeOut(tween(160, easing = Leave)),
    )
    BackdropKind.Neon -> {
        // Lit at once to most of its brightness, then steadying: a sign switching on, without the stutter.
        val on = fadeIn(keyframes { durationMillis = 300; 0f at 0; 0.7f at 70 using Standard; 1f at 300 using Settle }) +
            scaleIn(tween(340, easing = Settle), initialScale = 0.985f)
        PageMotion(on, fadeOut(tween(140, easing = Leave)), on, fadeOut(tween(140, easing = Leave)))
    }
    BackdropKind.Dusk -> PageMotion(
        fadeIn(tween(300, easing = Standard)) + slideInVertically(tween(440, easing = Settle)) { it / 14 }, fadeOut(tween(160, easing = Leave)),
        fadeIn(tween(300, easing = Standard)), fadeOut(tween(180, easing = Leave)) + slideOutVertically(tween(260, easing = Leave)) { it / 14 },
    )
    BackdropKind.Grid -> PageMotion(
        slideInHorizontally(tween(340, easing = Settle)) { it }, slideOutHorizontally(tween(340, easing = Settle)) { -it / 3 },
        slideInHorizontally(tween(340, easing = Settle)) { -it / 3 }, slideOutHorizontally(tween(300, easing = Standard)) { it },
    )
    BackdropKind.Dot -> PageMotion(
        fadeIn(tween(200, easing = Standard)), fadeOut(tween(110, easing = Leave)),
        fadeIn(tween(200, easing = Standard)), fadeOut(tween(110, easing = Leave)),
    )
    BackdropKind.Device -> PageMotion(
        fadeIn(tween(120, easing = Standard)) + scaleIn(tween(260, easing = Settle), initialScale = 0.95f), fadeOut(tween(90, easing = Leave)),
        fadeIn(tween(120, easing = Standard)) + scaleIn(tween(260, easing = Settle), initialScale = 1.04f), fadeOut(tween(90, easing = Leave)),
    )
    BackdropKind.Paper -> PageMotion(
        slideInHorizontally(tween(420, easing = Settle)) { it } + fadeIn(tween(220, easing = Standard)), slideOutHorizontally(tween(420, easing = Settle)) { -it / 4 } + fadeOut(tween(300, easing = Leave)),
        slideInHorizontally(tween(420, easing = Settle)) { -it / 4 } + fadeIn(tween(220, easing = Standard)), slideOutHorizontally(tween(380, easing = Standard)) { it } + fadeOut(tween(300, easing = Leave)),
    )
    BackdropKind.Game -> PageMotion(
        slideInHorizontally(tween(360, easing = Settle)) { it / 5 } + fadeIn(tween(200, easing = Standard)), fadeOut(tween(140, easing = Leave)),
        slideInHorizontally(tween(360, easing = Settle)) { -it / 5 } + fadeIn(tween(200, easing = Standard)), fadeOut(tween(140, easing = Leave)),
    )
    BackdropKind.Acid -> PageMotion(
        fadeIn(tween(160, easing = Standard)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.92f), fadeOut(tween(130, easing = Leave)),
        fadeIn(tween(160, easing = Standard)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.92f), fadeOut(tween(130, easing = Leave)),
    )
}

/** How a tab's page gives way to its neighbour's: [enter] and [exit], for one side by side with another. */
internal class TabMotion(val enter: EnterTransition, val exit: ExitTransition)

/**
 * Each theme's way between its tabs, which sit side by side rather than one above another, so the
 * pages move the way the tabs lie: [dir] 1 to a tab on the right, -1 to the left. 墨: a soft fade
 * with a drift, as ink spreads. 霓: the next sign lit. 暮: the next postcard slid along the
 * rack. 格: a hard push. 点: a blink. 机: changing channel. 报: the page
 * turned. 游: scrolled to the next screen. 酸: popped across with a bounce.
 */
internal fun tabMotion(backdrop: BackdropKind, dir: Int, reduced: Boolean): TabMotion {
    if (reduced) return TabMotion(fadeIn(tween(150)), fadeOut(tween(100)))
    val d = if (dir >= 0) 1 else -1
    return when (backdrop) {
        BackdropKind.Ink -> TabMotion(
            fadeIn(tween(340, easing = Standard)) + slideInHorizontally(tween(440, easing = Settle)) { d * it / 20 },
            fadeOut(tween(180, easing = Leave)) + slideOutHorizontally(tween(260, easing = Leave)) { -d * it / 30 },
        )
        BackdropKind.Neon -> TabMotion(
            fadeIn(keyframes { durationMillis = 260; 0f at 0; 0.7f at 60 using Standard; 1f at 260 using Settle }) +
                slideInHorizontally(tween(320, easing = Settle)) { d * it / 24 },
            fadeOut(tween(110, easing = Leave)),
        )
        BackdropKind.Dusk -> TabMotion(
            fadeIn(tween(260, easing = Standard)) + slideInHorizontally(tween(400, easing = Settle)) { d * it / 6 },
            fadeOut(tween(200, easing = Leave)) + slideOutHorizontally(tween(340, easing = Standard)) { -d * it / 6 },
        )
        BackdropKind.Grid -> TabMotion(
            slideInHorizontally(tween(320, easing = Settle)) { d * it },
            slideOutHorizontally(tween(320, easing = Settle)) { -d * it },
        )
        BackdropKind.Dot -> TabMotion(fadeIn(tween(160, easing = Standard)), fadeOut(tween(90, easing = Leave)))
        BackdropKind.Device -> TabMotion(
            fadeIn(tween(110, delayMillis = 50, easing = Standard)) + scaleIn(tween(240, easing = Settle), initialScale = 0.97f),
            fadeOut(tween(70, easing = Leave)),
        )
        BackdropKind.Paper -> TabMotion(
            slideInHorizontally(tween(400, easing = Settle)) { d * it } + fadeIn(tween(200, easing = Standard)),
            slideOutHorizontally(tween(400, easing = Settle)) { -d * it / 4 } + fadeOut(tween(260, easing = Leave)),
        )
        BackdropKind.Game -> TabMotion(
            slideInHorizontally(tween(340, easing = Settle)) { d * it },
            slideOutHorizontally(tween(340, easing = Settle)) { -d * it },
        )
        BackdropKind.Acid -> TabMotion(
            fadeIn(tween(140, easing = Standard)) + slideInHorizontally(spring(dampingRatio = 0.72f, stiffness = 420f)) { d * it / 3 },
            fadeOut(tween(130, easing = Leave)) + slideOutHorizontally(tween(180, easing = Leave)) { -d * it / 5 },
        )
    }
}
