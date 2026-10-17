package io.lanyin.player.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * The hour of the day, as a fraction (18.5 is half past six in the evening), for backdrops that
 * follow the local time. Builds that need it still (the screenshot tests) set the system property
 * `lanyin.fixedHour`; nothing else does.
 */
object TimeOfDay {
    private val fixed: Float? = System.getProperty("lanyin.fixedHour")?.toFloatOrNull()

    fun hour(): Float = fixed ?: LocalTime.now().let { it.hour + it.minute / 60f }
}

/** [TimeOfDay.hour], read again each minute. */
@Composable
fun rememberHour(): State<Float> = produceState(TimeOfDay.hour()) {
    while (true) {
        delay(60_000)
        value = TimeOfDay.hour()
    }
}

/** How far [hour] is into the night (0 by day, 1 from 21:00 to 04:00), easing in and out over the hour either side. */
internal fun nightness(hour: Float): Float = when {
    hour >= 21f || hour < 4f -> 1f
    hour >= 19.5f -> (hour - 19.5f) / 1.5f
    hour < 5.5f -> 1f - (hour - 4f) / 1.5f
    else -> 0f
}
