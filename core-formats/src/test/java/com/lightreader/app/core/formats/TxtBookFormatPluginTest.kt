package com.lightreader.app.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TxtBookFormatPluginTest {
    @Test
    fun detectsUtf8AndBomCharsets() {
        assertEquals(StandardCharsets.UTF_8, detect("修仙第一章".toByteArray(StandardCharsets.UTF_8)))
        assertEquals(StandardCharsets.UTF_8, detect(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x41)))
        assertEquals(StandardCharsets.UTF_16LE, detect(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00)))
        assertEquals(StandardCharsets.UTF_16BE, detect(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x41)))
        assertEquals(StandardCharsets.UTF_16LE, detect("第一章 入山".toByteArray(StandardCharsets.UTF_16LE)))
        assertEquals(StandardCharsets.UTF_16BE, detect("第一章 入山".toByteArray(StandardCharsets.UTF_16BE)))
    }

    @Test
    fun fallsBackToGb18030ForChineseLegacyText() {
        val bytes = "第一章 入山".toByteArray(Charset.forName("GB18030"))
        assertEquals(Charset.forName("GB18030"), detect(bytes))
    }

    @Test
    fun detectsLegacyChineseAfterLongAsciiPrefix() {
        val text = "a".repeat(10_000) + "第一章 入山，山中修行自此开始。".repeat(200)
        assertEquals(Charset.forName("GB18030"), detect(text.toByteArray(Charset.forName("GB18030"))))
    }

    @Test
    fun detectsBig5AndDefaultsAsciiToUtf8() {
        val traditional = "第一章 入山，山中修行自此開始。".repeat(200)
        assertEquals(Charset.forName("Big5"), detect(traditional.toByteArray(Charset.forName("Big5"))))
        assertEquals(StandardCharsets.UTF_8, detect("plain ascii text".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun unknownHighBytesUseGb18030Fallback() {
        assertEquals(Charset.forName("GB18030"), detect(byteArrayOf(0x81.toByte())))
    }

    @Test
    fun chapterRuleCoversCommonChineseHeadingsWithoutMatchingBodyText() {
        assertTrue(TxtBookFormatPlugin.CHAPTER_PATTERN.matches("第一章 入山"))
        assertTrue(TxtBookFormatPlugin.CHAPTER_PATTERN.matches("第十二回　风云再起"))
        assertTrue(TxtBookFormatPlugin.CHAPTER_PATTERN.matches("番外 一"))
        assertFalse(TxtBookFormatPlugin.CHAPTER_PATTERN.matches("他说第一章的故事并没有结束。"))
    }

    private fun detect(bytes: ByteArray) = TxtBookFormatPlugin.detectCharset(ByteArrayInputStream(bytes))
}
