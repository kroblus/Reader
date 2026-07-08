package com.lightreader.app.ui

internal fun nextTaglineIndex(previousIndex: Int, taglineCount: Int, seed: Long = System.nanoTime()): Int {
    if (taglineCount <= 1) return 0
    val normalizedPrevious = previousIndex.floorMod(taglineCount)
    val step = ((seed and Long.MAX_VALUE) % (taglineCount - 1)).toInt() + 1
    return (normalizedPrevious + step) % taglineCount
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder >= 0) remainder else remainder + divisor
}
