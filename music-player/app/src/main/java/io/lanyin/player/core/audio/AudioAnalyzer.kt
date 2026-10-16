package io.lanyin.player.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/** Sample formats the analyzer reads. */
enum class PcmEncoding { PCM16, PCM24, PCM32, FLOAT }

/**
 * Listens to the decoded audio on its way to the speaker: an FFT of 1024 samples every 512, folded
 * into log-spaced bands, and the beat tracked from the jumps in the spectrum. Results are kept
 * against the audio's own timestamps, and the sink reports what it is playing now, so the visuals
 * read what is being heard rather than what was just decoded (which runs hundreds of milliseconds
 * ahead).
 *
 * The beat is a grid, as a listener taps it: the tempo comes from how the onsets of the last six
 * seconds repeat, the phase from where they line up, and beats are laid down on that grid, nudged
 * toward each new estimate rather than jumping to every loud note. Until a grid is found (the first
 * seconds of a song, or music without a steady pulse) beats are picked from the kick drum instead.
 *
 * [process], [anchor] and [reset] are called on the playback thread; [levels], [sinceBeat] and
 * [beatPeriod] from the UI.
 */
class AudioAnalyzer(
    private val bands: Int = 32,
    private val nowNanos: () -> Long = System::nanoTime,
) : AudioFeed {
    private val lock = Any()
    private val n = 1024
    private val fft = Fft(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * it / (n - 1))).toFloat() }
    private val re = FloatArray(n)
    private val im = FloatArray(n)

    /** The rate analysed at: the stream's, divided down by [decimate] for hi-res streams. */
    private var sampleRate = 44_100
    /**
     * Input frames averaged into one analysed sample. Every timing here (the FFT's bins, the
     * smoothing, the tempo window) is tuned in 512-sample hops at 44.1–48 kHz; a 96 or 192 kHz
     * stream is brought down to that so its kick still lands in the bass bands and the timing holds.
     */
    private var decimate = 1
    private var acc = 0f
    private var accCount = 0
    private var channels = 2
    private var encoding = PcmEncoding.PCM16
    private var edges = bandEdges(sampleRate)

    // The window being filled, the timestamp of its first sample, and of the sample a hop in, where
    // the next window starts.
    private val block = FloatArray(n)
    private var filled = 0
    private var blockStartUs = 0L
    private var nextStartUs = 0L
    /** Hops analysed since the last reset; the beat grid counts in these. */
    private var frameIndex = 0L

    // Per-band level shaping: a slowly falling peak per band (so quiet bands still move), and smoothing.
    private val peakDb = FloatArray(bands) { -30f }
    private val smooth = FloatArray(bands)
    // Each band's last compressed magnitude, for the rises that mark an onset: every hop for the
    // grid, every other hop for the kick picker (whose thresholds were set at that pace).
    private val prevOnset = FloatArray(bands)
    private val prevKick = FloatArray(bands)
    private val kickHistory = FloatArray(24)
    private var kickCount = 0
    private var lastBeatUs = Long.MIN_VALUE

    // What has been analysed, by time.
    private val cap = 512
    private val times = LongArray(cap)
    private val frames = Array(cap) { FloatArray(bands) }
    private var head = 0
    private var count = 0
    private val beats = LongArray(64)
    private var beatHead = 0
    private var beatCount = 0

    // The grid: onset strength over the last six seconds, the tempo's recent estimates (in hops),
    // the period settled on (0 while there is none) and the hop the next beat falls on.
    private val envelope = FloatArray(ENVELOPE)
    private var envelopeCount = 0
    private var sinceTempo = 0
    private val periods = FloatArray(PERIODS)
    private var periodCount = 0
    private var period = 0f
    private var nextBeat = 0.0
    private var misses = 0
    private var correlation = 0f
    // Scratch for the tempo search, kept to spare the playback thread the garbage.
    private val work = FloatArray(ENVELOPE)
    private val shaped = FloatArray(ENVELOPE)
    private val sums = FloatArray(ENVELOPE + 1)
    private val ac = FloatArray(ENVELOPE)

    /** How strong recent kicks have been, in onset units. */
    private var kickStrength = 0f

    /** The beat period the music has settled on, in µs; 0 while there is no clear pulse. */
    var beatPeriodUs = 0L
        private set

    // Where playback is: a position from the sink and when it was true.
    private var anchorUs = Long.MIN_VALUE
    private var anchorNanos = 0L
    private var playing = false

    fun configure(sampleRate: Int, channels: Int, encoding: PcmEncoding) = synchronized(lock) {
        decimate = (sampleRate / 44_100).coerceAtLeast(1)
        val rate = sampleRate / decimate
        if (rate != this.sampleRate) edges = bandEdges(rate)
        this.sampleRate = rate
        this.channels = channels.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        acc = 0f
        accCount = 0
    }

    /** Reads (without consuming) the frames between [buffer]'s position and limit, the first at [presentationTimeUs]. */
    fun process(buffer: ByteBuffer, presentationTimeUs: Long) = synchronized(lock) {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytes = when (encoding) {
            PcmEncoding.PCM16 -> 2
            PcmEncoding.PCM24 -> 3
            PcmEncoding.PCM32, PcmEncoding.FLOAT -> 4
        }
        val frameBytes = bytes * channels
        val frameCount = b.remaining() / frameBytes
        // Per input frame; an analysed sample spans [decimate] of them.
        val usPerFrame = 1_000_000.0 / (sampleRate * decimate)
        var p = b.position()
        for (f in 0 until frameCount) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += sample(b, p)
                p += bytes
            }
            if (accCount == 0) {
                if (filled == 0) blockStartUs = presentationTimeUs + (f * usPerFrame).toLong()
                else if (filled == HOP) nextStartUs = presentationTimeUs + (f * usPerFrame).toLong()
            }
            // Averaging neighbours is a gentle low-pass before dropping to the lower rate.
            acc += sum / channels
            if (++accCount < decimate) continue
            block[filled++] = acc / decimate
            acc = 0f
            accCount = 0
            if (filled == n) {
                analyse(blockStartUs + (n / 2 * decimate * usPerFrame).toLong())
                // Windows overlap by half: keep the second half as the next one's first.
                System.arraycopy(block, HOP, block, 0, n - HOP)
                filled = n - HOP
                blockStartUs = nextStartUs
            }
        }
    }

    private fun sample(b: ByteBuffer, p: Int): Float = when (encoding) {
        PcmEncoding.PCM16 -> b.getShort(p) / 32768f
        PcmEncoding.PCM24 -> {
            val v = (b.get(p).toInt() and 0xff) or ((b.get(p + 1).toInt() and 0xff) shl 8) or (b.get(p + 2).toInt() shl 16)
            v / 8_388_608f
        }
        PcmEncoding.PCM32 -> b.getInt(p) / 2_147_483_648f
        PcmEncoding.FLOAT -> b.getFloat(p)
    }

    private fun analyse(timeUs: Long) {
        for (i in 0 until n) {
            re[i] = block[i] * window[i]
            im[i] = 0f
        }
        fft.transform(re, im)
        // A full-scale sine peaks at n/4 through a Hann window: call that 0 dB.
        val scale = n / 4f
        val frame = frames[head]
        val kickHop = frameIndex % 2 == 0L
        var onset = 0f
        var kick = 0f
        for (band in 0 until bands) {
            var peak = 0f
            for (k in edges[band] until edges[band + 1]) peak = max(peak, hypot(re[k], im[k]))
            val db = 20f * log10(peak / scale + 1e-9f)
            // The band's own recent peak falls slowly (about 12 dB over four seconds), never below -50 dB.
            peakDb[band] = max(max(db, peakDb[band] - 0.035f), -50f)
            val relative = ((db - (peakDb[band] - 36f)) / 36f).coerceIn(0f, 1f)
            val absolute = ((db + 66f) / 60f).coerceIn(0f, 1f)
            val level = 0.6f * relative + 0.4f * absolute
            smooth[band] = if (level > smooth[band]) smooth[band] + (level - smooth[band]) * 0.45f else smooth[band] + (level - smooth[band]) * 0.12f
            frame[band] = smooth[band]
            // Rises only, on log-compressed magnitude rather than decibels, so a loud attack
            // counts and a faint click in a quiet band doesn't.
            val compressed = ln(1f + 100f * peak / scale)
            // For the grid, the whole spectrum, leaning to the drums and the middle: a snare, a
            // chord change and a sung syllable all mark the pulse, and the top end least.
            onset += (compressed - prevOnset[band]).coerceAtLeast(0f) * when {
                band < bands * 3 / 16 -> 0.8f
                band < bands * 3 / 8 -> 1f
                band < bands * 11 / 16 -> 0.7f
                else -> 0.3f
            }
            prevOnset[band] = compressed
            if (kickHop) {
                // For the kick picker, weighted hard to the bass: the kick and bass carry the beat,
                // while hi-hats and strums rise between beats.
                kick += (compressed - prevKick[band]).coerceAtLeast(0f) * when {
                    // 40 Hz to about 120 Hz: the kick and the bass.
                    band < bands * 3 / 16 -> 1f
                    // Up to about 400 Hz: the body of the drums.
                    band < bands * 3 / 8 -> 0.3f
                    // Above that, a tick's splatter rises across many bands at once and would outvote the kick.
                    else -> 0f
                }
                prevKick[band] = compressed
            }
        }
        times[head] = timeUs
        head = (head + 1) % cap
        if (count < cap) count++

        track(onset, timeUs)
        if (kickHop) pickKick(kick, timeUs)
        frameIndex++
    }

    /** The grid: re-estimates tempo and phase now and then, and lays down the beats that fall due. */
    private fun track(onset: Float, timeUs: Long) {
        envelope[envelopeCount % ENVELOPE] = onset
        envelopeCount++
        val now = frameIndex
        if (++sinceTempo >= TEMPO_EVERY && envelopeCount >= ENVELOPE / 2) {
            sinceTempo = 0
            val len = minOf(envelopeCount, ENVELOPE)
            val found = tempo(len)
            if (found > 0f && correlation > MIN_TEMPO_CORRELATION) {
                misses = 0
                periods[periodCount % PERIODS] = found
                periodCount++
                // The median of the last few estimates: one confused by a fill doesn't move the grid.
                val p = median(periods, minOf(periodCount, PERIODS))
                val last = now - phase(len, p)
                val due = last + p * ceil((now - last) / p + 1e-9)
                if (period == 0f) {
                    nextBeat = due
                } else {
                    // Ease the running grid toward the new estimate, as a phase-locked loop does, so
                    // the beats keep an even pace; jump only when it's plainly off.
                    val err = ((due - nextBeat + p / 2).mod(p.toDouble())) - p / 2
                    nextBeat += if (abs(err) > 0.3 * p) err else PULL * err
                }
                period = p
                beatPeriodUs = (p * hopUs()).toLong()
            } else if (period > 0f && ++misses >= MISSES_TO_LOSE) {
                // The pulse has gone (a breakdown, a rubato passage): stop ticking on.
                period = 0f
                periodCount = 0
                misses = 0
                beatPeriodUs = 0
            }
        }
        if (period <= 0f) return
        while (nextBeat <= now) {
            emit(timeUs - ((now - nextBeat) * hopUs()).toLong())
            nextBeat += period
        }
    }

    private fun hopUs(): Double = HOP * 1_000_000.0 / sampleRate

    /**
     * The beat period in the onset envelope, in hops: the lag between a third of a second and a
     * second (180 to 60 bpm) at which the envelope best matches itself, counting its double and
     * half too (a beat repeats at twice its period, and its off-beats at half), and leaning toward
     * 120 bpm as people do when a rhythm could be counted at two speeds. 0 when nothing repeats.
     * Leaves the shaped envelope in [shaped] for [phase], and how well the lag matched in
     * [correlation].
     */
    private fun tempo(len: Int): Float {
        val hopS = HOP.toFloat() / sampleRate
        for (i in 0 until len) work[i] = envelope[(envelopeCount - len + i) % ENVELOPE]
        // Onsets land on whole hops, so a steady beat alternates between neighbouring lags;
        // blurring the envelope a little lets those line up.
        sums[0] = 0f
        for (i in 0 until len) {
            var s = 0f
            for (d in -2..2) {
                val j = i + d
                if (j in 0 until len) s += work[j] * (3 - abs(d))
            }
            shaped[i] = s / 9f
            sums[i + 1] = sums[i] + shaped[i]
        }
        // What rises above its surroundings (0.4 s around), so a loud passage doesn't outweigh the pulse.
        val span = (0.4f / hopS).toInt().coerceAtLeast(1)
        var mean = 0f
        for (i in 0 until len) {
            val from = (i - span / 2).coerceAtLeast(0)
            val to = (i + span - span / 2).coerceAtMost(len)
            shaped[i] = (shaped[i] - (sums[to] - sums[from]) / span).coerceAtLeast(0f)
            mean += shaped[i]
        }
        mean /= len
        var energy = 0f
        for (i in 0 until len) {
            shaped[i] -= mean
            energy += shaped[i] * shaped[i]
        }
        correlation = 0f
        if (energy < 1e-9f) return 0f
        val minLag = (0.33f / hopS).toInt()
        val maxLag = minOf((1f / hopS).toInt(), len / 2 - 1)
        if (maxLag <= minLag) return 0f
        val lags = minOf(len / 2, 2 * maxLag + 2)
        for (lag in 1 until lags) {
            var sum = 0f
            for (i in 0 until len - lag) sum += shaped[i] * shaped[i + lag]
            ac[lag] = sum / energy
        }
        var best = 0
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            var r = ac[lag]
            if (2 * lag < lags) r += 0.5f * ac[2 * lag]
            if (lag / 2 >= 1) r += 0.25f * ac[lag / 2]
            val octaves = ln(lag * hopS / 0.5f) / LN2
            val score = r * exp(-0.5f * (octaves / 0.9f) * (octaves / 0.9f))
            if (score > bestScore) {
                bestScore = score
                best = lag
            }
        }
        if (best == 0) return 0f
        correlation = ac[best]
        // Between whole hops: the top of a parabola through the peak and its neighbours.
        if (best - 1 >= 1 && best + 1 < lags) {
            val a = ac[best - 1]
            val b = ac[best]
            val c = ac[best + 1]
            val d = a - 2 * b + c
            if (d != 0f) return best + (0.5f * (a - c) / d).coerceIn(-0.5f, 0.5f)
        }
        return best.toFloat()
    }

    /**
     * How many hops before the newest the last beat of a [p]-hop grid falls: the offset that lands
     * the grid on the most onset, recent beats counting most.
     */
    private fun phase(len: Int, p: Float): Float {
        val steps = max(p.toInt(), 1) * 2
        var best = 0f
        var bestScore = Float.NEGATIVE_INFINITY
        for (s in 0 until steps) {
            val phi = p * s / steps
            var score = 0f
            var w = 1f
            var pos = len - 1 - phi
            while (pos >= 0f) {
                val i = pos.toInt()
                val fr = pos - i
                score += w * (shaped[i] * (1 - fr) + if (i + 1 < len) shaped[i + 1] * fr else 0f)
                w *= PHASE_DECAY
                pos -= p
            }
            if (score > bestScore) {
                bestScore = score
                best = phi
            }
        }
        return best
    }

    /**
     * Without a grid, beats from the kick: a jump well above the recent average, about as strong as
     * the recent kicks, and most of a beat after the last.
     */
    private fun pickKick(kick: Float, timeUs: Long) {
        val seen = minOf(kickCount, kickHistory.size)
        var mean = kick
        if (seen > 0) {
            var sum = 0f
            for (i in 0 until seen) sum += kickHistory[i]
            mean = sum / seen
        }
        // No beat sooner than this after the last: an off-beat hi-hat or a fill is not the beat.
        val gapOk = lastBeatUs == Long.MIN_VALUE || timeUs - lastBeatUs > UNSURE_GAP_US
        // And about as strong as the recent beats: a bright tick between kicks rises, but far less.
        // The memory fades (halving in about three seconds), so a quieter passage finds its beat again.
        kickStrength *= 0.995f
        val strongEnough = kick >= kickStrength * 0.45f
        if (kickCount >= 8 && gapOk && strongEnough && kick > mean * 1.6f + ONSET_FLOOR) {
            kickStrength = if (kickStrength <= 0f) kick else kickStrength * 0.7f + kick * 0.3f
            if (period <= 0f) emit(timeUs)
        }
        kickHistory[kickCount % kickHistory.size] = kick
        kickCount++
    }

    /** Keeps a beat heard at [timeUs], unless one landed too recently (as the grid takes over from the kicks). */
    private fun emit(timeUs: Long) {
        val gap = max(MIN_GAP_US, (beatPeriodUs * 0.7).toLong())
        if (lastBeatUs != Long.MIN_VALUE && timeUs - lastBeatUs < gap) return
        lastBeatUs = timeUs
        onBeat?.invoke(timeUs)
        beats[beatHead] = timeUs
        beatHead = (beatHead + 1) % beats.size
        if (beatCount < beats.size) beatCount++
    }

    /** Told of every beat as it's found; for tests and evaluation. */
    internal var onBeat: ((Long) -> Unit)? = null

    /** Beat times kept, oldest first; for tests. */
    internal fun beatTimes(): List<Long> = synchronized(lock) {
        List(beatCount) { beats[(beatHead - beatCount + it + beats.size) % beats.size] }
    }

    /** The sink is playing (or paused at) [positionUs], in the same time base as the timestamps given to [process]. */
    fun anchor(positionUs: Long, playing: Boolean) = synchronized(lock) {
        anchorUs = positionUs
        anchorNanos = nowNanos()
        this.playing = playing
    }

    fun setPlaying(playing: Boolean) = synchronized(lock) {
        if (playing != this.playing && anchorUs != Long.MIN_VALUE) {
            anchorUs = heardUs()
            anchorNanos = nowNanos()
        }
        this.playing = playing
    }

    /** After a seek or a new track: what was analysed no longer lines up. */
    fun reset() = synchronized(lock) {
        filled = 0
        acc = 0f
        accCount = 0
        frameIndex = 0
        count = 0
        head = 0
        beatCount = 0
        kickCount = 0
        lastBeatUs = Long.MIN_VALUE
        envelopeCount = 0
        sinceTempo = 0
        periodCount = 0
        period = 0f
        misses = 0
        beatPeriodUs = 0
        kickStrength = 0f
        smooth.fill(0f)
        prevOnset.fill(0f)
        prevKick.fill(0f)
        anchorUs = Long.MIN_VALUE
    }

    private fun heardUs(): Long {
        if (!playing) return anchorUs
        // Between the sink's reports, playback runs on; don't guess further than half a second.
        val elapsed = ((nowNanos() - anchorNanos) / 1_000).coerceIn(0, 500_000)
        return anchorUs + elapsed
    }

    override fun levels(out: FloatArray, lagMs: Long): Boolean = synchronized(lock) {
        out.fill(0f)
        if (!playing || anchorUs == Long.MIN_VALUE || count == 0) return false
        val t = heardUs() - lagMs * 1_000
        val oldest = (head - count + cap) % cap
        val newest = (head - 1 + cap) % cap
        if (t < times[oldest] || t > times[newest] + STALE_US) return false
        // Frames are in time order from oldest to newest: find the pair around t.
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (times[(oldest + mid) % cap] <= t) lo = mid else hi = mid - 1
        }
        val a = (oldest + lo) % cap
        val bIdx = (oldest + minOf(lo + 1, count - 1)) % cap
        val span = (times[bIdx] - times[a]).coerceAtLeast(1)
        val f = if (bIdx == a) 0f else ((t - times[a]).toFloat() / span).coerceIn(0f, 1f)
        val fa = frames[a]
        val fb = frames[bIdx]
        for (i in 0 until minOf(out.size, bands)) out[i] = fa[i] + (fb[i] - fa[i]) * f
        return true
    }

    override fun sinceBeat(): Float = synchronized(lock) {
        if (!playing || anchorUs == Long.MIN_VALUE) return NO_BEAT
        val t = heardUs()
        var latest = Long.MIN_VALUE
        for (i in 0 until beatCount) {
            val b = beats[(beatHead - 1 - i + beats.size) % beats.size]
            if (b <= t) {
                latest = b
                break
            }
        }
        if (latest == Long.MIN_VALUE) NO_BEAT else (t - latest) / 1_000_000f
    }

    override fun beatPeriod(): Float = synchronized(lock) { if (playing) beatPeriodUs / 1_000_000f else 0f }

    /** FFT bins bounding each band, log-spaced from 40 Hz to 16 kHz (or near Nyquist), each at least one bin wide. */
    private fun bandEdges(rate: Int): IntArray {
        val lowHz = 40.0
        val highHz = minOf(16_000.0, rate / 2.0 * 0.95)
        val e = IntArray(bands + 1)
        for (i in 0..bands) {
            val hz = lowHz * Math.pow(highHz / lowHz, i / bands.toDouble())
            e[i] = (hz * n / rate).toInt().coerceIn(1, n / 2)
        }
        for (i in 1..bands) if (e[i] <= e[i - 1]) e[i] = minOf(e[i - 1] + 1, n / 2)
        return e
    }

    companion object {
        const val NO_BEAT = 99f
        /** Analysis hop: half the FFT, so onsets are placed to about 12 ms. */
        private const val HOP = 512
        /** Without a grid, kicks no closer than this. */
        private const val UNSURE_GAP_US = 420_000L
        /** No two beats closer than this, whatever the grid says: about 3 a second at most. */
        private const val MIN_GAP_US = 300_000L

        /** A kick must rise at least this much (compressed units) above the recent average. */
        private const val ONSET_FLOOR = 0.6f
        /** About six seconds of hops: long enough for a few bars, short enough to follow a tempo change. */
        private const val ENVELOPE = 512
        /** Re-estimate the grid every this many hops (about a tenth of a second). */
        private const val TEMPO_EVERY = 8
        private const val PERIODS = 5
        private const val MIN_TEMPO_CORRELATION = 0.05f
        /** How hard each new estimate pulls the running grid. */
        private const val PULL = 0.35
        /** How much less each earlier beat counts when placing the grid. */
        private const val PHASE_DECAY = 0.85f
        /** Estimates in a row without a pulse (about two seconds) before the grid is let go. */
        private const val MISSES_TO_LOSE = 24
        private const val STALE_US = 250_000L
        private val LN2 = ln(2f)

        private fun median(values: FloatArray, size: Int): Float {
            val sorted = values.copyOf(size).also { it.sort() }
            return if (size % 2 == 1) sorted[size / 2] else (sorted[size / 2 - 1] + sorted[size / 2]) / 2f
        }
    }
}
