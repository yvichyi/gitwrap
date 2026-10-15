package io.lanyin.player.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.lanyin.player.core.netease.NeteaseClient
import io.lanyin.player.core.netease.NeteaseSession
import io.lanyin.player.core.netease.SessionStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the NetEase session in an app-private file (excluded from backups), sealed with
 * AES-GCM under a key that lives in the Android Keystore and never leaves it. If the key is
 * gone (app data cleared, device restored) the file can't be opened and the app starts fresh.
 */
class KeystoreSessionStore(private val file: File) : SessionStore {

    override suspend fun read(): NeteaseSession? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            val text = String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
            NeteaseClient.json.decodeFromString(NeteaseSession.serializer(), text)
        }.getOrNull()
    }

    override suspend fun write(session: NeteaseSession) = withContext(Dispatchers.IO) {
        // A broken Keystore (it happens on some devices) costs persistence, not the app.
        runCatching { seal(session) }.onFailure { android.util.Log.w("Lanyin", "session not saved: ${it.javaClass.simpleName}") }
        Unit
    }

    private fun seal(session: NeteaseSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(NeteaseClient.json.encodeToString(NeteaseSession.serializer(), session).toByteArray())
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) {
            file.writeBytes(sealed)
            tmp.delete()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "lanyin.netease.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
