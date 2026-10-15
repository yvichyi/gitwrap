package io.lanyin.player.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import io.lanyin.player.core.netease.CloudUpload
import io.lanyin.player.core.netease.NeteaseException
import io.lanyin.player.core.netease.UploadFile
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where an upload stands. */
enum class UploadStage { Waiting, Reading, Sending, Saving, Done, Failed }

/** One file on its way to the cloud drive. */
data class UploadJob(
    val id: Int,
    val name: String,
    val size: Long,
    val stage: UploadStage = UploadStage.Waiting,
    val sent: Long = 0,
    /** NetEase already held the bytes, so nothing had to be sent. */
    val instant: Boolean = false,
    val error: String? = null,
) {
    val finished: Boolean get() = stage == UploadStage.Done || stage == UploadStage.Failed
    val progress: Float get() = if (size > 0) (sent.toFloat() / size).coerceIn(0f, 1f) else 0f
}

/**
 * Files picked for the cloud drive, sent one at a time so each gets the whole uplink. The list
 * keeps finished ones until cleared; [onUploaded] runs after each song lands, to refresh the drive.
 */
class CloudUploads(
    private val scope: CoroutineScope,
    private val send: suspend (UploadFile, (Long) -> Unit) -> CloudUpload,
    private val onUploaded: () -> Unit,
) {
    private val _jobs = MutableStateFlow<List<UploadJob>>(emptyList())
    val jobs: StateFlow<List<UploadJob>> = _jobs.asStateFlow()

    private class Pending(val id: Int, val prepare: suspend () -> UploadFile)

    private val queue = Channel<Pending>(Channel.UNLIMITED)
    private var nextId = 1
    private val cancelled = mutableSetOf<Int>()
    private var running: Pair<Int, Job>? = null

    init {
        scope.launch {
            for (p in queue) {
                if (synchronized(cancelled) { cancelled.remove(p.id) }) continue
                val job = launch { run(p) }
                synchronized(cancelled) { running = p.id to job }
                job.join()
                synchronized(cancelled) { running = null }
            }
        }
    }

    /** Queues a file by [name] and [size]; [prepare] reads it (tags, checksum) when its turn comes. */
    fun add(name: String, size: Long, prepare: suspend () -> UploadFile): Int {
        val id = synchronized(cancelled) { nextId++ }
        _jobs.update { it + UploadJob(id, name, size) }
        queue.trySend(Pending(id, prepare))
        return id
    }

    /** Stops an upload, or takes a finished one off the list. */
    fun cancel(id: Int) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        _jobs.update { list -> list.filterNot { it.id == id } }
        if (job.finished) return
        synchronized(cancelled) {
            val r = running
            if (r?.first == id) r.second.cancel() else cancelled += id
        }
    }

    fun clearFinished() = _jobs.update { list -> list.filterNot { it.finished } }

    private suspend fun run(p: Pending) {
        try {
            set(p.id) { it.copy(stage = UploadStage.Reading) }
            val file = p.prepare()
            set(p.id) { it.copy(stage = UploadStage.Sending, size = file.size) }
            val result = send(file) { sent ->
                set(p.id) { it.copy(sent = sent, stage = if (sent >= file.size) UploadStage.Saving else UploadStage.Sending) }
            }
            set(p.id) { it.copy(stage = UploadStage.Done, sent = file.size, instant = !result.sent) }
            onUploaded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            set(p.id) { it.copy(stage = UploadStage.Failed, error = describe(e)) }
        }
    }

    private fun set(id: Int, change: (UploadJob) -> UploadJob) = _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }

    companion object {
        fun describe(e: Exception): String = when {
            e is NeteaseException && e.code == 301 -> "登录已过期"
            e is NeteaseException -> e.message ?: "网易云返回 ${e.code}"
            e is UnreadableFile -> e.message ?: "读不了这个文件"
            else -> "网络不好"
        }

        /** Hex MD5 of everything [open] yields. */
        fun md5(open: () -> java.io.InputStream): Pair<String, Long> {
            val digest = MessageDigest.getInstance("MD5")
            var size = 0L
            open().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    size += n
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } to size
        }
    }
}

class UnreadableFile(message: String) : IOException(message)

/** A picked document's name and size, as the picker reports them. */
fun Context.describeDocument(uri: Uri): Pair<String, Long> {
    var name: String? = null
    var size = 0L
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    return (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名.mp3") to size
}

/** Reads a picked document for upload: its checksum, exact size and tags. */
suspend fun Context.uploadFile(uri: Uri, name: String): UploadFile = withContext(Dispatchers.IO) {
    val open = { contentResolver.openInputStream(uri) ?: throw UnreadableFile("读不了「$name」") }
    val (md5, size) = try {
        CloudUploads.md5(open)
    } catch (e: SecurityException) {
        throw UnreadableFile("没有读取「$name」的权限")
    }
    if (size == 0L) throw UnreadableFile("「$name」是空文件")
    val tags = MediaMetadataRetriever()
    try {
        runCatching { tags.setDataSource(this@uploadFile, uri) }
        fun tag(key: Int) = runCatching { tags.extractMetadata(key) }.getOrNull()?.trim()?.ifEmpty { null }
        UploadFile(
            name = name,
            size = size,
            md5 = md5,
            title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            open = open,
        )
    } finally {
        runCatching { tags.release() }
    }
}
