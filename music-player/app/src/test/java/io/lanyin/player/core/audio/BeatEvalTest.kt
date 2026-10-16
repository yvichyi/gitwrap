package io.lanyin.player.core.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the analyzer over real recordings and writes the beats it finds, one file per clip, for
 * scoring against reference beats outside the build. Only with `BEAT_EVAL_DIR` set: a folder of
 * 44.1 kHz stereo 16-bit raw `.pcm` files; writes `<name>.ours.txt` beside each.
 */
class BeatEvalTest {
    @Test
    fun `find beats in recordings`() {
        val dir = System.getenv("BEAT_EVAL_DIR")?.let(::File)
        assumeTrue("BEAT_EVAL_DIR not set", dir != null && dir.isDirectory)
        for (pcm in dir!!.listFiles { f -> f.name.endsWith(".pcm") }!!.sortedBy { it.name }) {
            val beats = mutableListOf<Long>()
            val analyzer = AudioAnalyzer().apply { configure(44_100, 2, PcmEncoding.PCM16) }
            analyzer.onBeat = { beats += it }
            val bytes = pcm.readBytes()
            val frameBytes = 4
            val chunk = 4_096 * frameBytes
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(chunk, bytes.size - offset)
                val buf = ByteBuffer.wrap(bytes, offset, len).slice().order(ByteOrder.nativeOrder())
                analyzer.process(buf, offset.toLong() / frameBytes * 1_000_000L / 44_100)
                offset += len
            }
            File(dir, pcm.nameWithoutExtension + ".ours.txt").writeText(beats.joinToString("\n") { "%.4f".format(it / 1_000_000.0) })
        }
    }
}
