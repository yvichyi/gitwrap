package io.lanyin.player.playback

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.lanyin.player.R
import io.lanyin.player.core.model.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词幕 (Lyricon), the LSPosed status-bar lyrics module, reached through its provider SDK: it gets
 * the whole song's lyrics once, word timings and translations included, and works out the line
 * itself from the playback state, so nothing is sent while a song simply plays on. Phones without
 * the module never answer and nothing more happens.
 */
class LyriconLyrics(
    private val context: Context,
    private val scope: CoroutineScope,
    private val lyrics: StateFlow<NowPlayingLyrics.Lyrics?>,
    private val duration: () -> Long,
) {
    private var provider: LyriconProvider? = null
    private var watching: Job? = null
    private var registered = false

    fun start() {
        if (provider != null) return
        provider = runCatching {
            LyriconFactory.createProvider(
                context.applicationContext,
                logo = runCatching { ProviderLogo.fromDrawable(context, R.drawable.ic_notify_lyric, 96, 96) }.getOrNull(),
            )
        }.onFailure { Log.w(TAG, "Lyricon unavailable: ${it.message}") }.getOrNull() ?: return
        watching = scope.launch {
            lyrics.collect { now ->
                // The duration is read here, on the player's thread; the song is encoded and sent off it.
                val song = now?.let { toLyricon(it, duration()) }
                withContext(Dispatchers.Default) { runCatching { provider?.player?.setSong(song) } }
            }
        }
    }

    /** Follows the 状态栏歌词 setting: registered only while it is on. */
    fun setEnabled(on: Boolean) {
        val p = provider ?: return
        if (on == registered) return
        registered = on
        runCatching { if (on) p.register() else p.unregister() }
    }

    /** Where playback is and whether it moves; sent on play, pause, seek and song change. */
    fun playback(playing: Boolean, positionMs: Long, speed: Float) {
        val state = PlaybackState.Builder()
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, positionMs.coerceAtLeast(0), if (playing) speed else 0f, SystemClock.elapsedRealtime())
            .build()
        runCatching { provider?.player?.setPlaybackState(state) }
    }

    fun stop() {
        watching?.cancel()
        watching = null
        runCatching { provider?.destroy() }
        provider = null
        registered = false
    }

    companion object {
        private const val TAG = "LyriconLyrics"

        /**
         * Our lyrics in Lyricon's terms. Blank lines (breaks between verses) are left out; a line
         * whose end is unknown runs to the next one, or for five seconds when it is the last.
         */
        fun toLyricon(l: NowPlayingLyrics.Lyrics, durationMs: Long): LyriconSong {
            val lines = l.lines.filter { it.text.isNotBlank() }
            return LyriconSong(
                id = l.songId.toString(),
                name = l.title,
                artist = l.artist,
                duration = durationMs.coerceAtLeast(0),
                lyrics = lines.mapIndexed { i, line -> line.toRich(lines.getOrNull(i + 1)?.startMs) },
            )
        }

        private fun LyricLine.toRich(nextStart: Long?): RichLyricLine {
            val end = when {
                endMs > startMs -> endMs
                nextStart != null && nextStart > startMs -> nextStart
                else -> startMs + 5_000
            }
            return RichLyricLine(
                begin = startMs,
                end = end,
                text = text.trim(),
                words = words?.filter { it.text.isNotEmpty() }?.map { LyriconWord(begin = it.startMs, end = it.startMs + it.durationMs, text = it.text) },
                translation = translation?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
