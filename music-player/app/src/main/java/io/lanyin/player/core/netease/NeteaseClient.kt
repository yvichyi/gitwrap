package io.lanyin.player.core.netease

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A NetEase reply: the JSON body and any cookies the server set. */
class NeteaseResponse(val body: JsonObject, val setCookies: Map<String, String>) {
    val code: Int get() = (body["code"] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() } ?: 0
    val message: String? get() = (body["message"] ?: body["msg"])?.let { (it as? JsonPrimitive)?.content }
}

/** NetEase answered, but not with success. 301 means the call needs a login. */
class NeteaseException(val code: Int, message: String?) : IOException(message ?: "网易云返回 $code")

/**
 * Speaks NetEase's two request protocols the way its own clients do (headers and cookies follow
 * NeteaseCloudMusicApi's `util/request.js`): **eapi** to `interface.music.163.com` for app calls,
 * **weapi** to `music.163.com` for the web ones. Holds the session and saves it when it changes.
 */
class NeteaseClient(
    private val http: OkHttpClient,
    private val store: SessionStore,
    private val apiBase: HttpUrl = "https://interface.music.163.com".toHttpUrl(),
    private val webBase: HttpUrl = "https://music.163.com".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<NeteaseSession?>(null)

    /** Null until first loaded from the store. */
    val session: StateFlow<NeteaseSession?> = _session.asStateFlow()

    suspend fun currentSession(): NeteaseSession = mutex.withLock {
        _session.value ?: (store.read() ?: NeteaseSession.fresh(now()).also { store.write(it) }).also { _session.value = it }
    }

    suspend fun updateSession(transform: (NeteaseSession) -> NeteaseSession): NeteaseSession {
        currentSession()
        return mutex.withLock {
            transform(_session.value!!).also {
                store.write(it)
                _session.value = it
            }
        }
    }

    /** POST an app-protocol call. [path] starts with `/api/`. */
    suspend fun eapi(path: String, data: JsonObject = JsonObject(emptyMap())): NeteaseResponse {
        val s = currentSession()
        val time = now()
        val header = linkedMapOf(
            "osver" to OS_VER,
            "deviceId" to s.deviceId,
            "os" to OS,
            "appver" to APP_VER,
            "versioncode" to "140",
            "mobilename" to "",
            "buildver" to (time / 1000).toString(),
            "resolution" to "1920x1080",
            "__csrf" to s.csrf,
            "channel" to CHANNEL,
            "requestId" to "${time}_${Random.nextInt(1000).toString().padStart(4, '0')}",
        )
        s.musicU?.let { header["MUSIC_U"] = it } ?: s.musicA?.let { header["MUSIC_A"] = it }
        val json = buildJsonObject {
            data.forEach { (k, v) -> put(k, v) }
            put("e_r", false)
            put("header", JsonObject(header.mapValues { JsonPrimitive(it.value) }))
        }.toString()
        val request = Request.Builder()
            .url(apiBase.newBuilder().addPathSegment("eapi").addPathSegments(path.removePrefix("/api/")).build())
            .header("Cookie", cookieHeader(header))
            .header("User-Agent", UA_APP)
            .post(FormBody.Builder().add("params", NeteaseCrypto.eapi(path, json)).build())
            .build()
        return execute(request)
    }

    /** POST a web-protocol call. [path] starts with `/api/`. */
    suspend fun weapi(path: String, data: JsonObject = JsonObject(emptyMap())): NeteaseResponse {
        val s = currentSession()
        val time = now()
        val cookie = LinkedHashMap(s.cookies)
        cookie["__remember_me"] = "true"
        cookie["ntes_kaola_ad"] = "1"
        cookie["_ntes_nuid"] = s.nuid
        cookie["_ntes_nnid"] = "${s.nuid},$time"
        cookie["WNMCID"] = s.wnmcid
        cookie["WEVNSM"] = "1.0.0"
        cookie["osver"] = OS_VER
        cookie["deviceId"] = s.deviceId
        cookie["os"] = OS
        cookie["channel"] = CHANNEL
        cookie["appver"] = APP_VER
        if ("login" !in path) cookie["NMTID"] = NeteaseCrypto.randomHex(16)
        if (s.musicU != null) cookie.remove("MUSIC_A")
        val json = buildJsonObject {
            data.forEach { (k, v) -> put(k, v) }
            // Without the plain-response flag some calls (anonymous registration) are refused.
            put("e_r", false)
            put("csrf_token", s.csrf)
        }.toString()
        val encrypted = NeteaseCrypto.weapi(json)
        val request = Request.Builder()
            .url(webBase.newBuilder().addPathSegment("weapi").addPathSegments(path.removePrefix("/api/")).build())
            .header("Cookie", cookieHeader(cookie))
            .header("Referer", webBase.toString().trimEnd('/'))
            .header("User-Agent", UA_WEB)
            .post(FormBody.Builder().add("params", encrypted.params).add("encSecKey", encrypted.encSecKey).build())
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): NeteaseResponse {
        val response = http.newCall(request).await()
        response.use {
            val text = it.body?.string().orEmpty()
            val body: JsonElement = try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw IOException("网易云返回了无法解析的内容（HTTP ${it.code}）", e)
            }
            if (body !is JsonObject) throw IOException("网易云返回了无法解析的内容（HTTP ${it.code}）")
            return NeteaseResponse(body.jsonObject, parseSetCookies(it.headers("Set-Cookie")))
        }
    }

    companion object {
        // The desktop client's identity, as the reference implementation sends it.
        private const val OS = "pc"
        private const val OS_VER = "Microsoft-Windows-10-Professional-build-19045-64bit"
        private const val APP_VER = "3.1.17.204416"
        private const val CHANNEL = "netease"
        private const val UA_APP = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        private const val UA_WEB =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

        internal fun cookieHeader(values: Map<String, String>): String =
            values.entries.joinToString("; ") { (k, v) -> "${encodeURIComponent(k)}=${encodeURIComponent(v)}" }

        /** `name=value` of each Set-Cookie; the last non-blank value wins. */
        internal fun parseSetCookies(headers: List<String>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (h in headers) {
                val pair = h.substringBefore(';')
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty() && value.isNotEmpty()) out[name] = value
            }
            return out
        }

        /** JavaScript's encodeURIComponent, which the reference uses for cookie values. */
        internal fun encodeURIComponent(s: String): String = buildString {
            for (b in s.toByteArray()) {
                val c = b.toInt() and 0xff
                val ch = c.toChar()
                if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.!~*'()") append(ch) else append('%').append("%02X".format(c))
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.close() }
        }
    })
}
