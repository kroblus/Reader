package com.lightreader.app.core.backup

import com.lightreader.app.core.model.ReaderPreferences
import kotlinx.serialization.Serializable

/** Independent of the Room schema. Version 1 is the first published archive contract. */
@Serializable data class BackupManifest(
    val archiveVersion: Int = 1,
    val createdAt: Long,
    val books: List<BackupBook>,
    val settings: ReaderPreferences,
)

@Serializable data class BackupBook(
    val id: String, val title: String, val author: String?, val format: String,
    val addedAt: Long, val lastReadAt: Long?, val sourceUrl: String?, val fingerprint: String?,
    val chapters: List<BackupChapter>, val progress: BackupProgress?, val bookmarks: List<BackupBookmark>,
)

@Serializable data class BackupChapter(
    val id: Long, val order: Int, val title: String, val path: String,
    val charCount: Int, val sourceUrl: String?, val bytes: Long, val sha256: String,
)

@Serializable data class BackupProgress(
    val chapterId: Long, val charOffset: Int, val chapterIndex: Int, val pageIndex: Int,
    val chapterTitle: String, val styleHash: Int, val updatedAt: Long,
)

@Serializable data class BackupBookmark(
    val id: String, val chapterId: Long, val charOffset: Int, val excerpt: String, val createdAt: Long,
)

data class RestorePreview(
    val token: String,
    val manifest: BackupManifest,
    val duplicateBookIds: Set<String>,
    val invalidBooks: Map<String, String>,
) {
    val restorableBooks get() = manifest.books.filter { it.id !in duplicateBookIds && it.id !in invalidBooks }
}

data class RestoreResult(val restored: Int, val skipped: Int, val invalid: Int, val settingsRestored: Boolean, val settingsError: String? = null)

class BackupValidationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
