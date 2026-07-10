package com.lightreader.app.core.formats

/**
 * Deliberately conservative TXT cleanup. It only removes standalone navigation,
 * advertising, or URL lines, never prose that merely mentions a URL or a phrase.
 */
class TxtTextCleaner {
    fun clean(line: String): String? {
        val normalized = line.trim()
        if (normalized.isBlank()) return null
        if (normalized.length > MAX_NOISE_LINE_LENGTH) return normalized
        if (FULL_URL.matches(normalized) || DOMAIN_ONLY.matches(normalized)) return null
        if (NOISE_MARKERS.any { marker -> normalized.contains(marker, ignoreCase = true) }) return null
        return normalized
    }

    private companion object {
        const val MAX_NOISE_LINE_LENGTH = 180
        val FULL_URL = Regex("""^(?:https?://|www\.)[^\s]+$""", RegexOption.IGNORE_CASE)
        val DOMAIN_ONLY = Regex("""^[A-Za-z0-9][A-Za-z0-9.-]{0,120}\.(?:com|cn|net|org|cc|vip|info)(?:/[^\s]*)?$""", RegexOption.IGNORE_CASE)
        val NOISE_MARKERS = listOf(
            "最新网址", "请记住本站", "收藏本站", "手机用户请浏览", "广告", "上一章", "下一章",
            "返回目录", "加入书签", "加入书架", "本章未完", "点击下一页", "read more",
        )
    }
}
