package io.lanyin.player.ui.comments

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lanyin.player.core.model.Comment
import io.lanyin.player.core.model.CommentPage
import io.lanyin.player.core.model.CommentSort
import io.lanyin.player.core.model.CommentUser
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.ui.music.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where comments come from and go to; NetEase in the app, a fake in tests. */
interface CommentSource {
    suspend fun page(songId: Long, sort: CommentSort, pageNo: Int, cursor: String?): CommentPage
    suspend fun like(songId: Long, commentId: Long, like: Boolean)
    suspend fun post(songId: Long, content: String, replyTo: Long?): Comment?
}

fun NeteaseApi.asCommentSource() = object : CommentSource {
    override suspend fun page(songId: Long, sort: CommentSort, pageNo: Int, cursor: String?) = comments(songId, sort, pageNo, cursor)
    override suspend fun like(songId: Long, commentId: Long, like: Boolean) = likeComment(songId, commentId, like)
    override suspend fun post(songId: Long, content: String, replyTo: Long?) = postComment(songId, content, replyTo)
}

/**
 * A song's comments as the page shows them: one order at a time, more as you scroll, likes that
 * show at once (and go back if NetEase refuses), and posting or replying. [me] is who posts;
 * null when signed out, which turns posting and liking into a prompt to log in.
 */
@Stable
class CommentsController(
    private val songId: Long,
    private val source: CommentSource?,
    private val scope: CoroutineScope,
    private val me: CommentUser?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var sort by mutableStateOf(CommentSort.RECOMMENDED)
        private set
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var total by mutableIntStateOf(0)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** The comment being answered, or null for a new one. */
    var replyTo by mutableStateOf<Comment?>(null)
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set

    /** Something to tell the listener: sent, refused, needs login. */
    var notice by mutableStateOf<String?>(null)

    val signedIn: Boolean get() = me != null

    private var pageNo = 1
    private var cursor: String? = null
    private var job: Job? = null

    /** Shows what is given, without asking anyone; for previews. */
    fun preview(page: CommentPage, sort: CommentSort = CommentSort.RECOMMENDED) {
        this.sort = sort
        comments = page.comments
        total = page.total
        hasMore = page.hasMore
    }

    fun start() {
        if (comments.isEmpty() && !loading) select(sort, force = true)
    }

    fun select(sort: CommentSort, force: Boolean = false) {
        if (sort == this.sort && !force) return
        val src = source ?: return
        this.sort = sort
        job?.cancel()
        loading = true
        error = null
        comments = emptyList()
        hasMore = false
        pageNo = 1
        cursor = null
        job = scope.launch {
            try {
                val page = src.page(songId, sort, 1, null)
                comments = page.comments
                total = page.total
                hasMore = page.hasMore && page.comments.isNotEmpty()
                cursor = page.cursor
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describe(e)
            } finally {
                loading = false
            }
        }
    }

    fun retry() = select(sort, force = true)

    fun loadMore() {
        val src = source ?: return
        if (!hasMore || loading || loadingMore) return
        loadingMore = true
        val sortNow = sort
        job = scope.launch {
            try {
                val page = src.page(songId, sortNow, pageNo + 1, cursor)
                if (sortNow != sort) return@launch
                pageNo++
                cursor = page.cursor
                val seen = comments.mapTo(HashSet()) { it.id }
                comments = comments + page.comments.filter { it.id !in seen }
                hasMore = page.hasMore && page.comments.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "没能加载更多：${describe(e)}"
            } finally {
                loadingMore = false
            }
        }
    }

    fun toggleLike(comment: Comment) {
        val src = source ?: return
        if (me == null) {
            notice = "登录后才能点赞"
            return
        }
        val like = !comment.liked
        fun flip(c: Comment, on: Boolean) = c.copy(liked = on, likedCount = (c.likedCount + if (on) 1 else -1).coerceAtLeast(0))
        comments = comments.map { if (it.id == comment.id) flip(it, like) else it }
        scope.launch {
            try {
                src.like(songId, comment.id, like)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                comments = comments.map { if (it.id == comment.id) flip(it, !like) else it }
                notice = "没能点赞：${describe(e)}"
            }
        }
    }

    fun send() {
        val src = source ?: return
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        val user = me ?: run {
            notice = "登录后才能发表评论"
            return
        }
        val answering = replyTo
        sending = true
        scope.launch {
            try {
                val posted = src.post(songId, text, answering?.id)
                    ?: Comment(-now(), user, text, now(), 0, false, answering?.let { io.lanyin.player.core.model.QuotedComment(it.user.nickname, it.content) })
                comments = listOf(posted) + comments
                total++
                draft = ""
                replyTo = null
                notice = if (answering != null) "已回复 ${answering.user.nickname}" else "已发表"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "没能发表：${describe(e)}"
            } finally {
                sending = false
            }
        }
    }
}
