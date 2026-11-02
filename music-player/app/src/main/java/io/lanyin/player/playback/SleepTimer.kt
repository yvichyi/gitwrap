package io.lanyin.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 定时关闭: music stops after a while, or at the end of the song playing. The playback service
 * follows [state]: over the last [FADE_MS] it lowers the volume to nothing ([volumeAt]), then
 * pauses and puts the volume back.
 */
class SleepTimer(private val clock: () -> Long = System::currentTimeMillis) {
    /** Set to end at [endsAt] (epoch ms), or, with [endOfTrack], when the song playing ends. */
    data class Set(val endsAt: Long?, val endOfTrack: Boolean)

    private val _state = MutableStateFlow<Set?>(null)
    val state: StateFlow<Set?> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = Set(clock() + minutes * 60_000L, endOfTrack = false)
    }

    fun endOfTrack() {
        _state.value = Set(null, endOfTrack = true)
    }

    fun cancel() {
        _state.value = null
    }

    /** How long is left, or null when it isn't counting down. */
    fun remainingMs(now: Long = clock()): Long? = _state.value?.endsAt?.let { (it - now).coerceAtLeast(0) }

    /** The volume at [now]: full until the last [FADE_MS], then down to silence along a gentle curve. */
    fun volumeAt(now: Long = clock()): Float {
        val left = remainingMs(now) ?: return 1f
        if (left >= FADE_MS) return 1f
        val f = left.toFloat() / FADE_MS
        return f * f
    }

    companion object {
        const val FADE_MS = 30_000L
    }
}
