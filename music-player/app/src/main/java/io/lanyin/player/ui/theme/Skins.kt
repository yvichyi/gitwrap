package io.lanyin.player.ui.theme

/**
 * A whole visual language, not just a palette: each theme brings its own shapes, type,
 * backdrop, controls, lyric layout and motion (see [LanyinStyle]).
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val tagline: String,
    val darkOnly: Boolean = false,
    val lightOnly: Boolean = false,
) {
    INK("mo", "墨", "黑白留白，只留文字"),
    DOT("dian", "点", "点阵字形，黑白一点红", darkOnly = true),
    DEVICE("ji", "机", "暖灰机身，按键与指针"),
    FILM("ying", "影", "胶片颗粒，光影流转", darkOnly = true),
    GRID("ge", "格", "瑞士网格，粗边硬影", lightOnly = true),
    PAPER("bao", "报", "报纸版面，铅字和红笔"),
    GAME("you", "游", "像素掌机，四色绿屏", lightOnly = true),
    ACID("suan", "酸", "铬金大字，荧光绿 Y2K", darkOnly = true),
    DUSK("mu", "暮", "晚霞暖光，柔和圆角"),
    NEON("ni", "霓", "雨后夜街，霓虹招牌", darkOnly = true);

    companion object {
        /** 墨 is the flagship: the most restrained theme sets the bar for the rest. */
        val Default = INK
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Artwork style of the now-playing page; combines freely with any [AppTheme]. */
enum class PlayerSkin(val id: String, val label: String, val description: String) {
    VINYL("vinyl", "旋转黑胶", "唱片带惯性转动，唱臂随播放落下"),
    COVER("cover", "方形封面", "大封面，暂停时轻轻收起"),
    LYRICS("lyrics", "沉浸歌词", "不要封面，整屏都是歌词"),
    CASSETTE("cassette", "复古卡带", "磁带转轮随播放转动");

    companion object {
        val Default = VINYL
        fun fromId(id: String?): PlayerSkin = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Lyric text size on the now-playing page, as a multiple of [BASE_SP]. Chinese glyphs fill their
 * em square, so 18sp already reads like a 20–22sp Latin line; the active line is enlarged a
 * little further by the theme's [LyricStyle.activeScale].
 */
enum class LyricSize(val id: String, val label: String, val scale: Float) {
    SMALL("small", "小", 0.86f),
    MEDIUM("medium", "中", 1f),
    LARGE("large", "大", 1.22f);

    val sp: Float get() = BASE_SP * scale

    companion object {
        const val BASE_SP = 18f
        val Default = MEDIUM
        fun fromId(id: String?): LyricSize = entries.firstOrNull { it.id == id } ?: Default
    }
}

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        val Default = SYSTEM
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: Default
    }
}
