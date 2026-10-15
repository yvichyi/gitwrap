package io.lanyin.player.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
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

/** Where a preset sits in the sheet. */
enum class PresetGroup(val label: String) {
    STYLE("曲风"),
    SCENE("场景"),
    TONE("人声 · 低音"),
    RETRO("复古"),
}

/**
 * A starting point: an EQ curve plus how much of each effect. [eq] is null for 原声 (nothing
 * applied) and 自定义 (the listener's own curve). Curves marked VLC are VideoLAN's long-standing
 * presets (`modules/audio_filter/equalizer_presets.h`, inherited from Winamp) at 0.6 of their
 * strength, since the chain makes its own headroom; the rest are tuned here.
 */
enum class EffectPreset(
    val id: String,
    val label: String,
    val group: PresetGroup?,
    val eq: FloatArray?,
    val amounts: EffectAmounts = EffectAmounts(),
) {
    OFF("off", "原声", null, null),

    // —— 曲风 ——
    POP("pop", "流行", PresetGroup.STYLE, floatArrayOf(-1f, 3f, 4.5f, 5f, 3.5f, 0f, -1.5f, -1.5f, -1f, -1f)), // VLC
    ROCK("rock", "摇滚", PresetGroup.STYLE, floatArrayOf(5f, 3f, -3.5f, -5f, -2f, 2.5f, 5.5f, 6.5f, 6.5f, 6.5f)), // VLC
    SOFT_ROCK("softrock", "软摇滚", PresetGroup.STYLE, floatArrayOf(2.5f, 2.5f, 1.5f, 0f, -2.5f, -3.5f, -2f, 0f, 1.5f, 5.5f)), // VLC
    METAL("metal", "金属", PresetGroup.STYLE, floatArrayOf(4f, 3f, 0f, -2f, -3f, 0f, 2.5f, 4f, 4.5f, 4f)),
    JAZZ("jazz", "爵士", PresetGroup.STYLE, floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f), EffectAmounts(reverb = 0.1f, room = 0.3f)),
    BLUES("blues", "蓝调", PresetGroup.STYLE, floatArrayOf(2f, 1.5f, 1f, 0f, 0f, 1f, 2f, 1f, 0f, 0f), EffectAmounts(warmth = 0.15f)),
    CLASSICAL("classical", "古典", PresetGroup.STYLE, floatArrayOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f), EffectAmounts(reverb = 0.15f, room = 0.6f)),
    FOLK("folk", "民谣", PresetGroup.STYLE, floatArrayOf(-1f, 0f, 1.5f, 2f, 1f, 1.5f, 2.5f, 2f, 1f, 0.5f), EffectAmounts(reverb = 0.08f, room = 0.3f, warmth = 0.15f)),
    COUNTRY("country", "乡村", PresetGroup.STYLE, floatArrayOf(0f, 1f, 2f, 1f, 0f, 1f, 2.5f, 3f, 2.5f, 2f)),
    GUOFENG("guofeng", "国风", PresetGroup.STYLE, floatArrayOf(0f, 0f, 1f, 0f, -1f, 1f, 2.5f, 3f, 2f, 1.5f), EffectAmounts(reverb = 0.18f, room = 0.55f, width = 0.15f)),
    HIPHOP("hiphop", "嘻哈", PresetGroup.STYLE, floatArrayOf(5f, 4.5f, 2f, 0f, -1f, -1f, 1f, 1.5f, 2f, 2.5f), EffectAmounts(bass = 0.2f)),
    RNB("rnb", "R&B", PresetGroup.STYLE, floatArrayOf(3f, 5f, 4f, 1f, -1.5f, -1f, 1.5f, 2f, 2.5f, 3f)),
    ELECTRONIC("electronic", "电子", PresetGroup.STYLE, floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f), EffectAmounts(width = 0.2f)),
    DANCE("dance", "舞曲", PresetGroup.STYLE, floatArrayOf(6f, 4.5f, 1.5f, 0f, 0f, -3.5f, -4.5f, -4.5f, 0f, 0f)), // VLC
    CLUB("club", "俱乐部", PresetGroup.STYLE, floatArrayOf(0f, 0f, 5f, 3.5f, 3.5f, 3.5f, 2f, 0f, 0f, 0f), EffectAmounts(width = 0.15f)), // VLC
    TECHNO("techno", "Techno", PresetGroup.STYLE, floatArrayOf(5f, 3.5f, 0f, -3.5f, -3f, 0f, 5f, 6f, 6f, 5.5f)), // VLC
    PARTY("party", "派对", PresetGroup.STYLE, floatArrayOf(4.5f, 4.5f, 0f, 0f, 0f, 0f, 0f, 0f, 4.5f, 4.5f)), // VLC
    REGGAE("reggae", "雷鬼", PresetGroup.STYLE, floatArrayOf(0f, 0f, 0f, -3.5f, 0f, 4f, 4f, 0f, 0f, 0f)), // VLC
    SKA("ska", "斯卡", PresetGroup.STYLE, floatArrayOf(-1.5f, -3f, -2.5f, 0f, 2.5f, 3.5f, 5.5f, 6f, 6.5f, 6f)), // VLC

    // —— 场景 ——
    LIVE("live", "LiveHouse", PresetGroup.SCENE, floatArrayOf(-3f, 0f, 2.5f, 3.5f, 3.5f, 3.5f, 2.5f, 1.5f, 1.5f, 1.5f), EffectAmounts(width = 0.3f, reverb = 0.35f, room = 0.45f)), // VLC
    HALL("hall", "音乐厅", PresetGroup.SCENE, floatArrayOf(6f, 6f, 3.5f, 3.5f, 0f, -3f, -3f, -3f, 0f, 0f), EffectAmounts(width = 0.2f, reverb = 0.42f, room = 0.75f)), // VLC
    CHURCH("church", "教堂", PresetGroup.SCENE, floatArrayOf(1f, 1f, 0.5f, 0f, 0f, 0f, -1f, -2f, -3f, -4f), EffectAmounts(width = 0.25f, reverb = 0.6f, room = 0.95f)),
    KTV("ktv", "KTV", PresetGroup.SCENE, floatArrayOf(0f, 0f, 0f, 1f, 2.5f, 3f, 2f, 1f, 0f, 0f), EffectAmounts(reverb = 0.35f, room = 0.5f, echo = 0.45f)),
    STUDIO("studio", "录音棚", PresetGroup.SCENE, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 1f, 1f, 0.5f), EffectAmounts(reverb = 0.1f, room = 0.2f, compress = 0.25f)),
    SURROUND("surround", "3D 环绕", PresetGroup.SCENE, floatArrayOf(2f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 2f), EffectAmounts(width = 0.8f, reverb = 0.15f, room = 0.4f)),
    ROTATE("rotate", "8D 环绕", PresetGroup.SCENE, floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0.5f, 1f, 1f), EffectAmounts(width = 0.2f, reverb = 0.2f, room = 0.5f, rotate = 0.75f)),
    PSYCHEDELIC("psychedelic", "迷幻电音", PresetGroup.SCENE, floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f), EffectAmounts(width = 0.5f, reverb = 0.25f, room = 0.6f, echo = 0.3f, rotate = 0.35f)),
    CAR("car", "车载", PresetGroup.SCENE, floatArrayOf(3f, 3f, 1.5f, 0f, -1f, 0f, 1f, 1.5f, 1f, 0f), EffectAmounts(bass = 0.2f, compress = 0.45f)),
    HEADPHONES("headphones", "耳机", PresetGroup.SCENE, floatArrayOf(3f, 6.5f, 3.5f, -2f, -1.5f, 1f, 3f, 6f, 7.5f, 8.5f)), // VLC
    NIGHT("night", "夜间", PresetGroup.SCENE, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -0.5f, -1f, -1.5f, -2f), EffectAmounts(compress = 0.85f)),

    // —— 人声 · 低音 ——
    VOCAL("vocal", "人声", PresetGroup.TONE, floatArrayOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    CLEAR_VOCAL("clearvocal", "清澈人声", PresetGroup.TONE, floatArrayOf(-2f, -1.5f, -1f, 0f, 1f, 2.5f, 3.5f, 3f, 2f, 1f), EffectAmounts(compress = 0.2f)),
    BASS("bass", "重低音", PresetGroup.TONE, floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f), EffectAmounts(bass = 0.5f)),
    FULL_BASS("fullbass", "超重低音", PresetGroup.TONE, floatArrayOf(-5f, 6f, 6f, 3.5f, 1f, -2.5f, -5f, -6f, -6.5f, -6.5f), EffectAmounts(bass = 0.7f)), // VLC
    BASS_TREBLE("basstreble", "低音+高音", PresetGroup.TONE, floatArrayOf(4.5f, 3.5f, 0f, -4.5f, -3f, 1f, 5f, 6.5f, 7f, 7f)), // VLC
    TREBLE("treble", "高音增强", PresetGroup.TONE, floatArrayOf(-3f, -3f, -3f, -1.5f, 1f, 3.5f, 5f, 5f, 5f, 5.5f)), // VLC, at half
    LOUDNESS("loudness", "响度补偿", PresetGroup.TONE, floatArrayOf(4f, 3f, 1.5f, 0f, -0.5f, 0f, 0.5f, 1.5f, 2.5f, 3f)),
    SOFT("soft", "柔和", PresetGroup.TONE, floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f, -1f, -2f, -3f, -4f)),

    // —— 复古 ——
    VINYL("vinyl", "黑胶", PresetGroup.RETRO, floatArrayOf(1.5f, 1.5f, 1f, 0.5f, 0f, 0f, -0.5f, -1.5f, -3f, -5f), EffectAmounts(warmth = 0.45f, crackle = 0.5f)),
    TUBE("tube", "电子管", PresetGroup.RETRO, floatArrayOf(1f, 1f, 0.5f, 0.5f, 0f, 0f, 0f, -0.5f, -1f, -2f), EffectAmounts(warmth = 0.7f)),
    CASSETTE("cassette", "卡带", PresetGroup.RETRO, floatArrayOf(1f, 1.5f, 1f, 0f, 0f, 0f, -0.5f, -1.5f, -3f, -5f), EffectAmounts(warmth = 0.35f, lofi = 0.25f, crackle = 0.15f)),
    LOFI("lofi", "Lo-fi", PresetGroup.RETRO, floatArrayOf(2f, 1.5f, 1f, 0f, 0f, 0f, -1f, -2f, -3f, -4f), EffectAmounts(bass = 0.2f, warmth = 0.4f, lofi = 0.4f, crackle = 0.35f)),
    RADIO("radio", "收音机", PresetGroup.RETRO, floatArrayOf(-6f, -6f, -3f, 0f, 2f, 3f, 2f, 0f, -3f, -6f), EffectAmounts(warmth = 0.3f, lofi = 0.9f, compress = 0.4f)),

    CUSTOM("custom", "自定义", null, null);

    companion object {
        fun fromId(id: String?): EffectPreset = entries.firstOrNull { it.id == id } ?: OFF

        fun inGroup(group: PresetGroup): List<EffectPreset> = entries.filter { it.group == group }
    }
}

/** How much of each effect, every one 0..1. */
data class EffectAmounts(
    /** A shelf under 90 Hz, up to +10 dB. */
    val bass: Float = 0f,
    /** Stereo width, by raising the side signal. */
    val width: Float = 0f,
    /** How much room is heard. */
    val reverb: Float = 0f,
    /** How big the room is: a small studio to a stone church. */
    val room: Float = 0.35f,
    /** A ping-pong echo, about a quarter second. */
    val echo: Float = 0f,
    /** Evens out loud and quiet. */
    val compress: Float = 0f,
    /** Tube-style saturation with the top softened. */
    val warmth: Float = 0f,
    /** Narrows the band towards an AM radio's. */
    val lofi: Float = 0f,
    /** Surface noise: hiss and the odd click. */
    val crackle: Float = 0f,
    /** Sends the sound around the head (8D). */
    val rotate: Float = 0f,
)

/** The listener's choice: a preset (or their own curve) and the amounts, starting from the preset's. */
data class EffectsSettings(
    val preset: EffectPreset = EffectPreset.OFF,
    val custom: List<Float> = List(EQ_FREQUENCIES.size) { 0f },
    val amounts: EffectAmounts = EffectAmounts(),
) {
    val enabled: Boolean get() = preset != EffectPreset.OFF

    val bass: Float get() = amounts.bass
    val width: Float get() = amounts.width
    val reverb: Float get() = amounts.reverb

    /** The curve in use, in dB per band. */
    val curve: FloatArray get() = preset.eq?.copyOf() ?: if (preset == EffectPreset.CUSTOM) custom.toFloatArray() else FloatArray(EQ_FREQUENCIES.size)

    /** Picks a preset, taking its amounts as the new starting point. */
    fun choose(p: EffectPreset): EffectsSettings = copy(preset = p, amounts = p.amounts)

    /** Changes the amounts, keeping the preset. */
    fun with(change: EffectAmounts.() -> EffectAmounts): EffectsSettings = copy(amounts = amounts.change())

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

    fun lowPass(rate: Int, hz: Float, q: Float = 0.707f) {
        val w = 2.0 * PI * hz.coerceAtMost(rate * 0.45f) / rate
        val alpha = (sin(w) / (2 * q)).toFloat()
        val cs = cos(w).toFloat()
        set((1 - cs) / 2, 1 - cs, (1 - cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
    }

    fun highPass(rate: Int, hz: Float, q: Float = 0.707f) {
        val w = 2.0 * PI * hz.coerceAtMost(rate * 0.45f) / rate
        val alpha = (sin(w) / (2 * q)).toFloat()
        val cs = cos(w).toFloat()
        set((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
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

/**
 * A Freeverb-style room: four damped combs and two allpasses per side. [size] sets how long the
 * tail rings (comb feedback) and how dull it gets (damping), from a small studio to a church.
 */
private class Room(rate: Int) {
    private val scale = rate / 44_100f
    private val combs = Array(2) { side -> intArrayOf(1116, 1188, 1277, 1356).map { Comb(((it + side * 23) * scale).toInt()) } }
    private val passes = Array(2) { side -> intArrayOf(556, 441).map { AllPass(((it + side * 23) * scale).toInt()) } }
    private var feedback = 0.82f
    private var damp = 0.4f

    var size: Float = 0.35f
        set(value) {
            field = value
            feedback = 0.72f + 0.24f * value
            damp = 0.5f - 0.25f * value
        }

    fun process(input: Float, side: Int): Float {
        var out = 0f
        for (c in combs[side]) out += c.process(input, feedback, damp)
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
        fun process(x: Float, feedback: Float, damp: Float): Float {
            val y = buf[i]
            store = y * (1f - damp) + store * damp
            buf[i] = x + store * feedback
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

/** Two delay lines feeding each other, so echoes bounce left and right and darken as they fade. */
private class PingPong(rate: Int) {
    private val left = FloatArray((rate * 0.23f).toInt().coerceAtLeast(1))
    private val right = FloatArray((rate * 0.29f).toInt().coerceAtLeast(1))
    private var li = 0
    private var ri = 0
    private var toneL = 0f
    private var toneR = 0f
    var outL = 0f
        private set
    var outR = 0f
        private set

    fun process(input: Float, feedback: Float) {
        outL = left[li]
        outR = right[ri]
        toneL += 0.45f * (outR - toneL)
        toneR += 0.45f * (outL - toneR)
        left[li] = input + toneL * feedback
        right[ri] = toneR * feedback
        li = (li + 1) % left.size
        ri = (ri + 1) % right.size
    }

    fun clear() {
        left.fill(0f); right.fill(0f)
        toneL = 0f; toneR = 0f; outL = 0f; outR = 0f
    }
}

/**
 * The sound effects, on interleaved float samples. Per channel: headroom, the ten-band EQ, a
 * bass shelf, warmth (saturation, top softened) and the lo-fi band; then a compressor across
 * channels; then, on the stereo pair, width, 8D rotation, echo and the room; surface noise; and a
 * soft limiter so boosts never clip harshly. [settings] may change from any thread; the filters
 * are worked out again on the audio thread.
 */
class EffectsChain {
    @Volatile
    var settings: EffectsSettings = EffectsSettings()

    private var applied: EffectsSettings? = null
    private var rate = 44_100
    private var channels = 2
    private val eq = Array(EQ_FREQUENCIES.size) { Biquad() }
    private val bassShelf = Biquad()
    private val warmTop = Biquad()
    private val bandLow = Array(2) { Biquad() }
    private val bandHigh = Array(2) { Biquad() }
    private var headroom = 1f
    private var room: Room? = null
    private var echo: PingPong? = null

    // Compressor state: a level follower in dB and its attack and release coefficients.
    private var envelope = 0f
    private var attack = 0f
    private var release = 0f

    // 8D: where the sound is on its circle.
    private var phase = 0.0

    // Surface noise.
    private var seed = 0x2545F491
    private var click = 0f

    val enabled: Boolean get() = settings.enabled

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate
        channels = channelCount.coerceIn(1, Biquad.MAX_CHANNELS)
        applied = null
        room = Room(sampleRate)
        echo = PingPong(sampleRate)
        attack = exp(-1f / (0.005f * sampleRate))
        release = exp(-1f / (0.15f * sampleRate))
    }

    fun clear() {
        eq.forEach { it.clear() }
        bassShelf.clear()
        warmTop.clear()
        bandLow.forEach { it.clear() }
        bandHigh.forEach { it.clear() }
        room?.clear()
        echo?.clear()
        envelope = 0f
        click = 0f
    }

    private fun update(s: EffectsSettings) {
        val a = s.amounts
        val curve = s.curve
        for (i in eq.indices) {
            // Bands at or above Nyquist can't be filtered; leave them flat.
            val hz = EQ_FREQUENCIES[i]
            eq[i].peaking(rate, if (hz < rate * 0.45f) hz else rate * 0.45f, 1.41f, if (hz < rate * 0.45f) curve[i] else 0f)
        }
        val bassDb = a.bass * 10f
        bassShelf.lowShelf(rate, 90f, 0.8f, bassDb)
        warmTop.lowPass(rate, 18_000f - 9_000f * a.warmth)
        // The lo-fi band closes in from both ends: 20 Hz–19 kHz down to about 380 Hz–3.2 kHz.
        val lo = 20f + 360f * a.lofi
        val hi = 19_000f - 15_800f * a.lofi
        bandLow.forEach { it.highPass(rate, lo) }
        bandHigh.forEach { it.lowPass(rate, hi) }
        room?.size = a.room
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
        val a = s.amounts
        val width = 1f + a.width * 1.2f
        val widthGain = 1f / (1f + a.width * 0.3f)
        val wet = a.reverb * 0.32f
        val room = room
        val echo = echo
        val echoWet = a.echo * 0.42f
        val echoFeedback = 0.25f + 0.3f * a.echo
        val drive = 1f + 4f * a.warmth
        val driveNorm = 1f / tanh(drive)
        val warmMix = a.warmth * 0.7f
        // Compressor: threshold from -10 down to -30 dB, ratio 1.5 up to 6, half the reduction made back.
        val threshold = -10f - 20f * a.compress
        val slope = 1f - 1f / (1.5f + 4.5f * a.compress)
        val makeup = -threshold * slope * 0.5f
        val step = 2.0 * PI * ROTATE_HZ / rate
        val hiss = a.crackle * 0.0035f
        val clicksPerFrame = a.crackle * 3f / rate
        for (f in 0 until frames) {
            val base = f * channels
            var peak = 0f
            for (ch in 0 until channels) {
                var x = buf[base + ch] * headroom
                for (b in eq) x = b.process(x, ch)
                if (a.bass > 0f) x = bassShelf.process(x, ch)
                if (a.warmth > 0f) {
                    val sat = tanh(x * drive) * driveNorm
                    x = warmTop.process(x * (1f - warmMix) + sat * warmMix, ch)
                }
                if (a.lofi > 0f && ch < 2) x = bandHigh[ch].process(bandLow[ch].process(x, 0), 0)
                buf[base + ch] = x
                peak = max(peak, abs(x))
            }
            if (a.compress > 0f) {
                val db = if (peak > 1e-6f) 20f * log10(peak) else -120f
                envelope = if (db > envelope) attack * envelope + (1f - attack) * db else release * envelope + (1f - release) * db
                val over = envelope - threshold
                val gain = 10f.pow(((if (over > 0f) -over * slope else 0f) + makeup) / 20f)
                for (ch in 0 until channels) buf[base + ch] *= gain
            }
            if (channels >= 2) {
                var l = buf[base]
                var r = buf[base + 1]
                if (a.lofi > 0f) {
                    // Radios and old records are near mono.
                    val m = (l + r) * 0.5f
                    l += (m - l) * a.lofi * 0.8f
                    r += (m - r) * a.lofi * 0.8f
                }
                if (a.width > 0f) {
                    val m = (l + r) * 0.5f
                    val d = (l - r) * 0.5f * width
                    l = (m + d) * widthGain
                    r = (m - d) * widthGain
                }
                if (a.rotate > 0f) {
                    // Round the head: an equal-power pan on a slow circle, a little quieter behind.
                    phase = (phase + step) % (2.0 * PI)
                    val pan = sin(phase).toFloat()
                    val behind = 1f - 0.2f * a.rotate * max(0f, -cos(phase).toFloat())
                    val m = (l + r) * 0.5f * SQRT2
                    val gl = sqrt((1f - pan) * 0.5f)
                    val gr = sqrt((1f + pan) * 0.5f)
                    l = (l * (1f - a.rotate) + m * gl * a.rotate) * behind
                    r = (r * (1f - a.rotate) + m * gr * a.rotate) * behind
                }
                if (echoWet > 0f && echo != null) {
                    echo.process((l + r) * 0.5f, echoFeedback)
                    l += echo.outL * echoWet
                    r += echo.outR * echoWet
                }
                if (wet > 0f && room != null) {
                    val mono = (l + r) * 0.5f * 0.15f
                    l = l * (1f - wet * 0.5f) + room.process(mono, 0) * wet
                    r = r * (1f - wet * 0.5f) + room.process(mono, 1) * wet
                }
                if (hiss > 0f) {
                    val c = crackle(clicksPerFrame, a.crackle)
                    l += c + noise() * hiss
                    r += c + noise() * hiss
                }
                buf[base] = limit(l)
                buf[base + 1] = limit(r)
                for (ch in 2 until channels) buf[base + ch] = limit(buf[base + ch])
            } else {
                var x = buf[base]
                if (echoWet > 0f && echo != null) {
                    echo.process(x, echoFeedback)
                    x += (echo.outL + echo.outR) * 0.5f * echoWet
                }
                if (wet > 0f && room != null) x = x * (1f - wet * 0.5f) + room.process(x * 0.15f, 0) * wet
                if (hiss > 0f) x += crackle(clicksPerFrame, a.crackle) + noise() * hiss
                buf[base] = limit(x)
            }
        }
    }

    /** White noise in -1..1 (xorshift). */
    private fun noise(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return x / 2_147_483_648f
    }

    /** A dust click now and then: a sharp spike that dies within a millisecond. */
    private fun crackle(chance: Float, amount: Float): Float {
        if ((noise() + 1f) * 0.5f < chance) click = (0.04f + 0.12f * abs(noise())) * amount * if (noise() > 0f) 1f else -1f
        val out = click
        click *= 0.55f
        return out
    }

    companion object {
        private const val KNEE = 0.89f
        private const val SQRT2 = 1.4142135f

        /** Once round the head every nine seconds or so. */
        const val ROTATE_HZ = 0.11

        /** Leaves everything below the knee alone and bends what is above it smoothly towards ±1. */
        fun limit(x: Float): Float {
            val a = abs(x)
            if (a <= KNEE) return x
            return sign(x) * (KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE)))
        }
    }
}
