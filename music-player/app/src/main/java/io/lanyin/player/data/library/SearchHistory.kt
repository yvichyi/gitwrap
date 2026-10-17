package io.lanyin.player.data.library

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What was searched on this phone, newest first, kept in a small file. Searching the same words
 * again moves them back to the top rather than listing them twice.
 */
class SearchHistory(private val file: File?, private val scope: CoroutineScope, private val limit: Int = 12) {
    private val serializer = ListSerializer(String.serializer())
    private val mutex = Mutex()
    private val _words = MutableStateFlow<List<String>>(emptyList())
    val words: StateFlow<List<String>> = _words.asStateFlow()

    init {
        if (file != null) {
            scope.launch(Dispatchers.IO) {
                val loaded = runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
                mutex.withLock { if (_words.value.isEmpty()) _words.value = loaded.take(limit) }
            }
        }
    }

    fun record(words: String) {
        val w = words.trim().take(60)
        if (w.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val next = (listOf(w) + _words.value.filter { !it.equals(w, ignoreCase = true) }).take(limit)
                if (next == _words.value) return@withLock
                _words.value = next
                file?.let { f -> runCatching { f.writeText(Json.encodeToString(serializer, next)) } }
            }
        }
    }

    fun clear() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                _words.value = emptyList()
                file?.delete()
            }
        }
    }
}
