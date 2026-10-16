package io.lanyin.player.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sound effects and the analyzer, checked with synthetic signals. */
class AudioDspTest {

    private fun sine(hz: Float, rate: Int, frames: Int, amp: Float = 0.5f, channels: Int = 2, from: Int = 0) =
        FloatArray(frames * channels) { i -> amp * sin(2 * PI * hz * ((from + i / channels).toDouble() / rate)).toFloat() }

    private fun peak(buf: FloatArray, channels: Int = 2, ch: Int = 0, skipFrames: Int = 0): Float {
        var p = 0f
        for (f in skipFrames until buf.size / channels) p = maxOf(p, abs(buf[f * channels + ch]))
        return p
    }

    @Test
    fun `the fft finds a sine in its bin`() {
        val n = 256
        val fft = Fft(n)
        val re = FloatArray(n) { sin(2 * PI * 10 * it / n).toFloat() }
        val im = FloatArray(n)
        fft.transform(re, im)
        val mags = FloatArray(n / 2) { hypot(re[it], im[it]) }
        assertEquals(10, mags.indices.maxBy { mags[it] })
        assertEquals(n / 2f, mags[10], 0.01f)
    }

    @Test
    fun `a peaking band lifts its own frequency and leaves others alone`() {
        val rate = 44_100
        val boost = Biquad().apply { peaking(rate, 1_000f, 1.41f, 6f) }
        val at = sine(1_000f, rate, rate / 2, channels = 1)
        val below = sine(62f, rate, rate / 2, channels = 1)
        for (i in at.indices) at[i] = boost.process(at[i], 0)
        boost.clear()
        for (i in below.indices) below[i] = boost.process(below[i], 0)
        // +6 dB doubles the amplitude; two octaves below hardly changes.
        assertEquals(1.0f, peak(at, 1, skipFrames = 4_000), 0.03f)
        assertEquals(0.5f, peak(below, 1, skipFrames = 8_000), 0.03f)
    }

    @Test
    fun `原声 leaves the samples exactly as they were`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        val buf = sine(440f, 44_100, 2_048)
        val before = buf.copyOf()
        chain.process(buf, 2_048)
        assertArrayEquals(before, buf, 0f)
    }

    @Test
    fun `boosting presets never clip`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = EffectsSettings().choose(EffectPreset.BASS).with { copy(bass = 1f) }
        val buf = sine(60f, 44_100, 44_100, amp = 0.98f)
        chain.process(buf, 44_100)
        assertTrue(buf.all { abs(it) <= 1f })
        assertTrue(peak(buf, skipFrames = 20_000) > 0.5f)
    }

    @Test
    fun `width spreads the sides and leaves the middle`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = EffectsSettings(preset = EffectPreset.CUSTOM, amounts = EffectAmounts(width = 1f))
        val mono = sine(440f, 44_100, 4_096)
        chain.process(mono, 4_096)
        for (f in 0 until 4_096) assertEquals(mono[f * 2], mono[f * 2 + 1], 1e-5f)

        val side = sine(440f, 44_100, 4_096, amp = 0.3f).also { for (f in 0 until 4_096) it[f * 2 + 1] = -it[f * 2] }
        val sideBefore = peak(side, ch = 0)
        chain.process(side, 4_096)
        assertTrue(peak(side, ch = 0, skipFrames = 1_000) > sideBefore * 1.4f)
    }

    @Test
    fun `a room rings on after a click`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = EffectsSettings(preset = EffectPreset.CUSTOM, amounts = EffectAmounts(reverb = 1f))
        val buf = FloatArray(44_100 * 2)
        buf[0] = 0.9f
        buf[1] = 0.9f
        chain.process(buf, 44_100)
        // 50–300 ms after the click there is still sound.
        var tail = 0f
        for (f in 2_205 until 13_230) tail = maxOf(tail, abs(buf[f * 2]))
        assertTrue(tail > 0.005f)
    }

    @Test
    fun `moving a band makes the preset's curve your own`() {
        val rock = EffectsSettings().choose(EffectPreset.LIVE)
        assertEquals(0.35f, rock.reverb, 0f)
        val mine = rock.withBand(0, 20f)
        assertEquals(EffectPreset.CUSTOM, mine.preset)
        assertEquals(EQ_RANGE_DB, mine.curve[0], 0f)
        assertEquals(EffectPreset.LIVE.eq!![5], mine.curve[5], 0f)
    }

    private fun custom(amounts: EffectAmounts) = EffectsSettings(preset = EffectPreset.CUSTOM, amounts = amounts)

    private fun rms(buf: FloatArray, channels: Int, ch: Int, from: Int, to: Int): Float {
        var sum = 0.0
        for (f in from until to) sum += buf[f * channels + ch].toDouble().let { it * it }
        return kotlin.math.sqrt(sum / (to - from)).toFloat()
    }

    @Test
    fun `every preset stays within full scale and keeps the music`() {
        val rate = 44_100
        for (p in EffectPreset.entries) {
            val chain = EffectsChain().apply { configure(rate, 2) }
            chain.settings = EffectsSettings().choose(p)
            // A loud chord across the spectrum.
            val buf = FloatArray(rate * 2) { i ->
                val t = (i / 2).toDouble() / rate
                (0.3 * sin(2 * PI * 55 * t) + 0.3 * sin(2 * PI * 440 * t) + 0.3 * sin(2 * PI * 5_000 * t)).toFloat()
            }
            chain.process(buf, rate)
            assertTrue("${p.id} is finite", buf.all { it.isFinite() })
            assertTrue("${p.id} stays within ±1", buf.all { abs(it) <= 1f })
            assertTrue("${p.id} keeps the music", rms(buf, 2, 0, rate / 2, rate) > 0.05f)
        }
    }

    @Test
    fun `presets come in groups and keep their ids`() {
        assertTrue(EffectPreset.entries.size >= 40)
        for (g in PresetGroup.entries) assertTrue(g.label, EffectPreset.inGroup(g).size >= 5)
        assertEquals(EffectPreset.entries.size, EffectPreset.entries.map { it.id }.toSet().size)
        // Ids saved by earlier versions still mean the same thing.
        for (id in listOf("off", "pop", "rock", "jazz", "classical", "electronic", "vocal", "bass", "soft", "live", "surround", "custom")) {
            assertEquals(id, EffectPreset.fromId(id).id)
        }
        assertTrue(EffectPreset.entries.all { it.eq == null || it.eq!!.size == EQ_FREQUENCIES.size && it.eq!!.all { db -> abs(db) <= EQ_RANGE_DB } })
    }

    @Test
    fun `a bigger room rings longer`() {
        fun tail(room: Float): Float {
            val chain = EffectsChain().apply { configure(44_100, 2) }
            chain.settings = custom(EffectAmounts(reverb = 1f, room = room))
            val buf = FloatArray(44_100 * 2 * 2)
            buf[0] = 0.9f; buf[1] = 0.9f
            chain.process(buf, 88_200)
            return rms(buf, 2, 0, 44_100, 66_150)
        }
        assertTrue(tail(0.95f) > tail(0.1f) * 3f)
    }

    @Test
    fun `an echo answers a click about a quarter second later`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = custom(EffectAmounts(echo = 1f))
        val buf = FloatArray(44_100 * 2)
        buf[0] = 0.8f; buf[1] = 0.8f
        chain.process(buf, 44_100)
        val echoAt = (0.23f * 44_100).toInt()
        assertTrue(abs(buf[echoAt * 2]) > 0.1f)
        // Nothing in between.
        assertTrue(peak(buf.copyOfRange(200, (echoAt - 10) * 2)) < 1e-4f)
    }

    @Test
    fun `compression brings loud and quiet closer`() {
        fun level(amp: Float, compress: Float): Float {
            val chain = EffectsChain().apply { configure(44_100, 2) }
            chain.settings = custom(EffectAmounts(compress = compress))
            val buf = sine(440f, 44_100, 44_100, amp = amp)
            chain.process(buf, 44_100)
            return rms(buf, 2, 0, 22_050, 44_100)
        }
        val plain = level(0.8f, 0f) / level(0.05f, 0f)
        val squeezed = level(0.8f, 1f) / level(0.05f, 1f)
        assertTrue("$squeezed vs $plain", squeezed < plain / 2.5f)
    }

    @Test
    fun `the lo-fi band keeps the middle and loses both ends`() {
        fun through(hz: Float): Float {
            val chain = EffectsChain().apply { configure(44_100, 2) }
            chain.settings = custom(EffectAmounts(lofi = 1f))
            val buf = sine(hz, 44_100, 22_050, amp = 0.3f)
            chain.process(buf, 22_050)
            return rms(buf, 2, 0, 11_025, 22_050) / (0.3f / kotlin.math.sqrt(2f))
        }
        assertTrue(through(1_000f) > 0.8f)
        assertTrue(through(60f) < 0.2f)
        assertTrue(through(12_000f) < 0.2f)
    }

    @Test
    fun `warmth rounds off a loud sine`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = custom(EffectAmounts(warmth = 1f))
        val buf = sine(200f, 44_100, 22_050, amp = 0.9f)
        chain.process(buf, 22_050)
        val crest = peak(buf, skipFrames = 4_000) / rms(buf, 2, 0, 4_000, 22_050)
        // A pure sine's crest factor is √2.
        assertTrue("crest $crest", crest < 1.3f)
    }

    @Test
    fun `8D carries the sound from one ear to the other`() {
        val rate = 44_100
        val chain = EffectsChain().apply { configure(rate, 2) }
        chain.settings = custom(EffectAmounts(rotate = 1f))
        val seconds = 10
        val buf = sine(440f, rate, rate * seconds, amp = 0.4f)
        chain.process(buf, rate * seconds)
        val period = 1.0 / EffectsChain.ROTATE_HZ
        fun balance(at: Double): Float {
            val f = (at * rate).toInt()
            return rms(buf, 2, 1, f - 2_000, f + 2_000) / rms(buf, 2, 0, f - 2_000, f + 2_000)
        }
        // A quarter of the way round it is on the right; three quarters, on the left.
        assertTrue(balance(period / 4) > 4f)
        assertTrue(balance(period * 3 / 4) < 0.25f)
    }

    @Test
    fun `surface noise crackles over silence, quietly`() {
        val chain = EffectsChain().apply { configure(44_100, 2) }
        chain.settings = custom(EffectAmounts(crackle = 1f))
        val buf = FloatArray(44_100 * 2 * 3)
        chain.process(buf, 44_100 * 3)
        assertTrue(peak(buf) > 0.02f)
        assertTrue(peak(buf) < 0.25f)
        assertTrue(rms(buf, 2, 0, 0, 44_100 * 3) < 0.01f)
    }

    private fun pcm16(samples: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
        for (x in samples) b.putShort((x * 32767).toInt().toShort())
        b.flip()
        return b
    }

    /** Feeds [seconds] of [signal] (stereo PCM16, 44.1 kHz) in 4096-frame buffers, as a sink would. */
    private fun feed(analyzer: AudioAnalyzer, seconds: Float, rate: Int = 44_100, signal: (Int) -> Float) {
        val total = (rate * seconds).toInt()
        var frame = 0
        while (frame < total) {
            val count = minOf(4_096, total - frame)
            val buf = FloatArray(count * 2) { i -> signal(frame + i / 2) }
            analyzer.process(pcm16(buf), frame * 1_000_000L / rate)
            frame += count
        }
    }

    @Test
    fun `the analyzer hears a tone in the right band, at the right time`() {
        var now = 0L
        val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(44_100, 2, PcmEncoding.PCM16) }
        feed(analyzer, 2f) { f -> 0.5f * sin(2 * PI * 1_000 * f / 44_100.0).toFloat() }
        val out = FloatArray(32)
        // Nothing is playing yet.
        assertFalse(analyzer.levels(out))
        analyzer.anchor(1_000_000, playing = true)
        assertTrue(analyzer.levels(out))
        // 1 kHz sits about two thirds of the way up a 40 Hz–16 kHz log scale.
        val loudest = out.indices.maxBy { out[it] }
        assertTrue("loudest band $loudest", loudest in 15..19)
        assertTrue(out[2] < out[loudest] * 0.6f)
        // Playing on past the end of what was analysed (2 s), it says it doesn't know.
        analyzer.anchor(1_900_000, playing = true)
        assertTrue(analyzer.levels(out))
        now += 400_000_000
        assertFalse(analyzer.levels(out))
    }

    @Test
    fun `beats are found in a kick drum pattern`() {
        var now = 0L
        val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(44_100, 2, PcmEncoding.PCM16) }
        // A 60 Hz thump every half second, dying away over 80 ms as a drum does, over quiet noise.
        // (Cut off sharply instead, its end would click across the spectrum, and the click is an
        // onset too.)
        val rng = java.util.Random(1)
        feed(analyzer, 4f) { f ->
            val t = f / 44_100.0
            val inBeat = (t % 0.5) < 0.08
            val decay = kotlin.math.exp(-(t % 0.5) / 0.025).toFloat()
            (if (inBeat) 0.8f * decay * sin(2 * PI * 60 * t).toFloat() else 0f) + (rng.nextFloat() - 0.5f) * 0.01f
        }
        // Just after the beat at 3.0 s.
        analyzer.anchor(3_060_000, playing = true)
        assertTrue("since beat ${analyzer.sinceBeat()}", analyzer.sinceBeat() < 0.12f)
        // Between beats, a while since the last one.
        analyzer.anchor(3_400_000, playing = true)
        assertTrue(analyzer.sinceBeat() in 0.3f..0.5f)
        analyzer.reset()
        assertEquals(AudioAnalyzer.NO_BEAT, analyzer.sinceBeat(), 0f)
    }
    @Test
    fun `the beat follows the kick, not the hi-hats between`() {
        var now = 0L
        val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(44_100, 2, PcmEncoding.PCM16) }
        // 120 bpm: a 60 Hz kick every half second, and a bright hi-hat every eighth of a second.
        val rng = java.util.Random(2)
        feed(analyzer, 8f) { f ->
            val t = f / 44_100.0
            val kick = if ((t % 0.5) < 0.08) 0.7f * sin(2 * PI * 60 * t).toFloat() else 0f
            // A hi-hat is bright: noise high up the spectrum, nothing in the bass.
            val hat = if ((t % 0.125) < 0.02) 0.35f * sin(2 * PI * 9_000 * t).toFloat() * (0.5f + rng.nextFloat()) else 0f
            kick + hat + (rng.nextFloat() - 0.5f) * 0.01f
        }
        assertEquals(500_000.0, analyzer.beatPeriodUs.toDouble(), 60_000.0)
        val late = analyzer.beatTimes().filter { it > 4_000_000 }
        val gaps = late.zipWithNext { a, b -> b - a }
        assertTrue("beats $late", gaps.isNotEmpty() && gaps.all { it in 400_000..600_000 })
    }

    @Test
    fun `the beat keeps time through a kick left out`() {
        var now = 0L
        val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(44_100, 2, PcmEncoding.PCM16) }
        // 120 bpm with every fourth kick missing, as a syncopated bar or a break might leave it.
        val rng = java.util.Random(5)
        feed(analyzer, 10f) { f ->
            val t = f / 44_100.0
            val n = (t / 0.5).toInt()
            val kick = if (n % 4 != 3 && (t % 0.5) < 0.08) 0.7f * kotlin.math.exp(-(t % 0.5) / 0.025).toFloat() * sin(2 * PI * 60 * t).toFloat() else 0f
            kick + (rng.nextFloat() - 0.5f) * 0.01f
        }
        assertEquals(500_000.0, analyzer.beatPeriodUs.toDouble(), 30_000.0)
        val late = analyzer.beatTimes().filter { it > 5_000_000 }
        val gaps = late.zipWithNext { a, b -> b - a }
        // A beat where the missing kicks would have been, too: no gap of a whole second.
        assertTrue("beats $late", gaps.size >= 7 && gaps.all { it in 420_000..580_000 })
        // And on the kicks, not between them.
        assertTrue("beats $late", late.all { val off = (it % 500_000).let { o -> minOf(o, 500_000 - o) }; off < 60_000 })
    }

    @Test
    fun `a hi-res stream finds the same beat as a CD-rate one`() {
        for (rate in listOf(96_000, 192_000)) {
            var now = 0L
            val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(rate, 2, PcmEncoding.PCM16) }
            val rng = java.util.Random(4)
            feed(analyzer, 8f, rate) { f ->
                val t = f / rate.toDouble()
                val kick = if ((t % 0.5) < 0.08) 0.7f * sin(2 * PI * 60 * t).toFloat() else 0f
                kick + (rng.nextFloat() - 0.5f) * 0.01f
            }
            assertEquals("tempo at $rate Hz", 500_000.0, analyzer.beatPeriodUs.toDouble(), 60_000.0)
            val late = analyzer.beatTimes().filter { it > 4_000_000 }
            val gaps = late.zipWithNext { a, b -> b - a }
            assertTrue("beats at $rate Hz: $late", gaps.isNotEmpty() && gaps.all { it in 400_000..600_000 })
        }
    }

    @Test
    fun `busy music without a steady kick gives at most about two beats a second`() {
        var now = 0L
        val analyzer = AudioAnalyzer(nowNanos = { now }).apply { configure(44_100, 2, PcmEncoding.PCM16) }
        // Bursts of low noise at random moments, as a dense, uneven arrangement might give.
        val rng = java.util.Random(3)
        val onsets = generateSequence(0.0) { it + 0.12 + rng.nextDouble() * 0.2 }.takeWhile { it < 6.0 }.toList()
        feed(analyzer, 6f) { f ->
            val t = f / 44_100.0
            val hit = onsets.any { t - it in 0.0..0.05 }
            (if (hit) 0.6f * sin(2 * PI * 70 * t).toFloat() else 0f) + (rng.nextFloat() - 0.5f) * 0.01f
        }
        val gaps = analyzer.beatTimes().zipWithNext { a, b -> b - a }
        assertTrue("gaps $gaps", gaps.all { it >= 300_000 })
    }
}
