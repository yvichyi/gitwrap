package io.lanyin.player.core.netease

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors in `netease/crypto-vectors.json` were produced by NeteaseCloudMusicApi 4.32.0's own
 * `util/crypto.js` (weapi with its random key pinned), so these tests hold the Kotlin port to
 * the reference byte for byte.
 */
class NeteaseCryptoTest {

    private val vectors: JsonObject by lazy {
        val text = javaClass.classLoader!!.getResource("netease/crypto-vectors.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    private fun cases(name: String) = vectors.getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun `eapi matches the reference`() {
        val cases = cases("eapi")
        assertTrue(cases.isNotEmpty())
        cases.forEach { assertEquals(it.str("path"), it.str("params"), NeteaseCrypto.eapi(it.str("path"), it.str("json"))) }
    }

    @Test
    fun `weapi matches the reference for a given secret`() {
        cases("weapi").forEach {
            val r = NeteaseCrypto.weapi(it.str("json"), it.str("secretKey"))
            assertEquals(it.str("json"), it.str("params"), r.params)
            assertEquals(it.str("json"), it.str("encSecKey"), r.encSecKey)
        }
    }

    @Test
    fun `anonymous username matches the reference`() {
        cases("anonymous").forEach { assertEquals(it.str("username"), NeteaseCrypto.anonymousUsername(it.str("deviceId"))) }
    }

    @Test
    fun `random parts have the clients' shapes`() {
        val id = NeteaseCrypto.newDeviceId()
        assertTrue(id, Regex("[0-9A-F]{52}").matches(id))
        val w = NeteaseCrypto.weapi("{}")
        assertEquals(256, w.encSecKey.length)
        assertTrue(NeteaseCrypto.randomHex(16).length == 32)
    }
}
