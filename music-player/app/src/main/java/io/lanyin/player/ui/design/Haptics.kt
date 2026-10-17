package io.lanyin.player.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.Lanyin

/**
 * The feel of a press in each theme: the faintest tick for 墨, 暮, 点 and 报; a key's tap for 霓;
 * a solid click for 格; a shutter for 影; the clack of a real key for 机 and 游; a springy
 * confirm for 酸. The system's own haptics setting still decides whether anything is felt.
 */
internal fun hapticFor(kind: BackdropKind): Int = when (kind) {
    BackdropKind.Ink, BackdropKind.Dusk, BackdropKind.Paper -> HapticFeedbackConstants.CLOCK_TICK
    BackdropKind.Dot -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    BackdropKind.Neon -> HapticFeedbackConstants.KEYBOARD_TAP
    BackdropKind.Grid -> HapticFeedbackConstants.CONTEXT_CLICK
    BackdropKind.Film -> HapticFeedbackConstants.KEYBOARD_PRESS
    BackdropKind.Device, BackdropKind.Game -> HapticFeedbackConstants.VIRTUAL_KEY
    BackdropKind.Acid -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
}

/** Something to call on a press: the theme's haptic, or nothing when 触感反馈 is off. */
@Composable
fun rememberThemeHaptic(): () -> Unit {
    val view = LocalView.current
    val on = LocalHapticsOn.current
    val kind = Lanyin.style.backdrop
    return remember(view, on, kind) {
        val feel = hapticFor(kind)
        val tap: () -> Unit = { if (on) view.performHapticFeedback(feel) }
        tap
    }
}
