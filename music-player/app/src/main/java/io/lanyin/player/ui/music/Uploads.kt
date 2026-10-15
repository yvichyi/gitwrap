package io.lanyin.player.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.data.library.UploadJob
import io.lanyin.player.data.library.UploadStage
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin

/** What a row says about its upload. */
fun UploadJob.label(): String = when (stage) {
    UploadStage.Waiting -> "等待上传"
    UploadStage.Reading -> "正在读取文件…"
    UploadStage.Sending -> "${(progress * 100).toInt()}% · ${byteSize(sent)} / ${byteSize(size)}"
    UploadStage.Saving -> "正在存入云盘…"
    UploadStage.Done -> if (instant) "秒传完成，云盘里已有这份文件" else "已上传 · ${byteSize(size)}"
    UploadStage.Failed -> "没有传上去：${error ?: "原因不明"}"
}

/** Files on their way to the cloud drive, over the song list. */
@Composable
fun UploadPanel(jobs: List<UploadJob>, onCancel: (Int) -> Unit, onClearFinished: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val active = jobs.count { !it.finished }
    val failed = jobs.count { it.stage == UploadStage.Failed }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat))
            .padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    active > 0 -> "正在上传 · 还剩 $active 个"
                    failed > 0 -> "上传结束 · ${jobs.size - failed} 个成功，$failed 个失败"
                    else -> "上传完成 · ${jobs.size} 个"
                },
                style = style.type.label, modifier = Modifier.weight(1f),
            )
            if (active < jobs.size) {
                Text(
                    "清除已完成",
                    style = style.type.caption, color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClearFinished).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        jobs.forEach { job -> UploadRow(job, onCancel = { onCancel(job.id) }) }
    }
}

@Composable
private fun UploadRow(job: UploadJob, onCancel: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val shown by animateFloatAsState(job.progress, style.motion.spec(), label = "upload")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(job.name, style = style.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                job.label(),
                style = style.type.caption.copy(fontFamily = style.numeric, fontWeight = if (job.stage == UploadStage.Sending) FontWeight.Medium else FontWeight.Normal),
                color = when (job.stage) {
                    UploadStage.Failed -> c.like
                    UploadStage.Sending, UploadStage.Saving -> c.accent
                    else -> c.muted
                },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!job.finished) {
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.content.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceIn(0f, 1f)).background(c.accent))
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        IconAction(LineIcon.Close, if (job.finished) "移出列表" else "取消上传", onCancel, tint = c.muted, iconSize = 18.dp)
    }
}
