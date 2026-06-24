package com.lightreader.app.core.reader

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageSlice
import com.lightreader.app.core.model.ReaderPreferences

interface PaginationEngine {
    fun paginate(
        text: String,
        widthPx: Int,
        heightPx: Int,
        scaledDensity: Float,
        preferences: ReaderPreferences,
    ): List<PageSlice>
}

class StaticLayoutPaginationEngine : PaginationEngine {
    @SuppressLint("InlinedApi")
    override fun paginate(
        text: String,
        widthPx: Int,
        heightPx: Int,
        scaledDensity: Float,
        preferences: ReaderPreferences,
    ): List<PageSlice> {
        if (text.isEmpty()) return listOf(PageSlice(0, 0, ""))
        require(widthPx > 0 && heightPx > 0)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = preferences.fontSizeSp * scaledDensity
            typeface = Typeface.create(
                when (preferences.fontFamily) {
                    FontFamilyOption.SANS -> Typeface.SANS_SERIF
                    FontFamilyOption.SERIF -> Typeface.SERIF
                    FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
                },
                if (preferences.fontWeight >= 500) Typeface.BOLD else Typeface.NORMAL,
            )
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(
                preferences.paragraphSpacingSp * scaledDensity * 0.15f,
                preferences.lineSpacingMultiplier,
            )
            // These are compile-time integer constants accepted by StaticLayout on API 26+.
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .apply {
                if (preferences.justified) setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
            }
            .build()

        val pages = ArrayList<PageSlice>()
        var firstLine = 0
        var start = 0
        while (firstLine < layout.lineCount && start < text.length) {
            val pageTop = layout.getLineTop(firstLine)
            val pageBottom = pageTop + heightPx
            var lastLine = layout.getLineForVertical(pageBottom).coerceAtMost(layout.lineCount - 1)
            while (lastLine > firstLine && layout.getLineBottom(lastLine) > pageBottom) lastLine--
            if (lastLine < firstLine) lastLine = firstLine
            var end = layout.getLineEnd(lastLine)
            if (end <= start) end = (start + 1).coerceAtMost(text.length)
            pages += PageSlice(start, end, text.substring(start, end))
            start = end
            firstLine = lastLine + 1
        }
        return pages
    }
}
