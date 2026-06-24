package com.lightreader.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.reader.palette

@Composable
fun ReaderPageCanvas(
    page: ReaderPage,
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
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.foreground.toInt()
        textSize = layoutPreferences.fontSizeSp * density.density * density.fontScale
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
    val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.secondary.toInt()
        textSize = 12f * density.density * density.fontScale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val horizontalPaddingPx = layoutPreferences.horizontalPaddingDp * density.density

    Canvas(
        modifier.semantics {
            contentDescription = page.text.take(240).ifBlank { "空白页面" }
        },
    ) {
        drawRect(Color(palette.background))
        drawIntoCanvas { canvas ->
            page.lines.forEach { line ->
                val x = line.xOffsetPx
                if (layoutPreferences.justified && !line.isLastLineOfParagraph && line.text.length > 1) {
                    val targetWidth = line.availableWidthPx
                    val gap = ((targetWidth - line.widthPx) / (line.text.length - 1)).coerceAtLeast(0f)
                    var cursorX = x
                    line.text.forEach { character ->
                        val value = character.toString()
                        canvas.nativeCanvas.drawText(value, cursorX, line.baselinePx, textPaint)
                        cursorX += textPaint.measureText(value) + gap
                    }
                } else {
                    canvas.nativeCanvas.drawText(line.text, x, line.baselinePx, textPaint)
                }
            }

            if (displayPreferences.showHeader) {
                val maxWidth = size.width - horizontalPaddingPx * 2f
                val title = TextUtils.ellipsize(page.chapterTitle, secondaryPaint, maxWidth, TextUtils.TruncateAt.END).toString()
                canvas.nativeCanvas.drawText(
                    title,
                    horizontalPaddingPx,
                    safeTopPx + 22f * density.density,
                    secondaryPaint,
                )
            }

            if (displayPreferences.showStatus) {
                val footerY = size.height - safeBottomPx - 16f * density.density
                val progressText = "%.1f%%".format(overallProgress.coerceIn(0f, 1f) * 100f)
                canvas.nativeCanvas.drawText(progressText, horizontalPaddingPx, footerY, secondaryPaint)
                val chapterPageText = "${page.pageIndex + 1}/${pageCount.coerceAtLeast(1)}"
                val centerX = (size.width - secondaryPaint.measureText(chapterPageText)) / 2f
                canvas.nativeCanvas.drawText(chapterPageText, centerX, footerY, secondaryPaint)
                val timeX = size.width - horizontalPaddingPx - secondaryPaint.measureText(currentTime)
                canvas.nativeCanvas.drawText(currentTime, timeX, footerY, secondaryPaint)
            }

            if (displayPreferences.showRightProgressBar) {
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.secondary.toInt(); alpha = 150 }
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
