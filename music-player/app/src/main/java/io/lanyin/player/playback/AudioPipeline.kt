package io.lanyin.player.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
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
 * only when the song is opened (NetEase's expire), and what has been heard is kept in a 1 GB
 * cache keyed by song and quality, so a replay needs no network at all.
 */
@OptIn(UnstableApi::class)
class AudioPipeline(context: Context, private val http: OkHttpClient, private val resolver: StreamResolver) {

    /** The quality to ask for; follows the setting. */
    @Volatile
    var preferred: Quality = Quality.LOSSLESS

    private val appContext = context.applicationContext

    private val cache: SimpleCache by lazy {
        SimpleCache(File(appContext.cacheDir, "audio"), LeastRecentlyUsedCacheEvictor(CACHE_BYTES), StandaloneDatabaseProvider(appContext))
    }

    val dataSourceFactory: DataSource.Factory by lazy {
        val network = OkHttpDataSource.Factory(http)
        val resolving = ResolvingDataSource.Factory(network) { spec -> resolve(spec) }
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory { spec -> songId(spec.uri)?.let { "song:$it:${preferred.level}" } ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Don't retry a song NetEase has refused; retry network trouble as usual. */
    val loadErrorPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            if (generateSequence(info.exception as Throwable?) { it.cause }.any { it is UnplayableException }) C.TIME_UNSET else super.getRetryDelayMsFor(info)
    }

    private fun resolve(spec: DataSpec): DataSpec {
        val id = songId(spec.uri) ?: return spec
        // Called on the player's loading thread, which may block.
        val stream = runBlocking { resolver.resolve(id, preferred) }
        return spec.buildUpon().setUri(Uri.parse(stream.url)).build()
    }

    companion object {
        const val CACHE_BYTES = 1L shl 30

        fun uriFor(songId: String): Uri = Uri.parse("lanyin://song/$songId")

        fun songId(uri: Uri): Long? = if (uri.scheme == "lanyin" && uri.host == "song") uri.lastPathSegment?.toLongOrNull() else null
    }
}
