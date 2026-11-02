package io.lanyin.player.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What went wrong on this phone, kept on this phone: slow frames counted per screen and theme
 * (with the worst ones listed), errors the app reported, and crashes with their stack traces.
 * [report] gathers it into a plain-text report the person can choose to share; nothing is ever
 * sent anywhere by the app, and cookies, tokens and the like are taken out of messages first.
 * [dir] null keeps everything in memory (tests).
 */
class Diagnostics(private val dir: File?, private val clock: () -> Long = System::currentTimeMillis) {

    /** Where the app is, as the person would name it (首页, 播放页 …); set by the UI. */
    @Volatile
    var where: String = "启动"

    /** The theme showing, as its one-character name. */
    @Volatile
    var theme: String = ""

    private class Stat(var frames: Long = 0, var slow: Long = 0, var stalls: Long = 0, var worstMs: Int = 0)

    private val lock = Any()
    private val stats = LinkedHashMap<String, Stat>()
    private val slowFrames = ArrayDeque<String>()
    private val errors = ArrayDeque<String>()
    private var dirty = false

    init {
        dir?.mkdirs()
        load()
    }

    /**
     * One frame drawn in [durationMs] when the screen allowed [budgetMs]: counted for the current
     * screen and theme; dropped past twice the budget (as Android's own jank tracking counts it),
     * a stall past 100 ms. Stalls are listed.
     */
    fun frame(durationMs: Float, budgetMs: Float) {
        val key = "$where\t$theme"
        synchronized(lock) {
            val s = stats.getOrPut(key) { Stat() }
            s.frames++
            if (durationMs > budgetMs * 2f) s.slow++
            if (durationMs > STALL_MS) {
                s.stalls++
                remember(slowFrames, "${stamp()}\t$where\t$theme\t${durationMs.toInt()} ms", MAX_SLOW)
            }
            if (durationMs.toInt() > s.worstMs) s.worstMs = durationMs.toInt()
            dirty = true
        }
    }

    /** Something the app reported as having failed: [what] in a few words, [detail] the error's own message. */
    fun error(what: String, detail: String? = null) {
        val line = "${stamp()}\t$where\t$theme\t$what" + (detail?.let { "：${redact(it)}" } ?: "")
        synchronized(lock) {
            remember(errors, line, MAX_ERRORS)
            dirty = true
        }
        flush()
    }

    /** A crash, written straight away: the process is about to end. */
    fun crash(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val entry = "== ${stamp()} · $where · $theme · 线程 ${thread.name}\n${redact(trace).lineSequence().take(60).joinToString("\n")}\n"
        dir ?: return
        runCatching {
            val f = File(dir, CRASHES)
            f.appendText(entry)
            // Keep the last few dozen kilobytes.
            if (f.length() > MAX_CRASH_BYTES) f.writeText(f.readText().takeLast(MAX_CRASH_BYTES / 2))
        }
        flush()
    }

    /** Catches crashes on any thread, writes them down, then lets the system's handler end the app as usual. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { crash(t, e) }
            previous?.uncaughtException(t, e)
        }
    }

    /** How much there is: screens measured, slow frames, errors, crashes. */
    fun summary(): Summary = synchronized(lock) {
        Summary(
            frames = stats.values.sumOf { it.frames },
            slow = stats.values.sumOf { it.slow },
            stalls = stats.values.sumOf { it.stalls },
            errors = errors.size,
            crashes = crashCount(),
        )
    }

    data class Summary(val frames: Long, val slow: Long, val stalls: Long, val errors: Int, val crashes: Int)

    /** The whole report, about the app and phone in [about], then frames by screen, the worst stalls, errors and crashes. */
    fun report(about: List<String>): String = buildString {
        appendLine("澜音诊断报告")
        about.forEach { appendLine(redact(it)) }
        appendLine("生成于 ${stamp()}")
        appendLine("（只记录了页面、主题、耗时和出错信息；不含账号、Cookie 或听过什么歌。）")
        appendLine()
        synchronized(lock) {
            appendLine("== 卡顿，按页面和主题 ==")
            appendLine("页面\t主题\t帧数\t掉帧\t严重(>100ms)\t最长")
            if (stats.isEmpty()) appendLine("（还没有记录）")
            stats.entries.sortedByDescending { it.value.slow }.forEach { (key, s) ->
                val share = if (s.frames > 0) "%.1f%%".format(Locale.ROOT, s.slow * 100.0 / s.frames) else "-"
                appendLine("$key\t${s.frames}\t${s.slow} ($share)\t${s.stalls}\t${s.worstMs} ms")
            }
            appendLine()
            appendLine("== 最近的严重卡顿 ==")
            if (slowFrames.isEmpty()) appendLine("（没有）")
            slowFrames.reversed().forEach { appendLine(it) }
            appendLine()
            appendLine("== 出错 ==")
            if (errors.isEmpty()) appendLine("（没有）")
            errors.reversed().forEach { appendLine(it) }
        }
        appendLine()
        appendLine("== 闪退 ==")
        val crashes = dir?.let { File(it, CRASHES) }?.takeIf { it.isFile }?.readText().orEmpty()
        appendLine(crashes.ifBlank { "（没有）" })
    }

    /** Forgets everything recorded. */
    fun clear() {
        synchronized(lock) {
            stats.clear()
            slowFrames.clear()
            errors.clear()
            dirty = false
        }
        dir?.let { d -> listOf(STATS, SLOW, ERRORS, CRASHES).forEach { File(d, it).delete() } }
    }

    /** Writes what's been gathered, if anything changed; cheap to call often. */
    fun flush() {
        val d = dir ?: return
        val (statsText, slowText, errorsText) = synchronized(lock) {
            if (!dirty) return
            dirty = false
            Triple(
                stats.entries.joinToString("\n") { (k, s) -> "$k\t${s.frames}\t${s.slow}\t${s.stalls}\t${s.worstMs}" },
                slowFrames.joinToString("\n"),
                errors.joinToString("\n"),
            )
        }
        runCatching {
            File(d, STATS).writeText(statsText)
            File(d, SLOW).writeText(slowText)
            File(d, ERRORS).writeText(errorsText)
        }
    }

    private fun load() {
        val d = dir ?: return
        runCatching {
            File(d, STATS).takeIf { it.isFile }?.readLines()?.forEach { line ->
                val p = line.split('\t')
                if (p.size == 6) stats["${p[0]}\t${p[1]}"] = Stat(p[2].toLong(), p[3].toLong(), p[4].toLong(), p[5].toInt())
            }
            File(d, SLOW).takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }?.forEach { remember(slowFrames, it, MAX_SLOW) }
            File(d, ERRORS).takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }?.forEach { remember(errors, it, MAX_ERRORS) }
        }
    }

    private fun crashCount(): Int = dir?.let { File(it, CRASHES) }?.takeIf { it.isFile }
        ?.useLines { lines -> lines.count { it.startsWith("== ") } } ?: 0

    private fun remember(list: ArrayDeque<String>, line: String, max: Int) {
        list.addLast(line)
        while (list.size > max) list.removeFirst()
    }

    private fun stamp(): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date(clock()))

    companion object {
        const val STALL_MS = 100f
        private const val MAX_SLOW = 120
        private const val MAX_ERRORS = 120
        private const val MAX_CRASH_BYTES = 96 * 1024
        private const val STATS = "frames.tsv"
        private const val SLOW = "stalls.log"
        private const val ERRORS = "errors.log"
        private const val CRASHES = "crashes.log"

        private val secrets = Regex("""(?i)\b(MUSIC_U|MUSIC_A|MUSIC_R_U|__csrf|csrf_token|NMTID|WNMCID|__remember_me|_ntes_nuid|_ntes_nnid|deviceId|cookie|token|key)\s*[=:]\s*[^;\s,&"']+""")
        private val longTokens = Regex("""\b[A-Za-z0-9+/_-]{32,}={0,2}""")

        /** [text] with cookies, tokens and anything that looks like one taken out. */
        fun redact(text: String): String = text
            .replace(secrets) { "${it.groupValues[1]}=…" }
            .replace(longTokens, "…")
    }
}

/** The app's diagnostics, for places that aren't handed them (loaders, the player); nothing happens until one is installed. */
object Diag {
    @Volatile
    var current: Diagnostics? = null

    fun error(what: String, detail: String? = null) {
        current?.error(what, detail)
    }
}
