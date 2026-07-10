package com.lightreader.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterParserTest {
    private val parser = ChapterParser()

    @Test
    fun recognizesCommonChineseAndEnglishHeadings() {
        listOf(
            "第1章 入山", "第一章 入山", "第001章 入山", "第1回 风云",
            "第一回 风云", "第1节 起始", "卷一 山河", "第一卷 山河",
            "正文 第1章 入山", "Chapter 1 Arrival", "番外 一",
        ).forEach { assertNotNull(it, parser.chapterTitle(it)) }
        assertNull(parser.chapterTitle("他说第一章的故事并没有结束。"))
    }

    @Test
    fun createsDefaultChapterWhenNoHeadingExists() {
        val chapter = parser.parse("山中修行\n天地玄黄").single()
        assertEquals("正文", chapter.title)
        assertEquals(listOf("山中修行", "天地玄黄"), chapter.paragraphs.map { it.text })
    }

    @Test
    fun keepsPrefaceAndSplitsChapterBodiesWithoutLosingText() {
        val chapters = parser.parse("前言内容\n第一章 入山\n正文一\n第二章 筑基\n正文二")
        assertEquals(listOf("正文", "第一章 入山", "第二章 筑基"), chapters.map { it.title })
        assertEquals(listOf("前言内容", "正文一", "正文二"), chapters.flatMap { it.paragraphs }.map { it.text })
    }
}
