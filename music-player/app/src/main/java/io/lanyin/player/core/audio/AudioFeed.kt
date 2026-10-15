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
}
