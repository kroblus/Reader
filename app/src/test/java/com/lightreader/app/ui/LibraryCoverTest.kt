package com.lightreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryCoverTest {
    @Test
    fun generatedCorpusNamesBecomeReadableCoverTitles() {
        val title = "multi_page_sample_终章长夜.txt".coverDisplayTitle()
        assertEquals("终章长夜", title)
        assertFalse(title.contains("multi", ignoreCase = true))
    }

    @Test
    fun englishCoverUsesAtMostFourWords() {
        assertEquals("The Long Road Home", "The Long Road Home Again Forever.epub".coverDisplayTitle())
    }
}
