package io.lanyin.player.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

/** Sample formats the analyzer reads. */
enum class PcmEncoding { PCM16, PCM24, PCM32, FLOAT }

/**
 * Listens to the decoded audio on its way to the speaker: an FFT every 1024 samples, folded into
 * log-spaced bands, and beats found from jumps in the spectrum (weighted to the bass). Results are
 * kept against the audio's own timestamps, and the sink reports what it is playing now, so the
 * visuals read what is being heard rather than what was just decoded (which runs hundreds of
 * milliseconds ahead).
 *
 * [process], [anchor] and [reset] are called on the playback thread; [levels] and [sinceBeat]
 * from the UI.
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

    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = PcmEncoding.PCM16
    private var edges = bandEdges(sampleRate)

    // The block being filled, and the timestamp of its first sample.
    private val block = FloatArray(n)
    private var filled = 0
    private var blockStartUs = 0L

    // Per-band level shaping: a slowly falling peak per band (so quiet bands still move), and smoothing.
    private val peakDb = FloatArray(bands) { -30f }
    private val smooth = FloatArray(bands)
    private val prevDb = FloatArray(bands) { -90f }
    private val fluxHistory = FloatArray(24)
    private var fluxCount = 0
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

    // Where playback is: a position from the sink and when it was true.
    private var anchorUs = Long.MIN_VALUE
    private var anchorNanos = 0L
    private var playing = false

    fun configure(sampleRate: Int, channels: Int, encoding: PcmEncoding) = synchronized(lock) {
        if (sampleRate != this.sampleRate) edges = bandEdges(sampleRate)
        this.sampleRate = sampleRate
        this.channels = channels.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
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
        val usPerFrame = 1_000_000.0 / sampleRate
        var p = b.position()
        for (f in 0 until frameCount) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += sample(b, p)
                p += bytes
            }
            if (filled == 0) blockStartUs = presentationTimeUs + (f * usPerFrame).toLong()
            block[filled++] = sum / channels
            if (filled == n) {
                analyse(blockStartUs + (n / 2 * usPerFrame).toLong())
                filled = 0
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
        var flux = 0f
        for (band in 0 until bands) {
            var peak = 0f
            for (k in edges[band] until edges[band + 1]) peak = max(peak, hypot(re[k], im[k]))
            val db = 20f * log10(peak / scale + 1e-9f)
            // The band's own recent peak falls slowly (about 12 dB over four seconds), never below -50 dB.
            peakDb[band] = max(max(db, peakDb[band] - 0.07f), -50f)
            val relative = ((db - (peakDb[band] - 36f)) / 36f).coerceIn(0f, 1f)
            val absolute = ((db + 66f) / 60f).coerceIn(0f, 1f)
            val level = 0.6f * relative + 0.4f * absolute
            smooth[band] = if (level > smooth[band]) smooth[band] + (level - smooth[band]) * 0.7f else smooth[band] + (level - smooth[band]) * 0.22f
            frame[band] = smooth[band]
            // Rises only, weighted to the bass, where the beat lives.
            val rise = (db - prevDb[band]).coerceAtLeast(0f)
            flux += rise * if (band < bands / 4) 1f else 0.35f
            prevDb[band] = max(db, -90f)
        }
        times[head] = timeUs
        head = (head + 1) % cap
        if (count < cap) count++

        val seen = minOf(fluxCount, fluxHistory.size)
        var mean = flux
        if (seen > 0) {
            var sum = 0f
            for (i in 0 until seen) sum += fluxHistory[i]
            mean = sum / seen
        }
        val gapOk = lastBeatUs == Long.MIN_VALUE || timeUs - lastBeatUs > MIN_BEAT_GAP_US
        if (fluxCount >= 8 && gapOk && flux > mean * 1.6f + 6f) {
            lastBeatUs = timeUs
            beats[beatHead] = timeUs
            beatHead = (beatHead + 1) % beats.size
            if (beatCount < beats.size) beatCount++
        }
        fluxHistory[fluxCount % fluxHistory.size] = flux
        fluxCount++
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
        count = 0
        head = 0
        beatCount = 0
        fluxCount = 0
        lastBeatUs = Long.MIN_VALUE
        smooth.fill(0f)
        prevDb.fill(-90f)
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
        private const val MIN_BEAT_GAP_US = 250_000L
        private const val STALE_US = 250_000L
    }
}
