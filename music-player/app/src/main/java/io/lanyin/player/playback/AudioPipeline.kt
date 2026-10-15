package io.lanyin.player.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.lanyin.player.core.model.Quality
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * How audio reaches the player. Queue items carry `lanyin://song/{id}`; the address is resolved
 * only when the song is opened (NetEase's expire), and what has been heard is kept in a cache
 * (1 GB unless changed) keyed by song and quality, so a replay needs no network at all. Downloaded songs live in
 * a store of their own that is never evicted, and are played from there whenever complete.
 */
@OptIn(UnstableApi::class)
class AudioPipeline(context: Context, private val http: OkHttpClient, private val resolver: StreamResolver) {

    /** The quality to ask for; follows the setting. */
    @Volatile
    var preferred: Quality = Quality.LOSSLESS

    private val appContext = context.applicationContext

    val database: DatabaseProvider by lazy { StandaloneDatabaseProvider(appContext) }

    private val evictor = AdjustableLruEvictor(CACHE_BYTES)
    private val cacheLazy = lazy { SimpleCache(File(appContext.cacheDir, "audio"), evictor, database) }
    private val cache: SimpleCache by cacheLazy

    /** The most the listening cache keeps; follows the setting. */
    val cacheLimit: Long get() = evictor.maxBytes

    /** Changes [cacheLimit], evicting the oldest songs at once if over. Blocks: not on the main thread. */
    fun setCacheLimit(bytes: Long) = evictor.setMaxBytes(bytes, if (cacheLazy.isInitialized()) cache else null)

    fun cacheBytes(): Long = cache.cacheSpace

    fun clearCache() = StorageManager.clear(cache)

    /** Downloads: kept until removed, in app storage. */
    val downloadCache: SimpleCache by lazy { SimpleCache(File(appContext.filesDir, "downloads"), NoOpCacheEvictor(), database) }

    private val network: DataSource.Factory by lazy {
        val okhttp = OkHttpDataSource.Factory(http)
        DataSource.Factory { SchemeFallbackDataSource(okhttp.createDataSource()) }
    }

    /** Resolves a song's address and fetches it, for listening (a VIP song's preview clip included). */
    val networkFactory: DataSource.Factory by lazy { ResolvingDataSource.Factory(network) { spec -> resolve(spec, allowTrial = true) } }

    /** The same for downloads, which refuse a preview clip: it would be kept as if it were the song. */
    val downloadFactory: DataSource.Factory by lazy { ResolvingDataSource.Factory(network) { spec -> resolve(spec, allowTrial = false) } }

    val dataSourceFactory: DataSource.Factory by lazy {
        val streaming = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(networkFactory)
            .setCacheKeyFactory { spec -> songId(spec.uri)?.let { "song:$it:${preferred.level}" } ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Read-only, and only ever opened for a download that is complete.
        val offline = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec -> songId(spec.uri)?.let(::downloadKey) ?: spec.uri.toString() }
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
        DataSource.Factory {
            RoutingDataSource(offline.createDataSource(), streaming.createDataSource()) { spec -> songId(spec.uri)?.let(::isDownloaded) == true }
        }
    }

    /** Don't retry a song NetEase has refused; retry network trouble as usual. */
    val loadErrorPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            if (generateSequence(info.exception as Throwable?) { it.cause }.any { it is UnplayableException }) C.TIME_UNSET else super.getRetryDelayMsFor(info)
    }

    /** Whether all of [songId]'s audio is in the download store. */
    fun isDownloaded(songId: Long): Boolean = isComplete(downloadCache, downloadKey(songId))

    private fun resolve(spec: DataSpec, allowTrial: Boolean): DataSpec {
        val id = songId(spec.uri) ?: return spec
        // Called on the player's (or downloader's) loading thread, which may block.
        val stream = runBlocking { resolver.resolve(id, preferred) }
        if (stream.trialOnly && !allowTrial) throw UnplayableException("只能试听，下载不了完整的歌")
        return spec.buildUpon().setUri(Uri.parse(stream.url)).build()
    }

    companion object {
        /** The listening cache's limit until the setting says otherwise. */
        const val CACHE_BYTES = 1L shl 30

        /** The same address over the other scheme, for NetEase's CDN; null for anything else. */
        fun alternate(uri: Uri): Uri? {
            val host = uri.host ?: return null
            if (!host.endsWith(".126.net") && !host.endsWith(".163.com")) return null
            return when (uri.scheme) {
                "http" -> uri.buildUpon().scheme("https").build()
                "https" -> uri.buildUpon().scheme("http").build()
                else -> null
            }
        }

        /** A queue item's address; [quality] marks a reload at another quality, so the player sees it as new. */
        fun uriFor(songId: String, quality: String? = null): Uri =
            Uri.parse("lanyin://song/$songId" + (quality?.let { "?q=$it" } ?: ""))

        fun songId(uri: Uri): Long? = if (uri.scheme == "lanyin" && uri.host == "song") uri.lastPathSegment?.toLongOrNull() else null

        /** A download's key in its store; any quality, so changing the setting keeps it. */
        fun downloadKey(songId: Long) = "download:$songId"

        fun isComplete(cache: Cache, key: String): Boolean {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            return length > 0 && cache.isCached(key, 0, length)
        }
    }
}

/** Opens [offline] when [useOffline] says so for the request, [online] otherwise. */
@OptIn(UnstableApi::class)
internal class RoutingDataSource(
    private val offline: DataSource,
    private val online: DataSource,
    private val useOffline: (DataSpec) -> Boolean,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        offline.addTransferListener(transferListener)
        online.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (useOffline(dataSpec)) offline else online
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(current).read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }
}

/**
 * NetEase's audio CDN nodes differ: some only answer plain http, some only https (and networks
 * differ too). Opens the address as given and, if that fails before any audio arrives, the same
 * address over the other scheme.
 */
@OptIn(UnstableApi::class)
private class SchemeFallbackDataSource(private val inner: DataSource) : DataSource by inner {
    override fun open(dataSpec: DataSpec): Long = try {
        inner.open(dataSpec)
    } catch (e: java.io.IOException) {
        val other = AudioPipeline.alternate(dataSpec.uri) ?: throw e
        runCatching { inner.close() }
        try {
            inner.open(dataSpec.withUri(other))
        } catch (second: java.io.IOException) {
            throw e
        }
    }
}
