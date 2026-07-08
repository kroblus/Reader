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
    val sourceUrl: String?,
)

data class ReadingProgress(
    val bookId: String,
    val chapterId: Long,
    val charOffset: Int,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterTitle: String,
    val styleHash: Int,
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
    val charOffset: Int = 0,
)

enum class ReaderTheme { EYE_CARE, SEPIA, LIGHT_GRAY, WARM_BROWN, FROST_BLUE, SAKURA_PINK, NIGHT, CUSTOM }
enum class AppSkin { MINT, OCEAN, APRICOT, SAKURA }
enum class PageTurnMode { NONE, HORIZONTAL, SLIDE, VERTICAL, SIMULATION }
enum class FontFamilyOption { SYSTEM, SANS, SERIF, MONOSPACE }
enum class ReaderLayoutPreset { COMFORT, COMPACT, IMMERSIVE, CUSTOM }

data class ReaderPreferences(
    val appSkin: AppSkin = AppSkin.MINT,
    val layoutPreset: ReaderLayoutPreset = ReaderLayoutPreset.COMFORT,
    val fontSizeSp: Float = 17f,
    val fontWeight: Int = 400,
    val lineSpacingMultiplier: Float = 1.75f,
    val paragraphSpacingDp: Float = 10f,
    val firstLineIndent: Boolean = true,
    val firstLineIndentEm: Float = 2f,
    val horizontalPaddingDp: Float = 20f,
    val verticalPaddingTopDp: Float = 46f,
    val verticalPaddingBottomDp: Float = 46f,
    val justified: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.EYE_CARE,
    val lastNonNightTheme: ReaderTheme = ReaderTheme.EYE_CARE,
    val customBackground: Long = 0xFFB8C9A7,
    val customForeground: Long = 0xFF26301F,
    val customSecondary: Long = 0xFF6F8063,
    val brightness: Float = -1f,
    val keepScreenOn: Boolean = false,
    val lockPortrait: Boolean = true,
    val showStatus: Boolean = true,
    val showHeader: Boolean = true,
    val showRightProgressBar: Boolean = false,
    val minimalMode: Boolean = false,
    val autoReadIntervalSeconds: Int = 8,
    val volumeKeys: Boolean = true,
    val fontFamily: FontFamilyOption = FontFamilyOption.SERIF,
    val pageTurnMode: PageTurnMode = PageTurnMode.HORIZONTAL,
    val fullScreenTapNext: Boolean = false,
)

enum class ParagraphType { NORMAL, TITLE }

data class BookParagraph(
    val text: String,
    val type: ParagraphType = ParagraphType.NORMAL,
    val sourceOffsets: IntArray = IntArray(text.length) { it },
) {
    val sourceStart: Int get() = sourceOffsets.firstOrNull() ?: 0
    val sourceEnd: Int get() = (sourceOffsets.lastOrNull()?.plus(1)) ?: sourceStart

    fun sourceOffset(localIndex: Int): Int = when {
        sourceOffsets.isEmpty() -> sourceStart
        localIndex <= 0 -> sourceOffsets.first()
        localIndex >= sourceOffsets.size -> sourceOffsets.last() + 1
        else -> sourceOffsets[localIndex]
    }

    override fun equals(other: Any?): Boolean = other is BookParagraph &&
        text == other.text && type == other.type && sourceOffsets.contentEquals(other.sourceOffsets)

    override fun hashCode(): Int = 31 * (31 * text.hashCode() + type.hashCode()) + sourceOffsets.contentHashCode()
}

data class ReaderPalette(
    val background: Long,
    val foreground: Long,
    val secondary: Long,
    val overlay: Long,
)

data class ReaderStyle(
    val fontSizeSp: Float,
    val fontWeight: Int,
    val lineHeightMultiplier: Float,
    val paragraphSpacingDp: Float,
    val firstLineIndentEm: Float,
    val horizontalPaddingDp: Float,
    val verticalPaddingTopDp: Float,
    val verticalPaddingBottomDp: Float,
    val justified: Boolean,
    val fontFamily: FontFamilyOption,
    val palette: ReaderPalette,
    val showHeader: Boolean,
    val showFooter: Boolean,
    val showRightProgressBar: Boolean,
    val maxContentWidthDp: Float = 640f,
) {
    fun layoutFingerprint(): Int = listOf(
        fontSizeSp, fontWeight, lineHeightMultiplier, paragraphSpacingDp,
        firstLineIndentEm, horizontalPaddingDp, verticalPaddingTopDp,
        verticalPaddingBottomDp, justified, fontFamily,
        maxContentWidthDp,
    ).hashCode()
}

data class ReaderViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val scaledDensity: Float,
    val safeTopPx: Int = 0,
    val safeBottomPx: Int = 0,
)

data class ReaderLine(
    val text: String,
    val paragraphIndex: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
    val xOffsetPx: Float,
    val availableWidthPx: Float,
    val baselinePx: Float,
    val widthPx: Float,
    val lineHeightPx: Float,
    val isFirstLineOfParagraph: Boolean,
    val isLastLineOfParagraph: Boolean,
    val isChapterTitle: Boolean = false,
)

data class ReaderPage(
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterTitle: String,
    val lines: List<ReaderLine>,
    val startOffset: Int,
    val endOffset: Int,
    val progressInChapter: Float,
) {
    val text: String get() = lines.joinToString("\n", transform = ReaderLine::text)
}

data class ReaderLayoutResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pages: List<ReaderPage>,
    val viewport: ReaderViewport,
    val layoutFingerprint: Int,
)

data class WebChapter(val title: String, val url: String)

data class WebBookPreview(
    val title: String,
    val author: String?,
    val description: String?,
    val sourceUrl: String,
    val finalUrl: String,
    val chapters: List<WebChapter>,
    val sample: String,
    val extractionPlan: ExtractionPlan,
    val parseWarnings: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ExtractionPlan(
    val titleSelector: String = "",
    val chapterLinkSelector: String = "",
    val contentSelector: String = "",
    val removeSelectors: List<String> = emptyList(),
)
