package com.lightreader.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryBoundedCacheTest {
    @Test
    fun evictsTheLeastRecentlyUsedEntryByByteBudget() {
        val cache = MemoryBoundedCache<String, String>(maxBytes = 10) { it.length.toLong() }
        cache.put("first", "12345")
        cache.put("second", "12345")
        cache["first"]

        cache.put("third", "12345")

        assertEquals("12345", cache["first"])
        assertNull(cache["second"])
        assertEquals("12345", cache["third"])
        assertEquals(10, cache.byteSize)
    }

    @Test
    fun keepsOneOversizedEntryInsteadOfThrashingIt() {
        val cache = MemoryBoundedCache<String, String>(maxBytes = 4) { it.length.toLong() }

        cache.put("large", "123456")

        assertEquals("123456", cache["large"])
        assertEquals(1, cache.size)
        assertEquals(6, cache.byteSize)
    }
}
