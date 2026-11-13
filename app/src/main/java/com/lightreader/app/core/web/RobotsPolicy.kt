package com.lightreader.app.core.web

import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Origin-scoped, bounded cache. A failed check never becomes an implicit allow. */
internal class RobotsPolicy(private val fetcher: HtmlFetcher, private val validator: NovelUrlValidator) {
    private data class Cached(val loadedAt: Long, val rules: RobotsRules)
    private val cache = LinkedHashMap<String, Cached>()
    private val mutex = Mutex()

    suspend fun check(url: String) {
        val uri = validator.validate(url)
        val origin = "${uri.scheme.lowercase()}://${uri.host.lowercase()}:${if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80}"
        val rules = mutex.withLock {
            cache[origin]?.takeIf { System.currentTimeMillis() - it.loadedAt < 60 * 60 * 1000 }?.rules ?: run {
                val text = try {
                    fetcher.fetchText("${uri.scheme}://${uri.authority}/robots.txt", 512 * 1024).html
                } catch (error: WebImportException) {
                    // A missing robots file is an explicit absence; network/access failures remain visible.
                    if (error.httpStatus == 404 || error.httpStatus == 410) "" else throw error
                }
                RobotsRules.parse(text).also {
                    if (cache.size >= 64) cache.remove(cache.keys.first())
                    cache[origin] = Cached(System.currentTimeMillis(), it)
                }
            }
        }
        val path = uri.rawPath.ifBlank { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
        if (!rules.allows(path)) throw WebImportException(WebImportFailure.ACCESS_RESTRICTED,
            "The site's robots.txt does not allow fetching this path.", sourceUrl = url)
    }
}

internal class RobotsRules private constructor(private val rules: List<Pair<String, Boolean>>) {
    fun allows(path: String): Boolean = rules.filter { (pattern, _) ->
        val end = pattern.endsWith('$')
        val value = if (end) pattern.dropLast(1) else pattern
        val regex = "^" + value.split('*').joinToString(".*") { Regex.escape(it) } + if (end) "$" else ""
        Regex(regex).containsMatchIn(normalizePath(path))
    }.maxWithOrNull(compareBy<Pair<String, Boolean>> { it.first.toByteArray().size }.thenBy { it.second })?.second ?: true

    companion object {
        private fun normalizePath(path: String): String = buildString {
            var index = 0
            while (index < path.length) {
                val character = path[index]
                if (character == '%' && index + 2 < path.length) {
                    val hex = path.substring(index + 1, index + 3)
                    val value = hex.toIntOrNull(16)
                    if (value != null) {
                        val decoded = value.toChar()
                        if (decoded in 'a'..'z' || decoded in 'A'..'Z' || decoded in '0'..'9' || decoded in "-._~") append(decoded)
                        else append('%').append(hex.uppercase())
                        index += 3
                        continue
                    }
                }
                val point = path.codePointAt(index)
                if (point > 127) String(Character.toChars(point)).toByteArray(Charsets.UTF_8).forEach { append("%%%02X".format(it.toInt() and 255)) }
                else append(character)
                index += Character.charCount(point)
            }
        }

        fun parse(text: String): RobotsRules {
            val groups = mutableListOf<Pair<List<String>, List<Pair<String, Boolean>>>>()
            var agents = mutableListOf<String>()
            var rules = mutableListOf<Pair<String, Boolean>>()
            var seenRule = false
            fun flush() {
                if (agents.isNotEmpty()) groups += agents.toList() to rules.toList()
                agents = mutableListOf()
                rules = mutableListOf()
                seenRule = false
            }
            text.lineSequence().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                if (!line.contains(':')) return@forEach
                val key = line.substringBefore(':').trim().lowercase()
                val value = line.substringAfter(':').trim()
                when (key) {
                    "user-agent" -> { if (seenRule) flush(); agents += value.lowercase() }
                    "allow", "disallow" -> if (agents.isNotEmpty()) {
                        seenRule = true
                        if (value.startsWith('/')) rules += normalizePath(value) to (key == "allow")
                    }
                }
            }
            flush()
            val matching = groups.filter { "lightreader" in it.first }
            return RobotsRules((matching.ifEmpty { groups.filter { "*" in it.first } }).flatMap { it.second }.distinct())
        }
    }
}
