package io.lanyin.player.playback

import io.lanyin.player.core.model.Song
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The queue as it stood: its songs, the one playing, how far into it, and what list it came from. */
@Serializable
data class SavedQueue(val songs: List<Song>, val index: Int, val positionMs: Long, val source: String = "")

/**
 * Keeps the queue across the app being killed, in one small file, so the system's media
 * controls and a headset button can pick up where the music left off, and opening the app shows
 * the last song ready to play again.
 */
class QueueStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): SavedQueue? = runCatching { json.decodeFromString(SavedQueue.serializer(), file.readText()) }
        .getOrNull()
        ?.takeIf { it.songs.isNotEmpty() }
        ?.let { it.copy(index = it.index.coerceIn(0, it.songs.size - 1), positionMs = it.positionMs.coerceAtLeast(0)) }

    /** Writes through a temporary file, so a kill mid-write can't leave half a queue. */
    fun write(queue: SavedQueue) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(SavedQueue.serializer(), queue))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun clear() {
        file.delete()
    }
}
