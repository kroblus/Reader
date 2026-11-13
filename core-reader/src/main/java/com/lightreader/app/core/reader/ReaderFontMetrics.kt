package com.lightreader.app.core.reader

import android.util.DisplayMetrics
import android.util.TypedValue
import com.lightreader.app.core.model.ReaderFontMetrics
import com.lightreader.app.core.model.ReaderViewport

const val READER_TITLE_SP_SCALE = 1.45f

/** Android 14+ applies a nonlinear curve: convert each target sp independently. */
fun readerFontMetrics(fontSizeSp: Float, metrics: DisplayMetrics): ReaderFontMetrics = ReaderFontMetrics(
    bodySp = fontSizeSp,
    bodyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, metrics),
    titlePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp * READER_TITLE_SP_SCALE, metrics),
    auxiliaryPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics),
)

/** Legacy/synthetic engine callers can supply a linear viewport. The application always
 * captures platform metrics before computing/cache lookup, including during live preview. */
fun ReaderViewport.metricsFor(fontSizeSp: Float): ReaderFontMetrics =
    fontMetrics?.takeIf { it.bodySp == fontSizeSp } ?: ReaderFontMetrics(
        fontSizeSp, fontSizeSp * scaledDensity,
        fontSizeSp * READER_TITLE_SP_SCALE * scaledDensity, 12f * scaledDensity,
    )
