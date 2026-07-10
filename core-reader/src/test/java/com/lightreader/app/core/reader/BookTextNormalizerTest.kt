package com.lightreader.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTextNormalizerTest {
    private val normalizer = BookTextNormalizer()

    @Test
    fun removesBomTabsAndMixedIndentationWithoutRenderingBlankLines() {
        val paragraphs = normalizer.normalize("\uFEFF\t　　第一章 入山  \r\n\r\n\n  \t山\t中修行　\n　　第二段　")
        assertEquals(listOf("第一章 入山", "山中修行", "第二段"), paragraphs.map { it.text })
        assertTrue(paragraphs.none { it.text.startsWith(' ') || it.text.startsWith('　') })
    }

    @Test
    fun preservesSourceOffsetsAfterLeadingIndentIsRemoved() {
        val source = "　　正文"
        val paragraph = normalizer.normalize(source).single()
        assertEquals("正文", paragraph.text)
        assertEquals(2, paragraph.sourceStart)
        assertEquals(source.length, paragraph.sourceEnd)
    }

    @Test
    fun emptyAndWhitespaceOnlyTextAreSafe() {
        assertTrue(normalizer.normalize("").isEmpty())
        assertTrue(normalizer.normalize("\t　 \r\n\n").isEmpty())
    }
}
