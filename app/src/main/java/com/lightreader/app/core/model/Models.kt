package com.lightreader.app.core.model

enum class BookFormat { TXT, EPUB, WEB }

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val chapterCount: Int,
    val totalChars: Long,
    val addedAt: Long,
    val lastReadAt: Long?,
    val sourceUrl: String?,
)

data class Chapter(
    val id: Long,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val contentPath: String,
    val charCount: Int,
)

data class ReadingProgress(
    val bookId: String,
    val chapterId: Long,
    val charOffset: Int,
    val updatedAt: Long,
)

data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterId: Long,
    val charOffset: Int,
    val excerpt: String,
    val createdAt: Long,
)

data class SearchResult(
    val chapterId: Long,
    val chapterTitle: String,
    val excerpt: String,
)

enum class ReaderTheme { DAY, SEPIA, NIGHT, CUSTOM }
enum class PageAnimation { NONE, SLIDE, COVER }
enum class FontFamilyOption { SANS, SERIF, MONOSPACE }

data class ReaderPreferences(
    val fontSizeSp: Float = 20f,
    val fontWeight: Int = 400,
    val lineSpacingMultiplier: Float = 1.55f,
    val paragraphSpacingSp: Float = 8f,
    val firstLineIndent: Boolean = true,
    val horizontalPaddingDp: Float = 24f,
    val verticalPaddingDp: Float = 20f,
    val justified: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.DAY,
    val customBackground: Long = 0xFFF8F5EE,
    val customForeground: Long = 0xFF24211D,
    val brightness: Float = -1f,
    val keepScreenOn: Boolean = false,
    val lockPortrait: Boolean = true,
    val showStatus: Boolean = true,
    val volumeKeys: Boolean = true,
    val fontFamily: FontFamilyOption = FontFamilyOption.SERIF,
    val pageAnimation: PageAnimation = PageAnimation.SLIDE,
)

data class PageSlice(val start: Int, val endExclusive: Int, val text: String)

data class WebChapter(val title: String, val url: String)

data class WebBookPreview(
    val title: String,
    val sourceUrl: String,
    val chapters: List<WebChapter>,
    val sample: String,
    val extractionPlan: ExtractionPlan,
)

@kotlinx.serialization.Serializable
data class ExtractionPlan(
    val titleSelector: String = "",
    val chapterLinkSelector: String = "",
    val contentSelector: String = "",
    val removeSelectors: List<String> = emptyList(),
)
