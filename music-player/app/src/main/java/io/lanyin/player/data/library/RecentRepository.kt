package io.lanyin.player.data.library

import io.lanyin.player.core.model.Song
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The last songs played on this phone, newest first, kept in a small file. A song played again
 * moves back to the top rather than appearing twice.
 */
class RecentRepository(private val file: File?, private val scope: CoroutineScope, private val limit: Int = 100) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Song.serializer())
    private val mutex = Mutex()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        if (file != null) {
            scope.launch(Dispatchers.IO) {
                val loaded = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
                mutex.withLock { if (_songs.value.isEmpty()) _songs.value = loaded }
            }
        }
    }

    fun record(song: Song) {
        if (song.id == 0L) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val next = (listOf(song) + _songs.value.filter { it.id != song.id }).take(limit)
                _songs.value = next
                file?.let { f -> runCatching { f.writeText(json.encodeToString(serializer, next)) } }
            }
        }
    }

    fun clear() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                _songs.value = emptyList()
                file?.delete()
            }
        }
    }
}
