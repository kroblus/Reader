package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelSourceRegistryTest {
    @Test
    fun dispatchesPersistedSourceIdBeforeFallingBackToUrlMatching() = runBlocking {
        val registry = NovelSourceRegistry(listOf(FakeAdapter("alpha"), FakeAdapter("beta")))

        assertEquals(
            "alpha:https://other.example/chapter",
            registry.chapterText("alpha", "1", "https://other.example/chapter", ExtractionPlan(), "Chapter"),
        )
        assertEquals(
            "beta:https://beta.example/chapter",
            registry.chapterText("https://beta.example/chapter", ExtractionPlan(), "Chapter"),
        )
    }

    @Test
    fun previewAlwaysPersistsTheSelectedAdapterIdentityAndVersion() = runBlocking {
        val registry = NovelSourceRegistry(listOf(FakeAdapter("alpha", "2026.07")))

        val preview = registry.preview("https://alpha.example/book")

        assertEquals("alpha", preview.sourceId)
        assertEquals("2026.07", preview.sourceVersion)
    }

    private class FakeAdapter(
        override val id: String,
        override val version: String = "1",
    ) : NovelSourceAdapter {
        override fun canHandle(url: String): Boolean = url.contains("$id.example")

        override suspend fun preview(url: String): WebBookPreview = WebBookPreview(
            title = "Test book",
            author = null,
            description = null,
            sourceUrl = url,
            finalUrl = url,
            chapters = emptyList(),
            sample = "",
            extractionPlan = ExtractionPlan(),
            sourceId = "wrong-id",
            sourceVersion = "wrong-version",
        )

        override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String = "$id:$url"
    }
}
