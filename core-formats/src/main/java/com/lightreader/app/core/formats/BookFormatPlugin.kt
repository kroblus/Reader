package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import com.lightreader.app.core.model.BookFormat
import java.io.File

data class ImportedChapter(
    val title: String,
    val file: File,
    val charCount: Int,
    val sourceUrl: String? = null,
)

data class ImportResult(
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    val chapters: List<ImportedChapter>,
)

/** Import behaviour chosen before source content is copied into private storage. */
data class BookImportOptions(
    val cleanTxtNoise: Boolean = true,
)

enum class ImportStage { INSPECTING, READING, PARSING, SAVING }

data class ImportProgress(
    val stage: ImportStage,
    val fraction: Float,
    val processed: Long = 0,
    val total: Long? = null,
) {
    val normalizedFraction: Float get() = fraction.coerceIn(0f, 1f)
}

typealias ImportProgressCallback = (ImportProgress) -> Unit

enum class BookImportFailure {
    FILE_UNREADABLE,
    EMPTY_CONTENT,
    ENCODING_QUALITY,
    UNSUPPORTED_FORMAT,
}

class BookImportException(
    val failure: BookImportFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

interface BookFormatPlugin {
    fun supports(displayName: String, mimeType: String?): Boolean
    suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
        options: BookImportOptions = BookImportOptions(),
        onProgress: ImportProgressCallback = {},
    ): ImportResult
}

internal fun safeBaseName(name: String): String =
    name.substringBeforeLast('.').trim().ifBlank { "未命名小说" }
