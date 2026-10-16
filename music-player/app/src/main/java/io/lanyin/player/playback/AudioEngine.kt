package io.lanyin.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import io.lanyin.player.core.audio.AudioAnalyzer
import io.lanyin.player.core.audio.EffectsChain
import io.lanyin.player.core.audio.PcmEncoding
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What happens to the sound between the decoder and the speaker: the analyzer listens (for the
 * spectrum and beats on screen) and the effects chain shapes it (音效). One per app, shared by the
 * playback service and the UI.
 */
class AudioEngine {
    val analyzer = AudioAnalyzer()
    val effects = EffectsChain()

    /** Renderers whose audio goes through the analyzer and the effects. */
    @OptIn(UnstableApi::class)
    fun renderersFactory(context: Context): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            AnalyzingAudioSink(
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(EffectsProcessor(effects)))
                    .build(),
                analyzer,
            )
    }
}

/**
 * Applies [EffectsChain] to 16-bit or float PCM. Always active for those formats so turning
 * effects on or off needs no reconfiguring; with them off the audio is copied through untouched.
 */
@OptIn(UnstableApi::class)
internal class EffectsProcessor(private val chain: EffectsChain) : BaseAudioProcessor() {
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        chain.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (!chain.enabled) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val samples = size / if (float) 4 else 2
        if (scratch.size < samples) scratch = FloatArray(samples)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (float) {
            for (i in 0 until samples) scratch[i] = input.getFloat()
        } else {
            for (i in 0 until samples) scratch[i] = input.getShort() / 32768f
        }
        chain.process(scratch, samples / inputAudioFormat.channelCount)
        out.order(ByteOrder.nativeOrder())
        if (float) {
            for (i in 0 until samples) out.putFloat(scratch[i])
        } else {
            for (i in 0 until samples) out.putShort((scratch[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    override fun onFlush() = chain.clear()

    override fun onReset() = chain.clear()
}

/**
 * Hands each decoded buffer to the analyzer once, with its timestamp, and tells it what the
 * speaker is playing whenever the player asks the sink: the two together line the visuals up
 * with what is heard.
 */
@OptIn(UnstableApi::class)
internal class AnalyzingAudioSink(sink: AudioSink, private val analyzer: AudioAnalyzer) : ForwardingAudioSink(sink) {
    private var lastTimeUs = Long.MIN_VALUE
    private var analysing = false
    private var playing = false

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val encoding = when (inputFormat.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> PcmEncoding.PCM16
            C.ENCODING_PCM_24BIT -> PcmEncoding.PCM24
            C.ENCODING_PCM_32BIT -> PcmEncoding.PCM32
            C.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
            else -> null
        }
        analysing = inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW && encoding != null && inputFormat.sampleRate > 0
        if (analysing) analyzer.configure(inputFormat.sampleRate, inputFormat.channelCount, encoding!!)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // A buffer the sink can't take yet comes back again; read each one once.
        if (analysing && presentationTimeUs != lastTimeUs) {
            lastTimeUs = presentationTimeUs
            analyzer.process(buffer, presentationTimeUs)
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        super.getCurrentPositionUs(sourceEnded).also { if (it != AudioSink.CURRENT_POSITION_NOT_SET) analyzer.anchor(it, playing) }

    override fun play() {
        playing = true
        analyzer.setPlaying(true)
        super.play()
    }

    override fun pause() {
        playing = false
        analyzer.setPlaying(false)
        super.pause()
    }

    // The renderer says so when one track gives way to the next without a flush.
    override fun handleDiscontinuity() {
        analyzer.newStream()
        super.handleDiscontinuity()
    }

    override fun flush() {
        lastTimeUs = Long.MIN_VALUE
        analyzer.reset()
        super.flush()
    }

    override fun reset() {
        lastTimeUs = Long.MIN_VALUE
        analyzer.reset()
        super.reset()
    }
}
