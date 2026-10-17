package io.lanyin.player.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.Comment
import io.lanyin.player.core.model.CommentSort
import io.lanyin.player.core.model.CommentUser
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.ThemedTextField
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.music.Placeholder
import io.lanyin.player.ui.music.relativeTime
import io.lanyin.player.ui.music.shortCount
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** The controller for [songId], wired to NetEase and the signed-in account. */
@Composable
fun rememberCommentsController(songId: Long): CommentsController {
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val account = services?.account?.state?.collectAsState()?.value
    val me = (account as? AccountState.LoggedIn)?.profile?.let { CommentUser(it.userId, it.nickname, it.avatarUrl) }
    return remember(songId, me) { CommentsController(songId, services?.api?.asCommentSource(), scope, me) }
}

/** A song's comments: the order to read them in, the comments, and a box to add one. */
@Composable
fun CommentsPage(
    song: Song,
    onBack: () -> Unit,
    controller: CommentsController = rememberCommentsController(song.id),
    now: Long = System.currentTimeMillis(),
) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    val input = remember { FocusRequester() }
    LaunchedEffect(controller) { controller.start() }
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(Modifier.statusBarsPadding().padding(start = m - 12.dp, end = m, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconAction(LineIcon.Back, "返回", onBack)
                        Text("评论", style = style.type.title, modifier = Modifier.padding(start = 4.dp))
                        if (controller.total > 0) {
                            Text(shortCount(controller.total), style = style.type.caption, color = c.muted, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        }
                    }
                }
                item {
                    Row(Modifier.padding(start = m, end = m, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(song.album.coverUrl, (song.id % 97).toInt(), 56.dp, Modifier.size(56.dp).clip(style.artShape))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.name, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item {
                    ChoiceRow(
                        CommentSort.entries.map { it.label }, controller.sort.label,
                        { label -> controller.select(CommentSort.entries.first { it.label == label }) },
                        Modifier.padding(start = m, end = m, top = 22.dp, bottom = 6.dp),
                    )
                }
                when {
                    controller.loading -> item { Placeholder("正在加载评论…") }
                    controller.error != null -> item { Placeholder(controller.error!!, "重试", controller::retry) }
                    controller.comments.isEmpty() -> item { Placeholder("还没有评论，来说第一句") }
                    else -> {
                        itemsIndexed(controller.comments, key = { _, it -> it.id }) { i, comment ->
                            CommentRow(
                                comment, now, i,
                                onLike = { controller.toggleLike(comment) },
                                onReply = {
                                    controller.replyTo = comment
                                    runCatching { input.requestFocus() }
                                },
                                modifier = Modifier.padding(horizontal = m),
                            )
                        }
                        item(key = "end-${controller.comments.size}") {
                            if (controller.hasMore) LaunchedEffect(controller.comments.size) { controller.loadMore() }
                            Text(
                                if (controller.hasMore) "正在加载更多…" else "没有更多了",
                                style = style.type.caption, color = c.muted, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            )
                        }
                    }
                }
            }
            InputBar(controller, input)
        }
    }
}

/**
 * One comment, set as the theme sets things: a note in the margin (墨), a tile on the wall (霓),
 * a postcard (暮), a boxed block with its number (格), a line of a screenplay (影), a grey tile
 * (点), a line in the device's log (机), a letter to the editor (报), a game's dialogue window
 * (游), a bubble of coloured gel (酸).
 */
@Composable
private fun CommentRow(comment: Comment, now: Long, index: Int, onLike: () -> Unit, onReply: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val kind = style.backdrop
    // Letters, screenplay lines and log lines go without a face.
    val face = kind != BackdropKind.Film && kind != BackdropKind.Paper && kind != BackdropKind.Device
    val framed = kind != BackdropKind.Ink && kind != BackdropKind.Film && kind != BackdropKind.Paper
    // On 酸's coloured plastic grey won't read; white does.
    val quiet = if (kind == BackdropKind.Acid) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else c.muted
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (framed) 6.dp else 0.dp)
            .commentFrame(index)
            .pressable(onClick = onReply)
            .padding(commentInset(kind)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (face) {
                Avatar(comment.user.avatarUrl, comment.user.nickname, 36.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CommentByline(comment, now, index, Modifier.weight(1f), quiet)
                    Row(Modifier.pressable(onClick = onLike).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (comment.likedCount > 0) {
                            Text(
                                shortCount(comment.likedCount),
                                style = style.type.caption.copy(fontFamily = style.numeric),
                                color = if (comment.liked) c.like else quiet,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        ThemedIcon(if (comment.liked) LineIcon.HeartFilled else LineIcon.Heart, if (comment.liked) "取消点赞" else "点赞", tint = if (comment.liked) c.like else quiet, size = 17.dp)
                    }
                }
                Text(
                    comment.content,
                    style = if (kind == BackdropKind.Dusk) style.type.body.copy(fontFamily = style.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else style.type.body,
                    // A screenplay's dialogue sits in from the margin.
                    modifier = Modifier.padding(top = 6.dp, start = if (kind == BackdropKind.Film) 24.dp else 0.dp),
                )
                comment.replyTo?.let { quoted ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accent)) { append(if (kind == BackdropKind.Paper) "原信 @${quoted.nickname}" else "@${quoted.nickname}") }
                            append("：")
                            append(quoted.content)
                        },
                        style = style.type.caption, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .then(if (style.edge == EdgeKind.Hairline || kind == BackdropKind.Paper) Modifier else Modifier.clip(style.chipShape))
                            .background(c.content.copy(alpha = if (style.dark) 0.07f else 0.045f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** What's being answered, a note when there is one, and the box with its send button. */
@Composable
private fun InputBar(controller: CommentsController, focus: FocusRequester) {
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    val docked = style.dock == DockKind.Docked
    LaunchedEffect(controller.notice) {
        if (controller.notice != null) {
            kotlinx.coroutines.delay(3_500)
            controller.notice = null
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            // Below the list, not over it: the field carries the theme's surface itself.
            .then(if (docked) Modifier.background(c.bg) else Modifier.padding(horizontal = 4.dp))
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = if (docked) 0.dp else 8.dp),
    ) {
        if (docked) Hairline()
        AnimatedVisibility(controller.notice != null) {
            Text(controller.notice.orEmpty(), style = style.type.caption, color = c.accent, modifier = Modifier.padding(start = m, end = m, top = 10.dp))
        }
        controller.replyTo?.let { target ->
            Row(Modifier.padding(start = m, end = m - 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("回复 @${target.user.nickname}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconAction(LineIcon.Close, "不回复了", { controller.replyTo = null }, tint = c.muted, iconSize = 18.dp)
            }
        }
        Row(Modifier.padding(start = if (docked) m else m - 4.dp, end = m - 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ThemedTextField(
                    controller.draft, { controller.draft = it.take(140) },
                    if (controller.signedIn) "说点什么…" else "登录后可以发表评论",
                    Modifier.focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { controller.send() }),
                )
            }
            val ready = controller.draft.isNotBlank() && !controller.sending
            IconAction(LineIcon.Send, "发送", { controller.send() }, tint = if (ready) c.accent else c.muted)
        }
    }
}
