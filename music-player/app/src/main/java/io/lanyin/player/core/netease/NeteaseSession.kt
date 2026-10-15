package io.lanyin.player.core.netease

import io.lanyin.player.core.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * Who this device is to NetEase: a stable device identity plus the cookies the servers handed
 * out. `MUSIC_U` is the login itself; `MUSIC_A` is the anonymous account used before login.
 * Never logged and never sent anywhere but NetEase.
 */
@Serializable
data class NeteaseSession(
    val deviceId: String,
    /** `_ntes_nuid`: 64 hex digits, fixed per install. */
    val nuid: String,
    /** `WNMCID`: six letters, a timestamp and a suffix, fixed per install. */
    val wnmcid: String,
    val cookies: Map<String, String> = emptyMap(),
    /** The signed-in profile as last fetched, so the app can show who you are before the network answers. */
    val profile: UserProfile? = null,
) {
    val musicU: String? get() = cookies["MUSIC_U"]?.takeIf { it.isNotEmpty() }
    val musicA: String? get() = cookies["MUSIC_A"]?.takeIf { it.isNotEmpty() }
    val csrf: String get() = cookies["__csrf"].orEmpty()
    val loggedIn: Boolean get() = musicU != null

    /** Keeps what the servers set, ignoring blanks (an expired cookie comes back empty). */
    fun withCookies(new: Map<String, String>): NeteaseSession = copy(cookies = cookies + new.filterValues { it.isNotEmpty() })

    /** Drops the login but keeps the device and the anonymous account. */
    fun loggedOut(): NeteaseSession = copy(cookies = cookies.filterKeys { it in ANONYMOUS_COOKIES }, profile = null)

    companion object {
        private val ANONYMOUS_COOKIES = setOf("MUSIC_A", "NMTID")

        fun fresh(now: Long = System.currentTimeMillis()): NeteaseSession {
            val letters = "abcdefghijklmnopqrstuvwxyz"
            val random = java.security.SecureRandom()
            val prefix = String(CharArray(6) { letters[random.nextInt(letters.length)] })
            return NeteaseSession(
                deviceId = NeteaseCrypto.newDeviceId(),
                nuid = NeteaseCrypto.randomHex(32),
                wnmcid = "$prefix.$now.01.0",
            )
        }
    }
}

/** Where the session lives between launches. On a device it is encrypted with a Keystore key. */
interface SessionStore {
    suspend fun read(): NeteaseSession?
    suspend fun write(session: NeteaseSession)
}

class MemorySessionStore(private var value: NeteaseSession? = null) : SessionStore {
    override suspend fun read(): NeteaseSession? = value
    override suspend fun write(session: NeteaseSession) {
        value = session
    }
}
