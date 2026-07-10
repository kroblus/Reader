package com.lightreader.app.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtTextCleanerTest {
    private val cleaner = TxtTextCleaner()

    @Test
    fun removesOnlyStandaloneNoiseLines() {
        assertNull(cleaner.clean("https://example.com/latest"))
        assertNull(cleaner.clean("请记住本站最新网址"))
        assertEquals("他在 https://example.com 的旧址前停下。", cleaner.clean("他在 https://example.com 的旧址前停下。"))
        assertEquals("正文内容不会被改写。", cleaner.clean("正文内容不会被改写。"))
    }
}
