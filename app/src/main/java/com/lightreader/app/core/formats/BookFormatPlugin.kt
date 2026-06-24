package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import com.lightreader.app.core.model.BookFormat
import java.io.File

data class ImportedChapter(
    val title: String,
    val file: File,
    val charCount: Int,
)

data class ImportResult(
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    val chapters: List<ImportedChapter>,
)

interface BookFormatPlugin {
    fun supports(displayName: String, mimeType: String?): Boolean
    suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
    ): ImportResult
}

internal fun safeBaseName(name: String): String =
    name.substringBeforeLast('.').trim().ifBlank { "未命名小说" }
