package io.lanyin.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The listening cache's limit, changed while it is open, and emptying it. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageTest {
    @get:Rule
    val dir = TemporaryFolder()

    private val evictor = AdjustableLruEvictor(3_000)
    private lateinit var cache: SimpleCache

    @Before
    fun setUp() {
        cache = SimpleCache(dir.newFolder("audio"), evictor, StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()))
    }

    @After
    fun tearDown() = cache.release()

    /** Writes a song the way a cache sink does: lock the range, fill a file, commit it. */
    private fun put(key: String, bytes: Int = 1_000) {
        val hole = cache.startReadWrite(key, 0, bytes.toLong())
        val file: File = cache.startFile(key, 0, bytes.toLong())
        file.writeBytes(ByteArray(bytes) { 7 })
        cache.commitFile(file, bytes.toLong())
        cache.releaseHoleSpan(hole)
        Thread.sleep(3)
    }

    /** Reading a song marks it as recently heard. */
    private fun hear(key: String) {
        val span = cache.startReadWrite(key, 0, C.LENGTH_UNSET.toLong())
        check(span.isCached)
        Thread.sleep(3)
    }

    @Test
    fun `lowering the limit evicts the least recently heard songs at once`() {
        put("a"); put("b"); put("c")
        assertEquals(3_000L, cache.cacheSpace)
        hear("a")
        evictor.setMaxBytes(2_000, cache)
        assertEquals(setOf("a", "c"), cache.keys)
        assertEquals(2_000L, evictor.maxBytes)

        // New songs push out the oldest under the new limit too.
        put("d")
        assertEquals(setOf("a", "d"), cache.keys)
    }

    @Test
    fun `raising the limit keeps more`() {
        evictor.setMaxBytes(5_000, cache)
        put("a"); put("b"); put("c"); put("d"); put("e")
        assertEquals(5_000L, cache.cacheSpace)
        put("f")
        assertEquals(setOf("b", "c", "d", "e", "f"), cache.keys)
    }

    @Test
    fun `clearing empties the cache`() {
        put("a"); put("b")
        StorageManager.clear(cache)
        assertEquals(emptySet<String>(), cache.keys)
        assertEquals(0L, cache.cacheSpace)
    }
}
