package io.lanyin.player.core.netease

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** A local audio file bound for the cloud drive: what NetEase is told about it, and its bytes. */
class UploadFile(
    /** The file's own name, extension included. */
    val name: String,
    val size: Long,
    /** Lowercase hex. */
    val md5: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Opens the bytes afresh; called once per attempt. */
    val open: () -> InputStream,
)

/** A song now in the cloud drive. [sent] is false when NetEase already held the bytes (秒传). */
data class CloudUpload(val songId: Long, val sent: Boolean)

/**
 * Sends bytes to NetEase's object store. Which upload node to use comes from its load balancer,
 * as the reference's `songUpload.js` asks; the nodes are named over plain http, and are reached
 * over https instead so the upload token never crosses the network in the clear.
 */
class NosUploader(
    http: OkHttpClient,
    private val lbs: HttpUrl = "https://wanproxy.127.net/lbs".toHttpUrl(),
    private val fallback: HttpUrl = "https://nosup-hz1.127.net".toHttpUrl(),
    /** How a node the balancer names is reached; tests keep plain http. */
    private val reach: (String) -> HttpUrl? = { secure(it) },
) {
    // A large FLAC on a slow uplink takes minutes; only a stalled socket should fail it.
    private val http = http.newBuilder().writeTimeout(java.time.Duration.ofMinutes(2)).readTimeout(java.time.Duration.ofMinutes(2)).build()

    suspend fun put(bucket: String, objectKey: String, token: String, file: UploadFile, onSent: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val node = node(bucket)
        val url = node.newBuilder()
            .addPathSegment(bucket)
            // One segment: the key's slash is sent as %2F.
            .addPathSegment(objectKey)
            .addQueryParameter("offset", "0")
            .addQueryParameter("complete", "true")
            .addQueryParameter("version", "1.0")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("x-nos-token", token)
            .header("Content-MD5", file.md5)
            .post(Body(file, "audio/mpeg".toMediaType(), onSent))
            .build()
        // Cancelling the coroutine cancels the call, which stops the body mid-write.
        http.newCall(request).await().use { r ->
            if (!r.isSuccessful) throw IOException("上传没有成功（HTTP ${r.code}）")
        }
    }

    /** The first upload node the balancer names, on https. */
    private fun node(bucket: String): HttpUrl {
        val request = Request.Builder().url(lbs.newBuilder().addQueryParameter("version", "1.0").addQueryParameter("bucketname", bucket).build()).build()
        val named = runCatching {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                NeteaseClient.json.decodeFromString<LbsDto>(r.body?.string().orEmpty()).upload
            }
        }.getOrDefault(emptyList())
        return named.firstNotNullOfOrNull(reach) ?: fallback
    }

    private class Body(private val file: UploadFile, private val type: MediaType, private val onSent: (Long) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.size
        override fun isOneShot() = true

        override fun writeTo(sink: BufferedSink) {
            file.open().source().use { source ->
                var sent = 0L
                var reported = 0L
                while (true) {
                    val n = source.read(sink.buffer, CHUNK)
                    if (n == -1L) break
                    sink.emit()
                    sent += n
                    if (sent - reported >= REPORT_EVERY || sent == file.size) {
                        reported = sent
                        onSent(sent)
                    }
                }
            }
        }
    }

    companion object {
        internal const val CHUNK = 64L * 1024
        internal const val REPORT_EVERY = 256L * 1024

        /** A node by name moved to https; a bare address can't be, since its certificate names the host. */
        fun secure(node: String): HttpUrl? {
            val url = node.toHttpUrlOrNull() ?: return null
            if (url.isHttps) return url
            val ipv4 = url.host.all { it.isDigit() || it == '.' }
            if (ipv4 || ':' in url.host) return null
            return url.newBuilder().scheme("https").port(443).build()
        }
    }
}

@Serializable
internal class LbsDto(val upload: List<String> = emptyList())
