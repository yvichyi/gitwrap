package io.lanyin.player.ui.music

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 「刚刚」「5 分钟前」「昨天 21:04」「3月2日」「2021年3月2日」, as Chinese apps say when. */
fun relativeTime(ms: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val diff = now - ms
    val then = Instant.ofEpochMilli(ms).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        then.toLocalDate() == today -> "${diff / 3_600_000} 小时前"
        then.toLocalDate() == today.minusDays(1) -> "昨天 " + then.format(DateTimeFormatter.ofPattern("HH:mm"))
        then.year == today.year -> then.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
        else -> then.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    }
}

/** 「1.2万」「3.4亿」 for big counts; the number itself below ten thousand. */
fun shortCount(n: Int): String = when {
    n >= 100_000_000 -> trimmed(n / 100_000_000.0) + "亿"
    n >= 10_000 -> trimmed(n / 10_000.0) + "万"
    else -> n.toString()
}

private fun trimmed(x: Double): String = if (x >= 100) x.toInt().toString() else "%.1f".format(Locale.ROOT, x).removeSuffix(".0")

/** 「512 KB」「31.4 MB」「5.2 GB」. */
fun byteSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(Locale.ROOT, bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(Locale.ROOT, bytes / (1L shl 20).toDouble())
    else -> "${(bytes / 1024).coerceAtLeast(if (bytes > 0) 1 else 0)} KB"
}
