package com.lightreader.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Short, bounded UI motion. Compose applies the system MotionDurationScale, including zero. */
internal object UiMotion {
    const val DurationMillis = 200
    const val ColorDurationMillis = 180

    fun enter(offsetPx: Int) = fadeIn(tween(DurationMillis, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(DurationMillis, easing = FastOutSlowInEasing)) { offsetPx }

    fun exit(offsetPx: Int) = fadeOut(tween(DurationMillis, easing = FastOutSlowInEasing)) +
        slideOutVertically(tween(DurationMillis, easing = FastOutSlowInEasing)) { offsetPx }

    fun enterHorizontal(offsetPx: Int) = fadeIn(tween(DurationMillis, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(DurationMillis, easing = FastOutSlowInEasing)) { offsetPx }

    fun exitHorizontal(offsetPx: Int) = fadeOut(tween(DurationMillis, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(DurationMillis, easing = FastOutSlowInEasing)) { offsetPx }
}
