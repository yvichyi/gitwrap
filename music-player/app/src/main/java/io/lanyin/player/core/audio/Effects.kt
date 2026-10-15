package io.lanyin.player.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** The equaliser's ten bands, an octave apart. */
val EQ_FREQUENCIES = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

/** How far each band goes either way, in dB. */
const val EQ_RANGE_DB = 12f

/**
 * A starting point: an EQ curve plus how much bass, width and room it brings. [eq] is null for
 * 原声 (nothing applied) and 自定义 (the listener's own curve).
 */
enum class EffectPreset(val id: String, val label: String, val eq: FloatArray?, val bass: Float = 0f, val width: Float = 0f, val reverb: Float = 0f) {
    OFF("off", "原声", null),
    POP("pop", "流行", floatArrayOf(-1f, 0f, 2f, 3f, 2f, 0f, -1f, 0f, 1f, 2f)),
    ROCK("rock", "摇滚", floatArrayOf(4f, 3f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 4f)),
    JAZZ("jazz", "爵士", floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    CLASSICAL("classical", "古典", floatArrayOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f), reverb = 0.12f),
    ELECTRONIC("electronic", "电子", floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f), width = 0.2f),
    VOCAL("vocal", "人声", floatArrayOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    BASS("bass", "重低音", floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f), bass = 0.5f),
    SOFT("soft", "柔和", floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f, -1f, -2f, -3f, -4f)),
    LIVE("live", "现场", floatArrayOf(-2f, 0f, 2f, 3f, 3f, 2f, 2f, 1f, 1f, 1f), width = 0.3f, reverb = 0.35f),
    SURROUND("surround", "3D 环绕", floatArrayOf(2f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 2f), width = 0.8f, reverb = 0.15f),
    CUSTOM("custom", "自定义", null);

    companion object {
        fun fromId(id: String?): EffectPreset = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** The listener's choice: a preset (or their own curve) and three amounts, each 0..1. */
data class EffectsSettings(
    val preset: EffectPreset = EffectPreset.OFF,
    val custom: List<Float> = List(EQ_FREQUENCIES.size) { 0f },
    val bass: Float = 0f,
    val width: Float = 0f,
    val reverb: Float = 0f,
) {
    val enabled: Boolean get() = preset != EffectPreset.OFF

    /** The curve in use, in dB per band. */
    val curve: FloatArray get() = preset.eq?.copyOf() ?: if (preset == EffectPreset.CUSTOM) custom.toFloatArray() else FloatArray(EQ_FREQUENCIES.size)

    /** Picks a preset, taking its bass, width and room as the new starting amounts. */
    fun choose(p: EffectPreset): EffectsSettings = copy(preset = p, bass = p.bass, width = p.width, reverb = p.reverb)

    /** Moves one band; a preset's curve becomes the listener's own from there. */
    fun withBand(index: Int, db: Float): EffectsSettings {
        val base = curve.toMutableList()
        base[index] = db.coerceIn(-EQ_RANGE_DB, EQ_RANGE_DB)
        return copy(preset = EffectPreset.CUSTOM, custom = base)
    }
}

/** A biquad filter (RBJ's cookbook), transposed direct form II, one per channel. */
class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = FloatArray(MAX_CHANNELS)
    private var z2 = FloatArray(MAX_CHANNELS)

    fun peaking(rate: Int, hz: Float, q: Float, db: Float) {
        val a = 10f.pow(db / 40f)
        val w = 2.0 * PI * hz / rate
        val alpha = (sin(w) / (2 * q)).toFloat()
        val cs = cos(w).toFloat()
        set(1 + alpha * a, -2 * cs, 1 - alpha * a, 1 + alpha / a, -2 * cs, 1 - alpha / a)
    }

    fun lowShelf(rate: Int, hz: Float, slope: Float, db: Float) {
        val a = 10f.pow(db / 40f)
        val w = 2.0 * PI * hz / rate
        val cs = cos(w).toFloat()
        val alpha = (sin(w) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)).toFloat()
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * cs + sa), 2 * a * ((a - 1) - (a + 1) * cs), a * ((a + 1) - (a - 1) * cs - sa),
            (a + 1) + (a - 1) * cs + sa, -2 * ((a - 1) + (a + 1) * cs), (a + 1) + (a - 1) * cs - sa,
        )
    }

    private fun set(nb0: Float, nb1: Float, nb2: Float, na0: Float, na1: Float, na2: Float) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    fun clear() {
        z1.fill(0f)
        z2.fill(0f)
    }

    fun process(x: Float, ch: Int): Float {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    companion object {
        const val MAX_CHANNELS = 8
    }
}

/** A small Freeverb-style room: four damped combs and two allpasses per side. */
private class Room(rate: Int) {
    private val scale = rate / 44_100f
    private val combs = Array(2) { side -> intArrayOf(1116, 1188, 1277, 1356).map { Comb(((it + side * 23) * scale).toInt()) } }
    private val passes = Array(2) { side -> intArrayOf(556, 441).map { AllPass(((it + side * 23) * scale).toInt()) } }

    fun process(input: Float, side: Int): Float {
        var out = 0f
        for (c in combs[side]) out += c.process(input)
        for (a in passes[side]) out = a.process(out)
        return out
    }

    fun clear() {
        combs.forEach { list -> list.forEach { it.clear() } }
        passes.forEach { list -> list.forEach { it.clear() } }
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var i = 0
        private var store = 0f
        fun process(x: Float): Float {
            val y = buf[i]
            store = y * 0.6f + store * 0.4f
            buf[i] = x + store * 0.82f
            i = (i + 1) % buf.size
            return y
        }
        fun clear() {
            buf.fill(0f)
            store = 0f
        }
    }

    private class AllPass(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var i = 0
        fun process(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * 0.5f
            i = (i + 1) % buf.size
            return b - x
        }
        fun clear() = buf.fill(0f)
    }
}

/**
 * The sound effects, on interleaved float samples: headroom, the ten-band EQ, a bass shelf,
 * stereo width (mid/side), a little room, and a soft limiter so boosts never clip harshly.
 * [settings] may change from any thread; the filters are worked out again on the audio thread.
 */
class EffectsChain {
    @Volatile
    var settings: EffectsSettings = EffectsSettings()

    private var applied: EffectsSettings? = null
    private var rate = 44_100
    private var channels = 2
    private val eq = Array(EQ_FREQUENCIES.size) { Biquad() }
    private val bassShelf = Biquad()
    private var headroom = 1f
    private var room: Room? = null

    val enabled: Boolean get() = settings.enabled

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate
        channels = channelCount.coerceIn(1, Biquad.MAX_CHANNELS)
        applied = null
        room = Room(sampleRate)
    }

    fun clear() {
        eq.forEach { it.clear() }
        bassShelf.clear()
        room?.clear()
    }

    private fun update(s: EffectsSettings) {
        val curve = s.curve
        for (i in eq.indices) {
            // Bands at or above Nyquist can't be filtered; leave them flat.
            val hz = EQ_FREQUENCIES[i]
            eq[i].peaking(rate, if (hz < rate * 0.45f) hz else rate * 0.45f, 1.41f, if (hz < rate * 0.45f) curve[i] else 0f)
        }
        val bassDb = s.bass * 10f
        bassShelf.lowShelf(rate, 90f, 0.8f, bassDb)
        // Make room for the biggest boost so loud passages don't hit the limiter all the time.
        val boost = max(curve.maxOrNull()?.coerceAtLeast(0f) ?: 0f, 0f) + bassDb * 0.6f
        headroom = 10f.pow(-boost * 0.8f / 20f)
        applied = s
    }

    /** Processes [frames] interleaved frames of [buf] in place. */
    fun process(buf: FloatArray, frames: Int) {
        val s = settings
        if (!s.enabled) return
        if (s !== applied) update(s)
        val width = 1f + s.width * 1.2f
        val widthGain = 1f / (1f + s.width * 0.3f)
        val wet = s.reverb * 0.32f
        val room = room
        for (f in 0 until frames) {
            val base = f * channels
            for (ch in 0 until channels) {
                var x = buf[base + ch] * headroom
                for (b in eq) x = b.process(x, ch)
                if (s.bass > 0f) x = bassShelf.process(x, ch)
                buf[base + ch] = x
            }
            if (channels >= 2) {
                var l = buf[base]
                var r = buf[base + 1]
                if (s.width > 0f) {
                    val m = (l + r) * 0.5f
                    val d = (l - r) * 0.5f * width
                    l = (m + d) * widthGain
                    r = (m - d) * widthGain
                }
                if (wet > 0f && room != null) {
                    val mono = (l + r) * 0.5f * 0.15f
                    l = l * (1f - wet * 0.5f) + room.process(mono, 0) * wet
                    r = r * (1f - wet * 0.5f) + room.process(mono, 1) * wet
                }
                buf[base] = limit(l)
                buf[base + 1] = limit(r)
            } else {
                var x = buf[base]
                if (wet > 0f && room != null) x = x * (1f - wet * 0.5f) + room.process(x * 0.15f, 0) * wet
                buf[base] = limit(x)
            }
        }
    }

    companion object {
        private const val KNEE = 0.89f

        /** Leaves everything below the knee alone and bends what is above it smoothly towards ±1. */
        fun limit(x: Float): Float {
            val a = abs(x)
            if (a <= KNEE) return x
            return sign(x) * (KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE)))
        }
    }
}
