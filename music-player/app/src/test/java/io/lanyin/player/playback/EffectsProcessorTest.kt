package io.lanyin.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import io.lanyin.player.core.audio.EffectPreset
import io.lanyin.player.core.audio.EffectsChain
import io.lanyin.player.core.audio.EffectsSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The effects as Media3 runs them: bytes in, bytes out. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EffectsProcessorTest {

    private fun input(): ByteBuffer {
        val b = ByteBuffer.allocateDirect(4_096 * 4).order(ByteOrder.nativeOrder())
        for (f in 0 until 4_096) {
            val s = (0.4 * sin(2 * Math.PI * 440 * f / 44_100) * 32767).toInt().toShort()
            b.putShort(s)
            b.putShort(s)
        }
        b.flip()
        return b
    }

    private fun run(chain: EffectsChain): Pair<ByteBuffer, ByteBuffer> {
        val p = EffectsProcessor(chain)
        p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        assertTrue(p.isActive)
        val inBuf = input()
        val copy = inBuf.duplicate()
        p.queueInput(inBuf)
        assertFalse(inBuf.hasRemaining())
        return copy to p.output
    }

    @Test
    fun `with effects off the bytes pass through untouched`() {
        val (before, after) = run(EffectsChain())
        assertEquals(before, after)
    }

    @Test
    fun `with a preset on the sound changes`() {
        val chain = EffectsChain().apply { settings = EffectsSettings().choose(EffectPreset.ROCK) }
        val (before, after) = run(chain)
        assertEquals(before.remaining(), after.remaining())
        assertTrue(before != after)
    }

    @Test
    fun `formats it can't shape are left to the sink`() {
        val p = EffectsProcessor(EffectsChain())
        p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        p.flush()
        assertFalse(p.isActive)
    }
}
