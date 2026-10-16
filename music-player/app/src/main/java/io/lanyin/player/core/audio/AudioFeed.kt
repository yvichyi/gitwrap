package io.lanyin.player.core.audio

/** What is being heard right now, for the visuals that move with the music. */
interface AudioFeed {
    /**
     * Fills [out] with band levels in 0..1, bass first, for the audio being heard [lagMs] ago.
     * False (and zeros) when nothing is playing or it isn't known yet.
     */
    fun levels(out: FloatArray, lagMs: Long = 0): Boolean

    /** Seconds since the last beat that has been heard; large when there hasn't been one lately. */
    fun sinceBeat(): Float

    /** Seconds from one beat to the next, once the music has settled on a tempo; 0 until then. */
    fun beatPeriod(): Float = 0f
}

/**
 * How hard the last beat still rings: 1 on it, falling to 0 over [seconds], or sooner at a quick
 * tempo, so that each beat has died away before the next lands and a fast song still reads as
 * separate hits rather than one long glow.
 */
fun AudioFeed.beatPulse(seconds: Float): Float = pulseAfter(sinceBeat(), beatPeriod(), seconds)

/** [beatPulse] from the time since a beat and the period. */
fun pulseAfter(since: Float, period: Float, seconds: Float): Float {
    // A beat "after now" is a clock that has stopped behind it, not a beat that rings.
    if (since < 0f) return 0f
    val span = if (period > 0f) minOf(seconds, period * 0.8f) else seconds
    return (1f - since / span).coerceIn(0f, 1f)
}
