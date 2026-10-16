package io.lanyin.player.playback

import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.model.Stream
import io.lanyin.player.core.netease.NeteaseApi
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** NetEase won't give this song to this account; [message] says why, in words for the listener. */
class UnplayableException(message: String) : IOException(message)

/**
 * Turns a song id into an address to stream from, at the quality the listener chose or the best
 * below it that NetEase will give. Addresses expire after about twenty minutes, so they are only
 * fetched when a song is about to play and kept for a quarter of an hour.
 */
class StreamResolver(private val api: NeteaseApi, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry(val stream: Stream, val at: Long)

    private val recent = ConcurrentHashMap<String, Entry>()
    private val _resolved = MutableStateFlow<Map<Long, Stream>>(emptyMap())

    /** What each song actually resolved to, for the quality label on the player. */
    val resolved: StateFlow<Map<Long, Stream>> = _resolved.asStateFlow()

    /**
     * Who is listening, as far as NetEase's answer depends on it: anonymous, or which account at
     * which VIP level. Logging in or upgrading changes it, so a 30-second preview or a stepped-down
     * stream fetched before is not played again in its place.
     */
    val tier: String
        get() {
            val session = api.session.value
            val profile = session?.profile
            return when {
                session?.loggedIn != true -> "anon"
                profile == null -> "user"
                else -> "u${profile.userId}v${profile.vipType}"
            }
        }

    suspend fun resolve(songId: Long, preferred: Quality): Stream {
        val key = "$songId:${preferred.level}:$tier"
        recent[key]?.takeIf { now() - it.at < TTL_MS }?.let { return it.stream }
        var last: Stream? = null
        for (quality in ladder(preferred)) {
            val stream = api.stream(songId, quality)
            last = stream
            if (stream.url != null) {
                recent[key] = Entry(stream, now())
                _resolved.update { it + (songId to stream) }
                return stream
            }
        }
        throw UnplayableException(reason(last))
    }

    /** Forgets cached addresses for a song (after one failed to load). */
    fun invalidate(songId: Long) {
        recent.keys.removeAll { it.startsWith("$songId:") }
    }

    companion object {
        const val TTL_MS = 15 * 60_000L

        /** The preferred quality, then two safer rungs: NetEase already steps down within a request. */
        fun ladder(preferred: Quality): List<Quality> =
            (listOf(preferred) + listOf(Quality.EXHIGH, Quality.STANDARD).filter { it.ordinal > preferred.ordinal }).distinct()

        fun reason(stream: Stream?): String = when {
            stream == null -> "这首歌暂时无法播放"
            stream.fee == 1 -> "需要黑胶 VIP 才能播放"
            stream.fee == 4 -> "需要单独购买这张专辑"
            stream.code == 404 || stream.code == -110 -> "这首歌暂时没有版权"
            else -> "这首歌暂时无法播放"
        }
    }
}
