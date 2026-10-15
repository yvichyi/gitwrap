package io.lanyin.player.data.library

import io.lanyin.player.core.netease.CloudUpload
import io.lanyin.player.core.netease.NeteaseException
import io.lanyin.player.core.netease.UploadFile
import io.lanyin.player.ui.music.label
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudUploadsTest {

    private fun file(name: String, size: Long = 1000) = UploadFile(name, size, "md5", open = { ByteArray(size.toInt()).inputStream() })

    @Test
    fun `files go one at a time and report progress, then the drive is refreshed`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        var refreshed = 0
        val uploads = CloudUploads(scope, send = { f, onSent ->
            order += "start ${f.name}"
            onSent(f.size / 2)
            if (f.name == "a.mp3") gate.await()
            onSent(f.size)
            order += "end ${f.name}"
            CloudUpload(1, sent = f.name != "b.flac")
        }, onUploaded = { refreshed++ })

        uploads.add("a.mp3", 0) { file("a.mp3") }
        uploads.add("b.flac", 0) { file("b.flac", 4000) }
        scope.advanceUntilIdle()
        // a is halfway and b hasn't started.
        assertEquals(listOf("start a.mp3"), order)
        val (a, b) = uploads.jobs.value
        assertEquals(UploadStage.Sending, a.stage)
        assertEquals(0.5f, a.progress)
        assertEquals(1000L, a.size)
        assertEquals(UploadStage.Waiting, b.stage)

        gate.complete(Unit)
        scope.advanceUntilIdle()
        assertEquals(listOf("start a.mp3", "end a.mp3", "start b.flac", "end b.flac"), order)
        assertTrue(uploads.jobs.value.all { it.stage == UploadStage.Done })
        assertFalse(uploads.jobs.value[0].instant)
        assertTrue("b was already on NetEase", uploads.jobs.value[1].instant)
        assertEquals(2, refreshed)

        uploads.clearFinished()
        assertTrue(uploads.jobs.value.isEmpty())
        scope.cancel()
    }

    @Test
    fun `a failure is kept with its reason and the next file still goes`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val uploads = CloudUploads(scope, send = { f, _ ->
            if (f.name == "bad.mp3") throw NeteaseException(507, "云盘空间不足")
            CloudUpload(2, sent = true)
        }, onUploaded = {})
        uploads.add("bad.mp3", 10) { file("bad.mp3") }
        uploads.add("unreadable.mp3", 10) { throw UnreadableFile("读不了「unreadable.mp3」") }
        uploads.add("good.mp3", 10) { file("good.mp3") }
        scope.advanceUntilIdle()
        val (bad, unreadable, good) = uploads.jobs.value
        assertEquals(UploadStage.Failed, bad.stage)
        assertEquals("云盘空间不足", bad.error)
        assertEquals("读不了「unreadable.mp3」", unreadable.error)
        assertEquals(UploadStage.Done, good.stage)
        assertEquals("没有传上去：云盘空间不足", bad.label())
        scope.cancel()
    }

    @Test
    fun `cancelling stops the running upload and skips a waiting one`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val never = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val uploads = CloudUploads(scope, send = { f, _ ->
            started += f.name
            if (f.name == "slow.mp3") never.await()
            CloudUpload(3, sent = true)
        }, onUploaded = {})
        val slow = uploads.add("slow.mp3", 10) { file("slow.mp3") }
        val skipped = uploads.add("skipped.mp3", 10) { file("skipped.mp3") }
        uploads.add("last.mp3", 10) { file("last.mp3") }
        scope.advanceUntilIdle()
        uploads.cancel(skipped)
        uploads.cancel(slow)
        scope.advanceUntilIdle()
        assertEquals(listOf("slow.mp3", "last.mp3"), started)
        assertEquals(listOf("last.mp3"), uploads.jobs.value.map { it.name })
        scope.cancel()
    }

    @Test
    fun `the checksum is the file's hex md5`() {
        val (md5, size) = CloudUploads.md5 { "abc".byteInputStream() }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5)
        assertEquals(3L, size)
    }
}
