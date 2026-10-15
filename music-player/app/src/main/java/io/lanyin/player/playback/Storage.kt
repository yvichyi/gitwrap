package io.lanyin.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.util.TreeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Media3's least-recently-used eviction, with a limit that can change while the cache is open.
 * Its state is only touched under the cache's own lock: the cache calls in holding it, and
 * [trim] takes it, so lowering the limit can't race a song being written.
 */
@OptIn(UnstableApi::class)
class AdjustableLruEvictor(maxBytes: Long) : CacheEvictor {
    @Volatile
    var maxBytes: Long = maxBytes
        private set

    private val spans = TreeSet<CacheSpan> { a, b ->
        if (a.lastTouchTimestamp != b.lastTouchTimestamp) a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp) else a.compareTo(b)
    }
    private var size = 0L

    /** Changes the limit and evicts down to it now. Blocks on the cache, so not on the main thread. */
    fun setMaxBytes(bytes: Long, cache: Cache?) {
        maxBytes = bytes
        if (cache != null) synchronized(cache) { evict(cache, 0) }
    }

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != androidx.media3.common.C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, required: Long) {
        while (size + required > maxBytes && spans.isNotEmpty()) cache.removeSpan(spans.first())
    }
}

/** What the app keeps on the phone, by kind. */
data class StorageUsage(
    /** Songs kept from listening, and the most that may be kept. */
    val audioBytes: Long,
    val audioLimit: Long,
    /** Covers and avatars. */
    val imageBytes: Long,
    val downloadBytes: Long,
    val downloadCount: Int,
)

/** The listening cache's size limits on offer. */
val CacheLimits: List<Long> = listOf(512L shl 20, 1L shl 30, 2L shl 30, 4L shl 30)

/**
 * Sizes up and clears what the app keeps: the listening cache, images and downloads. Downloads
 * are removed through their manager so their records go too.
 */
@OptIn(UnstableApi::class)
class StorageManager(
    private val audio: AudioPipeline,
    private val downloads: DownloadRepository,
    private val images: ImageStore,
) {
    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val done = downloads.items.value.filter { it.state == DownloadState.Done }
        StorageUsage(
            audioBytes = audio.cacheBytes(),
            audioLimit = audio.cacheLimit,
            imageBytes = images.size(),
            downloadBytes = audio.downloadCache.cacheSpace,
            downloadCount = done.size,
        )
    }

    suspend fun clearAudio() = withContext(Dispatchers.IO) { audio.clearCache() }

    suspend fun clearImages() = withContext(Dispatchers.IO) { images.clear() }

    fun removeAllDownloads() = downloads.removeAll()

    companion object {
        /** Empties [cache]; a song being written right now keeps what it has locked. */
        fun clear(cache: SimpleCache) {
            for (key in cache.keys.toList()) cache.removeResource(key)
        }
    }
}

/** The image loader's caches, as far as storage is concerned. */
interface ImageStore {
    fun size(): Long
    fun clear()
}
