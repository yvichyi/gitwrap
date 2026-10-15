package io.lanyin.player.core.netease

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The request encryption NetEase's clients use, re-implemented from the protocol described by
 * NeteaseCloudMusicApi (MIT). Checked byte for byte against its output in `NeteaseCryptoTest`.
 *
 * - **eapi** (the app protocol, used for most calls): AES-128-ECB over
 *   `path-36cd479b6b5-json-36cd479b6b5-md5("nobody{path}use{json}md5forencrypt")`, upper-case hex.
 * - **weapi** (the web protocol): AES-128-CBC twice, the second time under a random 16-character
 *   key, which travels RSA-encrypted (raw, no padding) and reversed.
 */
object NeteaseCrypto {
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val ID_XOR_KEY = "3go8&\$8*3*3h0k(2)2"
    private const val PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8" +
            "z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    private val rsaKey: RSAPublicKey by lazy {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY))) as RSAPublicKey
    }
    private val random = SecureRandom()

    fun eapi(path: String, json: String): String {
        val digest = md5Hex("nobody${path}use${json}md5forencrypt")
        val message = "$path-36cd479b6b5-$json-36cd479b6b5-$digest"
        return aes(message.toByteArray(), EAPI_KEY, iv = null).toHex().uppercase()
    }

    class Weapi(val params: String, val encSecKey: String)

    fun weapi(json: String, secretKey: String = randomSecret()): Weapi {
        val once = base64(aes(json.toByteArray(), PRESET_KEY, IV))
        val twice = base64(aes(once.toByteArray(), secretKey, IV))
        return Weapi(params = twice, encSecKey = rsaRaw(secretKey.reversed().toByteArray()))
    }

    /** The anonymous account's "username": the device id beside a salted digest of itself. */
    fun anonymousUsername(deviceId: String): String {
        val xored = String(CharArray(deviceId.length) { i -> (deviceId[i].code xor ID_XOR_KEY[i % ID_XOR_KEY.length].code).toChar() })
        val digest = base64(MessageDigest.getInstance("MD5").digest(xored.toByteArray()))
        return base64("$deviceId $digest".toByteArray())
    }

    /** A device id in the clients' format: 52 upper-case hex digits. */
    fun newDeviceId(): String = buildString { repeat(52) { append("0123456789ABCDEF"[random.nextInt(16)]) } }

    fun randomHex(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).toHex()

    internal fun randomSecret(): String = buildString { repeat(16) { append(BASE62[random.nextInt(BASE62.length)]) } }

    private fun aes(data: ByteArray, key: String, iv: String?): ByteArray {
        val cipher = Cipher.getInstance(if (iv == null) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
        val spec = SecretKeySpec(key.toByteArray(), "AES")
        if (iv == null) cipher.init(Cipher.ENCRYPT_MODE, spec) else cipher.init(Cipher.ENCRYPT_MODE, spec, IvParameterSpec(iv.toByteArray()))
        return cipher.doFinal(data)
    }

    /** Textbook RSA: the message as a big-endian number, raised to e mod n, as 256 hex digits. */
    private fun rsaRaw(message: ByteArray): String =
        BigInteger(1, message).modPow(rsaKey.publicExponent, rsaKey.modulus).toString(16).padStart(256, '0')

    private fun md5Hex(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray()).toHex()

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
