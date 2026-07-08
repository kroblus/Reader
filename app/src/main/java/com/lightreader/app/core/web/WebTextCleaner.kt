package com.lightreader.app.core.web

class WebTextCleaner {
    fun clean(rawText: String, chapterTitle: String): String {
        val titleKey = normalizeTitle(chapterTitle)
        val output = ArrayList<String>()
        var previousBlank = false
        rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .forEachIndexed { index, rawLine ->
                val line = normalizeLine(rawLine)
                if (line.isBlank()) {
                    if (!previousBlank && output.isNotEmpty()) {
                        output += ""
                        previousBlank = true
                    }
                    return@forEachIndexed
                }
                if (index <= 4 && titleKey.isNotBlank() && normalizeTitle(line) == titleKey) return@forEachIndexed
                if (isNoiseLine(line)) return@forEachIndexed
                output += line
                previousBlank = false
            }
        return output
            .dropWhile(String::isBlank)
            .dropLastWhile(String::isBlank)
            .joinToString("\n")
            .trim()
    }

    private fun normalizeLine(raw: String): String =
        raw.replace('\u00A0', ' ')
            .replace('\u3000', ' ')
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()

    private fun normalizeTitle(raw: String): String =
        normalizeLine(raw).replace(Regex("""[\s:：,，.。!！?？\-_\[\]【】()（）]+"""), "").lowercase()

    private fun isNoiseLine(line: String): Boolean {
        val normalized = normalizeLine(line)
        val lower = normalized.lowercase()
        if (normalized.length <= 120 && NOISE_PHRASES.any { lower.contains(it) }) return true
        if (AD_PATTERNS.any { it.containsMatchIn(normalized) }) return true
        return normalized.length <= 80 && LINK_HEAVY_PATTERN.containsMatchIn(normalized)
    }

    private companion object {
        val NOISE_PHRASES = listOf(
            "previous chapter", "next chapter", "back to catalog", "add bookmark", "mobile users",
            "latest address", "remember this site", "please bookmark",
            "\u4E0A\u4E00\u7AE0", "\u4E0B\u4E00\u7AE0", "\u8FD4\u56DE\u76EE\u5F55", "\u56DE\u5230\u76EE\u5F55",
            "\u52A0\u5165\u4E66\u7B7E", "\u52A0\u5165\u4E66\u67B6", "\u8BF7\u6536\u85CF\u672C\u7AD9",
            "\u624B\u673A\u7528\u6237\u8BF7\u6D4F\u89C8", "\u6700\u65B0\u7F51\u5740", "\u70B9\u51FB\u4E0B\u4E00\u9875",
        )
        val AD_PATTERNS = listOf(
            Regex("""(?i)https?://\S+"""),
            Regex("""(?i)www\.[A-Za-z0-9._/-]+"""),
            Regex("""\u672C\u7AD9\u57DF\u540D"""),
            Regex("""\u8BF7\u8BB0\u4F4F\u672C\u7AD9"""),
            Regex("""\u5E7F\u544A"""),
        )
        val LINK_HEAVY_PATTERN = Regex("""(?:<|>|://|www\.|上一章|下一章|目录|书架)""", RegexOption.IGNORE_CASE)
    }
}
