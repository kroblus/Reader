package com.lightreader.app.core.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTextCleanerTest {
    private val cleaner = WebTextCleaner()

    @Test
    fun removesNavigationAdsAndDuplicateTitleWithoutRewritingBody() {
        val cleaned = cleaner.clean(
            """
            Chapter 1 Start

            Please remember this site latest address
            The hero kept the original sentence exactly as it appeared in the source.
            Next chapter


            Another paragraph stayed readable after blank lines were normalized.
            """.trimIndent(),
            "Chapter 1 Start",
        )

        assertFalse(cleaned.contains("Chapter 1 Start"))
        assertFalse(cleaned.contains("latest address", ignoreCase = true))
        assertFalse(cleaned.contains("Next chapter", ignoreCase = true))
        assertTrue(cleaned.contains("The hero kept the original sentence exactly as it appeared in the source."))
        assertTrue(cleaned.contains("Another paragraph stayed readable"))
    }

    @Test
    fun keepsLongBodyLineThatMentionsNextChapterAsStoryText() {
        val line = "The narrator said the next chapter of their life would begin only after the storm passed, and the sentence is clearly story text rather than a navigation link."
        val cleaned = cleaner.clean(line, "Chapter 2")

        assertTrue(cleaned.contains(line))
    }
}
