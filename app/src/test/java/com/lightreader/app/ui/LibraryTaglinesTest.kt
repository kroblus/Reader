package com.lightreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryTaglinesTest {
    @Test
    fun nextTaglineIndexDoesNotRepeatWhenThereAreMultipleTaglines() {
        repeat(30) { previous ->
            repeat(20) { seed ->
                val next = nextTaglineIndex(previous, taglineCount = 30, seed = seed.toLong())
                assertNotEquals(previous, next)
                assertEquals(true, next in 0 until 30)
            }
        }
    }

    @Test
    fun nextTaglineIndexHandlesSmallCounts() {
        assertEquals(0, nextTaglineIndex(previousIndex = 7, taglineCount = 0, seed = 1L))
        assertEquals(0, nextTaglineIndex(previousIndex = 7, taglineCount = 1, seed = 1L))
    }

    @Test
    fun nextTaglineIndexNormalizesOutOfRangePreviousIndex() {
        assertEquals(1, nextTaglineIndex(previousIndex = -1, taglineCount = 3, seed = 1L))
        assertEquals(0, nextTaglineIndex(previousIndex = 7, taglineCount = 3, seed = 1L))
    }
}
