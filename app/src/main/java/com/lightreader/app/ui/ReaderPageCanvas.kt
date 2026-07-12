package com.lightreader.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.lightreader.app.R
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.reader.effectiveLayout
import com.lightreader.app.core.reader.palette
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.reader.UnicodeTextBoundary
import kotlin.math.roundToInt

@Composable
fun ReaderPageCanvas(
    page: ReaderPage,
    bookTitle: String,
    pageCount: Int,
    layoutPreferences: ReaderPreferences,
    displayPreferences: ReaderPreferences,
    overallProgress: Float,
    currentTime: String,
    safeTopPx: Int,
    safeBottomPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val palette = displayPreferences.palette()
    val layoutValues = layoutPreferences.effectiveLayout()
    val displayStyle = displayPreferences.toReaderStyle()
    val footerProgressText = stringResource(
        R.string.reader_footer_book_progress,
        (overallProgress.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100),
    )
    val textPaint = remember(layoutPreferences, density.density, density.fontScale) { TextPaint(Paint.ANTI_ALIAS_FLAG) }.apply {
        color = palette.foreground.toInt()
        textSize = layoutValues.fontSizeSp * density.density * density.fontScale
        typeface = Typeface.create(
            when (layoutPreferences.fontFamily) {
                FontFamilyOption.SYSTEM -> Typeface.DEFAULT
                FontFamilyOption.SANS -> Typeface.SANS_SERIF
                FontFamilyOption.SERIF -> Typeface.SERIF
                FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
            },
            if (layoutPreferences.fontWeight >= 500) Typeface.BOLD else Typeface.NORMAL,
        )
    }
    val secondaryPaint = remember(palette.secondary, density.density, density.fontScale) { TextPaint(Paint.ANTI_ALIAS_FLAG) }.apply {
        color = palette.secondary.toInt()
        textSize = 12f * density.density * density.fontScale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val titlePaint = remember(layoutPreferences, density.density, density.fontScale) { TextPaint(Paint.ANTI_ALIAS_FLAG) }.apply {
        color = (if (displayStyle.showHeader) palette.secondary else palette.foreground).toInt()
        textSize = layoutValues.fontSizeSp * (if (displayStyle.showHeader) 1.16f else 1.38f) * density.density * density.fontScale
        typeface = Typeface.create(
            when (layoutPreferences.fontFamily) {
                FontFamilyOption.SYSTEM -> Typeface.DEFAULT
                FontFamilyOption.SANS -> Typeface.SANS_SERIF
                FontFamilyOption.SERIF -> Typeface.SERIF
                FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
            },
            Typeface.BOLD,
        )
    }
    val horizontalPaddingPx = layoutValues.horizontalPaddingDp * density.density
    val blankPageDescription = stringResource(R.string.reader_blank_page)

    Canvas(
        modifier.semantics {
            val accessibleText = page.text.ifBlank { blankPageDescription }
            text = AnnotatedString(accessibleText)
            if (page.text.isBlank()) contentDescription = blankPageDescription
        },
    ) {
        drawRect(Color(palette.background))
        drawIntoCanvas { canvas ->
            page.lines.forEach { line ->
                val x = line.xOffsetPx
                val paint = if (line.isChapterTitle) titlePaint else textPaint
                val graphemeGaps = (UnicodeTextBoundary.graphemeCount(line.text) - 1).coerceAtLeast(0)
                if (!line.isChapterTitle && layoutPreferences.justified && !line.isLastLineOfParagraph && graphemeGaps > 0) {
                    val targetWidth = line.availableWidthPx
                    val gap = ((targetWidth - line.widthPx) / graphemeGaps).coerceAtLeast(0f)
                    val previousLetterSpacing = paint.letterSpacing
                    paint.letterSpacing = if (paint.textSize > 0f) gap / paint.textSize else 0f
                    canvas.nativeCanvas.drawText(line.text, x, line.baselinePx, paint)
                    paint.letterSpacing = previousLetterSpacing
                } else {
                    canvas.nativeCanvas.drawText(line.text, x, line.baselinePx, paint)
                }
            }

            if (displayStyle.showHeader) {
                val maxWidth = size.width - horizontalPaddingPx * 2f
                val title = TextUtils.ellipsize(readerHeaderTitle(page, bookTitle), secondaryPaint, maxWidth, TextUtils.TruncateAt.END).toString()
                canvas.nativeCanvas.drawText(
                    title,
                    horizontalPaddingPx,
                    safeTopPx + HEADER_BASELINE_DP * density.density,
                    secondaryPaint,
                )
            }

            if (displayStyle.showFooter) {
                val footerY = size.height - safeBottomPx - 16f * density.density
                canvas.nativeCanvas.drawText(footerProgressText, horizontalPaddingPx, footerY, secondaryPaint)
                val chapterPageText = "${page.pageIndex + 1}/${pageCount.coerceAtLeast(1)}"
                val centerX = (size.width - secondaryPaint.measureText(chapterPageText)) / 2f
                canvas.nativeCanvas.drawText(chapterPageText, centerX, footerY, secondaryPaint)
                val timeX = size.width - horizontalPaddingPx - secondaryPaint.measureText(currentTime)
                canvas.nativeCanvas.drawText(currentTime, timeX, footerY, secondaryPaint)
            }

            if (displayStyle.showRightProgressBar) {
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.secondary.toInt(); alpha = 82 }
                val width = 3.5f * density.density
                val top = safeTopPx + 8f * density.density
                val bottom = size.height - safeBottomPx - 8f * density.density
                val filledBottom = top + (bottom - top) * overallProgress.coerceIn(.01f, 1f)
                val right = size.width - 3f * density.density
                canvas.nativeCanvas.drawRoundRect(right - width, top, right, filledBottom, width, width, barPaint)
            }
        }
    }
}

internal fun readerHeaderTitle(page: ReaderPage, bookTitle: String): String =
    if (bookTitle.isNotBlank() && page.pageIndex == 0 && page.lines.any { it.isChapterTitle }) {
        bookTitle
    } else {
        page.chapterTitle
    }

private const val HEADER_BASELINE_DP = 14f
