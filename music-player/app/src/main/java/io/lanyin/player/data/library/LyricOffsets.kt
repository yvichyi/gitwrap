package io.lanyin.player.data.library

import io.lanyin.player.core.model.LyricLine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 歌词时间微调, remembered per song in a small file: how many milliseconds a song's lyrics run
 * late (positive) or early (negative) against what the person hears. Songs set back to 0 are
 * forgotten.
 */
class LyricOffsets(private val file: File?, private val scope: CoroutineScope) {
    private val serializer = MapSerializer(Long.serializer(), Long.serializer())
    private val mutex = Mutex()

    @Volatile
    private var offsets: Map<Long, Long> = file?.let { f -> runCatching { Json.decodeFromString(serializer, f.readText()) }.getOrNull() }.orEmpty()

    operator fun get(songId: Long): Long = offsets[songId] ?: 0L

    fun set(songId: Long, ms: Long) {
        val next = if (ms == 0L) offsets - songId else offsets + (songId to ms)
        offsets = next
        val f = file ?: return
        scope.launch(Dispatchers.IO) {
            mutex.withLock { runCatching { f.writeText(Json.encodeToString(serializer, next)) } }
        }
    }
}

/** [this] with every time moved by [ms] (later when positive), nothing before the start. */
fun List<LyricLine>.shifted(ms: Long): List<LyricLine> = if (ms == 0L) this else map { line ->
    line.copy(
        startMs = (line.startMs + ms).coerceAtLeast(0),
        endMs = if (line.endMs > 0) (line.endMs + ms).coerceAtLeast(0) else line.endMs,
        words = line.words?.map { w -> w.copy(startMs = (w.startMs + ms).coerceAtLeast(0)) },
    )
}
