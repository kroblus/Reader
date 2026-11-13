package com.lightreader.app.core.backup

import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderLayoutSnapshot
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

object BackupValidator {
    const val MAX_FILES = 50_000
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024

    fun safeFile(root: File, relative: String): File {
        require(relative.isNotBlank() && relative.length <= 240 && !relative.startsWith('/') &&
            '\\' !in relative && ':' !in relative && relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Unsafe archive path"
        }
        val resolved = File(root, relative).canonicalFile
        require(resolved.path.startsWith(root.canonicalPath + File.separator)) { "Archive path escapes staging" }
        return resolved
    }

    fun validate(manifest: BackupManifest, root: File, entryNames: Set<String>, checkCancellation: () -> Unit = {}): Map<String, String> {
        require(manifest.archiveVersion == 1) { "Unsupported archive version: ${manifest.archiveVersion}" }
        validateSettings(manifest.settings)
        require(manifest.books.size <= 10_000 && manifest.books.map { it.id }.distinct().size == manifest.books.size) { "Duplicate or excessive book identities" }
        val referenced = manifest.books.flatMap { it.chapters }.map { it.path }
        require(referenced.size <= MAX_FILES && referenced.distinct().size == referenced.size) { "Duplicate content paths" }
        require(entryNames == referenced.toSet() + "manifest.json") { "Missing or unexpected archive resources" }
        val invalid = linkedMapOf<String, String>()
        manifest.books.forEach { book ->
            checkCancellation()
            try {
                require(book.id.isNotBlank() && book.id.length <= 128 && book.title.isNotBlank()) { "Invalid book metadata" }
                BookFormat.valueOf(book.format)
                require(book.chapters.isNotEmpty() && book.chapters.map { it.id }.distinct().size == book.chapters.size)
                require(book.chapters.map { it.order }.sorted() == book.chapters.indices.toList()) { "Invalid chapter order" }
                val chapters = book.chapters.associateBy { it.id }
                book.chapters.forEach { chapter ->
                    require(chapter.id > 0 && chapter.charCount >= 0 && chapter.bytes in 0..MAX_ENTRY_BYTES)
                    val file = safeFile(root, chapter.path)
                    require(file.isFile && file.length() == chapter.bytes) { "Missing or truncated chapter" }
                    require(sha256(file, checkCancellation) == chapter.sha256) { "Chapter checksum mismatch" }
                    var chars = 0L
                    file.reader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)
                        while (true) { checkCancellation(); val n = reader.read(buffer); if (n < 0) break; chars += n }
                    }
                    require(chars == chapter.charCount.toLong()) { "UTF-16 length mismatch" }
                }
                fun anchor(chapterId: Long, offset: Int) {
                    val chapter = chapters[chapterId] ?: error("Missing anchor chapter")
                    require(offset in 0..chapter.charCount) { "Invalid UTF-16 anchor" }
                }
                book.progress?.let { anchor(it.chapterId, it.charOffset) }
                require(book.bookmarks.map { it.id }.distinct().size == book.bookmarks.size)
                book.bookmarks.forEach { anchor(it.chapterId, it.charOffset) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: IllegalArgumentException) { invalid[book.id] = error.message ?: "Invalid book" }
              catch (error: IllegalStateException) { invalid[book.id] = error.message ?: "Invalid reference" }
        }
        return invalid
    }

    private fun validateSettings(value: ReaderPreferences) {
        // Wider than today's sliders to retain older valid preferences, but never permit
        // unbounded/NaN layout work supplied by an external archive.
        fun checkLayout(font: Float, weight: Int, line: Float, paragraph: Float, indent: Float, horizontal: Float, top: Float, bottom: Float) {
            require(listOf(font, line, paragraph, indent, horizontal, top, bottom).all { it.isFinite() }) { "Non-finite layout preference" }
            require(font in 8f..72f && weight in 100..1000 && line in .8f..4f && paragraph in 0f..128f && indent in 0f..8f &&
                horizontal in 0f..256f && top in 0f..256f && bottom in 0f..256f) { "Layout preference out of range" }
        }
        checkLayout(value.fontSizeSp, value.fontWeight, value.lineSpacingMultiplier, value.paragraphSpacingDp,
            value.firstLineIndentEm, value.horizontalPaddingDp, value.verticalPaddingTopDp, value.verticalPaddingBottomDp)
        value.lastCustomLayout?.let { checkLayout(it.fontSizeSp, it.fontWeight, it.lineSpacingMultiplier, it.paragraphSpacingDp,
            it.firstLineIndentEm, it.horizontalPaddingDp, it.verticalPaddingTopDp, it.verticalPaddingBottomDp) }
        require(value.brightness.isFinite() && (value.brightness == -1f || value.brightness in 0f..1f))
        require(value.autoReadIntervalSeconds in 3..60 && value.libraryTaglineIndex >= 0)
    }

    fun sha256(file: File, checkCancellation: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { checkCancellation(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
